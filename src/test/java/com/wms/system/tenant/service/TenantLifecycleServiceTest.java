package com.wms.system.tenant.service;

import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TenantLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T02:00:00Z");
    private final TenantLifecycleService service = new TenantLifecycleService(
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void suspensionDoesNotStartRetentionCountdown() {
        Tenant tenant = Tenant.builder().status(TenantStatus.ACTIVE).build();

        service.suspend(tenant);

        assertEquals(TenantStatus.SUSPENDED, tenant.getStatus());
        assertNull(tenant.getClosedAt());
        assertNull(tenant.getPurgeDueAt());
    }

    @Test
    void closingStartsExactlyThirtyDayRetentionCountdown() {
        Tenant tenant = Tenant.builder().status(TenantStatus.ACTIVE).build();

        service.close(tenant);

        assertEquals(TenantStatus.CLOSED, tenant.getStatus());
        assertEquals(NOW, tenant.getClosedAt().toInstant());
        assertEquals(NOW.plusSeconds(30L * 24 * 60 * 60), tenant.getPurgeDueAt().toInstant());
    }

    @Test
    void closedTenantCanBeRestoredInsideRetentionWindow() {
        Tenant tenant = Tenant.builder().status(TenantStatus.ACTIVE).build();
        service.close(tenant);

        service.restore(tenant);

        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
        assertNull(tenant.getClosedAt());
        assertNull(tenant.getPurgeDueAt());
    }

    @Test
    void invalidTransitionIsRejected() {
        Tenant tenant = Tenant.builder().status(TenantStatus.PURGED).build();

        assertThrows(IllegalStateException.class, () -> service.activate(tenant));
    }

    @Test
    void purgeCannotStartBeforeRetentionDeadline() {
        Tenant tenant = Tenant.builder().status(TenantStatus.ACTIVE).build();
        service.close(tenant);

        assertThrows(IllegalStateException.class, () -> service.markPurgePending(tenant));
        assertEquals(TenantStatus.CLOSED, tenant.getStatus());
    }
}
