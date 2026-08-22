package com.wms.system.service;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.entity.TenantSessionSecurityAudit;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.SysUserWarehouseRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.repository.TenantSessionSecurityAuditRepository;
import com.wms.system.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SysRoleRepository roleRepository;
    @Mock
    private SysUserRoleRepository userRoleRepository;

    @Mock
    private SysUserWarehouseRepository userWarehouseRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PermissionCacheService cacheService;
    @Mock
    private SecurityVersionService securityVersionService;
    @Mock
    private TenantSessionSecurityAuditRepository sessionSecurityAuditRepository;

    private UserManagementService service;
    private User operator;
    private User target;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByIdAndCompanyId(any(Long.class), any(Long.class)))
            .thenAnswer(invocation -> userRepository.findById(invocation.getArgument(0)));
        lenient().when(roleRepository.findByCompanyIdAndRoleCode(any(Long.class), any(String.class)))
            .thenAnswer(invocation -> roleRepository.findByRoleCode(invocation.getArgument(1)));
        lenient().when(userRoleRepository.existsByCompanyIdAndUserIdAndRoleId(
                any(Long.class), any(Long.class), any(Long.class)))
            .thenAnswer(invocation -> userRoleRepository.existsByUserIdAndRoleId(
                invocation.getArgument(1), invocation.getArgument(2)));
        lenient().when(userRoleRepository.countActiveUsersByCompanyIdAndRoleCode(
                any(Long.class), any(String.class)))
            .thenAnswer(invocation -> userRoleRepository.countActiveUsersByRoleCode(
                invocation.getArgument(1)));
        lenient().when(userRoleRepository.findRoleIdsByCompanyIdAndUserId(
                any(Long.class), any(Long.class)))
            .thenAnswer(invocation -> userRoleRepository.findRoleIdsByUserId(
                invocation.getArgument(1)));
        lenient().when(roleRepository.findByCompanyIdAndIdIn(any(Long.class), any()))
            .thenAnswer(invocation -> roleRepository.findByIdIn(invocation.getArgument(1)));
        service = new UserManagementService(
            userRepository,
            roleRepository,
            userRoleRepository,
            userWarehouseRepository,
            passwordEncoder,
            cacheService,
            securityVersionService,
            sessionSecurityAuditRepository
        );

        operator = User.builder()
            .id(1L)
            .username("admin")
            .password("encoded")
            .enabled(true)
            .build();
        operator.setCompanyId(1L);
        target = User.builder()
            .id(2L)
            .username("employee")
            .password("encoded")
            .displayName("Employee")
            .enabled(true)
            .defaultRoleId(5L)
            .build();
        target.setCompanyId(1L);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new SecurityUser(operator),
                null,
                List.of()
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logicallyDeletesAndRevokesRoles() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleCode("TENANT_ADMIN")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("revoked-password");

        service.deleteUser(2L, 1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRoleRepository).deleteByCompanyIdAndUserId(1L, 2L);
        verify(userWarehouseRepository).deleteByCompanyIdAndUserId(1L, 2L);
        verify(userRepository).save(captor.capture());
        verify(cacheService).onUserDeleted(2L);

        User deleted = captor.getValue();
        assertThat(deleted.getIsDeleted()).isTrue();
        assertThat(deleted.getEnabled()).isFalse();
        assertThat(deleted.getDefaultRoleId()).isNull();
        assertThat(deleted.getUsername()).startsWith("deleted_2_");
        assertThat(deleted.getDeletedBy()).isEqualTo(1L);
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void rejectsSelfDeletion() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> service.deleteUser(1L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(userRoleRepository, never()).deleteByUserId(any());
        verify(userRepository, never()).save(any());
    }

    // ========== P0.5 Password Management ==========

    @Test
    void changesOwnPasswordAndClearsMustChangeFlag() {
        target.setMustChangePassword(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("old-pass", "encoded")).thenReturn(true);
        when(passwordEncoder.matches("NewPass2026", "encoded")).thenReturn(false);
        when(passwordEncoder.encode("NewPass2026")).thenReturn("encoded-new");

        service.changeOwnPassword(2L, "old-pass", "NewPass2026");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-new");
        assertThat(captor.getValue().getMustChangePassword()).isFalse();
    }

    @Test
    void rejectsPasswordChangeWithWrongOldPassword() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("wrong-old", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword(2L, "wrong-old", "NewPass2026"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PASSWORD_INCORRECT);

        verify(userRepository, never()).save(any());
    }

    @Test
    void ownerRevokesAllSessionsOnlyAfterPasswordVerification() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("current-pass", "encoded")).thenReturn(true);

        service.revokeOwnSessions(2L, "current-pass");

        verify(securityVersionService).bump(target);
        verify(userRepository).save(target);
        ArgumentCaptor<TenantSessionSecurityAudit> audit =
            ArgumentCaptor.forClass(TenantSessionSecurityAudit.class);
        verify(sessionSecurityAuditRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("SELF_REVOKE_ALL_SESSIONS");
        assertThat(audit.getValue().getTargetUserId()).isEqualTo(2L);
        assertThat(audit.getValue().getReason()).isEqualTo("SELF_SERVICE");
        assertThat(audit.getValue().getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void ownerCannotRevokeSessionsWithWrongPassword() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("wrong-pass", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> service.revokeOwnSessions(2L, "wrong-pass"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PASSWORD_INCORRECT);

        verify(securityVersionService, never()).bump(any());
        verify(sessionSecurityAuditRepository, never()).save(any());
    }

    @Test
    void tenantAdministratorRevokesTargetSessionsWithAuditReason() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        service.revokeUserSessions(
            2L, 1L, "admin", "TENANT_ADMIN", "Employee reported account compromise");

        verify(securityVersionService).bump(target);
        ArgumentCaptor<TenantSessionSecurityAudit> audit =
            ArgumentCaptor.forClass(TenantSessionSecurityAudit.class);
        verify(sessionSecurityAuditRepository).save(audit.capture());
        assertThat(audit.getValue().getOperatorId()).isEqualTo(1L);
        assertThat(audit.getValue().getTargetUserId()).isEqualTo(2L);
        assertThat(audit.getValue().getReason())
            .isEqualTo("Employee reported account compromise");
    }

    @Test
    void rejectsWeakNewPassword() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("old-pass", "encoded")).thenReturn(true);

        // Too short, digits only, letters only - all violate the policy
        for (String weak : new String[]{"a1", "12345678", "abcdefgh"}) {
            assertThatThrownBy(() -> service.changeOwnPassword(2L, "old-pass", weak))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PASSWORD_TOO_WEAK);
        }

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsNewPasswordIdenticalToOld() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("SamePass1", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> service.changeOwnPassword(2L, "SamePass1", "SamePass1"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PASSWORD_SAME_AS_OLD);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetsPasswordToTemporaryAndSetsMustChangeFlag() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode(any())).thenReturn("encoded-temp");

        var response = service.resetPassword(2L, 1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-temp");
        assertThat(captor.getValue().getMustChangePassword()).isTrue();

        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getUsername()).isEqualTo("employee");
        assertThat(response.getMustChangePassword()).isTrue();
        assertThat(response.getTemporaryPassword()).hasSize(12);
    }

    @Test
    void rejectsResettingOwnPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> service.resetPassword(1L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsDeletingLastSuperAdmin() {
        SysRole superAdmin = SysRole.builder()
            .id(3L)
            .roleCode("TENANT_ADMIN")
            .roleName("Super Admin")
            .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleCode("TENANT_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 3L)).thenReturn(true);
        when(userRoleRepository.countActiveUsersByRoleCode("TENANT_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser(2L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(userRoleRepository, never()).deleteByUserId(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsDisablingOwnAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> service.validateProfileChange(1L, 1L, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);
    }

    @Test
    void rejectsDisablingLastActiveSuperAdmin() {
        SysRole superAdmin = superAdminRole();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleCode("TENANT_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 3L)).thenReturn(true);
        when(userRoleRepository.countActiveUsersByRoleCode("TENANT_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.validateProfileChange(2L, 1L, false))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);
    }

    @Test
    void allowsDisablingSuperAdminWhenAnotherActiveAdminExists() {
        SysRole superAdmin = superAdminRole();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleCode("TENANT_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 3L)).thenReturn(true);
        when(userRoleRepository.countActiveUsersByRoleCode("TENANT_ADMIN")).thenReturn(2L);

        service.validateProfileChange(2L, 1L, false);
    }

    @Test
    void rejectsReplacingOwnRoles() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> service.validateRoleReplacement(1L, 1L, Set.of(5L)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);
    }

    @Test
    void rejectsRemovingFinalActiveSuperAdminRole() {
        SysRole superAdmin = superAdminRole();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleCode("TENANT_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 3L)).thenReturn(true);
        when(userRoleRepository.countActiveUsersByRoleCode("TENANT_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.validateRoleReplacement(2L, 1L, Set.of(5L)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);
    }

    @Test
    void allowsRoleReplacementWhenSuperAdminRoleIsRetained() {
        SysRole superAdmin = superAdminRole();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleCode("TENANT_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 3L)).thenReturn(true);

        service.validateRoleReplacement(2L, 1L, Set.of(3L, 5L));

        verify(userRoleRepository, never()).countActiveUsersByRoleCode(any());
    }

    @Test
    void securityAdministratorCannotAssignAProtectedRole() {
        SysRole securityAdmin = privilegedRole(4L, "SECURITY_ADMIN");

        assertThatThrownBy(() -> service.validateRoleAssignment(
            List.of(securityAdmin),
            SysRole.SECURITY_ADMIN_ROLE_CODE
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);
    }

    @Test
    void securityAdministratorCannotModifyAProtectedAccount() {
        SysRole securityAdmin = privilegedRole(4L, "SECURITY_ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRoleRepository.findRoleIdsByUserId(2L)).thenReturn(Set.of(4L));
        when(roleRepository.findByIdIn(Set.of(4L))).thenReturn(List.of(securityAdmin));

        assertThatThrownBy(() -> service.validateProfileChange(
            2L,
            1L,
            true,
            SysRole.SECURITY_ADMIN_ROLE_CODE
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);
    }

    @Test
    void securityAdministratorMayModifyAnOrdinaryAccount() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRoleRepository.findRoleIdsByUserId(2L)).thenReturn(Set.of());

        service.validateProfileChange(2L, 1L, true, SysRole.SECURITY_ADMIN_ROLE_CODE);
    }

    @Test
    void securityAdministratorCannotResetAProtectedAccountPassword() {
        SysRole superAdmin = privilegedRole(3L, "TENANT_ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRoleRepository.findRoleIdsByUserId(2L)).thenReturn(Set.of(3L));
        when(roleRepository.findByIdIn(Set.of(3L))).thenReturn(List.of(superAdmin));

        assertThatThrownBy(() -> service.resetPassword(
            2L,
            1L,
            SysRole.SECURITY_ADMIN_ROLE_CODE
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(userRepository, never()).save(any());
    }

    private SysRole superAdminRole() {
        return SysRole.builder()
            .id(3L)
            .roleCode("TENANT_ADMIN")
            .roleName("Super Admin")
            .status("ACTIVE")
            .build();
    }

    private SysRole privilegedRole(Long id, String roleCode) {
        return SysRole.builder()
            .id(id)
            .roleCode(roleCode)
            .roleName(roleCode)
            .roleType(SysRole.ROLE_TYPE_SYSTEM)
            .systemCategory(SysRole.SYSTEM_CATEGORY_PRIVILEGED)
            .status("ACTIVE")
            .build();
    }
}
