package com.wms.system.tenant.web;

import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class TenantRequestGateTest {

    private final TenantRequestGate gate = new TenantRequestGate();

    @Test
    void appAndPlatformCannotCallTenantBusinessApis() {
        assertFalse(decide(RequestSurface.APP, null, "/api/inventory").allowed());
        assertFalse(decide(RequestSurface.PLATFORM, null, "/api/inventory").allowed());
        assertTrue(decide(RequestSurface.APP, null, "/public/v1/signup/start").allowed());
        assertTrue(decide(RequestSurface.APP, null, "/public/v1/signup").allowed());
        assertTrue(decide(RequestSurface.PLATFORM, null, "/api/platform/tenants").allowed());
    }

    @Test
    void activeTenantCanUseBusinessApi() {
        assertTrue(decide(RequestSurface.TENANT, TenantStatus.ACTIVE, "/api/inventory").allowed());
        assertFalse(decide(
            RequestSurface.TENANT, TenantStatus.ACTIVE, "/api/platform/tenants"
        ).allowed());
        assertFalse(decide(
            RequestSurface.TENANT, TenantStatus.ACTIVE, "/public/v1/signup"
        ).allowed());
    }

    @Test
    void suspendedTenantIsRestrictedToRecoverySurface() {
        assertEquals(423, decide(RequestSurface.TENANT, TenantStatus.SUSPENDED, "/api/inventory").status());
        assertTrue(decide(
            RequestSurface.TENANT, TenantStatus.SUSPENDED, "/api/company-access/appeal"
        ).allowed());
    }

    @Test
    void closedAndPurgePendingTenantsCannotUseBusinessApi() {
        assertEquals(410, decide(RequestSurface.TENANT, TenantStatus.CLOSED, "/api/inventory").status());
        assertEquals(410, decide(
            RequestSurface.TENANT, TenantStatus.PURGE_PENDING, "/api/inventory"
        ).status());
    }

    private TenantRequestGate.GateDecision decide(
        RequestSurface surface,
        TenantStatus status,
        String path
    ) {
        TenantContext context = new TenantContext(
            status == null ? null : 42L,
            status == null ? null : "acme",
            surface.name().toLowerCase() + ".bcwms.com",
            surface,
            status
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return gate.decide(context, request);
    }
}
