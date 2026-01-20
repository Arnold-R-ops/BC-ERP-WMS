package com.wms.system.repository;

import com.wms.system.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Inventory Batch Repository
 *
 * V3.0 Architecture: Single Source of Truth for Stock Data
 *
 * This repository is the ONLY source for inventory data.
 * All stock queries aggregate from this table.
 *
 * Key Queries:
 * - FIFO query (ORDER BY expiryDate ASC) - Core feature
 * - Stock aggregation (SUM(quantity) WHERE active = true)
 * - Expiry alert (soon-to-expire batches)
 * - Batch code lookup (unique identifier)
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 3.0 (Single Source of Truth Architecture)
 */
@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    /**
     * Check if batch code exists (for collision detection)
     *
     * @param batchCode Hashids batch code
     * @return true if exists
     */
    boolean existsByBatchCode(String batchCode);

    /**
     * ⭐ FIFO Query: Find active batches by product (ORDER BY expiryDate ASC)
     *
     * Core FIFO Logic:
     * - Filter: product_id = ? AND active = true AND quantity > 0
     * - Sort: ORDER BY expiry_date ASC (earliest expiry first)
     *
     * Usage: Automatic FIFO outbound (先进先出出库)
     *
     * @param productId Product ID
     * @param active Active flag (true for valid batches)
     * @return List of batches sorted by expiry date (earliest first)
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.product.id = :productId " +
           "AND b.active = :active " +
           "AND b.quantity > 0 " +
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findByProductIdAndActiveOrderByExpiryDateAsc(
        @Param("productId") Long productId,
        @Param("active") Boolean active
    );

    /**
     * ⭐ Stock Aggregation: Calculate total stock for product
     *
     * V3.0 Core Query: SUM(quantity) WHERE active = true
     *
     * @param productId Product ID
     * @return Total stock (null if no batches)
     */
    @Query("SELECT SUM(b.quantity) FROM InventoryBatch b " +
           "WHERE b.product.id = :productId " +
           "AND b.active = true")
    Integer sumQuantityByProduct(@Param("productId") Long productId);

    /**
     * Find all active batches by product
     *
     * @param productId Product ID
     * @param active Active flag
     * @return List of batches
     */
    List<InventoryBatch> findByProductIdAndActive(Long productId, Boolean active);

    /**
     * Find batches by location
     *
     * @param locationId Location ID
     * @param active Active flag
     * @return List of batches
     */
    List<InventoryBatch> findByLocationIdAndActive(Long locationId, Boolean active);

    /**
     * Find expired batches (expiryDate < today AND active = true)
     *
     * @param today Today's date
     * @param active Active flag
     * @return List of expired batches
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.expiryDate < :today " +
           "AND b.active = :active " +
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findExpiredBatches(
        @Param("today") LocalDate today,
        @Param("active") Boolean active
    );

    /**
     * Find soon-to-expire batches (within N days)
     *
     * @param today Today's date
     * @param alertDate Alert date (today + N days)
     * @param active Active flag
     * @return List of soon-to-expire batches
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.expiryDate BETWEEN :today AND :alertDate " +
           "AND b.active = :active " +
           "AND b.quantity > 0 " +
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findSoonToExpireBatches(
        @Param("today") LocalDate today,
        @Param("alertDate") LocalDate alertDate,
        @Param("active") Boolean active
    );

    /**
     * Find batches by entry date range (for financial DSI calculation)
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of batches
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.entryDate BETWEEN :startDate AND :endDate " +
           "ORDER BY b.entryDate ASC")
    List<InventoryBatch> findByEntryDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find batches by purchase order item
     *
     * @param purchaseOrderItemId Purchase order item ID
     * @return List of batches
     */
    List<InventoryBatch> findByPurchaseOrderItemId(Long purchaseOrderItemId);

    /**
     * Find exhausted batches (quantity = 0 AND active = true)
     *
     * Used for cleanup or archival.
     *
     * @param active Active flag
     * @return List of exhausted batches
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.quantity = 0 " +
           "AND b.active = :active")
    List<InventoryBatch> findExhaustedBatches(@Param("active") Boolean active);

    /**
     * Count active batches by product
     *
     * @param productId Product ID
     * @param active Active flag
     * @return Count
     */
    long countByProductIdAndActive(Long productId, Boolean active);

    // ========== V3.3 多库位批次管理查询 (Multi-Location Batch Queries) ==========

    /**
     * ⭐ V3.3: Find batch by batch code and location code
     *
     * 核心查询方法：用于判断 (batchCode + locationCode) 是否已存在
     *
     * 业务场景：
     * - Split Putaway 入库时，检查是否需要创建新记录或累加数量
     * - 如果存在记录，则累加数量
     * - 如果不存在，则创建新记录
     *
     * @param batchCode Batch code
     * @param locationCode Location code
     * @return InventoryBatch or empty
     * @since V3.3
     */
    Optional<InventoryBatch> findByBatchCodeAndLocationCode(String batchCode, String locationCode);

    /**
     * ⭐ V3.3: Find all batches by batch code (across multiple locations)
     *
     * 查询同一批次在不同库位的所有记录
     *
     * 业务场景：
     * - 查询某个批次的总库存（跨库位）
     * - 批次追溯和溯源
     * - 库存盘点
     *
     * @param batchCode Batch code
     * @return List of batches across all locations
     * @since V3.3
     */
    List<InventoryBatch> findByBatchCode(String batchCode);

    /**
     * ⭐ V3.3: Find batches by location code
     *
     * 查询指定库位的所有批次
     *
     * 业务场景：
     * - 库位盘点
     * - 库位移库
     * - 库位清理
     *
     * @param locationCode Location code
     * @param active Active flag
     * @return List of batches at the specified location
     * @since V3.3
     */
    List<InventoryBatch> findByLocationCodeAndActive(String locationCode, Boolean active);

    // ========== V3.1 多单位自动拆包策略查询 (Loose Item First Strategy) ==========

    /**
     * ⭐ V3.1 FIFO Query: Find loose batches (零头批次) by product
     *
     * Loose Batch Definition:
     * - quantity % product.conversionRate != 0（已开箱的批次）
     *
     * Strategy: Loose Item First
     * - 优先查询零头批次（已开箱的）
     * - 按保质期升序排序（FIFO）
     * - 用于避免开新箱，优先消耗已开箱批次
     *
     * Usage: outboundWithFifo() 第一阶段查询
     *
     * @param productId Product ID
     * @param conversionRate Conversion rate (from Product entity)
     * @param active Active flag (true for valid batches)
     * @return List of loose batches sorted by expiry date (earliest first)
     * @since V3.1
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.product.id = :productId " +
           "AND b.active = :active " +
           "AND b.quantity > 0 " +
           "AND MOD(b.quantity, :conversionRate) <> 0 " +  // 零头批次（已开箱）
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findLooseBatchesByProductOrderByExpiryDateAsc(
        @Param("productId") Long productId,
        @Param("conversionRate") Integer conversionRate,
        @Param("active") Boolean active
    );

    /**
     * ⭐ V3.1 FIFO Query: Find full pack batches (整箱批次) by product
     *
     * Full Pack Batch Definition:
     * - quantity % product.conversionRate == 0（未开箱的批次）
     *
     * Strategy: Open new pack only when loose items are insufficient
     * - 仅在零头不足时查询整箱批次
     * - 按保质期升序排序（FIFO）
     * - 避免过早开箱，减少零头批次数量
     *
     * Usage: outboundWithFifo() 第二阶段查询
     *
     * @param productId Product ID
     * @param conversionRate Conversion rate (from Product entity)
     * @param active Active flag (true for valid batches)
     * @return List of full pack batches sorted by expiry date (earliest first)
     * @since V3.1
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.product.id = :productId " +
           "AND b.active = :active " +
           "AND b.quantity > 0 " +
           "AND MOD(b.quantity, :conversionRate) = 0 " +  // 整箱批次（未开箱）
           "ORDER BY b.expiryDate ASC")
    List<InventoryBatch> findFullPackBatchesByProductOrderByExpiryDateAsc(
        @Param("productId") Long productId,
        @Param("conversionRate") Integer conversionRate,
        @Param("active") Boolean active
    );
}
