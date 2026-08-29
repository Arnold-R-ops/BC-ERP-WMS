package com.wms.system.repository;

import com.wms.system.entity.ReceivingInspectionResult;
import com.wms.system.entity.enums.ReceivingInspectionResultStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ReceivingInspectionResultRepository extends JpaRepository<ReceivingInspectionResult, Long> {
    Optional<ReceivingInspectionResult> findByIdempotencyKey(String idempotencyKey);

    List<ReceivingInspectionResult> findByInspectionTaskIdOrderByIdDesc(Long inspectionTaskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReceivingInspectionResult r where r.id = :id")
    Optional<ReceivingInspectionResult> findByIdForUpdate(@Param("id") Long id);

    boolean existsByInspectionTaskIdAndStatus(
        Long inspectionTaskId, ReceivingInspectionResultStatus status);
}
