package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformAuditLogResponse(
    Long id,
    OffsetDateTime createdAt,
    String actorEmail,
    Long targetTenantId,
    String targetTenantName,
    String action,
    String resourceType,
    String result,
    Map<String, Object> summary
) {
}
