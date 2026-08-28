package com.wms.system.repository;

import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderAllocationRepository extends JpaRepository<PurchaseOrderAllocation, Long> {

    Optional<PurchaseOrderAllocation> findByPurchaseOrderItemIdAndWarehouseId(
        Long purchaseOrderItemId,
        Long warehouseId
    );

    List<PurchaseOrderAllocation> findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PurchaseOrderAllocation a where a.purchaseOrderItem.id = :purchaseOrderItemId order by a.id")
    List<PurchaseOrderAllocation> findByPurchaseOrderItemIdForUpdate(
        @Param("purchaseOrderItemId") Long purchaseOrderItemId
    );

    /**
     * Loads every allocation required by a page of receiving plans in one
     * query. All to-one associations used by the warehouse-safe projection are
     * fetch-joined to prevent per-plan/per-line lazy-load queries.
     */
    @Query("""
        select allocation
        from PurchaseOrderAllocation allocation
        join fetch allocation.purchaseOrderItem item
        join fetch item.purchaseOrder purchaseOrder
        join fetch item.productSku productSku
        join fetch allocation.warehouse warehouse
        where exists (
            select plan.id
            from ReceivingPlan plan
            where plan.id in :receivingPlanIds
              and plan.purchaseOrder = purchaseOrder
              and plan.warehouse = warehouse
        )
        order by purchaseOrder.id asc, warehouse.id asc, allocation.id asc
        """)
    List<PurchaseOrderAllocation> findForReceivingPlanProjection(
        @Param("receivingPlanIds") Collection<Long> receivingPlanIds
    );

    @Query("""
        select coalesce(sum(a.plannedQuantity - a.receivedQuantity - a.closedQuantity), 0)
        from PurchaseOrderAllocation a
        where a.purchaseOrderItem.id = :purchaseOrderItemId
          and a.status in :statuses
        """)
    Long sumOutstandingByPurchaseOrderItemId(
        @Param("purchaseOrderItemId") Long purchaseOrderItemId,
        @Param("statuses") Collection<PurchaseOrderAllocationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PurchaseOrderAllocation a where a.id = :id")
    Optional<PurchaseOrderAllocation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from PurchaseOrderAllocation a
        where a.purchaseOrderItem.id = :purchaseOrderItemId and a.warehouse.id = :warehouseId
        """)
    Optional<PurchaseOrderAllocation> findByItemAndWarehouseForUpdate(
        @Param("purchaseOrderItemId") Long purchaseOrderItemId,
        @Param("warehouseId") Long warehouseId
    );
}
