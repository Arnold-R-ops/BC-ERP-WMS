package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record PlatformOperationResponse(
    String authorizationId, Long companyId, String operationType, String resourceType,
    String resourceId, Map<String, Object> requestedChanges, String status, String reason, OffsetDateTime expiresAt,
    OffsetDateTime approvedAt, OffsetDateTime revokedAt, OffsetDateTime consumedAt
) {}
