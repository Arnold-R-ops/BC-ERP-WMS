package com.wms.system.repository;

import com.wms.system.entity.InventoryDispositionLine;
import com.wms.system.entity.enums.InventoryDispositionStatus;
import com.wms.system.entity.enums.InventoryDispositionType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;

public interface InventoryDispositionLineRepository extends JpaRepository<InventoryDispositionLine, Long> {
    List<InventoryDispositionLine> findByOrderIdOrderByIdAsc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryDispositionLine l where l.id in :ids order by l.id")
    List<InventoryDispositionLine> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("""
        select coalesce(sum(l.quantity - l.executedQuantity), 0)
        from InventoryDispositionLine l
        where l.sourceBatchId = :batchId
          and l.order.dispositionType = :type
          and l.order.status in :statuses
        """)
    Long sumOutstandingByBatch(
        @Param("batchId") Long batchId,
        @Param("type") InventoryDispositionType type,
        @Param("statuses") Collection<InventoryDispositionStatus> statuses
    );
}
