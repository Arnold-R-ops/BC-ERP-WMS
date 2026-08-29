package com.wms.system.repository;

import com.wms.system.entity.ReceivingInspectionResultLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivingInspectionResultLineRepository
    extends JpaRepository<ReceivingInspectionResultLine, Long> {
}
