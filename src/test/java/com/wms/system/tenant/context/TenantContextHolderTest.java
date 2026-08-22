package com.wms.system.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextHolderTest {

    @AfterEach
    void cleanUp() {
        TenantContextHolder.clear();
    }

    @Test
    void clearsContextAfterSuccessfulWork() {
        TenantContext context = new TenantContext(
            10L, "alpha", "alpha.bcwms.com", RequestSurface.TENANT,
            com.wms.system.tenant.model.TenantStatus.ACTIVE
        );

        Long tenantId = TenantContextHolder.runWithTenant(
            context,
            () -> TenantContextHolder.requireTenant().tenantId()
        );

        assertEquals(10L, tenantId);
        assertTrue(TenantContextHolder.current().isEmpty());
    }

    @Test
    void clearsContextWhenWorkThrows() {
        TenantContext context = new TenantContext(
            20L, "beta", "beta.bcwms.com", RequestSurface.TENANT,
            com.wms.system.tenant.model.TenantStatus.ACTIVE
        );

        assertThrows(IllegalArgumentException.class, () ->
            TenantContextHolder.runWithTenant(context, () -> {
                throw new IllegalArgumentException("test");
            })
        );

        assertTrue(TenantContextHolder.current().isEmpty());
    }

    @Test
    void appSurfaceCannotBeUsedAsTenantContext() {
        TenantContextHolder.set(new TenantContext(null, null, "app.bcwms.com", RequestSurface.APP, null));

        assertThrows(IllegalStateException.class, TenantContextHolder::requireTenant);
    }

    @Test
    void trustedResolutionTemporarilyReplacesAppSurfaceAndRestoresIt() {
        TenantContext app = new TenantContext(
            null, null, "app.bcwms.com", RequestSurface.APP, null
        );
        TenantContext alpha = new TenantContext(
            10L, "alpha", "alpha.bcwms.com", RequestSurface.TENANT,
            com.wms.system.tenant.model.TenantStatus.ACTIVE
        );
        TenantContextHolder.set(app);

        Long companyId = TenantContextHolder.runWithResolvedTenant(
            alpha,
            () -> TenantContextHolder.requireTenant().tenantId()
        );

        assertEquals(10L, companyId);
        assertSame(app, TenantContextHolder.current().orElseThrow());
    }

    @Test
    void trustedResolutionRestoresAppSurfaceWhenWorkThrows() {
        TenantContext app = new TenantContext(
            null, null, "app.bcwms.com", RequestSurface.APP, null
        );
        TenantContext alpha = new TenantContext(
            10L, "alpha", "alpha.bcwms.com", RequestSurface.TENANT,
            com.wms.system.tenant.model.TenantStatus.ACTIVE
        );
        TenantContextHolder.set(app);

        assertThrows(IllegalArgumentException.class, () ->
            TenantContextHolder.runWithResolvedTenant(alpha, () -> {
                throw new IllegalArgumentException("test");
            })
        );

        assertSame(app, TenantContextHolder.current().orElseThrow());
    }

    @Test
    void trustedResolutionCannotReplaceAnExistingCompany() {
        TenantContext alpha = new TenantContext(
            10L, "alpha", "alpha.bcwms.com", RequestSurface.TENANT,
            com.wms.system.tenant.model.TenantStatus.ACTIVE
        );
        TenantContext beta = new TenantContext(
            20L, "beta", "beta.bcwms.com", RequestSurface.TENANT,
            com.wms.system.tenant.model.TenantStatus.ACTIVE
        );
        TenantContextHolder.set(alpha);

        assertThrows(
            IllegalStateException.class,
            () -> TenantContextHolder.runWithResolvedTenant(beta, () -> null)
        );

        assertSame(alpha, TenantContextHolder.current().orElseThrow());
    }
}
