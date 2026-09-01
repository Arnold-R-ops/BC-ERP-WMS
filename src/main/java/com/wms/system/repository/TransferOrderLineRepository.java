package com.wms.system.repository;

import com.wms.system.entity.TransferOrderLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TransferOrderLineRepository extends JpaRepository<TransferOrderLine, Long> {
    List<TransferOrderLine> findByTransferOrderIdOrderById(Long transferOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TransferOrderLine l where l.id in :ids order by l.id")
    List<TransferOrderLine> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    boolean existsByBackorderLineIdAndTransferOrderBusinessStatusIn(
        Long backorderLineId,
        Collection<com.wms.system.entity.enums.TransferBusinessStatus> statuses
    );

    boolean existsByBackorderLineIdAndTransferOrderIdNotAndTransferOrderBusinessStatusIn(
        Long backorderLineId,
        Long transferOrderId,
        Collection<com.wms.system.entity.enums.TransferBusinessStatus> statuses
    );
}
