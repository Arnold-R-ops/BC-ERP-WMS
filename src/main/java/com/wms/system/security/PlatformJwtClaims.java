package com.wms.system.security;

import java.util.List;

public record PlatformJwtClaims(
    Long platformUserId,
    String normalizedEmail,
    List<String> roles,
    Long securityVersion,
    java.util.UUID sessionId,
    long issuedAtEpochMillis,
    long expiresAtEpochMillis
) {
    public PlatformJwtClaims(Long id, String email, List<String> roles, Long version) {
        this(id, email, roles, version, null, 0, 0);
    }
}
