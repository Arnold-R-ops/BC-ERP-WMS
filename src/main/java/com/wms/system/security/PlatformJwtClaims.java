package com.wms.system.security;

import java.util.List;

public record PlatformJwtClaims(
    Long platformUserId,
    String normalizedEmail,
    List<String> roles,
    Long securityVersion
) {
}
