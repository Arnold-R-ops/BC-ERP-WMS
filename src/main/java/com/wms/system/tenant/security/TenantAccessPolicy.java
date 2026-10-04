package com.wms.system.tenant.security;

import com.wms.system.tenant.model.TenantStatus;

/** Single purpose-aware status matrix shared by HTTP, workers and transaction admission. */
public final class TenantAccessPolicy {
    private TenantAccessPolicy() { }

    public static void require(TenantStatus status, TenantOperationPurpose purpose) {
        boolean allowed = switch (purpose) {
            case BUSINESS -> status == TenantStatus.ACTIVE;
            case AUTHENTICATION, PLATFORM_READ, WEBHOOK_INBOX ->
                status == TenantStatus.ACTIVE || status == TenantStatus.SUSPENDED;
            case PROVISIONING -> status == TenantStatus.PROVISIONING;
            case SECURITY_CONTROL -> status == TenantStatus.ACTIVE || status == TenantStatus.SUSPENDED;
        };
        if (!allowed) throw new TenantAccessException(
            status == TenantStatus.SUSPENDED ? "TENANT_SUSPENDED" : "TENANT_SERVICE_UNAVAILABLE",
            status == TenantStatus.SUSPENDED ? 423 : 410);
    }
}
