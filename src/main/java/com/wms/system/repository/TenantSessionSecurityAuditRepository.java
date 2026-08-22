package com.wms.system.repository;

import com.wms.system.entity.TenantSessionSecurityAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantSessionSecurityAuditRepository
        extends JpaRepository<TenantSessionSecurityAudit, Long> {
    List<TenantSessionSecurityAudit> findByCompanyIdOrderByCreatedAtDesc(
        Long companyId,
        Pageable pageable
    );
}
