package com.wms.system.service;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Inventory Batch Management Service (批次库存管理服务)
 *
 * Core Responsibilities:
 * 1. Batch stock queries (by product, location, expiry status)
 * 2. V3.0 stock aggregation (real-time SUM from InventoryBatch)
 * 3. Expiry warnings (过期预警)
 * 4. Batch lifecycle queries
 *
 * V3.0 Architecture:
 * - InventoryBatch is the ONLY inventory data source (Single Source of Truth)
 * - No Inventory aggregation table
 * - All stock queries aggregate from InventoryBatch with WHERE active = true
 * - Stock aggregation: SELECT SUM(quantity) WHERE product_sku_id = ? AND active = true
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
    private final ProductSkuRepository productSkuRepository;

    /**
     * ⭐ Get total stock for product (V3.0: Real-time aggregation)
     *
     * V3.0 Architecture:
     * - Query: SELECT SUM(quantity) FROM inventory_batch WHERE product_sku_id = ? AND active = true
     * - No Inventory table lookup
     * - Real-time aggregation for accuracy
     *
     * Performance Optimization (Future):
     * - Redis cache for hot products: stock:product:{productSkuId}:total
     * - Cache invalidation on stock changes
     *
     * @param productSkuId ProductSku ID
     * @return Integer Total stock (0 if no active batches)
     */
    @Transactional(readOnly = true)
    public Integer getTotalStock(Long productSkuId) {
        productSkuRepository.findById(productSkuId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                Map.of("productSkuId", productSkuId)
            ));

        Integer totalStock = inventoryBatchRepository.sumQuantityByProductSku(productSkuId);
        return totalStock != null ? totalStock : 0;
    }

    /**
     * Query all active batches for a product
     *
     * @param productSkuId ProductSku ID
     * @return List<InventoryBatch> Active batches (sorted by expiry date ASC)
     */
    @Transactional(readOnly = true)
    public List<InventoryBatch> getActiveBatchesByProductSku(Long productSkuId) {
        return inventoryBatchRepository.findByProductSkuIdAndActiveOrderByExpiryDateAsc(productSkuId, true);
    }

    /**
     * Query selectable existing batches for the emergency-correction workflow.
     * This intentionally does not alter the ordinary inventory query above.
     */
    @Transactional(readOnly = true)
    public List<InventoryBatch> getCorrectionBatchOptions(Long productSkuId) {
        return inventoryBatchRepository.findCorrectionCandidatesByProductSkuId(productSkuId);
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
     * V3.3 Update: Returns first batch if multiple locations exist
     * For location-specific query, use findByBatchCodeAndLocationCode()
     *
     * @param batchCode Batch code
     * @return InventoryBatch Batch entity
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public InventoryBatch findByBatchCode(String batchCode) {
        List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCode(batchCode);

        if (batches.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchCode", batchCode)
            );
        }

        // V3.3: Return first batch if multiple locations exist
        // Warning: Consider using findByBatchCodeAndLocationCode() for specific location
        return batches.get(0);
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
     * @param productSkuId ProductSku ID
     * @return long Batch count
     */
    @Transactional(readOnly = true)
    public long countActiveBatchesByProductSku(Long productSkuId) {
        return inventoryBatchRepository.countByProductSkuIdAndActive(productSkuId, true);
    }

}
