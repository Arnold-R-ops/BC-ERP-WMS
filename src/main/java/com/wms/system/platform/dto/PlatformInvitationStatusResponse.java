package com.wms.system.platform.dto;
import java.time.OffsetDateTime;
public record PlatformInvitationStatusResponse(String email,String displayName,String roleCode,OffsetDateTime expiresAt) { }
