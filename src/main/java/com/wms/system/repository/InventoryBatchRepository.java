package com.wms.system.repository;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.enums.InventoryQualityStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

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

    boolean existsByBatchCodeAndLocationId(String batchCode, Long locationId);

    /**
     * Loads one batch under a database write lock for authoritative inventory
     * posting. The surrounding service transaction owns the lock lifetime.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.id = :batchId")
    Optional<InventoryBatch> findByIdForPosting(@Param("batchId") Long batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.id IN :batchIds ORDER BY b.id")
    List<InventoryBatch> findAllByIdForPosting(@Param("batchIds") Collection<Long> batchIds);

    /** Warehouse-scoped FEFO stock locked for transfer allocation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM InventoryBatch b
        JOIN FETCH b.productSku p
        JOIN FETCH b.location l
        JOIN FETCH l.warehouse w
        WHERE w.id = :warehouseId
          AND p.id = :productSkuId
          AND b.active = true
          AND b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE
          AND b.quantity > b.reservedQuantity
          AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)
        ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
          b.expiryDate ASC, b.entryDate ASC, b.quantity ASC, b.id ASC
        """)
    List<InventoryBatch> findTransferCandidatesForUpdate(
        @Param("warehouseId") Long warehouseId,
        @Param("productSkuId") Long productSkuId
    );

    @Query("""
        SELECT b FROM InventoryBatch b
        JOIN FETCH b.productSku p
        JOIN FETCH b.location l
        JOIN FETCH l.warehouse w
        WHERE w.id = :warehouseId
          AND p.id = :productSkuId
          AND b.active = true
          AND b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE
          AND b.quantity > b.reservedQuantity
          AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)
        ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
          b.expiryDate ASC, b.entryDate ASC, b.quantity ASC, b.id ASC
        """)
    List<InventoryBatch> findTransferCandidates(
        @Param("warehouseId") Long warehouseId,
        @Param("productSkuId") Long productSkuId
    );

    @Query("""
        SELECT b FROM InventoryBatch b
        JOIN FETCH b.productSku p
        JOIN FETCH b.location l
        JOIN FETCH l.warehouse w
        WHERE b.active = true
          AND b.quantity > 0
          AND (:search = ''
            OR LOWER(b.batchCode) LIKE CONCAT('%', :search, '%')
            OR LOWER(COALESCE(b.externalBatchCode, '')) LIKE CONCAT('%', :search, '%')
            OR LOWER(COALESCE(b.qualitySegmentNo, '')) LIKE CONCAT('%', :search, '%')
            OR LOWER(p.skuCode) LIKE CONCAT('%', :search, '%')
            OR LOWER(p.name) LIKE CONCAT('%', :search, '%'))
        ORDER BY b.id DESC
        """)
    List<InventoryBatch> findQualityCandidates(@Param("search") String search);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM InventoryBatch b
        JOIN FETCH b.productSku p
        JOIN FETCH b.location l
        JOIN FETCH l.warehouse w
        WHERE p.id = :productSkuId
          AND b.id NOT IN :excludedBatchIds
          AND b.active = true
          AND b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE
          AND b.quantity > b.reservedQuantity
          AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)
        ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
          b.expiryDate ASC, b.quantity ASC, b.id ASC
        """)
    List<InventoryBatch> findSaleableForQualityReallocationForUpdate(
        @Param("productSkuId") Long productSkuId,
        @Param("excludedBatchIds") Collection<Long> excludedBatchIds
    );

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
        Number getInTransitQuantity();
        Number getTotalReservedQuantity();
        Number getTotalAvailableQuantity();
        Number getPendingInspectionQuantity();
        Number getQuarantinedQuantity();
        Number getDamagedQuantity();
        Number getExpiredQuantity();
        Number getRecallHoldQuantity();
        Number getReturnInspectionQuantity();
        LocalDate getFurthestExpiryDate();
        String getWarehouseNames();
    }

    interface InventoryDetailRow {
        Long getBatchId();
        String getBatchCode();
        String getWarehouseName();
        String getLocationCode();
        Number getQuantity();
        Number getReservedQuantity();
        Number getAvailableQuantity();
        InventoryQualityStatus getQualityStatus();
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
                COALESCE((
                    SELECT SUM(tdl.quantity - tdl.received_quantity - tdl.shortage_closed_quantity)
                    FROM transfer_dispatch_line tdl
                    JOIN transfer_order_line tol
                      ON tol.company_id = tdl.company_id AND tol.id = tdl.transfer_line_id
                    WHERE tdl.company_id = :companyId AND tol.product_sku_id = p.id
                ), 0) AS "inTransitQuantity",
                COALESCE(SUM(CASE WHEN b.id IS NOT NULL THEN b.reserved_quantity ELSE 0 END), 0) AS "totalReservedQuantity",
                COALESCE(SUM(CASE
                    WHEN b.quality_status = 'AVAILABLE'
                     AND (b.expiry_date IS NULL OR b.expiry_date >= CURRENT_DATE)
                    THEN b.quantity - b.reserved_quantity ELSE 0 END), 0) AS "totalAvailableQuantity",
                COALESCE(SUM(CASE WHEN b.quality_status = 'PENDING_INSPECTION' THEN b.quantity ELSE 0 END), 0) AS "pendingInspectionQuantity",
                COALESCE(SUM(CASE WHEN b.quality_status = 'QUARANTINED' THEN b.quantity ELSE 0 END), 0) AS "quarantinedQuantity",
                COALESCE(SUM(CASE WHEN b.quality_status = 'DAMAGED' THEN b.quantity ELSE 0 END), 0) AS "damagedQuantity",
                COALESCE(SUM(CASE WHEN b.quality_status = 'EXPIRED' THEN b.quantity ELSE 0 END), 0) AS "expiredQuantity",
                COALESCE(SUM(CASE WHEN b.quality_status = 'RECALL_HOLD' THEN b.quantity ELSE 0 END), 0) AS "recallHoldQuantity",
                COALESCE(SUM(CASE WHEN b.quality_status = 'RETURN_INSPECTION' THEN b.quantity ELSE 0 END), 0) AS "returnInspectionQuantity",
                MAX(b.expiry_date) AS "furthestExpiryDate",
                COALESCE(string_agg(DISTINCT w.name, ','), '') AS "warehouseNames"
            FROM product_skus p
            CROSS JOIN (SELECT LOWER(CAST(:search AS TEXT)) AS term) filter
            LEFT JOIN inventory_batch b
                ON b.product_sku_id = p.id
                AND b.company_id = :companyId
                AND b.active = true
                AND b.quantity > 0
            LEFT JOIN locations l
                ON l.id = b.location_id AND l.company_id = :companyId
            LEFT JOIN warehouses w
                ON w.id = l.warehouse_id AND w.company_id = :companyId
            WHERE p.company_id = :companyId
              AND p.is_deleted = false
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
            WHERE p.company_id = :companyId
              AND p.is_deleted = false
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
        @Param("companyId") Long companyId,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("""
        SELECT
            b.id AS batchId,
            b.batchCode AS batchCode,
            COALESCE(w.name, 'Unknown Warehouse') AS warehouseName,
            b.locationCode AS locationCode,
            b.quantity AS quantity,
            b.reservedQuantity AS reservedQuantity,
            CASE
              WHEN b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE
               AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)
              THEN b.quantity - b.reservedQuantity ELSE 0
            END AS availableQuantity,
            b.qualityStatus AS qualityStatus,
            b.expiryDate AS expiryDate,
            p.conversionRate AS conversionRate
        FROM InventoryBatch b
        JOIN b.productSku p
        LEFT JOIN b.location l
        LEFT JOIN l.warehouse w
        WHERE b.productSku.id = :productSkuId
          AND b.active = true
          AND b.quantity > 0
        ORDER BY
          CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
          b.expiryDate DESC,
          b.id ASC
        """)
    List<InventoryDetailRow> findActiveDetailRowsByProductSkuId(@Param("productSkuId") Long productSkuId);

    @Query("""
        SELECT
            b.id AS batchId,
            b.batchCode AS batchCode,
            COALESCE(w.name, 'Unknown Warehouse') AS warehouseName,
            b.locationCode AS locationCode,
            b.quantity AS quantity,
            b.reservedQuantity AS reservedQuantity,
            CASE
              WHEN b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE
               AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)
              THEN b.quantity - b.reservedQuantity ELSE 0
            END AS availableQuantity,
            b.qualityStatus AS qualityStatus,
            b.expiryDate AS expiryDate,
            p.conversionRate AS conversionRate
        FROM InventoryBatch b
        JOIN b.productSku p
        LEFT JOIN b.location l
        LEFT JOIN l.warehouse w
        WHERE b.locationCode = :locationCode
          AND b.active = true
          AND b.quantity > 0
        ORDER BY
          CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
          b.expiryDate DESC,
          b.id ASC
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
           "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, " +
           "b.expiryDate ASC, b.quantity ASC, b.id ASC")
    List<InventoryBatch> findByProductSkuIdAndActiveOrderByExpiryDateAsc(
        @Param("productSkuId") Long productSkuId,
        @Param("active") Boolean active
    );

    /** Saleable FEFO candidates used by reservations, allocations and sales entry. */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.productSku.id = :productSkuId " +
           "AND b.active = true " +
           "AND b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE " +
           "AND b.location IS NOT NULL " +
           "AND b.quantity > b.reservedQuantity " +
           "AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE) " +
           "ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, " +
           "b.expiryDate ASC, b.quantity ASC, b.id ASC")
    List<InventoryBatch> findSaleableByProductSkuIdOrderByExpiryDateAsc(
        @Param("productSkuId") Long productSkuId
    );

    /**
     * Returns the deliberately wider batch set used only when creating an
     * emergency stock correction. Ordinary inventory and FIFO queries must
     * continue to hide inactive or exhausted batches.
     *
     * <p>An inactive batch is selectable only when its authoritative quantity
     * is zero. The correction posting service remains responsible for enforcing
     * that only a governed positive inventory gain may reactivate it.</p>
     */
    @Query("""
        SELECT b
        FROM InventoryBatch b
        JOIN FETCH b.productSku p
        JOIN FETCH b.location l
        JOIN FETCH l.warehouse w
        WHERE b.productSku.id = :productSkuId
          AND b.location IS NOT NULL
          AND (
            (b.active = true AND b.quantity > 0)
            OR (b.active = false AND b.quantity = 0)
          )
        ORDER BY
          CASE WHEN b.active = true THEN 0 ELSE 1 END,
          CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
          b.expiryDate ASC,
          b.quantity ASC,
          b.id ASC
        """)
    List<InventoryBatch> findCorrectionCandidatesByProductSkuId(
        @Param("productSkuId") Long productSkuId
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
           "AND b.active = true " +
           "AND b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE " +
           "AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)")
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
           "WHERE b.expiryDate IS NOT NULL " +
           "AND b.expiryDate < :today " +
           "AND b.active = :active " +
           "AND b.location IS NOT NULL " +
           "ORDER BY b.expiryDate ASC, b.quantity ASC, b.id ASC")
    List<InventoryBatch> findExpiredBatches(
        @Param("today") LocalDate today,
        @Param("active") Boolean active
    );

    /** Due AVAILABLE batches locked for the automatic tenant-local expiry transition. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM InventoryBatch b
        JOIN FETCH b.productSku p
        JOIN FETCH b.location l
        JOIN FETCH l.warehouse w
        WHERE b.expiryDate IS NOT NULL
          AND b.expiryDate < :businessDate
          AND b.active = true
          AND b.quantity > 0
          AND b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE
        ORDER BY b.expiryDate ASC, b.id ASC
        """)
    List<InventoryBatch> findDueAvailableForExpiryForUpdate(
        @Param("businessDate") LocalDate businessDate
    );

    @Query("""
        SELECT b.id FROM InventoryBatch b
        WHERE b.expiryDate IS NOT NULL
          AND b.expiryDate < :businessDate
          AND b.active = true
          AND b.quantity > 0
          AND b.qualityStatus = com.wms.system.entity.enums.InventoryQualityStatus.AVAILABLE
        ORDER BY b.expiryDate ASC, b.id ASC
        """)
    List<Long> findDueAvailableIdsForExpiry(@Param("businessDate") LocalDate businessDate);

    /**
     * Find soon-to-expire batches (within N days)
     *
     * @param today Today's date
     * @param alertDate Alert date (today + N days)
     * @param active Active flag
     * @return List of soon-to-expire batches
     */
    @Query("SELECT b FROM InventoryBatch b " +
           "WHERE b.expiryDate IS NOT NULL " +
           "AND b.expiryDate BETWEEN :today AND :alertDate " +
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
