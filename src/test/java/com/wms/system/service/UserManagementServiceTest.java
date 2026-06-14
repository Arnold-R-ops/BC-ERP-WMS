package com.wms.system.service;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import com.wms.system.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SysRoleRepository roleRepository;
    @Mock
    private SysUserRoleRepository userRoleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PermissionCacheService cacheService;

    private UserManagementService service;
    private User operator;
    private User target;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(
            userRepository,
            roleRepository,
            userRoleRepository,
            passwordEncoder,
            cacheService
        );

        operator = User.builder()
            .id(1L)
            .username("admin")
            .password("encoded")
            .enabled(true)
            .build();
        target = User.builder()
            .id(2L)
            .username("employee")
            .password("encoded")
            .displayName("Employee")
            .enabled(true)
            .defaultRoleId(5L)
            .build();

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
        when(roleRepository.findByRoleCode("SUPER_ADMIN")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("revoked-password");

        service.deleteUser(2L, 1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRoleRepository).deleteByUserId(2L);
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

    @Test
    void rejectsDeletingLastSuperAdmin() {
        SysRole superAdmin = SysRole.builder()
            .id(3L)
            .roleCode("SUPER_ADMIN")
            .roleName("Super Admin")
            .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findByRoleCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));
        when(userRoleRepository.existsByUserIdAndRoleId(2L, 3L)).thenReturn(true);
        when(userRoleRepository.countActiveUsersByRoleCode("SUPER_ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser(2L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(userRoleRepository, never()).deleteByUserId(any());
        verify(userRepository, never()).save(any());
    }
}
