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
    long sessionStartedAtEpochMillis,
    long tenantSessionVersion
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
            securityVersion, 1L, Long.MAX_VALUE, 1L, 1L);
    }

    public TenantJwtClaims(Long userId, Long companyId, String username, String currentRole,
            List<String> availableRoles, Long securityVersion, long issued, long expires, long started) {
        this(userId, companyId, username, currentRole, availableRoles, securityVersion, issued, expires, started, 1L);
    }
}
