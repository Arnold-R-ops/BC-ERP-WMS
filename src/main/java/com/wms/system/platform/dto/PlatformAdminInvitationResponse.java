package com.wms.system.platform.dto;
import java.time.OffsetDateTime;
public record PlatformAdminInvitationResponse(Long id,String email,String displayName,String roleCode,String status,
    OffsetDateTime createdAt,OffsetDateTime expiresAt,OffsetDateTime acceptedAt,OffsetDateTime revokedAt,String activationPath) { }
