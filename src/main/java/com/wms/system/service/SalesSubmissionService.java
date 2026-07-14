package com.wms.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.sales.SalesOrderResponse;
import com.wms.system.dto.sales.UpdateSalesOrderRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.AllocationPolicy;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.FulfillmentStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * DRAFT → PENDING_APPROVAL → APPROVED_AWAITING_SHIPMENT → SHIPPED
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

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final CustomerService customerService;
    private final ProductRepository productRepository;
    private final SystemConfigService systemConfigService;
    private final AllocationService allocationService;
    private final InventoryReservationService inventoryReservationService;
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
     *    - Otherwise → APPROVED_AWAITING_SHIPMENT + allocate inventory
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
        log.info("🚀 Creating sales order: customerId={}, applicantId={}, applicantName={}, itemCount={}",
            request.getCustomerId(), applicantId, applicantName, request.getItems().size());

        // 1. Validate customer exists and is active
        customerService.validateCustomerActive(request.getCustomerId());

        // 2. Generate order number
        String orderNo = generateOrderNumber();
        log.info("📋 Generated order number: {}", orderNo);

        // 3. Create SalesOrder entity with status = DRAFT
        SalesOrder salesOrder = SalesOrder.builder()
            .orderNo(orderNo)
            .customerId(request.getCustomerId())
            .totalAmount(BigDecimal.ZERO)
            .status(SalesOrderStatus.DRAFT)
            .applicantId(applicantId)
            .applicantName(applicantName)
            .build();

        // P1-B2 渠道标记：请求带 channel 时覆盖默认值 MANUAL（如微信单传 WECHAT）；
        // 不能在 builder 里传 null，否则会覆盖 @Builder.Default 的 MANUAL
        if (org.springframework.util.StringUtils.hasText(request.getChannel())) {
            salesOrder.setChannel(request.getChannel().trim().toUpperCase());
        }

        // Save order first to get ID
        salesOrder = salesOrderRepository.save(salesOrder);
        log.info("✅ Sales order created: orderId={}, orderNo={}", salesOrder.getId(), salesOrder.getOrderNo());

        // 4. Create SalesOrderItem entities for each item
        List<SalesOrderItem> items = new ArrayList<>();
        for (CreateSalesOrderRequest.SalesOrderItemData itemData : request.getItems()) {
            // Validate product exists
            Product product = productRepository.findById(itemData.getProductId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_NOT_FOUND,
                    Map.of("productId", itemData.getProductId())
                ));

            // Calculate subtotal
            BigDecimal subtotal = itemData.getUnitPrice()
                .multiply(BigDecimal.valueOf(itemData.getQuantity()));

            // Create item
            SalesOrderItem item = SalesOrderItem.builder()
                .salesOrderId(salesOrder.getId())
                .productId(itemData.getProductId())
                .quantity(itemData.getQuantity())
                .requestedQty(itemData.getQuantity())
                .allocatedQty(0)
                .shippedQty(0)
                .backorderQty(0)
                .cancelledQty(0)
                .fulfillmentStatus(FulfillmentStatus.UNALLOCATED)
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

        // 6. Risk control check
        checkRiskControl(salesOrder, items);

        // 7. Save order with updated status
        salesOrder = salesOrderRepository.save(salesOrder);

        // 8. Add audit log
        addAuditLog(salesOrder, "CREATE", applicantName, "创建销售订单");

        // 9. If auto-approved, allocate inventory
        if (salesOrder.getStatus() == SalesOrderStatus.APPROVED_AWAITING_SHIPMENT) {
            log.info("🎯 Auto-approved, allocating inventory: orderId={}", salesOrder.getId());
            allocationService.allocateInventory(salesOrder.getId());
        }

        log.info("✅ Sales order creation completed: orderId={}, orderNo={}, status={}",
            salesOrder.getId(), salesOrder.getOrderNo(), salesOrder.getStatus());

        return convertToResponse(salesOrder);
    }

    /**
     * Approve sales order (manager workflow)
     *
     * Business Flow:
     * 1. Validate order status = PENDING_APPROVAL
     * 2. Update reviewed_by, reviewed_at, review_comment
     * 3. Set status = APPROVED_AWAITING_SHIPMENT
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
        SalesOrder salesOrder = salesOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // 2. Validate order status = PENDING_APPROVAL
        if (!salesOrder.canApprove()) {
            log.error("❌ Sales order cannot be approved: orderId={}, currentStatus={}",
                orderId, salesOrder.getStatus());
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_INVALID_STATUS,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getStatus().name(),
                    "requiredStatus", "PENDING_APPROVAL"
                )
            );
        }

        // 3. Update approval info
        salesOrder.setReviewedBy(managerId);
        salesOrder.setReviewedAt(LocalDateTime.now());
        salesOrder.setReviewComment(comment);
        salesOrder.setStatus(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT);
        salesOrder.setCommercialStatus(CommercialStatus.APPROVED);
        salesOrder.setFulfillmentStatus(FulfillmentStatus.UNALLOCATED);
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
        SalesOrder salesOrder = salesOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // 2. Validate order status = PENDING_APPROVAL
        if (!salesOrder.canApprove()) {
            log.error("❌ Sales order cannot be rejected: orderId={}, currentStatus={}",
                orderId, salesOrder.getStatus());
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_INVALID_STATUS,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getStatus().name(),
                    "requiredStatus", "PENDING_APPROVAL"
                )
            );
        }

        // 3. Update rejection info
        salesOrder.setReviewedBy(managerId);
        salesOrder.setReviewedAt(LocalDateTime.now());
        salesOrder.setReviewComment(reason);
        salesOrder.setStatus(SalesOrderStatus.REJECTED);
        salesOrder.setCommercialStatus(CommercialStatus.REJECTED);
        salesOrder.setFulfillmentStatus(FulfillmentStatus.CANCELLED);

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
     * 6. If was APPROVED_AWAITING_SHIPMENT, delete old outbound tasks and regenerate
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
        Long operatorId
    ) {
        log.info("🔄 Updating sales order: orderId={}, operatorId={}, itemCount={}",
            orderId, operatorId, request.getItems().size());

        // 1. Validate order exists
        SalesOrder salesOrder = salesOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // 2. Validate order can be modified
        if (!salesOrder.canModify()) {
            log.error("❌ Sales order cannot be modified: orderId={}, currentStatus={}",
                orderId, salesOrder.getStatus());
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_MODIFY,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getStatus().name(),
                    "allowedStatuses", "DRAFT, PENDING_APPROVAL"
                )
            );
        }

        // 3. Update customer if provided
        if (request.getCustomerId() != null) {
            customerService.validateCustomerActive(request.getCustomerId());
            salesOrder.setCustomerId(request.getCustomerId());
        }

        // 4. Delete existing items
        salesOrderItemRepository.deleteBySalesOrderId(orderId);
        log.info("🗑️ Deleted existing items: orderId={}", orderId);

        // 5. Create new items from request
        List<SalesOrderItem> items = new ArrayList<>();
        for (UpdateSalesOrderRequest.SalesOrderItemData itemData : request.getItems()) {
            // Validate product exists
            Product product = productRepository.findById(itemData.getProductId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_NOT_FOUND,
                    Map.of("productId", itemData.getProductId())
                ));

            // Calculate subtotal
            BigDecimal subtotal = itemData.getUnitPrice()
                .multiply(BigDecimal.valueOf(itemData.getQuantity()));

            // Create item
            SalesOrderItem item = SalesOrderItem.builder()
                .salesOrderId(orderId)
                .productId(itemData.getProductId())
                .quantity(itemData.getQuantity())
                .requestedQty(itemData.getQuantity())
                .allocatedQty(0)
                .shippedQty(0)
                .backorderQty(0)
                .cancelledQty(0)
                .fulfillmentStatus(FulfillmentStatus.UNALLOCATED)
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

        // 7. Re-run risk control check
        checkRiskControl(salesOrder, items);

        // 8. Save order
        salesOrder = salesOrderRepository.save(salesOrder);

        // 9. Add audit log
        addAuditLog(salesOrder, "UPDATE", "User-" + operatorId, "修改销售订单");

        // 10. If auto-approved, allocate inventory
        if (salesOrder.getStatus() == SalesOrderStatus.APPROVED_AWAITING_SHIPMENT) {
            log.info("🎯 Auto-approved after update, allocating inventory: orderId={}", orderId);
            allocationService.allocateInventory(orderId);
        }

        log.info("✅ Sales order updated successfully: orderId={}, orderNo={}, status={}",
            orderId, salesOrder.getOrderNo(), salesOrder.getStatus());

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

        SalesOrder salesOrder = salesOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        if (!salesOrder.getStatus().canPhysicallyDelete()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getStatus().name(),
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
     * 2. PENDING_APPROVAL / APPROVED_AWAITING_SHIPMENT → set status = CANCELLED
     * 3. If APPROVED_AWAITING_SHIPMENT, release inventory (delete outbound tasks)
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
        Long operatorId
    ) {
        log.info("🚫 Cancelling sales order: orderId={}, operatorId={}, reason={}",
            orderId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of("salesOrderId", orderId, "reason", "业务取消必须填写取消原因")
            );
        }

        SalesOrder salesOrder = salesOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // DRAFT → physical delete
        if (salesOrder.getStatus() == SalesOrderStatus.DRAFT) {
            salesOrderRepository.deleteById(orderId);
            log.info("✅ DRAFT order physically deleted during cancel: orderId={}", orderId);
            return null;
        }

        if (!salesOrder.getStatus().canCancel()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getStatus().name(),
                    "reason", "当前状态不允许取消"
                )
            );
        }

        if (salesOrder.getStatus() == SalesOrderStatus.APPROVED_AWAITING_SHIPMENT) {
            rejectTerminationWhenAnyTaskCompleted(orderId, "CANCEL");
            log.info("🗑️ Deleting outbound tasks to release inventory: orderId={}", orderId);
            inventoryReservationService.releaseOpenReservationsForOrder(orderId);
            outboundTaskRepository.deleteBySalesOrderId(orderId);
        }

        salesOrder.setStatus(SalesOrderStatus.CANCELLED);
        salesOrder.setCommercialStatus(CommercialStatus.CANCELLED);
        salesOrder.setFulfillmentStatus(FulfillmentStatus.CANCELLED);
        salesOrder = salesOrderRepository.save(salesOrder);
        addAuditLog(salesOrder, "CANCEL", "User-" + operatorId, "业务取消: " + reason);

        log.info("✅ Sales order cancelled: orderId={}, orderNo={}", orderId, salesOrder.getOrderNo());
        return convertToResponse(salesOrder);
    }

    /**
     * Void sales order (data noise, filtered from AI and statistics)
     *
     * Business Rule:
     * - DRAFT status → physical delete
     * - PENDING_APPROVAL / APPROVED_AWAITING_SHIPMENT → set status = VOIDED
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
    public SalesOrderResponse voidSalesOrder(Long orderId, String reason, Long operatorId) {
        log.info("🚫 Voiding sales order: orderId={}, operatorId={}, reason={}", orderId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of("salesOrderId", orderId, "reason", "系统作废必须填写作废原因")
            );
        }

        SalesOrder salesOrder = salesOrderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", orderId)
            ));

        // DRAFT → physical delete
        if (salesOrder.getStatus() == SalesOrderStatus.DRAFT) {
            salesOrderRepository.deleteById(orderId);
            log.info("✅ DRAFT order physically deleted during void: orderId={}", orderId);
            return null;
        }

        if (!salesOrder.getStatus().canVoid()) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "currentStatus", salesOrder.getStatus().name(),
                    "reason", "当前状态不允许作废"
                )
            );
        }

        if (salesOrder.getStatus() == SalesOrderStatus.APPROVED_AWAITING_SHIPMENT) {
            rejectTerminationWhenAnyTaskCompleted(orderId, "VOID");
            inventoryReservationService.releaseOpenReservationsForOrder(orderId);
            outboundTaskRepository.deleteBySalesOrderId(orderId);
        }

        salesOrder.setStatus(SalesOrderStatus.VOIDED);
        salesOrder.setCommercialStatus(CommercialStatus.VOIDED);
        salesOrder.setFulfillmentStatus(FulfillmentStatus.VOIDED);
        salesOrder = salesOrderRepository.save(salesOrder);
        addAuditLog(salesOrder, "VOID", "User-" + operatorId, "系统作废: " + reason);

        log.info("✅ Sales order voided: orderId={}, orderNo={}", orderId, salesOrder.getOrderNo());
        return convertToResponse(salesOrder);
    }

    // ========== Helper Methods ==========

    private void rejectTerminationWhenAnyTaskCompleted(Long orderId, String operation) {
        long completedTasks = outboundTaskRepository.countCompletedTasksBySalesOrderId(orderId);
        if (completedTasks > 0) {
            throw new BusinessException(
                ErrorKeys.SALES_ORDER_CANNOT_CANCEL,
                Map.of(
                    "salesOrderId", orderId,
                    "operation", operation,
                    "completedOutboundTasks", completedTasks,
                    "reason", "Order already has completed outbound tasks; use return/reversal flow instead"
                )
            );
        }
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
     * - Otherwise → APPROVED_AWAITING_SHIPMENT
     *
     * @param order Sales order
     * @param items Sales order items
     */
    private void checkRiskControl(SalesOrder order, List<SalesOrderItem> items) {
        log.info("🔍 Running risk control check: orderId={}, totalAmount={}",
            order.getId(), order.getTotalAmount());

        List<String> reviewReasons = new ArrayList<>();

        // Check 1: Unit price below minimum sales price
        for (SalesOrderItem item : items) {
            Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_NOT_FOUND,
                    Map.of("productId", item.getProductId())
                ));

            if (item.getUnitPrice().compareTo(product.getMinSalesPrice()) < 0) {
                String reason = String.format("产品 [%s] 单价 %.2f 低于最低限价 %.2f",
                    product.getName(), item.getUnitPrice(), product.getMinSalesPrice());
                reviewReasons.add(reason);
                log.warn("⚠️ Price below minimum: productId={}, unitPrice={}, minSalesPrice={}",
                    item.getProductId(), item.getUnitPrice(), product.getMinSalesPrice());
            }
        }

        // Check 2: Total amount exceeds approval threshold
        BigDecimal approvalThreshold = systemConfigService.getSalesApprovalAmountThreshold();
        if (order.getTotalAmount().compareTo(approvalThreshold) > 0) {
            String reason = String.format("订单总额 %.2f 超过审批阈值 %.2f",
                order.getTotalAmount(), approvalThreshold);
            reviewReasons.add(reason);
            log.warn("⚠️ Amount exceeds threshold: totalAmount={}, threshold={}",
                order.getTotalAmount(), approvalThreshold);
        }

        // Set status based on risk control
        if (!reviewReasons.isEmpty()) {
            order.setStatus(SalesOrderStatus.PENDING_APPROVAL);
            order.setCommercialStatus(CommercialStatus.PENDING_APPROVAL);
            order.setFulfillmentStatus(FulfillmentStatus.UNALLOCATED);
            order.setReviewReason(String.join("; ", reviewReasons));
            log.info("⚠️ Risk control triggered, order requires approval: orderId={}, reasons={}",
                order.getId(), order.getReviewReason());
        } else {
            order.setStatus(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT);
            order.setCommercialStatus(CommercialStatus.APPROVED);
            order.setFulfillmentStatus(FulfillmentStatus.UNALLOCATED);
            order.setApprovedAt(LocalDateTime.now());  // V4.2: 记录自动审批通过时间
            log.info("✅ Risk control passed, order auto-approved: orderId={}", order.getId());
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
                Product product = productRepository.findById(item.getProductId()).orElse(null);

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
                    .productId(item.getProductId())
                    .productName(product != null ? product.getName() : "Unknown")
                    .productBarcode(product != null ? product.getBarcode() : "")
                    .quantity(item.getQuantity())
                    .requestedQty(item.getRequestedQty())
                    .allocatedQty(item.getAllocatedQty())
                    .shippedQty(item.getShippedQty())
                    .backorderQty(item.getBackorderQty())
                    .cancelledQty(item.getCancelledQty())
                    .fulfillmentStatus(item.getFulfillmentStatus() != null ? item.getFulfillmentStatus().name() : null)
                    .fulfillmentStatusDescription(item.getFulfillmentStatus() != null ? item.getFulfillmentStatus().getDescription() : null)
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
            .status(order.getStatus().name())
            .statusDescription(order.getStatus().getDescription())
            .commercialStatus(order.getCommercialStatus() != null ? order.getCommercialStatus().name() : null)
            .commercialStatusDescription(order.getCommercialStatus() != null ? order.getCommercialStatus().getDescription() : null)
            .fulfillmentStatus(order.getFulfillmentStatus() != null ? order.getFulfillmentStatus().name() : null)
            .fulfillmentStatusDescription(order.getFulfillmentStatus() != null ? order.getFulfillmentStatus().getDescription() : null)
            .allocationPolicy(order.getAllocationPolicy() != null ? order.getAllocationPolicy().name() : null)
            .requestedShipDate(order.getRequestedShipDate())
            .promisedShipDate(order.getPromisedShipDate())
            .shortageReason(order.getShortageReason())
            .fulfillmentVersion(order.getFulfillmentVersion())
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
    public List<SalesOrderResponse> listSalesOrders(String status, Long customerId) {
        log.debug("Listing sales orders: status={}, customerId={}", status, customerId);

        List<SalesOrder> orders;

        if (status != null && customerId != null) {
            // Filter by both status and customer
            SalesOrderStatus orderStatus = SalesOrderStatus.valueOf(status);
            orders = salesOrderRepository.findByCustomerIdAndStatus(customerId, orderStatus);
        } else if (status != null) {
            // Filter by status only
            SalesOrderStatus orderStatus = SalesOrderStatus.valueOf(status);
            orders = salesOrderRepository.findByStatus(orderStatus);
        } else if (customerId != null) {
            // Filter by customer only
            orders = salesOrderRepository.findByCustomerId(customerId);
        } else {
            // No filter, return all
            orders = salesOrderRepository.findAll();
        }

        return orders.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
}
