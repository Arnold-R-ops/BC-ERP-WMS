package com.wms.system.repository;

import com.wms.system.entity.InventoryCorrectionPolicyAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryCorrectionPolicyAuditRepository
        extends JpaRepository<InventoryCorrectionPolicyAudit, Long> {
}
