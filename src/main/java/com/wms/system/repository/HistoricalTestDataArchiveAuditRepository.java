package com.wms.system.repository;

import com.wms.system.entity.HistoricalTestDataArchiveAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricalTestDataArchiveAuditRepository
    extends JpaRepository<HistoricalTestDataArchiveAudit, Long> {

    List<HistoricalTestDataArchiveAudit> findBySalesOrderIdOrderByCreatedAtDesc(Long salesOrderId);
}
