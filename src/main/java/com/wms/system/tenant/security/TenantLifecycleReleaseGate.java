package com.wms.system.tenant.security;

/** Not configurable: a later reviewed implementation must replace this release interlock. */
public final class TenantLifecycleReleaseGate {
    private TenantLifecycleReleaseGate() { }
    public static void requirePurgeReleased() {
        throw new TenantAccessException("TENANT_PURGE_NOT_RELEASED", 409);
    }
}
