package com.wms.system.service;

import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysPermissionRepository;
import com.wms.system.repository.SysRolePermissionRepository;
import com.wms.system.repository.SysRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RolePermissionService Tests")
class RolePermissionServiceTest {

    @Mock private SysRolePermissionRepository rolePermissionRepository;
    @Mock private SysRoleRepository roleRepository;
    @Mock private SysPermissionRepository permissionRepository;
    @Mock private PermissionCacheService cacheService;
    @Mock private SecurityVersionService securityVersionService;

    @InjectMocks
    private RolePermissionService rolePermissionService;

    private SysRole testRole;
    private SysPermission testPermission;

    @BeforeEach
    void setUp() {
        testRole = SysRole.builder()
                .id(1L)
                .roleCode("TEST_ROLE")
                .roleName("Test Role")
                .roleType("CUSTOM")
                .reviewStatus(SysRole.REVIEW_STATUS_DRAFT)
                .status("DISABLED")
                .build();

        testPermission = SysPermission.builder()
                .id(10L)
                .permissionCode("READ_ORDERS")
                .permissionName("Read Orders")
                .permissionType("MENU")
                .status("ACTIVE")
                .build();
    }

    // ========== assignPermissionToRole ==========

    @Test
    @DisplayName("assignPermissionToRole - success assigns new permission")
    void testAssignPermissionToRole_Success() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(testPermission));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(1L, 10L)).thenReturn(false);
        when(rolePermissionRepository.save(any(SysRolePermission.class))).thenReturn(new SysRolePermission());
        doNothing().when(cacheService).onRolePermissionChanged(1L);

        rolePermissionService.assignPermissionToRole(1L, 10L, 999L);

        verify(rolePermissionRepository).save(any(SysRolePermission.class));
        verify(cacheService).onRolePermissionChanged(1L);
        verify(securityVersionService).bumpForRoleAndDescendants(1L);
    }

    @Test
    @DisplayName("assignPermissionToRole - skips when already assigned")
    void testAssignPermissionToRole_AlreadyAssigned() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(testPermission));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(1L, 10L)).thenReturn(true);

        rolePermissionService.assignPermissionToRole(1L, 10L, 999L);

        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignPermissionToRole - throws when role not found")
    void testAssignPermissionToRole_RoleNotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rolePermissionService.assignPermissionToRole(99L, 10L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found");
    }

    @Test
    @DisplayName("assignPermissionToRole - throws when permission not found")
    void testAssignPermissionToRole_PermissionNotFound() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rolePermissionService.assignPermissionToRole(1L, 99L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Permission not found");
    }

    @Test
    @DisplayName("assignPermissionToRole - rejects reserved permission for custom role")
    void testAssignPermissionToRole_RejectsReservedPermissionForCustomRole() {
        testPermission.setRiskLevel(SysPermission.RISK_LEVEL_CRITICAL);
        testPermission.setCustomAssignable(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(testPermission));

        assertThatThrownBy(() -> rolePermissionService.assignPermissionToRole(1L, 10L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE);

        verify(rolePermissionRepository, never()).save(any());
    }

    // ========== removePermissionFromRole ==========

    @Test
    @DisplayName("removePermissionFromRole - calls delete and evicts cache")
    void testRemovePermissionFromRole() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        doNothing().when(rolePermissionRepository).deleteByRoleIdAndPermissionId(1L, 10L);
        doNothing().when(cacheService).onRolePermissionChanged(1L);

        rolePermissionService.removePermissionFromRole(1L, 10L);

        verify(rolePermissionRepository).deleteByRoleIdAndPermissionId(1L, 10L);
        verify(cacheService).onRolePermissionChanged(1L);
    }

    // ========== assignPermissionsToRole (batch) ==========

    @Test
    @DisplayName("assignPermissionsToRole - batch replaces all permissions")
    void testAssignPermissionsToRole_BatchReplace() {
        Set<Long> permissionIds = Set.of(10L, 20L, 30L);

        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        doNothing().when(rolePermissionRepository).deleteByRoleId(1L);
        when(permissionRepository.findByIdIn(permissionIds)).thenReturn(permissionIds.stream()
                .map(permissionId -> SysPermission.builder()
                        .id(permissionId)
                        .permissionCode("permission:" + permissionId)
                        .permissionName("Permission " + permissionId)
                        .permissionType("API")
                        .customAssignable(true)
                        .status("ACTIVE")
                        .build())
                .toList());
        when(rolePermissionRepository.save(any(SysRolePermission.class))).thenReturn(new SysRolePermission());
        doNothing().when(cacheService).onRolePermissionChanged(1L);

        rolePermissionService.assignPermissionsToRole(1L, permissionIds, 999L);

        verify(rolePermissionRepository).deleteByRoleId(1L);
        verify(rolePermissionRepository, times(3)).save(any(SysRolePermission.class));
        verify(cacheService).onRolePermissionChanged(1L);
    }

    @Test
    @DisplayName("assignPermissionsToRole - rejects non-existent permissions before deleting")
    void testAssignPermissionsToRole_RejectsNonExistent() {
        Set<Long> permissionIds = Set.of(10L, 99L); // 99 doesn't exist

        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findByIdIn(permissionIds)).thenReturn(List.of(testPermission));

        assertThatThrownBy(() -> rolePermissionService.assignPermissionsToRole(1L, permissionIds, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE);

        verify(rolePermissionRepository, never()).deleteByRoleId(anyLong());
        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignPermissionsToRole - validates reserved permissions before deleting existing assignments")
    void testAssignPermissionsToRole_ValidatesBeforeDelete() {
        testPermission.setRiskLevel(SysPermission.RISK_LEVEL_CRITICAL);
        testPermission.setCustomAssignable(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(permissionRepository.findByIdIn(Set.of(10L))).thenReturn(List.of(testPermission));

        assertThatThrownBy(() -> rolePermissionService.assignPermissionsToRole(1L, Set.of(10L), 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorKey())
                .isEqualTo(ErrorKeys.ROLE_PACKAGE_PERMISSION_NOT_ASSIGNABLE);

        verify(rolePermissionRepository, never()).deleteByRoleId(anyLong());
        verify(rolePermissionRepository, never()).save(any());
    }

    // ========== roleHasPermission ==========

    @Test
    @DisplayName("roleHasPermission - returns true when assigned")
    void testRoleHasPermission_True() {
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(1L, 10L)).thenReturn(true);

        assertThat(rolePermissionService.roleHasPermission(1L, 10L)).isTrue();
    }

    @Test
    @DisplayName("roleHasPermission - returns false when not assigned")
    void testRoleHasPermission_False() {
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(1L, 10L)).thenReturn(false);

        assertThat(rolePermissionService.roleHasPermission(1L, 10L)).isFalse();
    }
}
