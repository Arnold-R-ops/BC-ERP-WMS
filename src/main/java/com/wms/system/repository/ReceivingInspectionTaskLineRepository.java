package com.wms.system.repository;

import com.wms.system.entity.ReceivingInspectionTaskLine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ReceivingInspectionTaskLineRepository extends JpaRepository<ReceivingInspectionTaskLine, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ReceivingInspectionTaskLine l where l.id in :ids order by l.id")
    List<ReceivingInspectionTaskLine> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
