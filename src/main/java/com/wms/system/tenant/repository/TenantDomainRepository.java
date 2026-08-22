package com.wms.system.tenant.repository;

import com.wms.system.tenant.model.TenantDomain;
import com.wms.system.tenant.model.TenantDomainStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, Long> {
    Optional<TenantDomain> findByHostnameAndStatus(String hostname, TenantDomainStatus status);
    boolean existsByHostname(String hostname);
    Optional<TenantDomain> findByTenantIdAndPrimaryTrue(Long tenantId);
}
