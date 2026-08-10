package com.wms.system.repository;

import com.wms.system.entity.SysPermissionRequestAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysPermissionRequestAuditRepository
    extends JpaRepository<SysPermissionRequestAudit, Long> {

    List<SysPermissionRequestAudit> findByPermissionRequestIdOrderByCreatedAtDesc(
        Long permissionRequestId
    );
}

