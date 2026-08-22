package com.wms.system.tenant.context;

import com.wms.system.tenant.model.TenantStatus;

public record TenantContext(
    Long tenantId,
    String slug,
    String hostname,
    RequestSurface surface,
    TenantStatus tenantStatus
) {
    public boolean isTenantRequest() {
        return surface == RequestSurface.TENANT;
    }
}
