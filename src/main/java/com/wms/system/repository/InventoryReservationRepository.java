package com.wms.system.repository;

import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findBySalesOrderId(Long salesOrderId);

    List<InventoryReservation> findBySalesOrderItemId(Long salesOrderItemId);

    List<InventoryReservation> findByInventoryBatchId(Long inventoryBatchId);

    List<InventoryReservation> findBySalesOrderIdAndStatusIn(
        Long salesOrderId,
        Collection<ReservationStatus> statuses
    );

    @Query("""
        SELECT COALESCE(SUM(r.reservedQty - r.consumedQty - r.releasedQty), 0)
        FROM InventoryReservation r
        WHERE r.inventoryBatchId = :batchId
          AND r.status IN :statuses
        """)
    Integer sumOpenReservedQuantityByBatchId(
        @Param("batchId") Long batchId,
        @Param("statuses") Collection<ReservationStatus> statuses
    );
}
