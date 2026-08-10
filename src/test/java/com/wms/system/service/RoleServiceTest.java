package com.wms.system.service;

import com.wms.system.dto.RoleDTO;
import com.wms.system.entity.SysRole;
import com.wms.system.repository.SysRoleInheritRepository;
import com.wms.system.repository.SysRolePermissionRepository;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.SysUserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService Tests")
class RoleServiceTest {

    @Mock private SysRoleRepository roleRepository;
    @Mock private SysRoleInheritRepository roleInheritRepository;
    @Mock private SysRolePermissionRepository rolePermissionRepository;
    @Mock private SysUserRoleRepository userRoleRepository;
    @Mock private PermissionCacheService cacheService;
    @Mock private SecurityVersionService securityVersionService;

    @InjectMocks
    private RoleService roleService;

    private SysRole customRole;
    private SysRole systemRole;

    @BeforeEach
    void setUp() {
        customRole = SysRole.builder()
                .id(1L)
                .roleCode("CUSTOM_ROLE")
                .roleName("Custom Role")
                .roleType("CUSTOM")
                .importAllowed(true)
                .approvalTemplateCode(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE)
                .reviewStatus(SysRole.REVIEW_STATUS_DRAFT)
                .status("DISABLED")
                .sortOrder(10)
                .build();

        systemRole = SysRole.builder()
                .id(2L)
                .roleCode("SUPER_ADMIN")
                .roleName("Super Admin")
                .roleType("SYSTEM")
                .systemCategory(SysRole.SYSTEM_CATEGORY_PRIVILEGED)
                .importAllowed(false)
                .status("ACTIVE")
                .sortOrder(1)
                .build();
    }

    // ========== createRole ==========

    @Test
    @DisplayName("createRole - success when code is unique")
    void testCreateRole_Success() {
        RoleDTO dto = RoleDTO.builder()
                .roleCode("NEW_ROLE")
                .roleName("New Role")
                .roleType("CUSTOM")
                .status("ACTIVE")
                .build();

        when(roleRepository.existsByRoleCode("NEW_ROLE")).thenReturn(false);
        when(roleRepository.save(any(SysRole.class))).thenReturn(
                SysRole.builder().id(10L).roleCode("NEW_ROLE").roleName("New Role")
                        .roleType("CUSTOM").importAllowed(true)
                        .approvalTemplateCode(SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE)
                        .reviewStatus(SysRole.REVIEW_STATUS_DRAFT)
                        .status("DISABLED").sortOrder(0).build()
        );

        RoleDTO result = roleService.createRole(dto);

        assertThat(result).isNotNull();
        assertThat(result.getRoleCode()).isEqualTo("NEW_ROLE");
        verify(roleRepository).save(argThat(role ->
                SysRole.ROLE_TYPE_CUSTOM.equals(role.getRoleType())
                        && role.getSystemCategory() == null
                        && Boolean.TRUE.equals(role.getImportAllowed())
                        && SysRole.SIMPLE_APPROVAL_TEMPLATE_CODE.equals(role.getApprovalTemplateCode())
                        && role.isDraft()
                        && !role.isActive()));
    }

    @Test
    @DisplayName("createRole - throws when role code already exists")
    void testCreateRole_DuplicateCode() {
        RoleDTO dto = RoleDTO.builder()
                .roleCode("CUSTOM_ROLE")
                .roleName("Duplicate")
                .build();

        when(roleRepository.existsByRoleCode("CUSTOM_ROLE")).thenReturn(true);

        assertThatThrownBy(() -> roleService.createRole(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(roleRepository, never()).save(any());
    }

    // ========== updateRole ==========

    @Test
    @DisplayName("updateRole - success for custom role")
    void testUpdateRole_Success() {
        RoleDTO dto = RoleDTO.builder()
                .roleCode("CUSTOM_ROLE")
                .roleName("Updated Role")
                .status("ACTIVE")
                .sortOrder(20)
                .build();

        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(roleRepository.save(any(SysRole.class))).thenReturn(customRole);
        doNothing().when(cacheService).evictPermissionsForRole(1L);

        RoleDTO result = roleService.updateRole(1L, dto);

        assertThat(result).isNotNull();
        verify(roleRepository).save(any(SysRole.class));
        verify(cacheService).evictPermissionsForRole(1L);
        verify(cacheService).evictRoleInheritCache();
    }

    @Test
    @DisplayName("updateRole - throws when system role code is changed")
    void testUpdateRole_CannotModifySystemRoleCode() {
        RoleDTO dto = RoleDTO.builder()
                .roleCode("DIFFERENT_CODE") // changed code
                .roleName("Super Admin")
                .status("ACTIVE")
                .build();

        when(roleRepository.findById(2L)).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> roleService.updateRole(2L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("System roles cannot be modified online");
    }

    @Test
    @DisplayName("updateRole - throws when role not found")
    void testUpdateRole_NotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.updateRole(99L, RoleDTO.builder()
                .roleCode("X").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addRoleInheritance - rejects inheriting a privileged role")
    void testAddRoleInheritance_RejectsPrivilegedParent() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> roleService.addRoleInheritance(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("privileged role");

        verify(roleInheritRepository, never()).save(any());
    }

    // ========== deleteRole ==========

    @Test
    @DisplayName("deleteRole - success for custom role with no users")
    void testDeleteRole_Success() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(userRoleRepository.countByRoleId(1L)).thenReturn(0L);
        doNothing().when(cacheService).evictAllUserPermissions();

        roleService.deleteRole(1L);

        InOrder deletionOrder = inOrder(securityVersionService, roleRepository);
        deletionOrder.verify(securityVersionService).bumpForRoleAndDescendants(1L);
        deletionOrder.verify(roleRepository).delete(customRole);
        verify(cacheService).evictAllUserPermissions();
        verify(cacheService).evictRoleInheritCache();
    }

    @Test
    @DisplayName("deleteRole - throws for system role")
    void testDeleteRole_SystemRole() {
        when(roleRepository.findById(2L)).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> roleService.deleteRole(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system role");

        verify(roleRepository, never()).delete(any());
        verify(securityVersionService, never()).bumpForRoleAndDescendants(anyLong());
    }

    @Test
    @DisplayName("deleteRole - throws when role has users assigned")
    void testDeleteRole_HasUsers() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(userRoleRepository.countByRoleId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 users");

        verify(roleRepository, never()).delete(any());
        verify(securityVersionService, never()).bumpForRoleAndDescendants(anyLong());
    }

    // ========== getRoleById ==========

    @Test
    @DisplayName("getRoleById - returns DTO when found")
    void testGetRoleById_Success() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));

        RoleDTO result = roleService.getRoleById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getRoleCode()).isEqualTo("CUSTOM_ROLE");
    }

    @Test
    @DisplayName("getRoleById - throws when not found")
    void testGetRoleById_NotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getRoleById(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== getAllActiveRoles ==========

    @Test
    @DisplayName("getAllActiveRoles - returns active roles")
    void testGetAllActiveRoles() {
        when(roleRepository.findAllActive()).thenReturn(List.of(customRole, systemRole));

        List<RoleDTO> result = roleService.getAllActiveRoles();

        assertThat(result).hasSize(2);
    }
}
