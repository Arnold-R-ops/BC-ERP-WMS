package com.wms.system.tenant.repository;

import com.wms.system.tenant.model.TenantPurgeJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantPurgeJobRepository extends JpaRepository<TenantPurgeJob, Long> {
    Optional<TenantPurgeJob> findByTenantId(Long tenantId);
}
