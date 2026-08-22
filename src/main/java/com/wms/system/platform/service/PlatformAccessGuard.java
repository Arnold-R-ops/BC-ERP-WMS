package com.wms.system.platform.service;

import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PlatformAccessGuard {
    private final PlatformAccessGrantRepository grants;

    public PlatformSecurityUser requireRead(Long tenantId, String dataset) {
        PlatformSecurityUser user = requireAny(Set.of("ROLE_PLATFORM_SUPER_ADMIN", "ROLE_PLATFORM_TENANT_READ"));
        requireGrantUnlessSuper(user, "READ", tenantId, dataset);
        return user;
    }

    public PlatformSecurityUser requireExport(Long tenantId, String dataset) {
        PlatformSecurityUser user = requireAny(Set.of("ROLE_PLATFORM_SUPER_ADMIN", "ROLE_PLATFORM_TENANT_EXPORT"));
        requireGrantUnlessSuper(user, "EXPORT", tenantId, dataset);
        return user;
    }

    public PlatformSecurityUser requireSuperAdmin() {
        return requireAny(Set.of("ROLE_PLATFORM_SUPER_ADMIN"));
    }

    public PlatformSecurityUser requirePlatformUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformSecurityUser user)) {
            throw new AccessDeniedException("Platform authentication is required");
        }
        return user;
    }

    public boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof PlatformSecurityUser
            && authentication.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    public PlatformSecurityUser requireTenantDirectory() {
        PlatformSecurityUser user = requireAny(Set.of("ROLE_PLATFORM_SUPER_ADMIN", "ROLE_PLATFORM_TENANT_READ", "ROLE_PLATFORM_TENANT_EXPORT"));
        if (!isSuperAdmin() && activeTenantIds(user).isEmpty()) throw new AccessDeniedException("Platform access grant is required");
        return user;
    }

    public PlatformSecurityUser requireTenantListed(Long tenantId) {
        PlatformSecurityUser user = requireTenantDirectory();
        if (!isSuperAdmin() && !activeTenantIds(user).contains(tenantId)) throw new AccessDeniedException("Tenant is outside platform access grant");
        return user;
    }

    public boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof PlatformSecurityUser
            && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_PLATFORM_SUPER_ADMIN".equals(a.getAuthority()));
    }

    public java.util.List<Long> activeTenantIds(PlatformSecurityUser user) {
        if (isSuperAdmin()) return java.util.List.of();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        java.util.Set<Long> tenantIds = new java.util.LinkedHashSet<>();
        if (hasAuthority("ROLE_PLATFORM_TENANT_READ")) tenantIds.addAll(grants.activeTenantIds(user.getId(), "READ", now));
        if (hasAuthority("ROLE_PLATFORM_TENANT_EXPORT")) tenantIds.addAll(grants.activeTenantIds(user.getId(), "EXPORT", now));
        return java.util.List.copyOf(tenantIds);
    }

    private void requireGrantUnlessSuper(PlatformSecurityUser user, String capability, Long tenantId, String dataset) {
        if (!isSuperAdmin() && !grants.hasActive(user.getId(), capability, tenantId, dataset, java.time.OffsetDateTime.now())) {
            throw new AccessDeniedException("Platform access grant is required");
        }
    }

    public PlatformSecurityUser requireAuditRead() {
        // 2026-08-16: audit visibility is intentionally limited to the
        // permanent super administrator until delegated platform grants exist.
        return requireSuperAdmin();
    }

    public PlatformSecurityUser requireWrite() {
        // A platform super administrator is intentionally a permanent
        // read/export identity, not a company-data write bypass. Every write
        // still needs the separately granted role plus a company approval.
        return requireAny(Set.of("ROLE_PLATFORM_TENANT_WRITE"));
    }

    public PlatformSecurityUser requireDelete() {
        // See requireWrite(): delete is never implied by platform super admin.
        return requireAny(Set.of("ROLE_PLATFORM_TENANT_DELETE"));
    }

    private PlatformSecurityUser requireAny(Set<String> allowed) {
        PlatformSecurityUser user = requirePlatformUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream().map(a -> a.getAuthority()).noneMatch(allowed::contains)) {
            throw new AccessDeniedException("Platform role is not authorized");
        }
        return user;
    }
}
