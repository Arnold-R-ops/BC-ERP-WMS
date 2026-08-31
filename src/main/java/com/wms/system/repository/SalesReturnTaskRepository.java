package com.wms.system.repository;

import com.wms.system.entity.SalesReturnTask;
import com.wms.system.entity.enums.RmaTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesReturnTaskRepository extends JpaRepository<SalesReturnTask, Long> {
    Optional<SalesReturnTask> findByRmaId(Long rmaId);
    List<SalesReturnTask> findAllByOrderByPriorityAscIdDesc();
    List<SalesReturnTask> findByStatusOrderByPriorityAscIdDesc(RmaTaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SalesReturnTask t where t.id = :id")
    Optional<SalesReturnTask> findByIdForUpdate(@Param("id") Long id);
}
