package com.wms.system.tenant.service;

import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class TenantLifecycleService {

    public static final int CLOSED_DATA_RETENTION_DAYS = 30;

    private final Clock clock;

    public TenantLifecycleService() {
        this(Clock.systemUTC());
    }

    TenantLifecycleService(Clock clock) {
        this.clock = clock;
    }

    public void activate(Tenant tenant) {
        requireStatus(tenant, TenantStatus.PROVISIONING, TenantStatus.SUSPENDED);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setClosedAt(null);
        tenant.setPurgeDueAt(null);
    }

    public void suspend(Tenant tenant) {
        requireStatus(tenant, TenantStatus.ACTIVE);
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setClosedAt(null);
        tenant.setPurgeDueAt(null);
    }

    public void close(Tenant tenant) {
        requireStatus(tenant, TenantStatus.ACTIVE, TenantStatus.SUSPENDED);
        OffsetDateTime closedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        tenant.setStatus(TenantStatus.CLOSED);
        tenant.setClosedAt(closedAt);
        tenant.setPurgeDueAt(closedAt.plusDays(CLOSED_DATA_RETENTION_DAYS));
    }

    public void restore(Tenant tenant) {
        requireStatus(tenant, TenantStatus.CLOSED);
        if (!OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).isBefore(tenant.getPurgeDueAt())) {
            throw new IllegalStateException("Tenant retention window has expired");
        }
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setClosedAt(null);
        tenant.setPurgeDueAt(null);
    }

    public void markPurgePending(Tenant tenant) {
        requireStatus(tenant, TenantStatus.CLOSED);
        if (OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).isBefore(tenant.getPurgeDueAt())) {
            throw new IllegalStateException("Tenant retention window has not expired");
        }
        tenant.setStatus(TenantStatus.PURGE_PENDING);
    }

    private void requireStatus(Tenant tenant, TenantStatus... allowed) {
        for (TenantStatus status : allowed) {
            if (tenant.getStatus() == status) {
                return;
            }
        }
        throw new IllegalStateException("Unsupported tenant transition from " + tenant.getStatus());
    }
}
