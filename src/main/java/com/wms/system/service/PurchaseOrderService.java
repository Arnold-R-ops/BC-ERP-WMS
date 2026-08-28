package com.wms.system.service;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Purchase Order Management Service (采购单管理服务)
 *
 * P0-02 purchase-commercial aggregate. It owns draft editing, optional
 * approval and business cancellation. Shipment is represented by PurchaseAsn
 * and every physical receipt goes through the unified ReceivingTask workflow.
 *
 * Business flow: draft and warehouse allocation, optional approval, supplier
 * shipment through an independent ASN, then idempotent unified receiving.
 * Purchase-order commands never post inventory directly.
 *
 * @author WMS Team
 * @since 2025-01-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderReceivingRepository purchaseOrderReceivingRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductSkuOperationalPolicy productSkuOperationalPolicy;
    private final PurchaseGovernancePolicyService purchaseGovernancePolicyService;
    private final PurchaseOrderAllocationService purchaseOrderAllocationService;
    private final PurchaseOrderAllocationRepository purchaseOrderAllocationRepository;
    private final ReceivingPlanService receivingPlanService;
    private final PurchaseAsnService purchaseAsnService;

    /**
     * Create a purchase-order draft.
     *
     * Business Flow:
     * 1. Generate PO number (PO-YYYYMMDD-XXX format)
     * 2. Validate products exist
     * 3. Create PurchaseOrder with DRAFT status
     * 4. Create PurchaseOrderItem records (receipt metadata may be prefilled)
     * 5. Calculate totals (totalQuantity, totalCost)
     *
     * Inventory batches are generated only by the unified receipt executor.
     *
     * @param supplierId Supplier master-data ID
     * @param items List of purchase order items
     * @param expectedDate Expected delivery date
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @param remark Optional remark
     * @return PurchaseOrder created purchase order with DRAFT status
     * @throws BusinessException if product not found or validation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder createPurchaseOrder(
        Long supplierId,
        List<PurchaseOrderItemData> items,
        LocalDate expectedDate,
        Long operatorId,
        String operatorName,
        String remark
    ) {
        log.info("Creating purchase order: supplierId={}, itemCount={}, operator={}",
            supplierId, items.size(), operatorName);

        Supplier supplier = supplierRepository
            .findByIdAndCompanyIdAndIsDeletedFalse(supplierId, CompanyScope.currentCompanyId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SUPPLIER_NOT_FOUND,
                Map.of("supplierId", supplierId)
            ));
        if (!Boolean.TRUE.equals(supplier.getIsActive())) {
            throw new BusinessException(
                ErrorKeys.SUPPLIER_NOT_ACTIVE,
                Map.of("supplierId", supplierId, "supplierCode", supplier.getCode())
            );
        }

        // 1. Generate PO number
        String poNumber = generatePoNumber();
        log.info("Generated PO number: {}", poNumber);

        // 2. Create PurchaseOrder entity
        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
            .poNumber(poNumber)
            .supplier(supplier.getName())
            .supplierReference(supplier)
            .status(PurchaseOrderStatus.DRAFT)
            .totalQuantity(0)
            .totalCost(java.math.BigDecimal.ZERO)
            .expectedDate(expectedDate)
            .operatorId(operatorId)
            .operatorName(operatorName)
            .remark(remark)
            .auditLog(String.format("%s - Created by %s", LocalDateTime.now(), operatorName))
            .build();

        // 3. Create PurchaseOrderItem records
        List<PurchaseOrderItem> orderItems = new ArrayList<>();
        int totalQuantity = 0;
        java.math.BigDecimal totalCost = java.math.BigDecimal.ZERO;

        for (PurchaseOrderItemData itemData : items) {
            // Validate product exists
            ProductSku product = productSkuRepository.findById(itemData.getProductSkuId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                    Map.of("productSkuId", itemData.getProductSkuId())
                ));
            productSkuOperationalPolicy.requireEnabled(
                product,
                ProductSkuOperationalPolicy.PURCHASE_ORDER
            );

            // Create item
            PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .productSku(product)
                .orderedQuantity(itemData.getOrderedQuantity())
                .receivedQuantity(0)  // Initially 0
                .unitCost(itemData.getUnitCost())
                .productPriceSnapshot(product.getUnitPrice())  // V4.2: 固化下单时的商品标价快照
                .expiryDate(itemData.getExpiryDate())  // Optional at Stage 1
                .productionDate(itemData.getProductionDate())
                .externalBatchCode(itemData.getExternalBatchCode())
                .remark(itemData.getRemark())
                .build();

            orderItems.add(item);

            // Accumulate totals
            totalQuantity += itemData.getOrderedQuantity();
            if (itemData.getUnitCost() != null) {
                totalCost = totalCost.add(
                    itemData.getUnitCost().multiply(java.math.BigDecimal.valueOf(itemData.getOrderedQuantity()))
                );
            }
        }

        purchaseOrder.setItems(orderItems);
        purchaseOrder.setTotalQuantity(totalQuantity);
        purchaseOrder.setTotalCost(totalCost);

        // 4. Save (cascade will save items)
        PurchaseOrder savedOrder = purchaseOrderRepository.save(purchaseOrder);

        log.info("✅ Purchase order created: poNumber={}, status={}, totalQuantity={}, totalCost={}",
            savedOrder.getPoNumber(), savedOrder.getStatus(), savedOrder.getTotalQuantity(), savedOrder.getTotalCost());

        return savedOrder;
    }

    /**
     * Edit the business fields of an existing purchase order while it is still
     * as a draft. No status transition, batch creation, receipt or inventory
     * mutation is performed by this operation.
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder updateOrderingPurchaseOrder(
        Long purchaseOrderId,
        Long expectedVersion,
        Long supplierId,
        List<PurchaseOrderItemUpdateData> requestedItems,
        LocalDate expectedDate,
        Long editorId,
        String editorName,
        String remark
    ) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(purchaseOrderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                Map.of("purchaseOrderId", purchaseOrderId)
            ));

        if (!purchaseOrder.getStatus().isEditableDraft()) {
            throw new BusinessException(
                ErrorKeys.PO_INVALID_STATUS,
                Map.of(
                    "purchaseOrderId", purchaseOrderId,
                    "currentStatus", purchaseOrder.getStatus().name(),
                    "expectedStatus", PurchaseOrderStatus.DRAFT.name()
                )
            );
        }
        if (!Objects.equals(purchaseOrder.getVersion(), expectedVersion)) {
            throw new BusinessException(
                ErrorKeys.PO_EDIT_CONFLICT,
                Map.of(
                    "purchaseOrderId", purchaseOrderId,
                    "expectedVersion", expectedVersion,
                    "currentVersion", purchaseOrder.getVersion()
                )
            );
        }

        Supplier supplier = supplierRepository
            .findByIdAndCompanyIdAndIsDeletedFalse(supplierId, CompanyScope.currentCompanyId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SUPPLIER_NOT_FOUND,
                Map.of("supplierId", supplierId)
            ));
        if (!Boolean.TRUE.equals(supplier.getIsActive())) {
            throw new BusinessException(
                ErrorKeys.SUPPLIER_NOT_ACTIVE,
                Map.of("supplierId", supplierId, "supplierCode", supplier.getCode())
            );
        }

        Map<Long, PurchaseOrderItem> existingById = new HashMap<>();
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            existingById.put(item.getId(), item);
        }

        Set<Long> submittedItemIds = new HashSet<>();
        Set<Long> submittedProductIds = new HashSet<>();
        List<PurchaseOrderItem> newItems = new ArrayList<>();

        for (PurchaseOrderItemUpdateData itemData : requestedItems) {
            if (!submittedProductIds.add(itemData.getProductSkuId())) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("field", "items.productSkuId", "reason", "Duplicate product SKU")
                );
            }

            ProductSku product = productSkuRepository.findById(itemData.getProductSkuId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                    Map.of("productSkuId", itemData.getProductSkuId())
                ));
            productSkuOperationalPolicy.requireEnabled(
                product,
                ProductSkuOperationalPolicy.PURCHASE_ORDER
            );

            if (itemData.getId() == null) {
                PurchaseOrderItem newItem = PurchaseOrderItem.builder()
                    .purchaseOrder(purchaseOrder)
                    .productSku(product)
                    .orderedQuantity(itemData.getOrderedQuantity())
                    .receivedQuantity(0)
                    .unitCost(itemData.getUnitCost())
                    .productPriceSnapshot(product.getUnitPrice())
                    .expiryDate(itemData.getExpiryDate())
                    .productionDate(itemData.getProductionDate())
                    .externalBatchCode(itemData.getExternalBatchCode())
                    .remark(itemData.getRemark())
                    .build();
                newItems.add(newItem);
                continue;
            }

            if (!submittedItemIds.add(itemData.getId())) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("field", "items.id", "reason", "Duplicate purchase order item ID")
                );
            }
            PurchaseOrderItem existing = existingById.get(itemData.getId());
            if (existing == null) {
                throw new BusinessException(
                    ErrorKeys.PO_ITEM_NOT_FOUND,
                    Map.of("itemId", itemData.getId(), "purchaseOrderId", purchaseOrderId)
                );
            }

            boolean hasBatchHistory = inventoryBatchRepository
                .existsByPurchaseOrderItemId(existing.getId());
            if (hasBatchHistory && !existing.getProductSku().getId().equals(itemData.getProductSkuId())) {
                throw historyLocked(purchaseOrderId, existing.getId(), "productSkuId");
            }
            if (hasBatchHistory && !existing.getOrderedQuantity().equals(itemData.getOrderedQuantity())) {
                throw historyLocked(purchaseOrderId, existing.getId(), "orderedQuantity");
            }

            existing.setProductSku(product);
            existing.setOrderedQuantity(itemData.getOrderedQuantity());
            existing.setUnitCost(itemData.getUnitCost());
            existing.setExpiryDate(itemData.getExpiryDate());
            existing.setProductionDate(itemData.getProductionDate());
            existing.setExternalBatchCode(itemData.getExternalBatchCode());
            existing.setRemark(itemData.getRemark());
        }

        for (PurchaseOrderItem existing : new ArrayList<>(purchaseOrder.getItems())) {
            if (!submittedItemIds.contains(existing.getId())) {
                if (inventoryBatchRepository.existsByPurchaseOrderItemId(existing.getId())) {
                    throw historyLocked(purchaseOrderId, existing.getId(), "remove");
                }
                List<PurchaseOrderAllocation> allocations = purchaseOrderAllocationRepository
                    .findByPurchaseOrderItemIdForUpdate(existing.getId());
                if (allocations.stream().anyMatch(this::hasAllocationExecutionHistory)) {
                    throw historyLocked(purchaseOrderId, existing.getId(), "allocation");
                }
                if (!allocations.isEmpty()) {
                    purchaseOrderAllocationRepository.deleteAll(allocations);
                    // The FK is restrictive. Execute allocation deletes before
                    // Hibernate flushes orphan removal of the purchase line.
                    purchaseOrderAllocationRepository.flush();
                }
                purchaseOrder.removeItem(existing);
            }
        }
        for (PurchaseOrderItem newItem : newItems) {
            purchaseOrder.addItem(newItem);
        }

        purchaseOrder.setSupplierReference(supplier);
        purchaseOrder.setSupplier(supplier.getName());
        purchaseOrder.setExpectedDate(expectedDate);
        purchaseOrder.setRemark(remark);
        purchaseOrder.updateTotals();
        purchaseOrder.appendAuditLog(String.format(
            "Draft purchase order edited by %s (userId=%s, lines=%d)",
            editorName,
            editorId,
            requestedItems.size()
        ));

        try {
            return purchaseOrderRepository.saveAndFlush(purchaseOrder);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(
                ErrorKeys.PO_EDIT_CONFLICT,
                Map.of("purchaseOrderId", purchaseOrderId, "expectedVersion", expectedVersion)
            );
        }
    }

    private BusinessException historyLocked(Long purchaseOrderId, Long itemId, String field) {
        return new BusinessException(
            ErrorKeys.PO_ITEM_HISTORY_LOCKED,
            Map.of(
                "purchaseOrderId", purchaseOrderId,
                "itemId", itemId,
                "field", field
            )
        );
    }

    /** Submit a draft using the tenant's optional approval policy. */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder submit(
        Long purchaseOrderId,
        Long expectedVersion,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = lockedForWorkflow(purchaseOrderId, expectedVersion);
        if (!order.getStatus().canSubmit()) {
            throw invalidStatus(order, "DRAFT or REJECTED");
        }
        purchaseOrderAllocationService.requireCompletePlan(order);
        order.setStatus(purchaseGovernancePolicyService.effectivePurchaseApprovalMode()
            == com.wms.system.entity.enums.PurchaseApprovalMode.SIMPLE
                ? PurchaseOrderStatus.PENDING_APPROVAL
                : PurchaseOrderStatus.AWAITING_SHIPMENT);
        order.appendAuditLog(String.format(
            "Submitted by %s (userId=%s), resulting status=%s",
            actorName, actorId, order.getStatus()));
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_SHIPMENT) {
            receivingPlanService.ensureWaitingShipmentPlans(order, actorId, actorName);
        }
        return purchaseOrderRepository.saveAndFlush(order);
    }

    /** Approve a submitted order. A maker cannot approve their own order. */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder approve(
        Long purchaseOrderId,
        Long expectedVersion,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = lockedForWorkflow(purchaseOrderId, expectedVersion);
        if (!order.getStatus().canApprove()) {
            throw invalidStatus(order, PurchaseOrderStatus.PENDING_APPROVAL.name());
        }
        if (Objects.equals(order.getOperatorId(), actorId)) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("purchaseOrderId", purchaseOrderId,
                    "reason", "Maker cannot approve their own purchase order")
            );
        }
        order.setStatus(PurchaseOrderStatus.AWAITING_SHIPMENT);
        order.appendAuditLog(String.format("Approved by %s (userId=%s)", actorName, actorId));
        receivingPlanService.ensureWaitingShipmentPlans(order, actorId, actorName);
        return purchaseOrderRepository.saveAndFlush(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder reject(
        Long purchaseOrderId,
        Long expectedVersion,
        String reason,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = lockedForWorkflow(purchaseOrderId, expectedVersion);
        if (!order.getStatus().canApprove()) {
            throw invalidStatus(order, PurchaseOrderStatus.PENDING_APPROVAL.name());
        }
        order.setStatus(PurchaseOrderStatus.REJECTED);
        order.appendAuditLog(String.format(
            "Rejected by %s (userId=%s): %s", actorName, actorId, requiredReason(reason)));
        return purchaseOrderRepository.saveAndFlush(order);
    }

    /**
     * Withdrawal returns an order to an editable draft. The ASN repository is
     * consulted by the unified receiving slice before this command is exposed
     * in production; a confirmed ASN makes withdrawal illegal.
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder withdrawBeforeShipment(
        Long purchaseOrderId,
        Long expectedVersion,
        String reason,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = lockedForWorkflow(purchaseOrderId, expectedVersion);
        if (!order.getStatus().canWithdrawBeforeShipment()) {
            throw invalidStatus(order, "PENDING_APPROVAL, REJECTED or AWAITING_SHIPMENT");
        }
        if (purchaseAsnService.hasConfirmedOrReceipt(purchaseOrderId)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "WITHDRAW_PURCHASE_ORDER",
                    "purchaseOrderId", purchaseOrderId,
                    "reason", "CONFIRMED_SHIPMENT_EXISTS")
            );
        }
        purchaseAsnService.cancelPurchaseDownstream(
            purchaseOrderId, requiredReason(reason), actorId, actorName);
        order.setStatus(PurchaseOrderStatus.DRAFT);
        order.appendAuditLog(String.format(
            "Withdrawn before confirmed shipment by %s (userId=%s): %s",
            actorName, actorId, requiredReason(reason)));
        return purchaseOrderRepository.saveAndFlush(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder cancelGoverned(
        Long purchaseOrderId,
        Long expectedVersion,
        String reason,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = lockedForWorkflow(purchaseOrderId, expectedVersion);
        if (!order.getStatus().canCancelWithoutReceipt()) {
            throw invalidStatus(order, "any non-final state without a physical receipt");
        }
        purchaseAsnService.cancelPurchaseDownstream(
            purchaseOrderId, requiredReason(reason), actorId, actorName);
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.appendAuditLog(String.format(
            "Business cancelled by %s (userId=%s): %s",
            actorName, actorId, requiredReason(reason)));
        return purchaseOrderRepository.saveAndFlush(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder voidGoverned(
        Long purchaseOrderId,
        Long expectedVersion,
        String reason,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = lockedForWorkflow(purchaseOrderId, expectedVersion);
        if (!order.getStatus().canVoidWithoutDownstream()) {
            throw invalidStatus(order, "DRAFT or REJECTED without downstream records");
        }
        if (purchaseAsnService.hasAnyDownstream(purchaseOrderId)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "VOID_PURCHASE_ORDER",
                    "purchaseOrderId", purchaseOrderId,
                    "reason", "DOWNSTREAM_RECORD_EXISTS")
            );
        }
        order.setStatus(PurchaseOrderStatus.VOIDED);
        order.appendAuditLog(String.format(
            "Voided as erroneous data by %s (userId=%s): %s",
            actorName, actorId, requiredReason(reason)));
        return purchaseOrderRepository.saveAndFlush(order);
    }

    private PurchaseOrder lockedForWorkflow(Long purchaseOrderId, Long expectedVersion) {
        PurchaseOrder order = purchaseOrderReceivingRepository.findByIdForReceiving(purchaseOrderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                Map.of("purchaseOrderId", purchaseOrderId)));
        if (!Objects.equals(order.getVersion(), expectedVersion)) {
            throw new BusinessException(
                ErrorKeys.PO_EDIT_CONFLICT,
                Map.of("purchaseOrderId", purchaseOrderId,
                    "expectedVersion", expectedVersion,
                    "currentVersion", order.getVersion())
            );
        }
        return order;
    }

    private BusinessException invalidStatus(PurchaseOrder order, String expected) {
        return new BusinessException(
            ErrorKeys.PO_INVALID_STATUS,
            Map.of("purchaseOrderId", order.getId(),
                "currentStatus", order.getStatus().name(),
                "expectedStatus", expected)
        );
    }

    private boolean hasAllocationExecutionHistory(PurchaseOrderAllocation allocation) {
        return positive(allocation.getShippedQuantity())
            || positive(allocation.getReceivedQuantity())
            || positive(allocation.getClosedQuantity());
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reason", "reason", "A reason is required")
            );
        }
        return reason.trim();
    }

    /**
     * Generate PO number in format: PO-YYYYMMDD-XXX
     *
     * Algorithm:
     * 1. Get current date (YYYYMMDD)
     * 2. Query latest PO with same date prefix
     * 3. Extract sequence number and increment
     * 4. Format: PO-YYYYMMDD-XXX (zero-padded to 3 digits)
     *
     * Example: PO-20250113-001, PO-20250113-002, ...
     *
     * @return String Generated PO number
     */
    private String generatePoNumber() {
        // Get current date prefix
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String poPrefix = "PO-" + datePrefix;

        // Query latest PO with same prefix
        List<PurchaseOrder> latestOrders = purchaseOrderRepository.findLatestByDatePrefix(poPrefix);

        int nextSequence = 1;

        if (!latestOrders.isEmpty()) {
            String latestPoNumber = latestOrders.get(0).getPoNumber();
            // Extract sequence: "PO-20250113-001" -> "001"
            String sequencePart = latestPoNumber.substring(latestPoNumber.lastIndexOf('-') + 1);
            nextSequence = Integer.parseInt(sequencePart) + 1;
        }

        // Format: PO-YYYYMMDD-XXX (zero-padded to 3 digits)
        return String.format("%s-%03d", poPrefix, nextSequence);
    }

    /**
     * Query purchase order by ID
     *
     * @param id Purchase order ID
     * @return PurchaseOrder Purchase order entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public PurchaseOrder findById(Long id) {
        return purchaseOrderRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                Map.of("purchaseOrderId", id)
            ));
    }

    /**
     * Query purchase order by PO number
     *
     * @param poNumber PO number
     * @return PurchaseOrder Purchase order entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public PurchaseOrder findByPoNumber(String poNumber) {
        return purchaseOrderRepository.findByPoNumber(poNumber)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                Map.of("poNumber", poNumber)
            ));
    }

    /**
     * Query purchase orders by status
     *
     * @param status Purchase order status
     * @return List<PurchaseOrder> Purchase orders
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrder> findByStatus(PurchaseOrderStatus status) {
        return purchaseOrderRepository.findByStatus(status);
    }

    /**
     * Query purchase orders by status (paged)
     *
     * @param status Purchase order status
     * @param pageable Pagination info
     * @return List of purchase orders (paged content)
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable) {
        return purchaseOrderRepository.findByStatus(status, pageable).getContent();
    }

    /**
     * Query all purchase orders (paged)
     *
     * @param pageable Pagination info
     * @return List of purchase orders (paged content)
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrder> findAll(Pageable pageable) {
        return purchaseOrderRepository.findAll(pageable).getContent();
    }

    // ========== Inner Classes (Data Transfer Objects) ==========

    /**
     * Purchase order item data for creation
     */
    @lombok.Data
    @lombok.Builder
    public static class PurchaseOrderItemData {
        private Long productSkuId;
        private Integer orderedQuantity;
        private java.math.BigDecimal unitCost;
        private LocalDate expiryDate;  // Optional at Stage 1
        private LocalDate productionDate;
        private String externalBatchCode;
        private String remark;
    }

    @lombok.Data
    @lombok.Builder
    public static class PurchaseOrderItemUpdateData {
        private Long id;
        private Long productSkuId;
        private Integer orderedQuantity;
        private java.math.BigDecimal unitCost;
        private LocalDate expiryDate;
        private LocalDate productionDate;
        private String externalBatchCode;
        private String remark;
    }

}
