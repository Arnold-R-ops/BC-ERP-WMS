package com.wms.system.tenant.service;

import com.wms.system.platform.config.PlatformAccessProperties;
import com.wms.system.platform.model.PlatformExportJob;
import com.wms.system.platform.repository.PlatformExportJobRepository;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantDataPurgeService {
    private final TenantRepository tenantRepository;
    private final TenantPurgeJobRepository purgeJobRepository;
    private final PlatformExportJobRepository exportJobRepository;
    private final PlatformAccessProperties accessProperties;
    private final TenantLifecycleService lifecycleService;
    private final TenantDataPurgeExecutor executor;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    public void purgeDueCompany(Long tenantId) {
        Tenant tenant = prepare(tenantId);
        if (tenant == null) return;
        deleteExportArtifacts(tenantId);
        TenantContext context = new TenantContext(tenant.getId(), tenant.getSlug(),
            tenant.getSlug() + ".bcwms.com", RequestSurface.TENANT, TenantStatus.PURGE_PENDING);
        try {
            TenantContextHolder.runWithResolvedTenant(context, () -> {
                executor.execute(tenantId);
                return null;
            });
        } catch (RuntimeException exception) {
            recordFailure(tenantId, exception);
            throw exception;
        }
    }

    protected Tenant prepare(Long tenantId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Tenant tenant = tenantRepository.findByIdForUpdate(tenantId).orElseThrow();
            if (tenant.getStatus() == TenantStatus.PURGED) return null;
            if (tenant.getStatus() == TenantStatus.CLOSED) lifecycleService.markPurgePending(tenant);
            if (tenant.getStatus() != TenantStatus.PURGE_PENDING) {
                throw new IllegalStateException("Company is not due for purge");
            }
            if (tenant.getPurgeDueAt() == null || OffsetDateTime.now().isBefore(tenant.getPurgeDueAt())) {
                throw new IllegalStateException("Company retention deadline has not expired");
            }
            tenantRepository.save(tenant);
            TenantPurgeJob job = purgeJobRepository.findByTenantId(tenantId).orElseGet(() ->
                TenantPurgeJob.builder().tenantId(tenantId).status("PENDING").build());
            OffsetDateTime now = OffsetDateTime.now();
            if ("RUNNING".equals(job.getStatus()) && job.getLeaseExpiresAt() != null
                    && job.getLeaseExpiresAt().isAfter(now)) return null;
            job.setStatus("RUNNING");
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setStartedAt(now);
            job.setLeaseExpiresAt(now.plusMinutes(30));
            job.setCompletedAt(null);
            job.setLastError(null);
            purgeJobRepository.save(job);
            jdbcTemplate.update("insert into tenant_purge_audit_logs "
                + "(tenant_id,event_type,result,attempt_count,created_at) values (?,?,?,?,current_timestamp)",
                tenantId, "STARTED", "RUNNING", job.getAttemptCount());
            return tenant;
        });
    }

    private void deleteExportArtifacts(Long tenantId) {
        Path root = accessProperties.getExportDirectory().toAbsolutePath().normalize();
        List<PlatformExportJob> jobs = exportJobRepository.findByTargetTenantId(tenantId);
        for (PlatformExportJob job : jobs) {
            if (job.getFilePath() == null) continue;
            Path file = Path.of(job.getFilePath()).toAbsolutePath().normalize();
            if (!root.equals(file.getParent())) {
                throw new IllegalStateException("Export artifact path is outside the managed directory");
            }
            try { Files.deleteIfExists(file); }
            catch (Exception exception) { throw new IllegalStateException("Unable to delete export artifact", exception); }
        }
    }

    protected void recordFailure(Long tenantId, RuntimeException exception) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            purgeJobRepository.findByTenantId(tenantId).ifPresent(job -> {
                job.setStatus("FAILED");
                job.setLeaseExpiresAt(null);
                String message = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
                job.setLastError(message.substring(0, Math.min(1000, message.length())));
                purgeJobRepository.save(job);
                jdbcTemplate.update("insert into tenant_purge_audit_logs "
                    + "(tenant_id,event_type,result,attempt_count,error_detail,created_at) "
                    + "values (?,?,?,?,?,current_timestamp)",
                    tenantId, "FAILED", "FAILED", job.getAttemptCount(), job.getLastError());
            });
        });
    }
}
