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
    public static final String SUPER_ADMIN = "ROLE_PLATFORM_SUPER_ADMIN";
    public static final String OPERATIONS_ADMIN = "ROLE_PLATFORM_OPERATIONS_ADMIN";
    public static final String SECURITY_AUDITOR = "ROLE_PLATFORM_SECURITY_AUDITOR";
    public static final String LEGACY_READ = "ROLE_PLATFORM_TENANT_READ";
    public static final String LEGACY_EXPORT = "ROLE_PLATFORM_TENANT_EXPORT";
    private final PlatformAccessGrantRepository grants;

    public PlatformSecurityUser requireRead(Long tenantId, String dataset) {
        PlatformSecurityUser user = requireAny(Set.of(SUPER_ADMIN, OPERATIONS_ADMIN, LEGACY_READ));
        requireGrantUnlessSuper(user, "READ", tenantId, dataset);
        return user;
    }

    public PlatformSecurityUser requireExport(Long tenantId, String dataset) {
        PlatformSecurityUser user = requireAny(Set.of(SUPER_ADMIN, OPERATIONS_ADMIN, LEGACY_EXPORT));
        requireGrantUnlessSuper(user, "EXPORT", tenantId, dataset);
        return user;
    }

    public PlatformSecurityUser requireSuperAdmin() {
        return requireAny(Set.of(SUPER_ADMIN));
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
        PlatformSecurityUser user = requireAny(Set.of(SUPER_ADMIN, OPERATIONS_ADMIN, LEGACY_READ, LEGACY_EXPORT));
        if (!hasAllTenantDirectoryAccess() && activeTenantIds(user).isEmpty()) {
            throw new AccessDeniedException("Platform access grant is required");
        }
        return user;
    }

    public PlatformSecurityUser requireTenantListed(Long tenantId) {
        PlatformSecurityUser user = requireTenantDirectory();
        if (!hasAllTenantDirectoryAccess() && !activeTenantIds(user).contains(tenantId)) {
            throw new AccessDeniedException("Tenant is outside platform access grant");
        }
        return user;
    }

    public boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof PlatformSecurityUser
            && authentication.getAuthorities().stream().anyMatch(a -> SUPER_ADMIN.equals(a.getAuthority()));
    }

    public boolean isOperationsAdmin() {
        return hasAuthority(OPERATIONS_ADMIN);
    }

    public boolean isSecurityAuditor() {
        return hasAuthority(SECURITY_AUDITOR);
    }

    public boolean hasAllTenantDirectoryAccess() {
        return isSuperAdmin() || isOperationsAdmin();
    }

    public java.util.List<Long> activeTenantIds(PlatformSecurityUser user) {
        if (hasAllTenantDirectoryAccess()) return java.util.List.of();
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        java.util.Set<Long> tenantIds = new java.util.LinkedHashSet<>();
        if (hasAuthority(LEGACY_READ)) tenantIds.addAll(grants.activeTenantIds(user.getId(), "READ", now));
        if (hasAuthority(LEGACY_EXPORT)) tenantIds.addAll(grants.activeTenantIds(user.getId(), "EXPORT", now));
        return java.util.List.copyOf(tenantIds);
    }

    private void requireGrantUnlessSuper(PlatformSecurityUser user, String capability, Long tenantId, String dataset) {
        if (!isSuperAdmin() && !grants.hasActive(user.getId(), capability, tenantId, dataset, java.time.OffsetDateTime.now())) {
            throw new AccessDeniedException("Platform access grant is required");
        }
    }

    public PlatformSecurityUser requireAuditRead() {
        return requireAny(Set.of(SUPER_ADMIN, SECURITY_AUDITOR));
    }

    public PlatformSecurityUser requireAdminDirectoryRead() {
        return requireAny(Set.of(SUPER_ADMIN, SECURITY_AUDITOR));
    }

    public PlatformSecurityUser requireGrantInventoryRead() {
        return requireAny(Set.of(SUPER_ADMIN, SECURITY_AUDITOR));
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
