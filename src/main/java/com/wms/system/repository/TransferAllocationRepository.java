package com.wms.system.repository;

import com.wms.system.entity.TransferAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TransferAllocationRepository extends JpaRepository<TransferAllocation, Long> {
    List<TransferAllocation> findByTransferLineTransferOrderIdOrderById(Long transferOrderId);
    List<TransferAllocation> findByTransferLineIdOrderById(Long transferLineId);

    @Query("select a from TransferAllocation a join fetch a.transferLine l "
        + "join fetch l.transferOrder where a.sourceBatch.id in :batchIds order by a.id")
    List<TransferAllocation> findBySourceBatchIds(@Param("batchIds") Collection<Long> batchIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TransferAllocation a where a.sourceBatch.id in :batchIds order by a.id")
    List<TransferAllocation> findBySourceBatchIdsForUpdate(@Param("batchIds") Collection<Long> batchIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TransferAllocation a where a.id in :ids order by a.id")
    List<TransferAllocation> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TransferAllocation a where a.transferLine.id in :lineIds order by a.id")
    List<TransferAllocation> findByTransferLineIdsForUpdate(@Param("lineIds") Collection<Long> lineIds);
}
