package com.wms.system.platform.service;

import com.wms.system.platform.dto.PlatformEffectiveAccessResponse;
import com.wms.system.platform.model.PlatformAccessGrant;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.*;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAccessGrantServiceTest {
    private final PlatformAccessGuard guard = mock(PlatformAccessGuard.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final PlatformAccessGrantRepository grants = mock(PlatformAccessGrantRepository.class);
    private final PlatformUserRoleRepository userRoles = mock(PlatformUserRoleRepository.class);
    private final PlatformRoleRepository roles = mock(PlatformRoleRepository.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final PlatformAccessGrantService service = new PlatformAccessGrantService(
        guard, users, tenants, grants, userRoles, roles, audit);

    @Test
    void superAdministratorReceivesUnrestrictedMarkerWithoutGrantRows() {
        when(guard.requirePlatformUser()).thenReturn(user(7L));
        when(guard.isSuperAdmin()).thenReturn(true);

        PlatformEffectiveAccessResponse result = service.effectiveAccess();

        assertThat(result.superAdmin()).isTrue();
        assertThat(result.scopes()).isEmpty();
        verifyNoInteractions(grants);
    }

    @Test
    void effectiveScopesRequireBothConcreteGrantAndMatchingCandidateRole() {
        when(guard.requirePlatformUser()).thenReturn(user(9L));
        when(guard.isSuperAdmin()).thenReturn(false);
        when(guard.hasAuthority("ROLE_PLATFORM_TENANT_READ")).thenReturn(true);
        when(guard.hasAuthority("ROLE_PLATFORM_TENANT_EXPORT")).thenReturn(false);
        when(grants.activeGrants(eq(9L), any(OffsetDateTime.class))).thenReturn(List.of(
            grant(6L, "users", "READ"),
            grant(6L, "users", "EXPORT"),
            grant(8L, "inventory", "EXPORT")
        ));

        PlatformEffectiveAccessResponse result = service.effectiveAccess();

        assertThat(result.superAdmin()).isFalse();
        assertThat(result.scopes()).hasSize(1);
        assertThat(result.scopes().get(0).tenantId()).isEqualTo(6L);
        assertThat(result.scopes().get(0).datasetCode()).isEqualTo("users");
        assertThat(result.scopes().get(0).read()).isTrue();
        assertThat(result.scopes().get(0).export()).isFalse();
    }

    @Test
    void listUsesTenantTerminologyForLegacyDefaultTenant() {
        PlatformAccessGrant grant = PlatformAccessGrant.builder().id(11L)
            .granteePlatformUserId(9L).tenantId(1L).datasetCode("users").capability("READ")
            .effectiveFrom(OffsetDateTime.now().minusDays(2)).expiresAt(OffsetDateTime.now().minusDays(1)).build();
        when(grants.findByGranteePlatformUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(grant));
        when(users.findById(9L)).thenReturn(java.util.Optional.of(user(9L).getUser()));
        when(tenants.findById(1L)).thenReturn(java.util.Optional.of(Tenant.builder()
            .id(1L).tenantCode("COMPANY-000001").displayName("默认公司").slug("default-company").build()));

        var result = service.list(9L);

        assertThat(result).singleElement().extracting("tenantName").isEqualTo("默认租户");
        verify(guard).requireSuperAdmin();
    }

    private PlatformSecurityUser user(Long id) {
        return new PlatformSecurityUser(PlatformUser.builder().id(id).normalizedEmail("delegate@bcwms.invalid")
            .passwordHash("hidden").displayName("Delegate").enabled(true).build());
    }

    private PlatformAccessGrant grant(Long tenantId, String dataset, String capability) {
        return PlatformAccessGrant.builder().granteePlatformUserId(9L).tenantId(tenantId)
            .datasetCode(dataset).capability(capability).build();
    }
}
