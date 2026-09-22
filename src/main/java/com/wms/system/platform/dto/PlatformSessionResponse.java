package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformSessionResponse(
    String email,
    List<String> roles,
    boolean mfaEnabled,
    OffsetDateTime expiresAt,
    OffsetDateTime idleExpiresAt,
    OffsetDateTime serverNow
) {
    public PlatformSessionResponse(String email, List<String> roles, boolean mfaEnabled, OffsetDateTime expiresAt) {
        this(email, roles, mfaEnabled, expiresAt, null, null);
    }
}
