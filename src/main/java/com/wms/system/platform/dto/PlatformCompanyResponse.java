package com.wms.system.platform.dto;

import com.wms.system.tenant.model.Tenant;
import java.time.OffsetDateTime;

public record PlatformCompanyResponse(
    Long id, String tenantCode, String displayName, String slug, String status,
    OffsetDateTime closedAt, OffsetDateTime purgeDueAt, OffsetDateTime createdAt
) {
    public static PlatformCompanyResponse from(Tenant tenant) {
        return new PlatformCompanyResponse(tenant.getId(), tenant.getTenantCode(), displayNameForPlatform(tenant),
            tenant.getSlug(), tenant.getStatus().name(), tenant.getClosedAt(), tenant.getPurgeDueAt(),
            tenant.getCreatedAt());
    }

    public static String displayNameForPlatform(Tenant tenant) {
        if ("COMPANY-000001".equals(tenant.getTenantCode()) && "默认公司".equals(tenant.getDisplayName())) {
            return "默认租户";
        }
        return tenant.getDisplayName();
    }
}
