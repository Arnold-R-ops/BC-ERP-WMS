package com.wms.system.platform.service;

import com.wms.system.platform.model.PlatformRole;
import com.wms.system.platform.repository.*;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure mock fixture contract checks; no Spring context or JDBC connection. */
class PlatformAccessGrantRoleFixtureTest {
    private static final String CODE = PlatformAdminInvitationService.OPERATIONS_ADMIN;
    @Test void readsAndVerifiesExistingBaselineRoleWithoutCreatingIt() {
        var roles = mock(PlatformRoleRepository.class);
        var role = PlatformRole.builder().id(7L).roleCode(CODE).displayName("Baseline").build();
        when(roles.findByRoleCode(CODE)).thenReturn(Optional.of(role));
        assertSame(role, PlatformAccessGrantRoleRaceIntegrationTest.requireBaselineRole(roles, CODE));
        verify(roles).findByRoleCode(CODE); verifyNoMoreInteractions(roles);
    }
    @Test void missingBaselineRoleFailsWithoutRepairOrCreation() {
        var roles = mock(PlatformRoleRepository.class);
        when(roles.findByRoleCode(CODE)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> PlatformAccessGrantRoleRaceIntegrationTest.requireBaselineRole(roles, CODE));
        verify(roles).findByRoleCode(CODE); verifyNoMoreInteractions(roles);
    }
    @Test void wrongRoleCodeFailsVerification() {
        var roles = mock(PlatformRoleRepository.class);
        when(roles.findByRoleCode(CODE)).thenReturn(Optional.of(PlatformRole.builder().id(7L).roleCode("WRONG").build()));
        assertThrows(AssertionError.class, () -> PlatformAccessGrantRoleRaceIntegrationTest.requireBaselineRole(roles, CODE));
    }
    @Test void missingRoleIdFailsVerification() {
        var roles = mock(PlatformRoleRepository.class);
        when(roles.findByRoleCode(CODE)).thenReturn(Optional.of(PlatformRole.builder().roleCode(CODE).build()));
        assertThrows(AssertionError.class, () -> PlatformAccessGrantRoleRaceIntegrationTest.requireBaselineRole(roles, CODE));
    }
    @Test void nonPositiveRoleIdFailsVerification() {
        var roles = mock(PlatformRoleRepository.class);
        when(roles.findByRoleCode(CODE)).thenReturn(Optional.of(PlatformRole.builder().id(0L).roleCode(CODE).build()));
        assertThrows(AssertionError.class, () -> PlatformAccessGrantRoleRaceIntegrationTest.requireBaselineRole(roles, CODE));
    }
    @Test void setupStopsBeforeCreatingOwnedDataWhenBaselineIsMissing() {
        var fixture = new PlatformAccessGrantRoleRaceIntegrationTest();
        fixture.roles = mock(PlatformRoleRepository.class); fixture.users = mock(PlatformUserRepository.class);
        fixture.userRoles = mock(PlatformUserRoleRepository.class); fixture.tenants = mock(TenantRepository.class);
        when(fixture.roles.findByRoleCode(CODE)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, fixture::setUp);
        verifyNoInteractions(fixture.users, fixture.userRoles, fixture.tenants);
    }
    @Test void cleanupOnlyUsesOwnedUserAndTenantNeverBaselineRoles() {
        var fixture = new PlatformAccessGrantRoleRaceIntegrationTest();
        fixture.roles = mock(PlatformRoleRepository.class); fixture.users = mock(PlatformUserRepository.class);
        fixture.userRoles = mock(PlatformUserRoleRepository.class); fixture.tenants = mock(TenantRepository.class);
        fixture.grants = mock(PlatformAccessGrantRepository.class);
        ReflectionTestUtils.setField(fixture, "targetUserId", 10L); ReflectionTestUtils.setField(fixture, "tenantId", 20L);
        ReflectionTestUtils.setField(fixture, "operationsRoleId", 1L); ReflectionTestUtils.setField(fixture, "auditorRoleId", 2L);
        when(fixture.grants.findByGranteePlatformUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(fixture.userRoles.findByPlatformUserId(10L)).thenReturn(List.of());
        fixture.tearDown();
        verify(fixture.users).deleteById(10L); verify(fixture.tenants).deleteById(20L);
        verify(fixture.grants).findByGranteePlatformUserIdOrderByCreatedAtDesc(10L);
        verify(fixture.userRoles).findByPlatformUserId(10L);
        verifyNoInteractions(fixture.roles);
    }
}
