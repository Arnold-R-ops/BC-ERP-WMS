package com.wms.system.repository;

import com.wms.system.entity.SysRoleGovernanceAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysRoleGovernanceAuditRepository extends JpaRepository<SysRoleGovernanceAudit, Long> {
    List<SysRoleGovernanceAudit> findByRoleIdOrderByCreatedAtDesc(Long roleId);
    List<SysRoleGovernanceAudit> findByCompanyIdAndRoleIdOrderByCreatedAtDesc(
        Long companyId, Long roleId);
}
