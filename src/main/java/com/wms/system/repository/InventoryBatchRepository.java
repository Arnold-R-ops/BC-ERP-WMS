package com.wms.system.repository;

import com.wms.system.entity.InventoryBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    interface InventorySummaryRow {
        Long getProductSkuId();
        String getProductName();
        String getSkuCode();
        String getBarcode();
        String getSpecs();
        String getPackUnit();
        Number getConversionRate();
        Number getSafetyStock();
        Number getTotalQuantity();
        Number getTotalReservedQuantity();
        Number getTotalAvailableQuantity();
        LocalDate getFurthestExpiryDate();
        String getWarehouseNames();
    }

    interface InventoryDetailRow {
        String getBatchCode();
        String getWarehouseName();
        String getLocationCode();
        Number getQuantity();
        Number getReservedQuantity();
        Number getAvailableQuantity();
        LocalDate getExpiryDate();
        Number getConversionRate();
    }

    @Query(
        value = """
            SELECT
                p.id AS "productSkuId",
                p.name AS "productName",
                p.sku_code AS "skuCode",
                p.barcode AS "barcode",
                p.specs AS "specs",
                p.pack_unit AS "packUnit",
                p.conversion_rate AS "conversionRate",
                p.safety_stock AS "safetyStock",
                COALESCE(SUM(CASE WHEN b.id IS NOT NULL THEN b.quantity ELSE 0 END), 0) AS "totalQuantity",
                COALESCE(SUM(CASE WHEN b.id IS NOT NULL THEN b.reserved_quantity ELSE 0 END), 0) AS "totalReservedQuantity",
                COALESCE(SUM(CASE WHEN b.id IS NOT NULL THEN b.quantity - b.reserved_quantity ELSE 0 END), 0) AS "totalAvailableQuantity",
                MAX(b.expiry_date) AS "furthestExpiryDate",
                COALESCE(string_agg(DISTINCT w.name, ','), '') AS "warehouseNames"
            FROM product_skus p
            CROSS JOIN (SELECT LOWER(CAST(:search AS TEXT)) AS term) filter
            LEFT JOIN inventory_batch b
                ON b.product_sku_id = p.id
                AND b.active = true
                AND b.quantity > 0
            LEFT JOIN locations l ON l.id = b.location_id
            LEFT JOIN warehouses w ON w.id = l.warehouse_id
            WHERE p.is_deleted = false
              AND (
                filter.term = ''
                OR LOWER(p.name) LIKE CONCAT('%', filter.term, '%')
                OR LOWER(p.sku_code) LIKE CONCAT('%', filter.term, '%')
                OR LOWER(p.barcode) LIKE CONCAT('%', filter.term, '%')
              )
            GROUP BY p.id, p.name, p.sku_code, p.barcode, p.specs, p.pack_unit, p.conversion_rate, p.safety_stock
            ORDER BY p.id
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM product_skus p
            CROSS JOIN (SELECT LOWER(CAST(:search AS TEXT)) AS term) filter
            WHERE p.is_deleted = false
              AND (
                filter.term = ''
                OR LOWER(p.name) LIKE CONCAT('%', filter.term, '%')
                OR LOWER(p.sku_code) LIKE CONCAT('%', filter.term, '%')
                OR LOWER(p.barcode) LIKE CONCAT('%', filter.term, '%')
              )
            """,
        nativeQuery = true
    )
    Page<InventorySummaryRow> findInventorySummaryRows(
        @Param("search") String search,
        Pageable pageable
    );

    @Query("""
        SELECT
            b.batchCode AS batchCode,
            COALESCE(w.name, 'Unknown Warehouse') AS warehouseName,
            b.locationCode AS locationCode,
            b.quantity AS quantity,
            b.reservedQuantity AS reservedQuantity,
            (b.quantity - b.reservedQuantity) AS availableQuantity,
            b.expiryDate AS expiryDate,
            p.conversionRate AS conversionRate
        FROM InventoryBatch b
        JOIN b.productSku p
        LEFT JOIN b.location l
        LEFT JOIN l.warehouse w
        WHERE b.productSku.id = :productSkuId
          AND b.active = true
          AND b.quantity > 0
        ORDER BY b.expiryDate DESC
        """)
    List<InventoryDetailRow> findActiveDetailRowsByProductSkuId(@Param("productSkuId") Long productSkuId);

    @Query("""
        SELECT
            b.batchCode AS batchCode,
            COALESCE(w.name, 'Unknown Warehouse') AS warehouseName,
            b.locationCode AS locationCode,
            b.quantity AS quantity,
            b.reservedQuantity AS reservedQuantity,
            (b.quantity - b.reservedQuantity) AS availableQuantity,
            b.expiryDate AS expiryDate,
            p.conversionRate AS conversionRate
        FROM InventoryBatch b
        JOIN b.productSku p
        LEFT JOIN b.location l
        LEFT JOIN l.warehouse w
        WHERE b.locationCode = :locationCode
          AND b.active = true
          AND b.quantity > 0
        ORDER BY b.expiryDate DESC
        """)
    List<InventoryDetailRow> findActiveDetailRowsByLocationCode(@Param("locationCode") String locationCode);

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
     * - Filter: product_sku_id = ? AND active = true AND quantity > 0
     * - Sort: ORDER BY expiry_date ASC (earliest expiry first)
     *
     * Usage: Automatic FIFO outbound (先进先出出库)
     *
     * @param productSkuId ProductSku ID
     * @param active Active flag (true for valid batches)
     * @return List of batches sorted by expiry date (earliest first)
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.productSku.id = :productSkuId " +
           "AND b.active = :active " +
           "AND b.location IS NOT NULL " +
           "AND b.quantity > 0 " +
           "ORDER BY b.expiryDate ASC, b.quantity ASC, b.id ASC")
    List<InventoryBatch> findByProductSkuIdAndActiveOrderByExpiryDateAsc(
        @Param("productSkuId") Long productSkuId,
        @Param("active") Boolean active
    );

    /**
     * ⭐ Stock Aggregation: Calculate total stock for product
     *
     * V3.0 Core Query: SUM(quantity) WHERE active = true
     *
     * @param productSkuId ProductSku ID
     * @return Total stock (null if no batches)
     */
    @Query("SELECT SUM(b.quantity) FROM InventoryBatch b " +
           "WHERE b.productSku.id = :productSkuId " +
           "AND b.location IS NOT NULL " +
           "AND b.active = true")
    Integer sumQuantityByProductSku(@Param("productSkuId") Long productSkuId);

    @Query("SELECT SUM(b.reservedQuantity) FROM InventoryBatch b " +
           "WHERE b.productSku.id = :productSkuId " +
           "AND b.location IS NOT NULL " +
           "AND b.active = true")
    Integer sumReservedQuantityByProductSku(@Param("productSkuId") Long productSkuId);

    @Query("SELECT SUM(b.quantity - b.reservedQuantity) FROM InventoryBatch b " +
           "WHERE b.productSku.id = :productSkuId " +
           "AND b.location IS NOT NULL " +
           "AND b.active = true")
    Integer sumAvailableQuantityByProductSku(@Param("productSkuId") Long productSkuId);

    /**
     * Find all active batches by product
     *
     * @param productSkuId ProductSku ID
     * @param active Active flag
     * @return List of batches
     */
    List<InventoryBatch> findByProductSkuIdAndActive(Long productSkuId, Boolean active);

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
           "AND b.location IS NOT NULL " +
           "ORDER BY b.expiryDate ASC, b.quantity ASC, b.id ASC")
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
           "AND b.location IS NOT NULL " +
           "AND b.quantity > 0 " +
           "ORDER BY b.expiryDate ASC, b.quantity ASC, b.id ASC")
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

    boolean existsByPurchaseOrderItemId(Long purchaseOrderItemId);

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
     * @param productSkuId ProductSku ID
     * @param active Active flag
     * @return Count
     */
    long countByProductSkuIdAndActive(Long productSkuId, Boolean active);

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
     * @param productSkuId ProductSku ID
     * @param conversionRate Conversion rate (from ProductSku entity)
     * @param active Active flag (true for valid batches)
     * @return List of loose batches sorted by expiry date (earliest first)
     * @since V3.1
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.productSku.id = :productSkuId " +
           "AND b.active = :active " +
           "AND b.location IS NOT NULL " +
           "AND b.quantity > 0 " +
           "AND MOD(b.quantity, :conversionRate) <> 0 " +  // 零头批次（已开箱）
           "ORDER BY b.expiryDate ASC, b.quantity ASC, b.id ASC")
    List<InventoryBatch> findLooseBatchesByProductSkuOrderByExpiryDateAsc(
        @Param("productSkuId") Long productSkuId,
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
     * @param productSkuId ProductSku ID
     * @param conversionRate Conversion rate (from ProductSku entity)
     * @param active Active flag (true for valid batches)
     * @return List of full pack batches sorted by expiry date (earliest first)
     * @since V3.1
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.productSku.id = :productSkuId " +
           "AND b.active = :active " +
           "AND b.location IS NOT NULL " +
           "AND b.quantity > 0 " +
           "AND MOD(b.quantity, :conversionRate) = 0 " +  // 整箱批次（未开箱）
           "ORDER BY b.expiryDate ASC, b.quantity ASC, b.id ASC")
    List<InventoryBatch> findFullPackBatchesByProductSkuOrderByExpiryDateAsc(
        @Param("productSkuId") Long productSkuId,
        @Param("conversionRate") Integer conversionRate,
        @Param("active") Boolean active
    );

    /**
     * Find inventory batches by batch code and location
     *
     * Used for inbound operations to check if a batch already exists in a location
     *
     * @param batchCode Batch code
     * @param location Location entity
     * @return List of matching batches
     */
    @Query("SELECT b FROM InventoryBatch b WHERE b.batchCode = :batchCode AND b.location = :location")
    List<InventoryBatch> findByBatchCodeAndLocation(
        @Param("batchCode") String batchCode,
        @Param("location") com.wms.system.entity.Location location
    );

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM InventoryBatch b " +
           "WHERE b.location.id = :locationId " +
           "AND b.active = true " +
           "AND b.quantity > 0")
    Integer sumActiveQuantityByLocationId(@Param("locationId") Long locationId);

    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.location.id = :locationId " +
           "AND b.active = true " +
           "AND b.quantity > 0")
    List<InventoryBatch> findPositiveActiveBatchesByLocationId(@Param("locationId") Long locationId);

    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.location.id = :locationId " +
           "AND b.productSku.id = :productSkuId " +
           "AND b.active = true " +
           "AND b.quantity > 0")
    List<InventoryBatch> findPositiveActiveBatchesByLocationAndProduct(
        @Param("locationId") Long locationId,
        @Param("productSkuId") Long productSkuId
    );
}
