package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformAdminSecurityMutationResponse(
    Long targetUserId,
    String action,
    boolean changed,
    boolean enabled,
    String mfaStatus,
    List<String> roles,
    long activeGrantCount,
    long securityVersion,
    OffsetDateTime completedAt
) { }
