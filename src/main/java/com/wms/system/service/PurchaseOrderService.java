package com.wms.system.service;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.util.BatchCodeGenerator;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Purchase Order Management Service (采购单管理服务)
 *
 * Core Responsibilities:
 * 1. Three-stage purchase order workflow (ORDERING → IN_TRANSIT → PARTIALLY_RECEIVED/COMPLETED)
 * 2. Atomic batch code generation (Stage 2)
 * 3. Physical receipt with location assignment (Stage 3)
 * 4. State rollback mechanism (IN_TRANSIT → ORDERING)
 * 5. PO number generation (PO-YYYYMMDD-XXX format)
 *
 * Business Flow:
 * - Stage 1 (ORDERING): Create PO from Excel or manual input, expiry_date optional
 * - Stage 2 (IN_TRANSIT): Confirm ASN, validate expiry_date, generate Hashids batch codes atomically
 * - Stage 3 (COMPLETED): Physical receipt, assign locations, record entryDate, generate stock transactions
 *
 * V3.0 Architecture:
 * - InventoryBatch is the ONLY inventory data source (Single Source of Truth)
 * - No Inventory aggregation table maintenance
 * - All stock queries aggregate from InventoryBatch in real-time
 *
 * Technical Features:
 * - @Transactional: Ensures atomicity (batch generation + status change in same transaction)
 * - @Retryable: Auto-retry on optimistic lock conflicts (max 3 attempts)
 * - Error Key System: All exceptions use error keys for frontend i18n
 * - Audit logging: Record state changes in auditLog field
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 3.0 (Single Source of Truth Architecture + Batch Management)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final BatchCodeGenerator batchCodeGenerator;

    /**
     * ⭐ Stage 1: Create Purchase Order (ORDERING)
     *
     * Business Flow:
     * 1. Generate PO number (PO-YYYYMMDD-XXX format)
     * 2. Validate products exist
     * 3. Create PurchaseOrder with ORDERING status
     * 4. Create PurchaseOrderItem records (expiry_date optional at this stage)
     * 5. Calculate totals (totalQuantity, totalCost)
     *
     * ⚠️ Note: Batch codes are NOT generated at this stage.
     * They will be generated in Stage 2 when confirming the ASN.
     *
     * @param supplier Supplier name
     * @param items List of purchase order items
     * @param expectedDate Expected delivery date
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @param remark Optional remark
     * @return PurchaseOrder Created purchase order with ORDERING status
     * @throws BusinessException if product not found or validation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder createPurchaseOrder(
        String supplier,
        List<PurchaseOrderItemData> items,
        LocalDate expectedDate,
        Long operatorId,
        String operatorName,
        String remark
    ) {
        log.info("Creating purchase order: supplier={}, itemCount={}, operator={}",
            supplier, items.size(), operatorName);

        // 1. Generate PO number
        String poNumber = generatePoNumber();
        log.info("Generated PO number: {}", poNumber);

        // 2. Create PurchaseOrder entity
        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
            .poNumber(poNumber)
            .supplier(supplier)
            .status(PurchaseOrderStatus.ORDERING)
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
            Product product = productRepository.findById(itemData.getProductId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_NOT_FOUND,
                    Map.of("productId", itemData.getProductId())
                ));

            // Create item
            PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .product(product)
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
     * ⭐ Stage 2: Confirm ASN and Generate Batch Codes (IN_TRANSIT)
     *
     * Business Flow:
     * 1. Validate PO status (must be ORDERING)
     * 2. Validate expiry_date for all items (required at this stage)
     * 3. Generate Hashids batch codes for each item
     * 4. Create InventoryBatch records (location = null, entryDate = null)
     * 5. Update PO status to IN_TRANSIT
     * 6. Record audit log
     *
     * ⚠️ Atomic Operation: Batch generation + status change in same transaction.
     * If any batch code generation fails, entire operation rolls back.
     *
     * @param purchaseOrderId Purchase order ID
     * @param itemUpdates List of item updates with expiry_date (required)
     * @return PurchaseOrder Updated purchase order with IN_TRANSIT status
     * @throws BusinessException if status invalid or expiry_date missing
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder confirmAndGenerateBatchCodes(
        Long purchaseOrderId,
        List<ItemExpiryUpdate> itemUpdates
    ) {
        log.info("Confirming ASN and generating batch codes: purchaseOrderId={}", purchaseOrderId);

        // 1. Query purchase order
        PurchaseOrder purchaseOrder = findById(purchaseOrderId);

        // 2. Validate status (only ORDERING can be confirmed)
        if (!purchaseOrder.getStatus().canConfirmAndGenerateBatch()) {
            log.error("Invalid status for batch generation: poNumber={}, currentStatus={}",
                purchaseOrder.getPoNumber(), purchaseOrder.getStatus());

            throw new BusinessException(
                ErrorKeys.PO_INVALID_STATUS,
                Map.of(
                    "poNumber", purchaseOrder.getPoNumber(),
                    "currentStatus", purchaseOrder.getStatus().name(),
                    "expectedStatus", PurchaseOrderStatus.ORDERING.name()
                )
            );
        }

        // 3. Update expiry_date if provided and validate
        for (ItemExpiryUpdate update : itemUpdates) {
            PurchaseOrderItem item = purchaseOrder.getItems().stream()
                .filter(i -> i.getId().equals(update.getItemId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PO_ITEM_NOT_FOUND,
                    Map.of("itemId", update.getItemId())
                ));

            // Update expiry_date if provided
            if (update.getExpiryDate() != null) {
                item.setExpiryDate(update.getExpiryDate());
            }
            if (update.getProductionDate() != null) {
                item.setProductionDate(update.getProductionDate());
            }

            // Validate expiry_date is present (required at Stage 2)
            if (item.getExpiryDate() == null) {
                log.error("Expiry date missing for item: itemId={}, productId={}",
                    item.getId(), item.getProduct().getId());

                throw new BusinessException(
                    ErrorKeys.PO_EXPIRY_DATE_REQUIRED,
                    Map.of(
                        "itemId", item.getId(),
                        "productId", item.getProduct().getId(),
                        "productName", item.getProduct().getName()
                    )
                );
            }
        }

        // 4. Generate batch codes for all items (atomic operation)
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            // Generate unique batch code with collision detection
            String batchCode = batchCodeGenerator.generateUnique(
                item.getProduct().getId(),
                item.getId(),
                inventoryBatchRepository
            );

            log.info("Generated batch code: itemId={}, productId={}, batchCode={}",
                item.getId(), item.getProduct().getId(), batchCode);

            // Create InventoryBatch record (Stage 2: location = null, entryDate = null)
            InventoryBatch batch = InventoryBatch.builder()
                .batchCode(batchCode)
                .purchaseOrderItem(item)
                .product(item.getProduct())
                .location(null)  // Stage 2: Not assigned yet
                .locationCode("UNASSIGNED")
                .quantity(item.getOrderedQuantity())  // Initial quantity
                .initialQuantity(item.getOrderedQuantity())
                .expiryDate(item.getExpiryDate())
                .productionDate(item.getProductionDate())
                .externalBatchCode(item.getExternalBatchCode())
                .entryDate(null)  // Stage 2: Not received yet
                .active(true)
                .version(0)
                .build();

            inventoryBatchRepository.save(batch);

            log.info("✅ Batch created: batchCode={}, productId={}, quantity={}, expiryDate={}",
                batchCode, item.getProduct().getId(), batch.getQuantity(), batch.getExpiryDate());
        }

        // 5. Update PO status to IN_TRANSIT
        purchaseOrder.setStatus(PurchaseOrderStatus.IN_TRANSIT);
        purchaseOrder.appendAuditLog(
            String.format("Confirmed ASN and generated %d batch codes", purchaseOrder.getItems().size())
        );

        // 6. Save
        PurchaseOrder savedOrder = purchaseOrderRepository.save(purchaseOrder);

        log.info("✅ ASN confirmed: poNumber={}, status={}, batchCount={}",
            savedOrder.getPoNumber(), savedOrder.getStatus(), savedOrder.getItems().size());

        return savedOrder;
    }

    /**
     * ⭐ Stage 3: Physical Receipt with Location Assignment (COMPLETED)
     *
     * Business Flow:
     * 1. Validate PO status (must be IN_TRANSIT or PARTIALLY_RECEIVED)
     * 2. For each batch:
     *    - Query batch by batch code
     *    - Validate location exists
     *    - Assign location to batch
     *    - Record entryDate (current server time)
     *    - Generate stock transaction (PURCHASE_IN)
     *    - Update item.receivedQuantity
     * 3. Check if all items fully received:
     *    - If yes: Status = COMPLETED
     *    - If partial: Status = PARTIALLY_RECEIVED
     * 4. Record audit log
     *
     * ⚠️ V3.0 Architecture: No Inventory table update.
     * InventoryBatch is the single source of truth.
     *
     * Supports Partial Receiving:
     * - Same PurchaseOrderItem can have multiple InventoryBatch records
     * - receivedQuantity accumulates across multiple calls
     * - Different entryDate for each batch
     *
     * @param purchaseOrderId Purchase order ID
     * @param receiptData List of batch location assignments
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @return PurchaseOrder Updated purchase order with PARTIALLY_RECEIVED or COMPLETED status
     * @throws BusinessException if status invalid, batch not found, or location not found
     */
    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        retryFor = {OptimisticLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public PurchaseOrder receiveGoods(
        Long purchaseOrderId,
        List<BatchReceiptData> receiptData,
        Long operatorId,
        String operatorName
    ) {
        log.info("Processing physical receipt: purchaseOrderId={}, batchCount={}, operator={}",
            purchaseOrderId, receiptData.size(), operatorName);

        // 1. Query purchase order
        PurchaseOrder purchaseOrder = findById(purchaseOrderId);

        // 2. Validate status (only IN_TRANSIT or PARTIALLY_RECEIVED can receive)
        if (!purchaseOrder.getStatus().canReceive()) {
            log.error("Invalid status for receipt: poNumber={}, currentStatus={}",
                purchaseOrder.getPoNumber(), purchaseOrder.getStatus());

            throw new BusinessException(
                ErrorKeys.PO_INVALID_STATUS,
                Map.of(
                    "poNumber", purchaseOrder.getPoNumber(),
                    "currentStatus", purchaseOrder.getStatus().name(),
                    "expectedStatus", "IN_TRANSIT or PARTIALLY_RECEIVED"
                )
            );
        }

        // 3. Process each batch receipt
        int totalReceivedInThisBatch = 0;

        for (BatchReceiptData receipt : receiptData) {
            // Query batch by batch code (V3.3: returns List, get first one)
            List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCode(receipt.getBatchCode());

            if (batches.isEmpty()) {
                throw new BusinessException(
                    ErrorKeys.BATCH_NOT_FOUND,
                    Map.of("batchCode", receipt.getBatchCode())
                );
            }

            InventoryBatch batch = batches.get(0); // Get first batch

            // Validate batch is active
            if (!batch.getActive()) {
                log.error("Batch is inactive: batchCode={}", receipt.getBatchCode());

                throw new BusinessException(
                    ErrorKeys.BATCH_INACTIVE,
                    Map.of("batchCode", receipt.getBatchCode())
                );
            }

            // Query location
            Location location = locationRepository.findById(receipt.getLocationId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.LOCATION_NOT_FOUND,
                    Map.of("locationId", receipt.getLocationId())
                ));

            // Record quantity before (for audit)
            Integer quantityBefore = batch.getQuantity();

            // Assign location and record entry time (Stage 3 completion)
            batch.assignLocation(location);  // Sets location and entryDate

            // Save batch
            InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

            log.info("✅ Batch received: batchCode={}, location={}, quantity={}, entryDate={}",
                savedBatch.getBatchCode(), location.getLocationCode(),
                savedBatch.getQuantity(), savedBatch.getEntryDate());

            // Generate stock transaction (V3.0: No Inventory table update)
            StockTransaction transaction = StockTransaction.builder()
                .product(batch.getProduct())
                .location(location)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(savedBatch.getQuantity())
                .quantityBefore(quantityBefore)
                .quantityAfter(savedBatch.getQuantity())  // Same as quantity (no previous stock)
                .sourceOrderId(purchaseOrder.getPoNumber())
                .operatorId(operatorId)
                .operatorName(operatorName)
                .reasonCode("PURCHASE_INBOUND")  // V4.2: 结构化归因码
                .remarks(String.format("采购入库，批次 %s", savedBatch.getBatchCode()))  // V4.2: 归因短注释
                .remark(String.format("Purchase receipt - Batch: %s", savedBatch.getBatchCode()))
                .build();

            stockTransactionRepository.save(transaction);

            log.info("✅ Stock transaction created: batchCode={}, quantity={}, location={}",
                savedBatch.getBatchCode(), transaction.getQuantity(), location.getLocationCode());

            // Update item receivedQuantity
            PurchaseOrderItem item = batch.getPurchaseOrderItem();
            item.increaseReceivedQuantity(savedBatch.getQuantity());

            totalReceivedInThisBatch += savedBatch.getQuantity();
        }

        // 4. Check if order is fully received
        boolean fullyReceived = purchaseOrder.isFullyReceived();

        if (fullyReceived) {
            purchaseOrder.setStatus(PurchaseOrderStatus.COMPLETED);
            purchaseOrder.setActualEntryDate(LocalDateTime.now());
            log.info("✅ Purchase order fully received: poNumber={}", purchaseOrder.getPoNumber());
        } else {
            purchaseOrder.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
            log.info("⚠️ Purchase order partially received: poNumber={}, receivedQty={}/{}",
                purchaseOrder.getPoNumber(),
                purchaseOrder.getItems().stream().mapToInt(PurchaseOrderItem::getReceivedQuantity).sum(),
                purchaseOrder.getTotalQuantity());
        }

        // 5. Record audit log
        purchaseOrder.appendAuditLog(
            String.format("Received %d units in %d batches by %s",
                totalReceivedInThisBatch, receiptData.size(), operatorName)
        );

        // 6. Save
        PurchaseOrder savedOrder = purchaseOrderRepository.save(purchaseOrder);

        log.info("✅ Physical receipt completed: poNumber={}, status={}, operator={}",
            savedOrder.getPoNumber(), savedOrder.getStatus(), operatorName);

        return savedOrder;
    }

    /**
     * ⭐ Rollback to ORDERING (State Rollback)
     *
     * Business Flow:
     * 1. Validate status (only IN_TRANSIT can rollback)
     * 2. Mark all generated batches as inactive (active = false)
     * 3. Record invalidation reason in batch.remark
     * 4. Update PO status to ORDERING
     * 5. Record audit log
     *
     * ⚠️ Note: PARTIALLY_RECEIVED and COMPLETED cannot rollback.
     * Once physical receipt starts, rollback is prohibited.
     *
     * @param purchaseOrderId Purchase order ID
     * @param reason Rollback reason
     * @return PurchaseOrder Updated purchase order with ORDERING status
     * @throws BusinessException if status does not allow rollback
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder rollbackToOrdering(Long purchaseOrderId, String reason) {
        log.info("Rolling back to ORDERING: purchaseOrderId={}, reason={}", purchaseOrderId, reason);

        // 1. Query purchase order
        PurchaseOrder purchaseOrder = findById(purchaseOrderId);

        // 2. Validate status (only IN_TRANSIT can rollback)
        if (!purchaseOrder.getStatus().canRollbackToOrdering()) {
            log.error("Rollback not allowed: poNumber={}, currentStatus={}",
                purchaseOrder.getPoNumber(), purchaseOrder.getStatus());

            throw new BusinessException(
                ErrorKeys.PO_ROLLBACK_NOT_ALLOWED,
                Map.of(
                    "poNumber", purchaseOrder.getPoNumber(),
                    "currentStatus", purchaseOrder.getStatus().name()
                )
            );
        }

        // 3. Mark all batches as inactive
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            List<InventoryBatch> batches = inventoryBatchRepository.findByPurchaseOrderItemId(item.getId());

            for (InventoryBatch batch : batches) {
                batch.markAsInactive(reason);
                inventoryBatchRepository.save(batch);

                log.info("⚠️ Batch invalidated: batchCode={}, reason={}", batch.getBatchCode(), reason);
            }
        }

        // 4. Update status to ORDERING
        purchaseOrder.setStatus(PurchaseOrderStatus.ORDERING);
        purchaseOrder.appendAuditLog(
            String.format("Rolled back to ORDERING - Reason: %s", reason)
        );

        // 5. Save
        PurchaseOrder savedOrder = purchaseOrderRepository.save(purchaseOrder);

        log.info("✅ Rollback completed: poNumber={}, status={}", savedOrder.getPoNumber(), savedOrder.getStatus());

        return savedOrder;
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

    // ========== Delete/Cancel/Void Operations ==========

    /**
     * Delete purchase order (physical delete, ORDERING only)
     *
     * Business Rule:
     * - ORDERING status: physical DELETE from database
     * - Any other status: throws exception, use cancelPurchaseOrder or voidPurchaseOrder instead
     *
     * @param poId Purchase order ID
     * @param operatorId Operator user ID
     * @throws BusinessException if order not found or not in ORDERING status
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePurchaseOrder(Long poId, Long operatorId) {
        log.info("🗑️ Deleting purchase order (physical): poId={}, operatorId={}", poId, operatorId);

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                Map.of("purchaseOrderId", poId)
            ));

        if (!po.getStatus().canPhysicallyDelete()) {
            throw new BusinessException(
                ErrorKeys.PURCHASE_ORDER_INVALID_STATUS,
                Map.of(
                    "purchaseOrderId", poId,
                    "currentStatus", po.getStatus().name(),
                    "reason", "只有下单中状态的采购单可以物理删除，已生效的采购单请使用取消或作废"
                )
            );
        }

        purchaseOrderRepository.deleteById(poId);
        log.info("✅ Purchase order physically deleted: poId={}, poNumber={}", poId, po.getPoNumber());
    }

    /**
     * Cancel purchase order (business failure, kept for AI learning)
     *
     * Business Rule:
     * - ORDERING status → physical delete
     * - IN_TRANSIT / PARTIALLY_RECEIVED → set status = CANCELLED
     * - reason is required
     * - Data preserved for AI learning
     *
     * @param poId Purchase order ID
     * @param reason Cancellation reason (required)
     * @param operatorId Operator user ID
     * @return Updated PurchaseOrder
     * @throws BusinessException if order not found or cannot be cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder cancelPurchaseOrder(Long poId, String reason, Long operatorId) {
        log.info("🚫 Cancelling purchase order: poId={}, operatorId={}, reason={}", poId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.PURCHASE_ORDER_INVALID_STATUS,
                Map.of("purchaseOrderId", poId, "reason", "业务取消必须填写取消原因")
            );
        }

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                Map.of("purchaseOrderId", poId)
            ));

        // ORDERING → physical delete
        if (po.getStatus() == PurchaseOrderStatus.ORDERING) {
            purchaseOrderRepository.deleteById(poId);
            log.info("✅ ORDERING purchase order physically deleted during cancel: poId={}", poId);
            return null;
        }

        if (!po.getStatus().canCancel()) {
            throw new BusinessException(
                ErrorKeys.PURCHASE_ORDER_INVALID_STATUS,
                Map.of(
                    "purchaseOrderId", poId,
                    "currentStatus", po.getStatus().name(),
                    "reason", "当前状态不允许取消"
                )
            );
        }

        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.appendAuditLog("业务取消 by User-" + operatorId + ": " + reason);
        po = purchaseOrderRepository.save(po);

        log.info("✅ Purchase order cancelled: poId={}, poNumber={}", poId, po.getPoNumber());
        return po;
    }

    /**
     * Void purchase order (data noise, filtered from AI and statistics)
     *
     * Business Rule:
     * - ORDERING status → physical delete
     * - IN_TRANSIT / PARTIALLY_RECEIVED → set status = VOIDED
     * - VOIDED orders are excluded from AI training and business statistics
     * - Financial audit trail is preserved (PO number retained)
     *
     * @param poId Purchase order ID
     * @param reason Void reason (required)
     * @param operatorId Operator user ID
     * @return Updated PurchaseOrder
     * @throws BusinessException if order not found or cannot be voided
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder voidPurchaseOrder(Long poId, String reason, Long operatorId) {
        log.info("🚫 Voiding purchase order: poId={}, operatorId={}, reason={}", poId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.PURCHASE_ORDER_INVALID_STATUS,
                Map.of("purchaseOrderId", poId, "reason", "系统作废必须填写作废原因")
            );
        }

        PurchaseOrder po = purchaseOrderRepository.findById(poId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                Map.of("purchaseOrderId", poId)
            ));

        // ORDERING → physical delete
        if (po.getStatus() == PurchaseOrderStatus.ORDERING) {
            purchaseOrderRepository.deleteById(poId);
            log.info("✅ ORDERING purchase order physically deleted during void: poId={}", poId);
            return null;
        }

        if (!po.getStatus().canVoid()) {
            throw new BusinessException(
                ErrorKeys.PURCHASE_ORDER_INVALID_STATUS,
                Map.of(
                    "purchaseOrderId", poId,
                    "currentStatus", po.getStatus().name(),
                    "reason", "当前状态不允许作废"
                )
            );
        }

        po.setStatus(PurchaseOrderStatus.VOIDED);
        po.appendAuditLog("系统作废 by User-" + operatorId + ": " + reason);
        po = purchaseOrderRepository.save(po);

        log.info("✅ Purchase order voided: poId={}, poNumber={}", poId, po.getPoNumber());
        return po;
    }

    // ========== Inner Classes (Data Transfer Objects) ==========

    /**
     * Purchase order item data for creation
     */
    @lombok.Data
    @lombok.Builder
    public static class PurchaseOrderItemData {
        private Long productId;
        private Integer orderedQuantity;
        private java.math.BigDecimal unitCost;
        private LocalDate expiryDate;  // Optional at Stage 1
        private LocalDate productionDate;
        private String externalBatchCode;
        private String remark;
    }

    /**
     * Item expiry date update for Stage 2
     */
    @lombok.Data
    @lombok.Builder
    public static class ItemExpiryUpdate {
        private Long itemId;
        private LocalDate expiryDate;  // Required at Stage 2
        private LocalDate productionDate;
    }

    /**
     * Batch receipt data for Stage 3
     */
    @lombok.Data
    @lombok.Builder
    public static class BatchReceiptData {
        private String batchCode;  // Batch code from Stage 2
        private Long locationId;   // Location to assign
    }
}
