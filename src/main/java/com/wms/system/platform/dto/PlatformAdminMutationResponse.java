package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformAdminMutationResponse(
    Long targetUserId,
    String action,
    boolean changed,
    boolean enabled,
    List<String> roles,
    long activeGrantCount,
    long securityVersion,
    OffsetDateTime completedAt
) { }
