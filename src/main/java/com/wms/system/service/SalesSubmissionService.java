package com.wms.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.sales.SalesOrderResponse;
import com.wms.system.dto.sales.UpdateSalesOrderRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.AllocationPolicy;
import com.wms.system.entity.enums.BackorderStatus;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesAllocationStatus;
import com.wms.system.entity.enums.SalesApprovalMode;
import com.wms.system.entity.enums.SalesLogisticsStatus;
import com.wms.system.entity.enums.SalesWarehouseStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Sales Submission Service (销售订单提交服务)
 *
 * V3.7 Architecture: Sales order submission, approval workflow, modification, and cancellation
 *
 * Core Responsibilities:
 * 1. Create sales order with risk control
 * 2. Approve/reject sales order (manager workflow)
 * 3. Update sales order (DRAFT/PENDING_APPROVAL only)
 * 4. Cancel sales order and release inventory
 * 5. Order number generation (SO + yyyyMMdd + sequence)
 * 6. Audit log management
 *
 * Risk Control Logic:
 * - If ANY item's unit_price < product.min_sales_price → Trigger approval
 * - If total_amount > approval_threshold → Trigger approval
 * - Otherwise → Auto-approve and allocate inventory
 *
 * Status Flow:
 * DRAFT → PENDING_APPROVAL → APPROVED → COMPLETED
 *                          ↘ REJECTED
 *
 * Technical Features:
 * - @Transactional: Ensures atomicity
 * - Error Key System: All exceptions use error keys
 * - Audit Log: JSON format tracking all operations
 * - ObjectMapper: JSON serialization/deserialization
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesSubmissionService {

    private static final String EXTERNAL_ORDER_DEDUP_CONSTRAINT =
        "uq_sales_orders_company_channel_external_order";

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final CustomerService customerService;
    private final ProductSkuRepository productSkuRepository;
    private final ProductSkuOperationalPolicy productSkuOperationalPolicy;
    private final SalesOrderPolicyService policyService;
    private final AllocationService allocationService;
    private final InventoryReservationService inventoryReservationService;
    private final SalesOrderShipmentRepository salesOrderShipmentRepository;
    private final BackorderLineRepository backorderLineRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final SalesOrderStatusProjectionService statusProjectionService;
    private final ObjectMapper objectMapper;

    /**
     * ⭐ Core Method: Create sales order with risk control
     *
     * Business Flow:
     * 1. Validate customer exists and is active
     * 2. Generate order number (SO + yyyyMMdd + sequence)
     * 3. Create SalesOrder entity with status = DRAFT
     * 4. Create SalesOrderItem entities for each item
     * 5. Calculate total amount
     * 6. Risk control check:
     *    - If ANY item's unit_price < product.min_sales_price → PENDING_APPROVAL
     *    - If total_amount > approval_threshold → PENDING_APPROVAL
     *    - Otherwise → APPROVED + allocate inventory
     * 7. Save order and items
     * 8. Return SalesOrderResponse
     *
     * @param request CreateSalesOrderRequest
     * @param applicantId Applicant user ID
     * @param applicantName Applicant user name
     * @return SalesOrderResponse
     * @throws BusinessException if customer not found, inactive, or product not found
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse createSalesOrder(
        CreateSalesOrderRequest request,
        Long applicantId,
        String applicantName
    ) {
        return createSalesOrderInternal(
            request,
            applicantId,
            applicantName,
            false,
            null,
            null,
            null
        );
    }

    /**
     * Creates a channel-recovered order in PENDING_APPROVAL without allocating
     * inventory. This restricted entry point is used by reconciliation repair:
     * the caller must already have matched an existing customer and existing
     * product SKU mappings.
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse createChannelOrderPendingApproval(
        CreateSalesOrderRequest request,
        Long applicantId,
        String applicantName,
        String channel,
        String externalOrderId,
        String externalOrderNo
    ) {
        salesOrderRepository.findByExternalOrderId(externalOrderId).ifPresent(existing -> {
            throw new BusinessException(
                ErrorKeys.SHOPIFY_ORDER_ALREADY_SYNCED,
                Map.of(
                    "externalOrderId", externalOrderId,
                    "salesOrderId", existing.getId()
                )
            );
        });

        return createSalesOrderInternal(
            request,
            applicantId,
            applicantName,
            true,
            channel,
            externalOrderId,
            externalOrderNo
        );
    }

    private SalesOrderResponse createSalesOrderInternal(
        CreateSalesOrderRequest request,
        Long applicantId,
        String applicantName,
        boolean forcePendingApproval,
        String channel,
        String externalOrderId,
        String externalOrderNo
    ) {
        log.info("🚀 Creating sales order: customerId={}, applicantId={}, applicantName={}, itemCount={}",
            request.getCustomerId(), applicantId, applicantName, request.getItems().size());

        // 1. Manual entry only accepts managed clients; channel recovery may use consumers.
        if (forcePendingApproval) {
            customerService.validateCustomerActive(request.getCustomerId());
        } else {
            customerService.validateManualOrderCustomer(request.getCustomerId());
        }

        // 2. Generate order number
        String orderNo = generateOrderNumber();
        log.info("📋 Generated order number: {}", orderNo);

        // 3. Create SalesOrder entity with status = DRAFT
        SalesOrder salesOrder = SalesOrder.builder()
            .orderNo(orderNo)
            .customerId(request.getCustomerId())
            .totalAmount(BigDecimal.ZERO)
            .commercialStatus(CommercialStatus.DRAFT)
            .allocationStatus(SalesAllocationStatus.UNALLOCATED)
            .warehouseStatus(SalesWarehouseStatus.NOT_RELEASED)
            .logisticsStatus(SalesLogisticsStatus.NOT_CREATED)
            .approvalModeSnapshot(policyService.approvalMode())
            .applicantId(applicantId)
            .applicantName(applicantName)
            .consigneeName(request.getConsigneeName())
            .consigneePhone(request.getConsigneePhone())
            .shipAddress1(request.getShipAddress1())
            .shipAddress2(request.getShipAddress2())
            .shipCity(request.getShipCity())
            .shipProvince(request.getShipProvince())
            .shipZip(request.getShipZip())
            .shipCountryCode(request.getShipCountryCode())
            .build();

        // P1-B2 渠道标记：请求带 channel 时覆盖默认值 MANUAL（如微信单传 WECHAT）；
        // 不能在 builder 里传 null，否则会覆盖 @Builder.Default 的 MANUAL
        if (org.springframework.util.StringUtils.hasText(request.getChannel())) {
            salesOrder.setChannel(request.getChannel().trim().toUpperCase());
        }
        if (org.springframework.util.StringUtils.hasText(channel)) {
            salesOrder.setChannel(channel.trim().toUpperCase());
        }
        salesOrder.setExternalOrderId(externalOrderId);
        salesOrder.setExternalOrderNo(externalOrderNo);

        // Save order first to get ID
        salesOrder = saveNewSalesOrder(salesOrder, externalOrderId);
        log.info("✅ Sales order created: orderId={}, orderNo={}", salesOrder.getId(), salesOrder.getOrderNo());

        // 4. Create SalesOrderItem entities for each item
        List<SalesOrderItem> items = new ArrayList<>();
        for (CreateSalesOrderRequest.SalesOrderItemData itemData : request.getItems()) {
            // Validate product exists
            ProductSku product = productSkuRepository.findById(itemData.getProductSkuId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                    Map.of("productSkuId", itemData.getProductSkuId())
                ));
            productSkuOperationalPolicy.requireEnabled(
                product,
                ProductSkuOperationalPolicy.SALES_ORDER
            );

            // Calculate subtotal
            BigDecimal subtotal = itemData.getUnitPrice()
                .multiply(BigDecimal.valueOf(itemData.getQuantity()));

            // Create item
            SalesOrderItem item = SalesOrderItem.builder()
                .salesOrderId(salesOrder.getId())
                .productSkuId(itemData.getProductSkuId())
                .quantity(itemData.getQuantity())
                .requestedQty(itemData.getQuantity())
                .allocatedQty(0)
                .shippedQty(0)
                .backorderQty(0)
                .cancelledQty(0)
                .unitPrice(itemData.getUnitPrice())
                .productPriceSnapshot(product.getUnitPrice())  // V4.2: 固化下单时的商品标价快照
                .subtotal(subtotal)
                .rejectNearExpiry(itemData.getRejectNearExpiry() != null ? itemData.getRejectNearExpiry() : false)
                .specifiedBatchIds(parseSpecifiedBatchIds(itemData.getSpecifiedBatchIds()))
                .remark(itemData.getRemark())
                .build();

            items.add(item);
        }

        // Save all items
        items = salesOrderItemRepository.saveAll(items);
        log.info("✅ Sales order items created: count={}", items.size());

        // 5. Calculate total amount
        BigDecimal totalAmount = calculateTotalAmount(items);
        salesOrder.setTotalAmount(totalAmount);
        log.info("💰 Total amount calculated: {}", totalAmount);

        // 6. Keep a real draft or submit through the tenant review policy.
        boolean saveAsDraft = Boolean.TRUE.equals(request.getSaveAsDraft()) && !forcePendingApproval;
        if (!saveAsDraft) {
            applySubmissionPolicy(salesOrder, items, forcePendingApproval);
            recordStatusChange(salesOrder, applicantId, applicantName, salesOrder.getReviewReason());
        }

        // 7. Save order with updated status
        salesOrder = salesOrderRepository.save(salesOrder);

        // 8. Add audit log
        addAuditLog(salesOrder, "CREATE", applicantName, "创建销售订单");

        // 9. If auto-approved, allocate inventory
        if (salesOrder.getCommercialStatus() == CommercialStatus.APPROVED) {
            log.info("🎯 Auto-approved, allocating inventory: orderId={}", salesOrder.getId());
            allocationService.allocateInventory(salesOrder.getId());
        }

        log.info("Sales order creation completed: orderId={}, orderNo={}, commercialStatus={}",
            salesOrder.getId(), salesOrder.getOrderNo(), salesOrder.getCommercialStatus());

        return convertToResponse(salesOrder);
    }

    private SalesOrder saveNewSalesOrder(SalesOrder salesOrder, String externalOrderId) {
        if (!StringUtils.hasText(externalOrderId)) {
            return salesOrderRepository.save(salesOrder);
        }

        try {
            return salesOrderRepository.saveAndFlush(salesOrder);
        } catch (DataIntegrityViolationException ex) {
            if (!isExternalOrderDedupViolation(ex)) {
                throw ex;
            }

            throw new BusinessException(
                ErrorKeys.SHOPIFY_ORDER_ALREADY_SYNCED,
                Map.of(
                    "channel", salesOrder.getChannel(),
                    "externalOrderId", externalOrderId
                )
            );
        }
    }

    private boolean isExternalOrderDedupViolation(DataIntegrityViolationException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String rootCauseMessage = ex.getRootCause() == null ? "" : ex.getRootCause().getMessage();
        return (message + " " + rootCauseMessage)
            .toLowerCase()
            .contains(EXTERNAL_ORDER_DEDUP_CONSTRAINT);
    }

    /**
     * Approve sales order (manager workflow)
     *
     * Business Flow:
     * 1. Validate order status = PENDING_APPROVAL
     * 2. Update reviewed_by, reviewed_at, review_comment
     * 3. Set commercial status = APPROVED
     * 4. Call AllocationService.allocateInventory()
     * 5. Add audit log entry
     * 6. Return SalesOrderResponse
     *
     * @param orderId Sales order ID
     * @param managerId Manager user ID
     * @param managerName Manager user name
     * @param comment Approval comment
     * @return SalesOrderResponse
     * @throws BusinessException if order not found or invalid status
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse approveSalesOrder(
        Long orderId,
        Long managerId,
        String managerName,
        String comment,
        AllocationPolicy allocationPolicy,
        LocalDate requestedShipDate,
        LocalDate promisedShipDate
    ) {
        log.info("✅ Approving sales order: orderId={}, managerId={}, managerName={}",
            orderId, managerId, managerName);

        // 1. Validate order exists
        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // 2. Validate order status = PENDING_APPROVAL
        if (!salesOrder.canApprove()) {
            log.error("Sales order cannot be approved: orderId={}, currentStatus={}",
                orderId, salesOrder.getCommercialStatus());
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_INVALID_STATUS,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getCommercialStatus().name(),
                    "requiredStatus", "PENDING_APPROVAL"
                )
            );
        }

        // 3. Update approval info
        salesOrder.setReviewedBy(managerId);
        salesOrder.setReviewedAt(LocalDateTime.now());
        salesOrder.setReviewComment(comment);
        salesOrder.setCommercialStatus(CommercialStatus.APPROVED);
        recordStatusChange(salesOrder, managerId, managerName, comment);
        if (allocationPolicy != null) {
            salesOrder.setAllocationPolicy(allocationPolicy);
        }
        if (requestedShipDate != null) {
            salesOrder.setRequestedShipDate(requestedShipDate);
        }
        if (promisedShipDate != null) {
            salesOrder.setPromisedShipDate(promisedShipDate);
        }
        salesOrder.setApprovedAt(LocalDateTime.now());  // V4.2: 记录经理审批通过时间

        // 4. Save order
        salesOrder = salesOrderRepository.save(salesOrder);

        // 5. Add audit log
        addAuditLog(salesOrder, "APPROVE", managerName, "审批通过: " + (comment != null ? comment : ""));

        // 6. Allocate inventory
        log.info("🎯 Allocating inventory after approval: orderId={}", orderId);
        allocationService.allocateInventory(orderId);
        salesOrder = statusProjectionService.refresh(orderId);

        log.info("✅ Sales order approved successfully: orderId={}, orderNo={}",
            orderId, salesOrder.getOrderNo());

        return convertToResponse(salesOrder);
    }

    /**
     * Reject sales order (manager workflow)
     *
     * Business Flow:
     * 1. Validate order status = PENDING_APPROVAL
     * 2. Update reviewed_by, reviewed_at, review_comment
     * 3. Set status = REJECTED
     * 4. Add audit log entry
     * 5. Return SalesOrderResponse
     *
     * @param orderId Sales order ID
     * @param managerId Manager user ID
     * @param managerName Manager user name
     * @param reason Rejection reason
     * @return SalesOrderResponse
     * @throws BusinessException if order not found or invalid status
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse rejectSalesOrder(
        Long orderId,
        Long managerId,
        String managerName,
        String reason
    ) {
        log.info("❌ Rejecting sales order: orderId={}, managerId={}, managerName={}, reason={}",
            orderId, managerId, managerName, reason);

        // 1. Validate order exists
        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // 2. Validate order status = PENDING_APPROVAL
        if (!salesOrder.canApprove()) {
            log.error("Sales order cannot be rejected: orderId={}, currentStatus={}",
                orderId, salesOrder.getCommercialStatus());
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_INVALID_STATUS,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getCommercialStatus().name(),
                    "requiredStatus", "PENDING_APPROVAL"
                )
            );
        }

        // 3. Update rejection info
        salesOrder.setReviewedBy(managerId);
        salesOrder.setReviewedAt(LocalDateTime.now());
        salesOrder.setReviewComment(reason);
        salesOrder.setCommercialStatus(CommercialStatus.REJECTED);
        salesOrder.setAllocationStatus(SalesAllocationStatus.UNALLOCATED);
        salesOrder.setWarehouseStatus(SalesWarehouseStatus.NOT_RELEASED);
        recordStatusChange(salesOrder, managerId, managerName, reason);

        // 4. Save order
        salesOrder = salesOrderRepository.save(salesOrder);

        // 5. Add audit log
        addAuditLog(salesOrder, "REJECT", managerName, "审批拒绝: " + reason);

        log.info("✅ Sales order rejected successfully: orderId={}, orderNo={}",
            orderId, salesOrder.getOrderNo());

        return convertToResponse(salesOrder);
    }

    /**
     * Update sales order (DRAFT/PENDING_APPROVAL only)
     *
     * Business Flow:
     * 1. Validate order status = DRAFT or PENDING_APPROVAL
     * 2. Delete existing items
     * 3. Create new items from request
     * 4. Recalculate total amount
     * 5. Re-run risk control check
     * 6. If a pending order becomes APPROVED, allocate inventory once
     * 7. Add audit log entry
     * 8. Return SalesOrderResponse
     *
     * @param orderId Sales order ID
     * @param request UpdateSalesOrderRequest
     * @param operatorId Operator user ID
     * @return SalesOrderResponse
     * @throws BusinessException if order not found or cannot be modified
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse updateSalesOrder(
        Long orderId,
        UpdateSalesOrderRequest request,
        Long operatorId,
        String operatorName
    ) {
        log.info("🔄 Updating sales order: orderId={}, operatorId={}, itemCount={}",
            orderId, operatorId, request.getItems().size());

        // 1. Validate order exists
        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // 2. Validate order can be modified
        if (!salesOrder.canModify()) {
            log.error("Sales order cannot be modified: orderId={}, currentStatus={}",
                orderId, salesOrder.getCommercialStatus());
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_MODIFY,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getCommercialStatus().name(),
                    "allowedStatuses", "DRAFT, PENDING_APPROVAL"
                )
            );
        }

        // 3. Update customer if provided
        if (request.getCustomerId() != null) {
            customerService.validateManualOrderCustomer(request.getCustomerId());
            salesOrder.setCustomerId(request.getCustomerId());
        }

        // 4. Delete existing items
        salesOrderItemRepository.deleteBySalesOrderId(orderId);
        log.info("🗑️ Deleted existing items: orderId={}", orderId);

        // 5. Create new items from request
        List<SalesOrderItem> items = new ArrayList<>();
        for (UpdateSalesOrderRequest.SalesOrderItemData itemData : request.getItems()) {
            // Validate product exists
            ProductSku product = productSkuRepository.findById(itemData.getProductSkuId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                    Map.of("productSkuId", itemData.getProductSkuId())
                ));
            productSkuOperationalPolicy.requireEnabled(
                product,
                ProductSkuOperationalPolicy.SALES_ORDER
            );

            // Calculate subtotal
            BigDecimal subtotal = itemData.getUnitPrice()
                .multiply(BigDecimal.valueOf(itemData.getQuantity()));

            // Create item
            SalesOrderItem item = SalesOrderItem.builder()
                .salesOrderId(orderId)
                .productSkuId(itemData.getProductSkuId())
                .quantity(itemData.getQuantity())
                .requestedQty(itemData.getQuantity())
                .allocatedQty(0)
                .shippedQty(0)
                .backorderQty(0)
                .cancelledQty(0)
                .unitPrice(itemData.getUnitPrice())
                .productPriceSnapshot(product.getUnitPrice())  // V4.2: 固化下单时的商品标价快照
                .subtotal(subtotal)
                .rejectNearExpiry(itemData.getRejectNearExpiry() != null ? itemData.getRejectNearExpiry() : false)
                .specifiedBatchIds(parseSpecifiedBatchIds(itemData.getSpecifiedBatchIds()))
                .remark(itemData.getRemark())
                .build();

            items.add(item);
        }

        // Save all items
        items = salesOrderItemRepository.saveAll(items);
        log.info("✅ New items created: count={}", items.size());

        // 6. Recalculate total amount
        BigDecimal totalAmount = calculateTotalAmount(items);
        salesOrder.setTotalAmount(totalAmount);
        log.info("💰 Total amount recalculated: {}", totalAmount);

        // 7. A draft remains unsubmitted; a pending order is resubmitted through its policy.
        CommercialStatus previousCommercialStatus = salesOrder.getCommercialStatus();
        String previousReviewReason = salesOrder.getReviewReason();
        if (salesOrder.getCommercialStatus() == CommercialStatus.PENDING_APPROVAL) {
            applySubmissionPolicy(salesOrder, items, false);
        }
        if (salesOrder.getCommercialStatus() != previousCommercialStatus
            || !Objects.equals(salesOrder.getReviewReason(), previousReviewReason)) {
            recordStatusChange(salesOrder, operatorId, operatorName, salesOrder.getReviewReason());
        }

        // 8. Save order
        salesOrder = salesOrderRepository.save(salesOrder);

        // 9. Add audit log
        addAuditLog(salesOrder, "UPDATE", operatorName, "修改销售订单");

        // 10. If auto-approved, allocate inventory
        if (salesOrder.getCommercialStatus() == CommercialStatus.APPROVED) {
            log.info("🎯 Auto-approved after update, allocating inventory: orderId={}", orderId);
            allocationService.allocateInventory(orderId);
        }

        log.info("Sales order updated successfully: orderId={}, orderNo={}, commercialStatus={}",
            orderId, salesOrder.getOrderNo(), salesOrder.getCommercialStatus());

        return convertToResponse(salesOrder);
    }

    /**
     * Delete sales order (physical delete, DRAFT only)
     *
     * Business Rule:
     * - DRAFT status: physical DELETE from database
     * - Any other status: throws exception, use cancelSalesOrder or voidSalesOrder instead
     *
     * @param orderId Sales order ID
     * @param operatorId Operator user ID
     * @throws BusinessException if order not found or not in DRAFT status
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSalesOrder(Long orderId, Long operatorId) {
        log.info("🗑️ Deleting sales order (physical): orderId={}, operatorId={}", orderId, operatorId);

        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        if (salesOrder.getCommercialStatus() != CommercialStatus.DRAFT) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getCommercialStatus().name(),
                    "reason", "只有草稿状态的订单可以物理删除，已生效的订单请使用取消或作废"
                )
            );
        }

        salesOrderRepository.deleteById(orderId);
        log.info("✅ Sales order physically deleted: orderId={}, orderNo={}", orderId, salesOrder.getOrderNo());
    }

    /**
     * Cancel sales order (business failure, kept for AI learning)
     *
     * Business Flow:
     * 1. DRAFT status → physical delete (redirect to deleteSalesOrder)
     * 2. PENDING_APPROVAL / APPROVED → set commercial status = CANCELLED
     * 3. If APPROVED, release open reservations and void unstarted outbound tasks
     * 4. reason or remarks is required
     *
     * @param orderId Sales order ID
     * @param reason Cancellation reason (required)
     * @param operatorId Operator user ID
     * @return SalesOrderResponse
     * @throws BusinessException if order not found or cannot be cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse cancelSalesOrder(
        Long orderId,
        String reason,
        Long operatorId,
        String operatorName
    ) {
        log.info("🚫 Cancelling sales order: orderId={}, operatorId={}, reason={}",
            orderId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of("salesOrderId", orderId, "reason", "业务取消必须填写取消原因")
            );
        }

        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // DRAFT → physical delete
        if (salesOrder.getCommercialStatus() == CommercialStatus.DRAFT) {
            salesOrderRepository.deleteById(orderId);
            log.info("Draft order physically deleted during cancel: orderId={}", orderId);
            return null;
        }

        if (!salesOrder.canCancel()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getCommercialStatus().name(),
                    "reason", "当前状态不允许取消"
                )
            );
        }

        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderIdForUpdate(orderId);
        rejectTerminationWhenWarehouseStarted(orderId, "CANCEL", tasks);
        inventoryReservationService.releaseOpenReservationsForOrder(orderId);
        voidOpenTasks(tasks, operatorId, reason);
        cancelOpenBackorders(orderId);
        cancelOutstandingItemQuantities(orderId);

        salesOrder.setCommercialStatus(CommercialStatus.CANCELLED);
        recordStatusChange(salesOrder, operatorId, operatorName, reason);
        salesOrderRepository.save(salesOrder);
        salesOrder = statusProjectionService.refreshLocked(salesOrder);
        addAuditLog(salesOrder, "CANCEL", operatorName, "业务取消: " + reason);

        log.info("✅ Sales order cancelled: orderId={}, orderNo={}", orderId, salesOrder.getOrderNo());
        return convertToResponse(salesOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse submitSalesOrder(Long orderId, Long actorId, String actorName) {
        SalesOrder order = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)));
        if (order.getCommercialStatus() != CommercialStatus.DRAFT) {
            throw new BusinessException(ErrorKeys.SALES_ORDER_INVALID_STATUS, Map.of(
                "salesOrderId", orderId,
                "currentStatus", order.getCommercialStatus().name(),
                "requiredStatus", CommercialStatus.DRAFT.name()
            ));
        }
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(orderId);
        applySubmissionPolicy(order, items, false);
        recordStatusChange(order, actorId, actorName, order.getReviewReason());
        salesOrderRepository.save(order);
        addAuditLog(order, "SUBMIT", actorName, "提交销售订单");
        if (order.getCommercialStatus() == CommercialStatus.APPROVED) {
            allocationService.allocateInventory(orderId);
        }
        return convertToResponse(statusProjectionService.refresh(orderId));
    }

    /**
     * Void sales order (data noise, filtered from AI and statistics)
     *
     * Business Rule:
     * - DRAFT status → physical delete
     * - A submitted order with no downstream evidence → set status = VOIDED
     * - VOIDED orders are excluded from AI training and business statistics
     * - Financial audit trail is preserved (order number retained)
     *
     * @param orderId Sales order ID
     * @param reason Void reason (required)
     * @param operatorId Operator user ID
     * @return SalesOrderResponse
     * @throws BusinessException if order not found or cannot be voided
     */
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse voidSalesOrder(
        Long orderId,
        String reason,
        Long operatorId,
        String operatorName
    ) {
        log.info("🚫 Voiding sales order: orderId={}, operatorId={}, reason={}", orderId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of("salesOrderId", orderId, "reason", "系统作废必须填写作废原因")
            );
        }

        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // DRAFT → physical delete
        if (salesOrder.getCommercialStatus() == CommercialStatus.DRAFT) {
            salesOrderRepository.deleteById(orderId);
            log.info("✅ DRAFT order physically deleted during void: orderId={}", orderId);
            return null;
        }

        if (salesOrder.getCommercialStatus() == CommercialStatus.COMPLETED
            || salesOrder.getCommercialStatus() == CommercialStatus.CLOSED
            || salesOrder.getCommercialStatus() == CommercialStatus.CANCELLED
            || salesOrder.getCommercialStatus() == CommercialStatus.VOIDED) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getCommercialStatus().name(),
                    "reason", "当前状态不允许作废"
                )
            );
        }

        boolean hasReservations = !inventoryReservationService.listBySalesOrder(orderId).isEmpty();
        boolean hasTasks = !outboundTaskRepository.findBySalesOrderId(orderId).isEmpty();
        boolean hasShipments = !salesOrderShipmentRepository.findBySalesOrderIdOrderByIdAsc(orderId).isEmpty();
        boolean hasTransactions = !stockTransactionRepository.findBySourceOrderId(salesOrder.getOrderNo()).isEmpty();
        if (hasReservations || hasTasks || hasShipments || hasTransactions) {
            throw new BusinessException(ErrorKeys.SALES_ORDER_CANNOT_CANCEL, Map.of(
                "salesOrderId", orderId,
                "operation", "VOID",
                "reason", "订单已经产生预留、仓库、运单或库存流水证据，不能作废"
            ));
        }

        salesOrder.setCommercialStatus(CommercialStatus.VOIDED);
        recordStatusChange(salesOrder, operatorId, operatorName, reason);
        salesOrder = salesOrderRepository.save(salesOrder);
        addAuditLog(salesOrder, "VOID", operatorName, "系统作废: " + reason);

        log.info("✅ Sales order voided: orderId={}, orderNo={}", orderId, salesOrder.getOrderNo());
        return convertToResponse(salesOrder);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderResponse closeSalesOrder(
        Long orderId,
        String reason,
        Long operatorId,
        String operatorName
    ) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of("salesOrderId", orderId, "reason", "关闭剩余数量必须填写原因"));
        }
        SalesOrder order = salesOrderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)));
        if (order.getCommercialStatus() != CommercialStatus.APPROVED) {
            throw new BusinessException(ErrorKeys.SALES_ORDER_INVALID_STATUS, Map.of(
                "salesOrderId", orderId,
                "currentStatus", order.getCommercialStatus().name(),
                "requiredStatus", CommercialStatus.APPROVED.name()
            ));
        }
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(orderId);
        int shipped = items.stream().mapToInt(item -> value(item.getShippedQty())).sum();
        int remaining = items.stream()
            .mapToInt(item -> Math.max(0, value(item.getRequestedQty())
                - value(item.getShippedQty()) - value(item.getCancelledQty())))
            .sum();
        if (shipped <= 0 || remaining <= 0) {
            throw new BusinessException(ErrorKeys.SALES_ORDER_CANNOT_CANCEL, Map.of(
                "salesOrderId", orderId,
                "reason", "只有已部分发货且仍有未发数量的订单可以关闭剩余数量"
            ));
        }
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderIdForUpdate(orderId);
        long unsafeTasks = tasks.stream().filter(task -> task.getStatus() == OutboundTaskStatus.PICKING
            || task.getStatus() == OutboundTaskStatus.QUALITY_EXCEPTION).count();
        if (unsafeTasks > 0) {
            throw new BusinessException(ErrorKeys.SALES_ORDER_CANNOT_CANCEL, Map.of(
                "salesOrderId", orderId,
                "reason", "仍有拣货中或质量异常任务，必须先处理仓库实物"
            ));
        }
        inventoryReservationService.releaseOpenReservationsForOrder(orderId);
        voidOpenTasks(tasks, operatorId, reason);
        cancelOpenBackorders(orderId);
        cancelOutstandingItemQuantities(orderId);
        order.setCommercialStatus(CommercialStatus.CLOSED);
        recordStatusChange(order, operatorId, operatorName, reason);
        salesOrderRepository.save(order);
        order = statusProjectionService.refreshLocked(order);
        addAuditLog(order, "CLOSE", operatorName, "关闭未发数量: " + reason);
        return convertToResponse(order);
    }

    // ========== Helper Methods ==========

    private void rejectTerminationWhenWarehouseStarted(
        Long orderId,
        String operation,
        List<OutboundTask> tasks
    ) {
        long startedTasks = tasks.stream()
            .filter(task -> task.getStatus() != OutboundTaskStatus.PENDING
                && task.getStatus() != OutboundTaskStatus.VOIDED)
            .count();
        if (startedTasks > 0) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "operation", operation,
                    "startedOutboundTasks", startedTasks,
                    "reason", "仓库已经开始拣货或发生异常，必须先处理实物和仓库任务"
                )
            );
        }
    }

    private void voidOpenTasks(List<OutboundTask> tasks, Long actorId, String reason) {
        LocalDateTime terminatedAt = LocalDateTime.now();
        List<OutboundTask> pendingTasks = tasks.stream()
            .filter(task -> task.getStatus() == OutboundTaskStatus.PENDING)
            .toList();
        pendingTasks.forEach(task -> task.voidUnstarted(actorId, terminatedAt, reason));
        outboundTaskRepository.saveAll(pendingTasks);
    }

    private void cancelOpenBackorders(Long orderId) {
        List<BackorderLine> lines = backorderLineRepository.findBySalesOrderId(orderId);
        lines.stream().filter(BackorderLine::isOpenForAllocation)
            .forEach(line -> line.setStatus(BackorderStatus.CANCELLED));
        backorderLineRepository.saveAll(lines);
    }

    private void cancelOutstandingItemQuantities(Long orderId) {
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(orderId);
        items.forEach(item -> item.setCancelledQty(Math.max(0,
            value(item.getRequestedQty()) - value(item.getShippedQty()))));
        salesOrderItemRepository.saveAll(items);
    }

    private void recordStatusChange(
        SalesOrder order,
        Long actorId,
        String actorName,
        String reason
    ) {
        order.setStatusReason(reason == null || reason.isBlank() ? null : reason.trim());
        order.setStatusChangedBy(actorId);
        order.setStatusChangedByName(actorName);
        order.setStatusChangedAt(LocalDateTime.now());
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    /**
     * Generate unique order number
     *
     * Format: SO + yyyyMMdd + 3-digit sequence
     * Example: SO20260128001, SO20260128002
     *
     * @return Generated order number
     */
    private String generateOrderNumber() {
        String datePrefix = "SO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Find the last order number for today
        String lastOrderNo = salesOrderRepository.findAll().stream()
            .map(SalesOrder::getOrderNo)
            .filter(orderNo -> orderNo.startsWith(datePrefix))
            .max(String::compareTo)
            .orElse(null);

        int sequence = 1;
        if (lastOrderNo != null) {
            String sequenceStr = lastOrderNo.substring(datePrefix.length());
            sequence = Integer.parseInt(sequenceStr) + 1;
        }

        String orderNo = datePrefix + String.format("%03d", sequence);

        // Check uniqueness and retry if exists
        int retryCount = 0;
        while (salesOrderRepository.existsByOrderNo(orderNo) && retryCount < 10) {
            sequence++;
            orderNo = datePrefix + String.format("%03d", sequence);
            retryCount++;
        }

        if (salesOrderRepository.existsByOrderNo(orderNo)) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_ALREADY_EXISTS,
                Map.of("orderNo", orderNo)
            );
        }

        return orderNo;
    }

    /**
     * Calculate total amount from items
     *
     * @param items Sales order items
     * @return Total amount
     */
    private BigDecimal calculateTotalAmount(List<SalesOrderItem> items) {
        return items.stream()
            .map(SalesOrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Check risk control and set order status
     *
     * Risk Control Logic:
     * - If ANY item's unit_price < product.min_sales_price → PENDING_APPROVAL
     * - If total_amount > approval_threshold → PENDING_APPROVAL
     * - Otherwise → APPROVED
     *
     * @param order Sales order
     * @param items Sales order items
     */
    private void applySubmissionPolicy(
        SalesOrder order,
        List<SalesOrderItem> items,
        boolean forcePendingApproval
    ) {
        List<String> reviewReasons = new ArrayList<>();
        SalesApprovalMode mode = forcePendingApproval
            ? SalesApprovalMode.ALWAYS
            : policyService.approvalMode();
        order.setApprovalModeSnapshot(mode);

        if (forcePendingApproval) {
            reviewReasons.add("渠道对账修复单必须人工审批");
        } else if (mode == SalesApprovalMode.ALWAYS) {
            reviewReasons.add("租户策略要求所有销售订单人工审批");
        }

        if (!forcePendingApproval && mode == SalesApprovalMode.RISK_BASED) {
            for (SalesOrderItem item : items) {
                ProductSku product = productSkuRepository.findById(item.getProductSkuId())
                    .orElseThrow(() -> new BusinessException(
                        ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                        Map.of("productSkuId", item.getProductSkuId())
                    ));
                if (item.getUnitPrice().compareTo(product.getMinSalesPrice()) < 0) {
                    reviewReasons.add(String.format("产品 [%s] 单价 %.2f 低于最低限价 %.2f",
                        product.getName(), item.getUnitPrice(), product.getMinSalesPrice()));
                }
            }
            BigDecimal approvalThreshold = policyService.approvalAmountThreshold();
            if (order.getTotalAmount().compareTo(approvalThreshold) > 0) {
                reviewReasons.add(String.format("订单总额 %.2f 超过审批阈值 %.2f",
                    order.getTotalAmount(), approvalThreshold));
            }
        }

        if (!reviewReasons.isEmpty()) {
            order.setCommercialStatus(CommercialStatus.PENDING_APPROVAL);
            order.setAllocationStatus(SalesAllocationStatus.UNALLOCATED);
            order.setWarehouseStatus(SalesWarehouseStatus.NOT_RELEASED);
            order.setApprovedAt(null);
            order.setReviewReason(String.join("; ", reviewReasons));
        } else {
            order.setCommercialStatus(CommercialStatus.APPROVED);
            order.setReviewReason(null);
            order.setApprovedAt(LocalDateTime.now());
        }
    }

    /**
     * Convert entity to response DTO
     *
     * @param order Sales order entity
     * @return SalesOrderResponse
     */
    private SalesOrderResponse convertToResponse(SalesOrder order) {
        // Query items
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(order.getId());

        // Convert items to response
        List<SalesOrderResponse.SalesOrderItemResponse> itemResponses = items.stream()
            .map(item -> {
                ProductSku product = productSkuRepository.findById(item.getProductSkuId()).orElse(null);

                List<Long> specifiedBatchIds = null;
                if (item.hasSpecifiedBatches()) {
                    try {
                        specifiedBatchIds = objectMapper.readValue(
                            item.getSpecifiedBatchIds(),
                            new TypeReference<List<Long>>() {}
                        );
                    } catch (Exception e) {
                        log.error("Failed to parse specified batch IDs: {}", item.getSpecifiedBatchIds(), e);
                    }
                }

                return SalesOrderResponse.SalesOrderItemResponse.builder()
                    .id(item.getId())
                    .productSkuId(item.getProductSkuId())
                    .productName(product != null ? product.getName() : "Unknown")
                    .productBarcode(product != null ? product.getBarcode() : "")
                    .quantity(item.getQuantity())
                    .requestedQty(item.getRequestedQty())
                    .allocatedQty(item.getAllocatedQty())
                    .shippedQty(item.getShippedQty())
                    .backorderQty(item.getBackorderQty())
                    .cancelledQty(item.getCancelledQty())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(item.getSubtotal())
                    .rejectNearExpiry(item.getRejectNearExpiry())
                    .specifiedBatchIds(specifiedBatchIds)
                    .remark(item.getRemark())
                    .build();
            })
            .collect(Collectors.toList());

        String customerName;
        try {
            customerName = customerService.getCustomerName(order.getCustomerId());
            if (customerName == null || customerName.isBlank()) {
                customerName = "Unknown";
            }
        } catch (Exception e) {
            log.debug("Failed to load customer name: {}", e.getMessage());
            customerName = "Unknown";
        }

        return SalesOrderResponse.builder()
            .id(order.getId())
            .orderNo(order.getOrderNo())
            .customerId(order.getCustomerId())
            .customerName(customerName)
            .totalAmount(order.getTotalAmount())
            .commercialStatus(order.getCommercialStatus() != null ? order.getCommercialStatus().name() : null)
            .commercialStatusDescription(order.getCommercialStatus() != null ? order.getCommercialStatus().getDescription() : null)
            .allocationStatus(order.getAllocationStatus() != null ? order.getAllocationStatus().name() : null)
            .allocationStatusDescription(order.getAllocationStatus() != null ? order.getAllocationStatus().getDescription() : null)
            .warehouseStatus(order.getWarehouseStatus() != null ? order.getWarehouseStatus().name() : null)
            .warehouseStatusDescription(order.getWarehouseStatus() != null ? order.getWarehouseStatus().getDescription() : null)
            .logisticsStatus(order.getLogisticsStatus() != null ? order.getLogisticsStatus().name() : null)
            .logisticsStatusDescription(order.getLogisticsStatus() != null ? order.getLogisticsStatus().getDescription() : null)
            .approvalModeSnapshot(order.getApprovalModeSnapshot() != null ? order.getApprovalModeSnapshot().name() : null)
            .detailedLogisticsEnabled(policyService.detailedLogisticsEnabled())
            .allocationPolicy(order.getAllocationPolicy() != null ? order.getAllocationPolicy().name() : null)
            .requestedShipDate(order.getRequestedShipDate())
            .promisedShipDate(order.getPromisedShipDate())
            .shortageReason(order.getShortageReason())
            .statusVersion(order.getStatusVersion())
            .statusReason(order.getStatusReason())
            .statusChangedBy(order.getStatusChangedBy())
            .statusChangedByName(order.getStatusChangedByName())
            .statusChangedAt(order.getStatusChangedAt())
            .nextResponsibleRole(nextResponsibleRole(order))
            .allowedActions(allowedActions(order))
            .channel(order.getChannel())
            .externalOrderId(order.getExternalOrderId())
            .externalOrderNo(order.getExternalOrderNo())
            .consigneeName(order.getConsigneeName())
            .consigneePhone(order.getConsigneePhone())
            .shipAddress1(order.getShipAddress1())
            .shipAddress2(order.getShipAddress2())
            .shipCity(order.getShipCity())
            .shipProvince(order.getShipProvince())
            .shipZip(order.getShipZip())
            .shipCountryCode(order.getShipCountryCode())
            .reviewReason(order.getReviewReason())
            .reviewedBy(order.getReviewedBy())
            .reviewedByName(order.getReviewedBy() != null ? "Manager-" + order.getReviewedBy() : null)
            .reviewedAt(order.getReviewedAt())
            .reviewComment(order.getReviewComment())
            .applicantId(order.getApplicantId())
            .applicantName(order.getApplicantName())
            .items(itemResponses)
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }

    private String nextResponsibleRole(SalesOrder order) {
        if (order.getCommercialStatus() == CommercialStatus.PENDING_APPROVAL) return "APPROVER";
        if (order.getCommercialStatus() != CommercialStatus.APPROVED) return "NONE";
        if (order.getAllocationStatus() == SalesAllocationStatus.BACKORDERED
            || order.getAllocationStatus() == SalesAllocationStatus.WAITING_INBOUND) {
            return "PLANNING_OR_PURCHASING";
        }
        if (order.getWarehouseStatus() != SalesWarehouseStatus.SHIPPED
            && order.getWarehouseStatus() != SalesWarehouseStatus.NOT_RELEASED) {
            return "WAREHOUSE";
        }
        if (order.getLogisticsStatus() == SalesLogisticsStatus.EXCEPTION
            || order.getLogisticsStatus() == SalesLogisticsStatus.RETURNED) {
            return "SALES_OR_CUSTOMER_SERVICE";
        }
        return "SALES";
    }

    private List<String> allowedActions(SalesOrder order) {
        List<String> actions = new ArrayList<>();
        switch (order.getCommercialStatus()) {
            case DRAFT -> {
                actions.add("UPDATE"); actions.add("SUBMIT"); actions.add("DELETE");
            }
            case PENDING_APPROVAL -> {
                actions.add("UPDATE"); actions.add("APPROVE"); actions.add("REJECT");
                actions.add("CANCEL"); actions.add("VOID");
            }
            case APPROVED -> {
                if (order.getWarehouseStatus() == SalesWarehouseStatus.NOT_RELEASED
                    || order.getWarehouseStatus() == SalesWarehouseStatus.PENDING_PICK) actions.add("CANCEL");
                if (order.getWarehouseStatus() == SalesWarehouseStatus.PARTIALLY_SHIPPED) actions.add("CLOSE");
            }
            default -> { }
        }
        if (policyService.detailedLogisticsEnabled()
            && order.getCommercialStatus() != CommercialStatus.DRAFT
            && order.getCommercialStatus() != CommercialStatus.PENDING_APPROVAL
            && order.getCommercialStatus() != CommercialStatus.REJECTED
            && order.getCommercialStatus() != CommercialStatus.CANCELLED
            && order.getCommercialStatus() != CommercialStatus.VOIDED) {
            actions.add("UPDATE_LOGISTICS");
        }
        return actions;
    }

    /**
     * Add audit log entry
     *
     * Format:
     * [
     *   {
     *     "timestamp": "2026-01-28T10:00:00",
     *     "operator": "张三",
     *     "action": "CREATE",
     *     "details": "创建销售订单"
     *   }
     * ]
     *
     * @param order Sales order
     * @param action Action type
     * @param operator Operator name
     * @param details Action details
     */
    private void addAuditLog(SalesOrder order, String action, String operator, String details) {
        try {
            List<Map<String, String>> auditLog = new ArrayList<>();

            // Parse existing audit log
            if (order.getAuditLog() != null && !order.getAuditLog().trim().isEmpty()) {
                auditLog = objectMapper.readValue(
                    order.getAuditLog(),
                    new TypeReference<List<Map<String, String>>>() {}
                );
            }

            // Add new entry
            Map<String, String> entry = Map.of(
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "operator", operator,
                "action", action,
                "details", details
            );
            auditLog.add(entry);

            // Serialize back to JSON
            order.setAuditLog(objectMapper.writeValueAsString(auditLog));

            log.debug("📝 Audit log added: action={}, operator={}, details={}", action, operator, details);
        } catch (Exception e) {
            log.error("❌ Failed to add audit log: action={}, operator={}, details={}",
                action, operator, details, e);
        }
    }

    /**
     * Parse specified batch IDs from List to JSON string
     *
     * @param batchIds List of batch IDs
     * @return JSON string (e.g., "[123, 456]")
     */
    private String parseSpecifiedBatchIds(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(batchIds);
        } catch (Exception e) {
            log.error("❌ Failed to serialize batch IDs: {}", batchIds, e);
            return null;
        }
    }

    /**
     * Get sales order by ID
     *
     * @param id Sales order ID
     * @return SalesOrderResponse
     * @throws BusinessException if order not found
     */
    @Transactional(readOnly = true)
    public SalesOrderResponse getSalesOrder(Long id) {
        log.debug("Getting sales order by id: {}", id);

        SalesOrder salesOrder = salesOrderRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", id)
            ));

        return convertToResponse(salesOrder);
    }

    /**
     * List sales orders with optional filters
     *
     * @param status Order status (optional)
     * @param customerId Customer ID (optional)
     * @return List of sales orders
     */
    @Transactional(readOnly = true)
    public List<SalesOrderResponse> listSalesOrders(
        String commercialStatus,
        String allocationStatus,
        String warehouseStatus,
        Long customerId
    ) {
        CommercialStatus commercial = parseEnum(CommercialStatus.class, commercialStatus, "commercialStatus");
        SalesAllocationStatus allocation = parseEnum(SalesAllocationStatus.class, allocationStatus, "allocationStatus");
        SalesWarehouseStatus warehouse = parseEnum(SalesWarehouseStatus.class, warehouseStatus, "warehouseStatus");
        List<SalesOrder> orders = customerId == null
            ? salesOrderRepository.findAll()
            : salesOrderRepository.findByCustomerId(customerId);
        return orders.stream()
            .filter(order -> commercial == null || order.getCommercialStatus() == commercial)
            .filter(order -> allocation == null || order.getAllocationStatus() == allocation)
            .filter(order -> warehouse == null || order.getWarehouseStatus() == warehouse)
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", field, "value", value));
        }
    }
}
