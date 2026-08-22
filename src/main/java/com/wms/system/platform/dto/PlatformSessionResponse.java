package com.wms.system.platform.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformSessionResponse(
    String email,
    List<String> roles,
    boolean mfaEnabled,
    OffsetDateTime expiresAt
) { }
