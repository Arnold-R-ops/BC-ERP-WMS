package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformAdminRoleChangeResponse(
    Long targetUserId,
    boolean changed,
    boolean enabled,
    List<String> beforeRoles,
    List<String> afterRoles,
    long activeGrantCount,
    long securityVersion,
    OffsetDateTime completedAt
) { }
