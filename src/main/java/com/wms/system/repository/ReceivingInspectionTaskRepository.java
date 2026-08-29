package com.wms.system.repository;

import com.wms.system.entity.ReceivingInspectionTask;
import com.wms.system.entity.enums.ReceivingInspectionTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ReceivingInspectionTaskRepository extends JpaRepository<ReceivingInspectionTask, Long> {
    Optional<ReceivingInspectionTask> findByReceiptEventId(Long receiptEventId);

    List<ReceivingInspectionTask> findAllByOrderByIdDesc();

    List<ReceivingInspectionTask> findByStatusOrderByIdDesc(ReceivingInspectionTaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ReceivingInspectionTask t where t.id = :id")
    Optional<ReceivingInspectionTask> findByIdForUpdate(@Param("id") Long id);

    @Query("select t.warehouse.id from ReceivingInspectionTask t where t.id = :id")
    Optional<Long> findWarehouseId(@Param("id") Long id);
}
