package com.wms.system.repository;

import com.wms.system.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Purchase Order Item Repository
 *
 * Data access layer for PurchaseOrderItem entity.
 *
 * Key Queries:
 * - Find by purchase order (list all items in order)
 * - Find by product (track product procurement)
 * - Find partially received items (分批入库查询)
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management)
 */
@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    /**
     * Find all items for a purchase order
     *
     * @param purchaseOrderId Purchase order ID
     * @return List of items
     */
    List<PurchaseOrderItem> findByPurchaseOrderId(Long purchaseOrderId);

    /**
     * Find items by product
     *
     * @param productId Product ID
     * @return List of items
     */
    List<PurchaseOrderItem> findByProductId(Long productId);

    /**
     * Find partially received items (receivedQuantity > 0 AND < orderedQuantity)
     *
     * Used to track pending partial receipts.
     *
     * @param purchaseOrderId Purchase order ID
     * @return List of partially received items
     */
    @Query("SELECT item FROM PurchaseOrderItem item " +
           "WHERE item.purchaseOrder.id = :purchaseOrderId " +
           "AND item.receivedQuantity > 0 " +
           "AND item.receivedQuantity < item.orderedQuantity")
    List<PurchaseOrderItem> findPartiallyReceivedItems(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * Find fully received items (receivedQuantity == orderedQuantity)
     *
     * @param purchaseOrderId Purchase order ID
     * @return List of fully received items
     */
    @Query("SELECT item FROM PurchaseOrderItem item " +
           "WHERE item.purchaseOrder.id = :purchaseOrderId " +
           "AND item.receivedQuantity = item.orderedQuantity")
    List<PurchaseOrderItem> findFullyReceivedItems(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * Find unreceived items (receivedQuantity == 0)
     *
     * @param purchaseOrderId Purchase order ID
     * @return List of unreceived items
     */
    @Query("SELECT item FROM PurchaseOrderItem item " +
           "WHERE item.purchaseOrder.id = :purchaseOrderId " +
           "AND item.receivedQuantity = 0")
    List<PurchaseOrderItem> findUnreceivedItems(@Param("purchaseOrderId") Long purchaseOrderId);
}
