package com.wms.system.platform.dto;
import java.time.OffsetDateTime;
public record PlatformAccessGrantResponse(Long id, Long platformUserId, String platformUserEmail, String capability,
    Long tenantId, String tenantName, String datasetCode, OffsetDateTime effectiveFrom, OffsetDateTime expiresAt, OffsetDateTime revokedAt) { }
