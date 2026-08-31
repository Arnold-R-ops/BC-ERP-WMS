package com.wms.system.repository;

import com.wms.system.entity.SalesReturnRmaLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesReturnRmaLineRepository extends JpaRepository<SalesReturnRmaLine, Long> {
    List<SalesReturnRmaLine> findByRmaIdOrderById(Long rmaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SalesReturnRmaLine l where l.id in :ids order by l.id")
    List<SalesReturnRmaLine> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("""
        select coalesce(sum(l.requestedQuantity), 0) from SalesReturnRmaLine l
        where l.outboundTask.id = :outboundTaskId
          and l.rma.businessStatus not in (
            com.wms.system.entity.enums.RmaBusinessStatus.REJECTED,
            com.wms.system.entity.enums.RmaBusinessStatus.CANCELLED)
        """)
    Integer sumActiveRequestedByOutboundTaskId(@Param("outboundTaskId") Long outboundTaskId);
}
