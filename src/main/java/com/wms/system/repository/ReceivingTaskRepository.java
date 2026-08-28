package com.wms.system.repository;

import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReceivingTaskRepository extends JpaRepository<ReceivingTask, Long> {
    Optional<ReceivingTask> findByPurchaseAsnId(Long purchaseAsnId);
    Optional<ReceivingTask> findByOtherInboundApplicationId(Long otherInboundApplicationId);
    List<ReceivingTask> findByReceivingPlanId(Long receivingPlanId);
    List<ReceivingTask> findAllByOrderByIdDesc();
    List<ReceivingTask> findByStatusInOrderByIdDesc(Collection<ReceivingTaskStatus> statuses);
    List<ReceivingTask> findByWarehouseIdInOrderByIdDesc(Collection<Long> warehouseIds);
    List<ReceivingTask> findByStatusAndWarehouseIdInOrderByIdDesc(
        ReceivingTaskStatus status,
        Collection<Long> warehouseIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReceivingTask t where t.id = :id")
    Optional<ReceivingTask> findByIdForUpdate(@Param("id") Long id);
}
