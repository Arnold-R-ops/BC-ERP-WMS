package com.wms.system.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Resolves user identity from Spring Security Authentication in a test-safe way.
 */
public final class AuthUserResolver {

    private AuthUserResolver() {
    }

    public static Long resolveUserId(Authentication authentication) {
        if (authentication == null) {
            return 0L;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser) {
            SecurityUser securityUser = (SecurityUser) principal;
            return securityUser.getId() != null ? securityUser.getId() : 0L;
        }
        return 0L;
    }

    public static String resolveUsername(Authentication authentication) {
        if (authentication == null) {
            return "unknown";
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser) {
            return ((SecurityUser) principal).getUsername();
        }
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        if (principal instanceof String) {
            String value = (String) principal;
            if (!"anonymousUser".equalsIgnoreCase(value) && !value.isBlank()) {
                return value;
            }
        }

        String name = authentication.getName();
        return (name == null || name.isBlank()) ? "unknown" : name;
    }
}
