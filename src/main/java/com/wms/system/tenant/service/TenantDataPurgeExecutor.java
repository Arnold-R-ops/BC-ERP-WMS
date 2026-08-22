package com.wms.system.tenant.service;

import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class TenantDataPurgeExecutor {
    private final TenantRepository tenantRepository;
    private final TenantPurgeJobRepository purgeJobRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Long tenantId) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
            .orElseThrow(() -> new IllegalStateException("Company no longer exists"));
        if (tenant.getStatus() == TenantStatus.PURGED) return;
        if (tenant.getStatus() != TenantStatus.PURGE_PENDING) {
            throw new IllegalStateException("Company is not ready for purge");
        }

        Long deletedRows = jdbcTemplate.queryForObject("select bcwms_purge_company_data(?)", Long.class, tenantId);
        tenant.setDisplayName("Purged company #" + tenantId);
        tenant.setSlug("purged-" + tenantId);
        tenant.setTenantCode("PURGED-" + tenantId);
        tenant.setTimezone("UTC");
        tenant.setLocale("und");
        tenant.setStatus(TenantStatus.PURGED);
        tenantRepository.save(tenant);

        TenantPurgeJob job = purgeJobRepository.findByTenantId(tenantId).orElseThrow();
        job.setStatus("COMPLETED");
        job.setCompletedAt(OffsetDateTime.now());
        job.setLeaseExpiresAt(null);
        job.setLastError(null);
        purgeJobRepository.save(job);
        jdbcTemplate.update("insert into tenant_purge_audit_logs "
            + "(tenant_id,event_type,result,attempt_count,deleted_row_count,created_at) "
            + "values (?,?,?,?,?,current_timestamp)",
            tenantId, "COMPLETED", "SUCCESS", job.getAttemptCount(), deletedRows);
    }
}
