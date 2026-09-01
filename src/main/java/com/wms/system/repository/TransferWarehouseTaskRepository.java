package com.wms.system.repository;

import com.wms.system.entity.TransferWarehouseTask;
import com.wms.system.entity.enums.TransferTaskDirection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransferWarehouseTaskRepository extends JpaRepository<TransferWarehouseTask, Long> {
    List<TransferWarehouseTask> findByDirectionOrderByPriorityAscIdDesc(TransferTaskDirection direction);
    List<TransferWarehouseTask> findAllByOrderByPriorityAscIdDesc();
    Optional<TransferWarehouseTask> findByTransferOrderIdAndDirection(Long transferOrderId, TransferTaskDirection direction);
    boolean existsByTransferOrderId(Long transferOrderId);

    @Query("select t.transferOrder.id from TransferWarehouseTask t where t.id = :id")
    Optional<Long> findTransferOrderIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferWarehouseTask t where t.id = :id")
    Optional<TransferWarehouseTask> findByIdForUpdate(@Param("id") Long id);
}
