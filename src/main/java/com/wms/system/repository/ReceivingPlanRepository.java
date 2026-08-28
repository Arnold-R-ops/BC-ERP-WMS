package com.wms.system.repository;

import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ReceivingPlanRepository extends JpaRepository<ReceivingPlan, Long> {
    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    Optional<ReceivingPlan> findByPurchaseOrderIdAndWarehouseId(Long purchaseOrderId, Long warehouseId);

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findByPurchaseOrderIdOrderByIdAsc(Long purchaseOrderId);

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findByPurchaseOrderIdAndWarehouseIdInOrderByIdAsc(
        Long purchaseOrderId,
        Collection<Long> warehouseIds
    );

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findByWarehouseIdInOrderByIdDesc(Collection<Long> warehouseIds);

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findByStatusOrderByIdDesc(ReceivingPlanStatus status);

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findByStatusInOrderByIdDesc(
        Collection<ReceivingPlanStatus> statuses
    );

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findByStatusAndWarehouseIdInOrderByIdDesc(
        ReceivingPlanStatus status,
        Collection<Long> warehouseIds
    );

    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    List<ReceivingPlan> findByStatusInAndWarehouseIdInOrderByIdDesc(
        Collection<ReceivingPlanStatus> statuses,
        Collection<Long> warehouseIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"purchaseOrder", "warehouse"})
    @Query("select p from ReceivingPlan p where p.id = :id")
    Optional<ReceivingPlan> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p from ReceivingPlan p
        where p.purchaseOrder.id = :purchaseOrderId and p.warehouse.id = :warehouseId
        """)
    Optional<ReceivingPlan> findByPurchaseOrderAndWarehouseForUpdate(
        @Param("purchaseOrderId") Long purchaseOrderId,
        @Param("warehouseId") Long warehouseId
    );
}
