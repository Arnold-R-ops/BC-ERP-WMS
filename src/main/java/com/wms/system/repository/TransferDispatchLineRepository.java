package com.wms.system.repository;

import com.wms.system.entity.TransferDispatchLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TransferDispatchLineRepository extends JpaRepository<TransferDispatchLine, Long> {
    List<TransferDispatchLine> findByDispatchTransferOrderIdOrderById(Long transferOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TransferDispatchLine l where l.id in :ids order by l.id")
    List<TransferDispatchLine> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
