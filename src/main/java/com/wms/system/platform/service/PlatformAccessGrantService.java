package com.wms.system.platform.service;

import com.wms.system.platform.dto.*;
import com.wms.system.platform.model.*;
import com.wms.system.platform.repository.*;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@RequiredArgsConstructor
@org.springframework.stereotype.Service
public class PlatformAccessGrantService {
    private static final Duration DEFAULT_DURATION = Duration.ofDays(30), MAX_DURATION = Duration.ofDays(90);
    private final PlatformAccessGuard guard; private final PlatformUserRepository users; private final TenantRepository tenants;
    private final PlatformAccessGrantRepository grants; private final PlatformUserRoleRepository userRoles;
    private final PlatformRoleRepository roles; private final PlatformAuditService audit;

    @Transactional(readOnly = true)
    public List<PlatformManagedUserResponse> users() {
        guard.requireSuperAdmin();
        return users.findAll().stream().sorted(Comparator.comparing(PlatformUser::getNormalizedEmail))
            .map(u -> new PlatformManagedUserResponse(u.getId(), u.getNormalizedEmail(), u.getDisplayName(),
                Boolean.TRUE.equals(u.getEnabled()), isSuper(u.getId()), Boolean.TRUE.equals(u.getMfaEnabled()))).toList();
    }
    @Transactional(readOnly = true)
    public List<PlatformAccessGrantResponse> list(Long userId) {
        guard.requireSuperAdmin();
        return grants.findByGranteePlatformUserIdOrderByCreatedAtDesc(userId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public PlatformEffectiveAccessResponse effectiveAccess() {
        PlatformSecurityUser actor = guard.requirePlatformUser();
        if (guard.isSuperAdmin()) return new PlatformEffectiveAccessResponse(true, List.of());

        boolean readRole = guard.hasAuthority("ROLE_PLATFORM_TENANT_READ");
        boolean exportRole = guard.hasAuthority("ROLE_PLATFORM_TENANT_EXPORT");
        Map<ScopeKey, boolean[]> effective = new LinkedHashMap<>();
        for (PlatformAccessGrant grant : grants.activeGrants(actor.getId(), OffsetDateTime.now())) {
            ScopeKey key = new ScopeKey(grant.getTenantId(), grant.getDatasetCode());
            boolean[] capabilities = effective.computeIfAbsent(key, ignored -> new boolean[2]);
            if (readRole && "READ".equals(grant.getCapability())) capabilities[0] = true;
            if (exportRole && "EXPORT".equals(grant.getCapability())) capabilities[1] = true;
        }
        List<PlatformEffectiveAccessScopeResponse> scopes = effective.entrySet().stream()
            .filter(entry -> entry.getValue()[0] || entry.getValue()[1])
            .map(entry -> new PlatformEffectiveAccessScopeResponse(
                entry.getKey().tenantId(), entry.getKey().datasetCode(),
                entry.getValue()[0], entry.getValue()[1]))
            .toList();
        return new PlatformEffectiveAccessResponse(false, scopes);
    }
    @Transactional
    public List<PlatformAccessGrantResponse> create(PlatformAccessGrantRequest request, HttpServletRequest http) {
        PlatformSecurityUser actor = guard.requireSuperAdmin();
        if (actor.getId().equals(request.platformUserId())) throw new IllegalArgumentException("Platform administrators cannot grant themselves access");
        PlatformUser grantee = users.findById(request.platformUserId()).filter(u -> Boolean.TRUE.equals(u.getEnabled())).orElseThrow(() -> new NoSuchElementException("Platform administrator not found"));
        if (isSuper(grantee.getId())) throw new IllegalArgumentException("Super administrator access is permanent and cannot be delegated");
        OffsetDateTime from = request.effectiveFrom() == null ? OffsetDateTime.now() : request.effectiveFrom();
        OffsetDateTime until = request.expiresAt() == null ? from.plus(DEFAULT_DURATION) : request.expiresAt();
        if (!until.isAfter(from) || Duration.between(from, until).compareTo(MAX_DURATION) > 0) throw new IllegalArgumentException("Grant duration must be positive and no longer than 90 days");
        Set<Long> tenantIds = new LinkedHashSet<>(request.tenantIds());
        if (tenants.findAllById(tenantIds).size() != tenantIds.size()) throw new NoSuchElementException("Tenant not found");
        List<PlatformAccessGrantResponse> result = new ArrayList<>();
        for (String capability : new LinkedHashSet<>(request.capabilities())) for (Long tenantId : tenantIds) for (String dataset : new LinkedHashSet<>(request.datasets())) {
            PlatformAccessGrant saved = grants.save(PlatformAccessGrant.builder().granteePlatformUserId(grantee.getId()).grantedByPlatformUserId(actor.getId()).capability(capability).tenantId(tenantId).datasetCode(dataset).effectiveFrom(from).expiresAt(until).build());
            audit.record(actor.getId(), tenantId, "ACCESS_GRANTED", dataset, "SUCCESS", Map.of("authorizationId", saved.getId(), "operation", capability), http);
            result.add(response(saved));
        }
        return result;
    }
    @Transactional
    public void revoke(Long id, HttpServletRequest http) {
        PlatformSecurityUser actor = guard.requireSuperAdmin();
        PlatformAccessGrant grant = grants.findById(id).orElseThrow(() -> new NoSuchElementException("Access grant not found"));
        if (grant.getRevokedAt() == null) { grant.setRevokedAt(OffsetDateTime.now()); grant.setRevokedByPlatformUserId(actor.getId());
            audit.record(actor.getId(), grant.getTenantId(), "ACCESS_REVOKED", grant.getDatasetCode(), "SUCCESS", Map.of("authorizationId", grant.getId(), "operation", grant.getCapability()), http); }
    }
    private boolean isSuper(Long id) { return userRoles.findByPlatformUserId(id).stream().map(r -> roles.findById(r.getPlatformRoleId()).map(PlatformRole::getRoleCode).orElse("")).anyMatch("PLATFORM_SUPER_ADMIN"::equals); }
    private PlatformAccessGrantResponse response(PlatformAccessGrant g) { String email=users.findById(g.getGranteePlatformUserId()).map(PlatformUser::getNormalizedEmail).orElse("DEACTIVATED"); String tenant=tenants.findById(g.getTenantId()).map(PlatformCompanyResponse::displayNameForPlatform).orElse("REMOVED_TENANT"); return new PlatformAccessGrantResponse(g.getId(),g.getGranteePlatformUserId(),email,g.getCapability(),g.getTenantId(),tenant,g.getDatasetCode(),g.getEffectiveFrom(),g.getExpiresAt(),g.getRevokedAt()); }
    private record ScopeKey(Long tenantId, String datasetCode) { }
}
