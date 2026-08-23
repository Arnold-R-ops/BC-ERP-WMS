package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PlatformAdminDetailResponse(
    Long id,
    String displayName,
    String email,
    boolean enabled,
    List<PlatformAdminRoleResponse> roles,
    String mfaStatus,
    OffsetDateTime mfaEnrolledAt,
    OffsetDateTime mfaLockedUntil,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    long activeGrantCount,
    Map<String, Long> activeGrantCounts,
    boolean currentUser,
    boolean lastEnabledSuperAdmin
) { }
