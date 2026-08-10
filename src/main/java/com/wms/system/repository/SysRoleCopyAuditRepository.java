package com.wms.system.repository;

import com.wms.system.entity.SysRoleCopyAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysRoleCopyAuditRepository extends JpaRepository<SysRoleCopyAudit, Long> {
}

