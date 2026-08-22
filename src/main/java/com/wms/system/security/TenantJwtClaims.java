package com.wms.system.security;

import java.util.List;

public record TenantJwtClaims(
    Long userId,
    Long companyId,
    String username,
    String currentRole,
    List<String> availableRoles,
    Long securityVersion,
    long issuedAtEpochMillis,
    long expiresAtEpochMillis,
    long sessionStartedAtEpochMillis
) {
    public TenantJwtClaims(
        Long userId,
        Long companyId,
        String username,
        String currentRole,
        List<String> availableRoles,
        Long securityVersion
    ) {
        this(userId, companyId, username, currentRole, availableRoles,
            securityVersion, 1L, Long.MAX_VALUE, 1L);
    }
}
