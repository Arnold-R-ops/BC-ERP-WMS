package com.wms.system.repository;

import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Purchase Order Repository
 *
 * Data access layer for PurchaseOrder entity.
 *
 * Key Queries:
 * - Find by PO number (unique identifier)
 * - Find by status (workflow queries)
 * - Find by date range (report queries)
 * - Generate next PO number (sequential number generation)
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management)
 */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /**
     * Find purchase order by PO number
     *
     * @param poNumber Purchase order number (e.g., PO-20250113-001)
     * @return Purchase order or empty
     */
    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    /**
     * Check if PO number exists
     *
     * @param poNumber Purchase order number
     * @return true if exists
     */
    boolean existsByPoNumber(String poNumber);

    /**
     * Find purchase orders by status
     *
     * @param status Purchase order status
     * @return List of purchase orders
     */
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    /**
     * Find purchase orders by status (paged)
     *
     * @param status Purchase order status
     * @param pageable Pagination info
     * @return Page of purchase orders
     */
    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    /**
     * Find purchase orders by supplier
     *
     * @param supplier Supplier name
     * @return List of purchase orders
     */
    List<PurchaseOrder> findBySupplierContaining(String supplier);

    /**
     * Find purchase orders by status and supplier
     *
     * @param status Purchase order status
     * @param supplier Supplier name
     * @return List of purchase orders
     */
    List<PurchaseOrder> findByStatusAndSupplierContaining(
        PurchaseOrderStatus status,
        String supplier
    );

    /**
     * Find purchase orders by date range
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of purchase orders
     */
    @Query("SELECT po FROM PurchaseOrder po " +
           "WHERE po.expectedDate BETWEEN :startDate AND :endDate " +
           "ORDER BY po.expectedDate ASC")
    List<PurchaseOrder> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find latest purchase order by date (for PO number generation)
     *
     * Used to generate next sequential PO number: PO-YYYYMMDD-XXX
     *
     * @param datePrefix Date prefix (e.g., "PO-20250113")
     * @return Latest purchase order matching prefix
     */
    @Query("SELECT po FROM PurchaseOrder po " +
           "WHERE po.poNumber LIKE CONCAT(:datePrefix, '%') " +
           "ORDER BY po.poNumber DESC")
    List<PurchaseOrder> findLatestByDatePrefix(@Param("datePrefix") String datePrefix);

    /**
     * Find purchase orders by operator
     *
     * @param operatorId Operator user ID
     * @return List of purchase orders
     */
    List<PurchaseOrder> findByOperatorId(Long operatorId);

    /**
     * Count purchase orders by status
     *
     * @param status Purchase order status
     * @return Count
     */
    long countByStatus(PurchaseOrderStatus status);

    long countBySupplierReference_IdAndStatusIn(
        Long supplierId,
        List<PurchaseOrderStatus> statuses
    );
}
