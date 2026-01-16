package com.wms.system.service;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.StockTransactionRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory Batch Management Service (批次库存管理服务)
 *
 * Core Responsibilities:
 * 1. FIFO outbound logic (先进先出出库 - based on expiry date)
 * 2. Batch stock queries (by product, location, expiry status)
 * 3. V3.0 stock aggregation (real-time SUM from InventoryBatch)
 * 4. Expiry warnings (过期预警)
 * 5. Batch lifecycle management
 *
 * V3.0 Architecture:
 * - InventoryBatch is the ONLY inventory data source (Single Source of Truth)
 * - No Inventory aggregation table
 * - All stock queries aggregate from InventoryBatch with WHERE active = true
 * - Stock aggregation: SELECT SUM(quantity) WHERE product_id = ? AND active = true
 *
 * FIFO Logic:
 * - Query: ORDER BY expiryDate ASC (earliest expiry first)
 * - Deduct from earliest expiring batches first
 * - Ensure no expired batches are used for outbound
 * - Generate stock transactions for each batch deduction
 *
 * Technical Features:
 * - @Transactional: Ensures atomicity (multi-batch deduction + transaction generation)
 * - @Retryable: Auto-retry on optimistic lock conflicts (max 3 attempts)
 * - Error Key System: All exceptions use error keys for frontend i18n
 * - Optimistic locking: @Version field prevents concurrent stock oversell
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 3.0 (Single Source of Truth Architecture + FIFO Batch Management)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryBatchService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final StockTransactionRepository stockTransactionRepository;

    /**
     * ⭐ FIFO Outbound with Loose Item First Strategy (先进先出出库 - V3.1 零头优先策略)
     *
     * V3.1 Upgrade: Multi-Unit Auto-Unpacking Strategy
     * - Stage 1: Prioritize loose batches (零头批次优先 - quantity % conversionRate != 0)
     * - Stage 2: Use full pack batches only when loose items are insufficient (零头不足时才拆新箱)
     *
     * Business Flow:
     * 1. Query loose batches first (ORDER BY expiryDate ASC)
     * 2. If loose items insufficient, query full pack batches (ORDER BY expiryDate ASC)
     * 3. Filter out expired batches (reject outbound if expired batch exists)
     * 4. Deduct from earliest expiring batches first (FIFO within each stage)
     * 5. Update batch quantities (optimistic locking)
     * 6. Generate stock transactions for each batch deduction
     *
     * Loose Item First Rules:
     * - Loose batch: quantity % conversionRate != 0 (已开箱)
     * - Full pack batch: quantity % conversionRate == 0 (未开箱)
     * - Always consume loose items before opening new packs
     * - Minimize number of open packs in warehouse
     *
     * Example (conversionRate = 12):
     * - Warehouse has: [5 loose units, 24 full pack units]
     * - Request 10 units:
     *   → Use 5 from loose batch
     *   → Open 1 full pack (12 units), use 5, leave 7 as new loose batch
     * - Result: [7 loose units, 12 full pack units]
     *
     * V3.0 Architecture:
     * - No Inventory table update
     * - InventoryBatch.quantity is directly decremented
     * - Stock transactions record batch_code for traceability
     *
     * @param productId Product ID
     * @param requestedQuantity Requested outbound quantity
     * @param sourceType Source type (e.g., SALE_OUT, PRODUCTION_OUT)
     * @param sourceOrderId Source order ID (e.g., SO-20250113-001)
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @return List<StockTransaction> Generated transactions (one per batch)
     * @throws BusinessException if insufficient stock, expired batch found, or concurrency conflict
     * @since V3.1 (upgraded from V3.0)
     */
    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        retryFor = {OptimisticLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public List<StockTransaction> outboundWithFifo(
        Long productId,
        Integer requestedQuantity,
        SourceType sourceType,
        String sourceOrderId,
        Long operatorId,
        String operatorName
    ) {
        log.info("🚀 V3.1 FIFO outbound started: productId={}, requestedQty={}, sourceType={}, sourceOrder={}",
            productId, requestedQuantity, sourceType, sourceOrderId);

        // 1. Validate product exists
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_NOT_FOUND,
                Map.of("productId", productId)
            ));

        log.info("📦 Product info: name={}, packUnit={}, conversionRate={}, formattedRequest={}",
            product.getName(), product.getPackUnit(), product.getConversionRate(),
            product.formatQuantity(requestedQuantity));

        // 2. V3.1: Query batches with Loose Item First Strategy
        List<InventoryBatch> allBatches = new ArrayList<>();

        // Stage 1: Query loose batches first (零头批次优先)
        List<InventoryBatch> looseBatches = inventoryBatchRepository
            .findLooseBatchesByProductOrderByExpiryDateAsc(productId, product.getConversionRate(), true);

        if (!looseBatches.isEmpty()) {
            allBatches.addAll(looseBatches);
            log.info("✅ Stage 1: Found {} loose batches (已开箱批次)", looseBatches.size());
        } else {
            log.info("⚠️ Stage 1: No loose batches found");
        }

        // Stage 2: Query full pack batches (整箱批次)
        List<InventoryBatch> fullPackBatches = inventoryBatchRepository
            .findFullPackBatchesByProductOrderByExpiryDateAsc(productId, product.getConversionRate(), true);

        if (!fullPackBatches.isEmpty()) {
            allBatches.addAll(fullPackBatches);
            log.info("✅ Stage 2: Found {} full pack batches (整箱批次)", fullPackBatches.size());
        } else {
            log.info("⚠️ Stage 2: No full pack batches found");
        }

        // Validate we have batches
        if (allBatches.isEmpty()) {
            log.error("❌ No active batches found: productId={}", productId);

            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productId", productId,
                    "productName", product.getName(),
                    "requestedQuantity", requestedQuantity,
                    "availableQuantity", 0
                )
            );
        }

        // 3. Check for expired batches (reject outbound if found)
        LocalDate today = LocalDate.now();
        List<InventoryBatch> expiredBatches = allBatches.stream()
            .filter(b -> b.getExpiryDate().isBefore(today))
            .toList();

        if (!expiredBatches.isEmpty()) {
            InventoryBatch firstExpired = expiredBatches.get(0);
            log.error("❌ Expired batch found: batchCode={}, expiryDate={}, quantity={}",
                firstExpired.getBatchCode(), firstExpired.getExpiryDate(), firstExpired.getQuantity());

            throw new BusinessException(
                ErrorKeys.BATCH_EXPIRED,
                Map.of(
                    "batchCode", firstExpired.getBatchCode(),
                    "expiryDate", firstExpired.getExpiryDate().toString(),
                    "productId", productId,
                    "productName", product.getName()
                )
            );
        }

        // 4. FIFO deduction: deduct from batches in order (loose first, then full packs)
        int remaining = requestedQuantity;
        List<StockTransaction> transactions = new ArrayList<>();
        int looseBatchesUsed = 0;
        int fullPackBatchesUsed = 0;

        for (InventoryBatch batch : allBatches) {
            if (remaining <= 0) {
                break;  // All quantity fulfilled
            }

            // Skip exhausted batches
            if (batch.getQuantity() == 0) {
                continue;
            }

            // Track which type of batch we're using
            boolean isLooseBatch = product.isLooseQuantity(batch.getQuantity());

            // Calculate deduction for this batch
            int quantityBefore = batch.getQuantity();
            int toDeduct = Math.min(remaining, quantityBefore);

            // Deduct from batch
            batch.decreaseQuantity(toDeduct);  // Throws exception if insufficient
            InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

            // Log with formatted quantities (V3.1)
            log.info("✅ Batch deducted: batchCode={}, type={}, deducted={}, before={}, after={}",
                savedBatch.getBatchCode(),
                isLooseBatch ? "LOOSE" : "FULL_PACK",
                product.formatQuantity(toDeduct),
                product.formatQuantity(quantityBefore),
                product.formatQuantity(savedBatch.getQuantity()));

            // Track batch type usage
            if (isLooseBatch) {
                looseBatchesUsed++;
            } else {
                fullPackBatchesUsed++;
            }

            // Generate stock transaction
            StockTransaction transaction = StockTransaction.builder()
                .product(product)
                .location(batch.getLocation())
                .transactionType(TransactionType.OUT)
                .sourceType(sourceType)
                .quantity(toDeduct)
                .quantityBefore(quantityBefore)
                .quantityAfter(savedBatch.getQuantity())
                .sourceOrderId(sourceOrderId)
                .operatorId(operatorId)
                .operatorName(operatorName)
                .remark(String.format("V3.1 FIFO outbound (%s) - Batch: %s",
                    isLooseBatch ? "Loose Item" : "Full Pack", savedBatch.getBatchCode()))
                .build();

            StockTransaction savedTransaction = stockTransactionRepository.save(transaction);
            transactions.add(savedTransaction);

            log.info("📝 Transaction created: id={}, batchCode={}, quantity={}",
                savedTransaction.getId(), savedBatch.getBatchCode(), product.formatQuantity(toDeduct));

            remaining -= toDeduct;
        }

        // 5. Verify sufficient stock
        if (remaining > 0) {
            int availableQuantity = allBatches.stream()
                .mapToInt(InventoryBatch::getQuantity)
                .sum();

            log.error("❌ Insufficient stock: productId={}, requested={}, available={}, shortage={}",
                productId, requestedQuantity, availableQuantity, remaining);

            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productId", productId,
                    "productName", product.getName(),
                    "requestedQuantity", requestedQuantity,
                    "availableQuantity", availableQuantity,
                    "shortage", remaining
                )
            );
        }

        log.info("✅ V3.1 FIFO outbound completed: productId={}, quantity={}, looseBatches={}, fullPackBatches={}, totalTransactions={}",
            productId, product.formatQuantity(requestedQuantity), looseBatchesUsed, fullPackBatchesUsed, transactions.size());

        return transactions;
    }

    /**
     * ⭐ Get total stock for product (V3.0: Real-time aggregation)
     *
     * V3.0 Architecture:
     * - Query: SELECT SUM(quantity) FROM inventory_batch WHERE product_id = ? AND active = true
     * - No Inventory table lookup
     * - Real-time aggregation for accuracy
     *
     * Performance Optimization (Future):
     * - Redis cache for hot products: stock:product:{productId}:total
     * - Cache invalidation on stock changes
     *
     * @param productId Product ID
     * @return Integer Total stock (0 if no active batches)
     */
    @Transactional(readOnly = true)
    public Integer getTotalStock(Long productId) {
        Integer totalStock = inventoryBatchRepository.sumQuantityByProduct(productId);
        return totalStock != null ? totalStock : 0;
    }

    /**
     * Query all active batches for a product
     *
     * @param productId Product ID
     * @return List<InventoryBatch> Active batches (sorted by expiry date ASC)
     */
    @Transactional(readOnly = true)
    public List<InventoryBatch> getActiveBatchesByProduct(Long productId) {
        return inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(productId, true);
    }

    /**
     * Query batches by location
     *
     * @param locationId Location ID
     * @return List<InventoryBatch> Active batches at location
     */
    @Transactional(readOnly = true)
    public List<InventoryBatch> getActiveBatchesByLocation(Long locationId) {
        return inventoryBatchRepository.findByLocationIdAndActive(locationId, true);
    }

    /**
     * Query batch by batch code
     *
     * @param batchCode Batch code
     * @return InventoryBatch Batch entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public InventoryBatch findByBatchCode(String batchCode) {
        return inventoryBatchRepository.findByBatchCode(batchCode)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchCode", batchCode)
            ));
    }

    /**
     * ⚠️ Find expired batches (过期批次查询)
     *
     * Query batches where expiryDate < today AND active = true
     *
     * Usage:
     * - Automatic expiry alerts
     * - Batch cleanup/disposal workflows
     * - Financial loss calculation
     *
     * @return List<InventoryBatch> Expired batches
     */
    @Transactional(readOnly = true)
    public List<InventoryBatch> findExpiredBatches() {
        LocalDate today = LocalDate.now();
        return inventoryBatchRepository.findExpiredBatches(today, true);
    }

    /**
     * ⚠️ Find soon-to-expire batches (即将过期批次查询)
     *
     * Query batches where expiryDate BETWEEN today AND (today + days)
     *
     * Usage:
     * - Early expiry warnings (e.g., 7 days before expiry)
     * - Priority outbound scheduling
     * - Promotional campaigns for near-expiry products
     *
     * @param days Days threshold (e.g., 7 for 1 week warning)
     * @return List<InventoryBatch> Soon-to-expire batches
     */
    @Transactional(readOnly = true)
    public List<InventoryBatch> findSoonToExpireBatches(int days) {
        LocalDate today = LocalDate.now();
        LocalDate alertDate = today.plusDays(days);
        return inventoryBatchRepository.findSoonToExpireBatches(today, alertDate, true);
    }

    /**
     * Find exhausted batches (quantity = 0 AND active = true)
     *
     * Used for cleanup or archival.
     *
     * @return List<InventoryBatch> Exhausted batches
     */
    @Transactional(readOnly = true)
    public List<InventoryBatch> findExhaustedBatches() {
        return inventoryBatchRepository.findExhaustedBatches(true);
    }

    /**
     * Count active batches for a product
     *
     * @param productId Product ID
     * @return long Batch count
     */
    @Transactional(readOnly = true)
    public long countActiveBatchesByProduct(Long productId) {
        return inventoryBatchRepository.countByProductIdAndActive(productId, true);
    }

    /**
     * ⭐ Manual stock adjustment for specific batch
     *
     * Use Cases:
     * - Inventory reconciliation (盘点调整)
     * - Damage/loss recording
     * - Manual corrections
     *
     * ⚠️ Note: This directly modifies batch quantity.
     * For normal outbound, use outboundWithFifo() instead.
     *
     * @param batchCode Batch code
     * @param adjustmentQuantity Adjustment quantity (positive = increase, negative = decrease)
     * @param sourceType Source type (e.g., INVENTORY_GAIN, INVENTORY_LOSS)
     * @param sourceOrderId Source order ID (e.g., ST-20250113-001)
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @param remark Adjustment reason
     * @return StockTransaction Generated transaction
     * @throws BusinessException if batch not found, inactive, or insufficient stock
     */
    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        retryFor = {OptimisticLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public StockTransaction adjustBatchStock(
        String batchCode,
        Integer adjustmentQuantity,
        SourceType sourceType,
        String sourceOrderId,
        Long operatorId,
        String operatorName,
        String remark
    ) {
        log.info("Adjusting batch stock: batchCode={}, adjustment={}, sourceType={}",
            batchCode, adjustmentQuantity, sourceType);

        // 1. Query batch
        InventoryBatch batch = findByBatchCode(batchCode);

        // 2. Validate batch is active
        if (!batch.getActive()) {
            log.error("Batch is inactive: batchCode={}", batchCode);

            throw new BusinessException(
                ErrorKeys.BATCH_INACTIVE,
                Map.of("batchCode", batchCode)
            );
        }

        // 3. Record quantity before
        Integer quantityBefore = batch.getQuantity();

        // 4. Adjust quantity
        if (adjustmentQuantity > 0) {
            batch.increaseQuantity(adjustmentQuantity);
        } else if (adjustmentQuantity < 0) {
            batch.decreaseQuantity(Math.abs(adjustmentQuantity));
        } else {
            // No change
            log.warn("Adjustment quantity is zero: batchCode={}", batchCode);
        }

        // 5. Save batch
        InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

        log.info("✅ Batch adjusted: batchCode={}, before={}, after={}, adjustment={}",
            savedBatch.getBatchCode(), quantityBefore, savedBatch.getQuantity(), adjustmentQuantity);

        // 6. Generate transaction
        TransactionType transactionType = adjustmentQuantity > 0 ? TransactionType.IN : TransactionType.OUT;

        StockTransaction transaction = StockTransaction.builder()
            .product(batch.getProduct())
            .location(batch.getLocation())
            .transactionType(transactionType)
            .sourceType(sourceType)
            .quantity(Math.abs(adjustmentQuantity))
            .quantityBefore(quantityBefore)
            .quantityAfter(savedBatch.getQuantity())
            .sourceOrderId(sourceOrderId)
            .operatorId(operatorId)
            .operatorName(operatorName)
            .remark(String.format("Batch adjustment - %s", remark))
            .build();

        StockTransaction savedTransaction = stockTransactionRepository.save(transaction);

        log.info("✅ Adjustment transaction created: transactionId={}, batchCode={}, adjustment={}",
            savedTransaction.getId(), savedBatch.getBatchCode(), adjustmentQuantity);

        return savedTransaction;
    }
}
