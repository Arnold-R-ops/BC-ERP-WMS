package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.platform.repository.PlatformAdminDirectoryRepository;
import com.wms.system.platform.repository.PlatformUserRoleRepository;
import com.wms.system.security.PlatformSecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAdminDirectoryServiceTest {
    private final PlatformAdminDirectoryRepository directory = mock(PlatformAdminDirectoryRepository.class);
    private final PlatformUserRoleRepository userRoles = mock(PlatformUserRoleRepository.class);
    private final PlatformAccessGrantRepository accessGrants = mock(PlatformAccessGrantRepository.class);
    private final PlatformAccessGuard guard = mock(PlatformAccessGuard.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private PlatformAdminDirectoryService service;
    private PlatformSecurityUser actor;

    @BeforeEach
    void setUp() {
        service = new PlatformAdminDirectoryService(directory, userRoles, accessGrants, guard, audit);
        actor = new PlatformSecurityUser(PlatformUser.builder().id(7L)
            .normalizedEmail("current@example.com").passwordHash("hidden")
            .displayName("Current administrator").enabled(true).build());
        when(guard.requireAdminDirectoryRead()).thenReturn(actor);
    }

    @Test
    void listUsesOnePageQueryAndTwoBatchAggregatesWithSafeRoleAndMfaSemantics() {
        OffsetDateTime now = OffsetDateTime.now();
        PlatformUser current = user(7L, "Current administrator", "current@example.com", true,
            true, now.minusDays(10), now.plusMinutes(10));
        PlatformUser disabled = user(8L, "Former operator", "former@example.com", false,
            false, now.minusDays(20), now.minusMinutes(1));
        when(directory.search(eq("Admin"), eq(true), isNull(), eq("PLATFORM_SUPER_ADMIN"), eq(true),
            eq("TEMPORARILY_LOCKED"), any(OffsetDateTime.class), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(current, disabled),
                invocation.getArgument(7), 2));
        var superRole = role(7L, "PLATFORM_SUPER_ADMIN", "旧超级管理员名称");
        var readRole = role(7L, "PLATFORM_TENANT_READ", "公司数据读取");
        var legacyRole = role(8L, "PLATFORM_LEGACY_REVIEWER", "历史复核角色");
        when(userRoles.findRoleViewsByPlatformUserIdIn(eq(List.of(7L, 8L))))
            .thenReturn(List.of(superRole, readRole, legacyRole));
        var readCount = grantCount(7L, "READ", 2L);
        var exportCount = grantCount(7L, "EXPORT", 1L);
        when(accessGrants.countActiveByPlatformUserIds(eq(List.of(7L, 8L)), any(OffsetDateTime.class)))
            .thenReturn(List.of(readCount, exportCount));
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(1L);

        var result = service.list("  Admin  ", null, "platform_super_admin",
            "temporarily_locked", -2, 999, new MockHttpServletRequest());

        assertThat(result.getContent()).hasSize(2);
        var first = result.getContent().get(0);
        assertThat(first.roles()).extracting("code", "name", "type").containsExactly(
            org.assertj.core.groups.Tuple.tuple("PLATFORM_SUPER_ADMIN", "平台超级管理员", "JOB_ROLE"),
            org.assertj.core.groups.Tuple.tuple("PLATFORM_TENANT_READ", "租户数据读取能力", "TECHNICAL_CAPABILITY")
        );
        assertThat(first.mfaStatus()).isEqualTo("TEMPORARILY_LOCKED");
        assertThat(first.mfaLockedUntil()).isEqualTo(current.getMfaLockedUntil());
        assertThat(first.activeGrantCount()).isEqualTo(3L);
        assertThat(first.currentUser()).isTrue();
        assertThat(first.lastEnabledSuperAdmin()).isTrue();

        var second = result.getContent().get(1);
        assertThat(second.roles()).singleElement().satisfies(role -> {
            assertThat(role.code()).isEqualTo("PLATFORM_LEGACY_REVIEWER");
            assertThat(role.name()).isEqualTo("历史复核角色");
            assertThat(role.type()).isEqualTo("UNCLASSIFIED");
        });
        assertThat(second.mfaStatus()).isEqualTo("NOT_ENROLLED");
        assertThat(second.mfaEnrolledAt()).isNull();
        assertThat(second.mfaLockedUntil()).isNull();
        assertThat(second.enabled()).isFalse();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(directory).search(eq("Admin"), eq(true), isNull(), eq("PLATFORM_SUPER_ADMIN"), eq(true),
            eq("TEMPORARILY_LOCKED"), any(OffsetDateTime.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().toString()).contains("createdAt: DESC", "id: DESC");
        verify(userRoles, never()).findByPlatformUserId(anyLong());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditDetail = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(7L), isNull(), eq("ADMIN_DIRECTORY_READ"),
            eq("platform_admin_directory"), eq("SUCCESS"), auditDetail.capture(), any());
        assertThat(auditDetail.getValue()).containsEntry("keywordApplied", true)
            .containsEntry("keywordLength", 5).containsEntry("roleCode", "PLATFORM_SUPER_ADMIN")
            .containsEntry("mfaStatus", "TEMPORARILY_LOCKED")
            .doesNotContainValue("Admin");
    }

    @Test
    void detailSplitsActiveReadAndExportCountsWithoutReturningGrantRows() {
        OffsetDateTime now = OffsetDateTime.now();
        PlatformUser target = user(9L, "Target", "target@example.com", true,
            true, now.minusDays(2), null);
        when(directory.findById(9L)).thenReturn(Optional.of(target));
        var exportRole = role(9L, "PLATFORM_TENANT_EXPORT", "公司数据导出");
        when(userRoles.findRoleViewsByPlatformUserIdIn(List.of(9L))).thenReturn(List.of(exportRole));
        var readCount = grantCount(9L, "READ", 4L);
        var exportCount = grantCount(9L, "EXPORT", 2L);
        when(accessGrants.countActiveByPlatformUserIds(eq(List.of(9L)), any(OffsetDateTime.class)))
            .thenReturn(List.of(readCount, exportCount));
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(2L);

        var result = service.detail(9L, new MockHttpServletRequest());

        assertThat(result.mfaStatus()).isEqualTo("ENROLLED");
        assertThat(result.activeGrantCount()).isEqualTo(6L);
        assertThat(result.activeGrantCounts()).containsExactlyInAnyOrderEntriesOf(Map.of("READ", 4L, "EXPORT", 2L));
        assertThat(result.roles()).singleElement().satisfies(role ->
            assertThat(role.type()).isEqualTo("TECHNICAL_CAPABILITY"));
        verify(audit).record(eq(7L), isNull(), eq("ADMIN_DIRECTORY_READ"),
            eq("platform_admin_detail"), eq("SUCCESS"),
            eq(Map.of("targetPlatformUserId", 9L)), any());
    }

    @Test
    void ordinaryPlatformAdministrationRolesAreReturnedAsCanonicalJobRoles() {
        OffsetDateTime now = OffsetDateTime.now();
        PlatformUser target = user(9L, "Target", "target@example.com", true,
            true, now.minusDays(2), null);
        when(directory.findById(9L)).thenReturn(Optional.of(target));
        var operationsRole = role(9L, "PLATFORM_OPERATIONS_ADMIN", "legacy operations label");
        var auditorRole = role(9L, "PLATFORM_SECURITY_AUDITOR", "legacy auditor label");
        when(userRoles.findRoleViewsByPlatformUserIdIn(List.of(9L)))
            .thenReturn(List.of(operationsRole, auditorRole));
        when(accessGrants.countActiveByPlatformUserIds(eq(List.of(9L)), any(OffsetDateTime.class)))
            .thenReturn(List.of());
        when(userRoles.countEnabledUsersByRoleCode("PLATFORM_SUPER_ADMIN")).thenReturn(2L);

        var result = service.detail(9L, new MockHttpServletRequest());

        assertThat(result.roles()).extracting("code", "name", "type").containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "PLATFORM_OPERATIONS_ADMIN", "平台运营管理员", "JOB_ROLE"),
            org.assertj.core.groups.Tuple.tuple(
                "PLATFORM_SECURITY_AUDITOR", "安全审计员", "JOB_ROLE")
        );
    }

    @Test
    void missingDetailReturnsStableErrorAndWritesOneFailedReadAudit() {
        when(directory.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(404L, new MockHttpServletRequest()))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.PLATFORM_ADMIN_NOT_FOUND));

        verifyNoInteractions(userRoles, accessGrants);
        verify(audit).record(eq(7L), isNull(), eq("ADMIN_DIRECTORY_READ"),
            eq("platform_admin_detail"), eq("FAILED"),
            eq(Map.of("targetPlatformUserId", 404L, "errorKey", ErrorKeys.PLATFORM_ADMIN_NOT_FOUND)), any());
    }

    @Test
    void invalidFiltersAreRejectedBeforeDirectoryQueryAndAreAuditedWithoutInputText() {
        assertThatThrownBy(() -> service.list("x".repeat(101), null, null, null,
            0, 20, new MockHttpServletRequest())).isInstanceOf(BusinessException.class);

        verifyNoInteractions(directory, userRoles, accessGrants);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditDetail = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(7L), isNull(), eq("ADMIN_DIRECTORY_READ"),
            eq("platform_admin_directory"), eq("FAILED"), auditDetail.capture(), any());
        assertThat(auditDetail.getValue()).containsOnlyKeys("errorKey")
            .containsEntry("errorKey", ErrorKeys.VALIDATION_FAILED);
    }

    @Test
    void administratorWithoutDirectoryReadRoleIsRejectedBeforeAnyDirectoryOrAuditAccess() {
        when(guard.requireAdminDirectoryRead()).thenThrow(new AccessDeniedException("not a directory reader"));

        assertThatThrownBy(() -> service.list(null, null, null, null,
            0, 20, new MockHttpServletRequest())).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(directory, userRoles, accessGrants, audit);
    }

    private PlatformUser user(Long id, String name, String email, boolean enabled, boolean mfaEnabled,
                              OffsetDateTime enrolledAt, OffsetDateTime lockedUntil) {
        OffsetDateTime created = OffsetDateTime.now().minusDays(id);
        return PlatformUser.builder().id(id).displayName(name).normalizedEmail(email).passwordHash("hidden")
            .enabled(enabled).mfaEnabled(mfaEnabled).mfaEnrolledAt(enrolledAt).mfaLockedUntil(lockedUntil)
            .createdAt(created).updatedAt(created.plusDays(1)).build();
    }

    private PlatformUserRoleRepository.RoleView role(Long userId, String code, String name) {
        PlatformUserRoleRepository.RoleView view = mock(PlatformUserRoleRepository.RoleView.class);
        when(view.getPlatformUserId()).thenReturn(userId);
        when(view.getRoleCode()).thenReturn(code);
        when(view.getDisplayName()).thenReturn(name);
        return view;
    }

    private PlatformAccessGrantRepository.ActiveGrantCountView grantCount(Long userId, String capability, Long count) {
        PlatformAccessGrantRepository.ActiveGrantCountView view =
            mock(PlatformAccessGrantRepository.ActiveGrantCountView.class);
        when(view.getPlatformUserId()).thenReturn(userId);
        when(view.getCapability()).thenReturn(capability);
        when(view.getGrantCount()).thenReturn(count);
        return view;
    }
}
