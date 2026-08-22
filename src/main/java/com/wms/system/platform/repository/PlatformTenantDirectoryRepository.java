package com.wms.system.platform.repository;

import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface PlatformTenantDirectoryRepository extends Repository<Tenant, Long> {
    @Query(value = "select distinct t from Tenant t, PlatformAccessGrant g "
        + "where g.tenantId = t.id and g.granteePlatformUserId = :userId "
        + "and g.capability in :capabilities and g.revokedAt is null "
        + "and g.effectiveFrom <= :now and g.expiresAt > :now "
        + "and t.status <> :excludedStatus and (:status is null or t.status = :status) "
        + "and (:keywordApplied = false or lower(t.displayName) like lower(concat('%', :keyword, '%')) "
        + "or lower(t.tenantCode) like lower(concat('%', :keyword, '%')))",
        countQuery = "select count(distinct t.id) from Tenant t, PlatformAccessGrant g "
            + "where g.tenantId = t.id and g.granteePlatformUserId = :userId "
            + "and g.capability in :capabilities and g.revokedAt is null "
            + "and g.effectiveFrom <= :now and g.expiresAt > :now "
            + "and t.status <> :excludedStatus and (:status is null or t.status = :status) "
            + "and (:keywordApplied = false or lower(t.displayName) like lower(concat('%', :keyword, '%')) "
            + "or lower(t.tenantCode) like lower(concat('%', :keyword, '%')))" )
    Page<Tenant> searchAuthorized(@Param("userId") Long userId,
        @Param("capabilities") List<String> capabilities,
        @Param("now") OffsetDateTime now,
        @Param("keyword") String keyword,
        @Param("keywordApplied") boolean keywordApplied,
        @Param("status") TenantStatus status,
        @Param("excludedStatus") TenantStatus excludedStatus,
        Pageable pageable);
}
