package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformAdminSummaryResponse(
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
    boolean currentUser,
    boolean lastEnabledSuperAdmin
) { }
