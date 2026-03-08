package com.wms.system.service;

import com.wms.system.dto.MenuTreeDTO;
import com.wms.system.dto.PermissionDTO;
import com.wms.system.entity.SysPermission;
import com.wms.system.repository.SysPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("PermissionService Tests")
class PermissionServiceTest {

    @Mock private SysPermissionRepository permissionRepository;
    @Mock private PermissionCacheService cacheService;

    @InjectMocks
    private PermissionService permissionService;

    private SysPermission menuPermission;
    private SysPermission childPermission;

    @BeforeEach
    void setUp() {
        menuPermission = SysPermission.builder()
                .id(1L)
                .permissionCode("menu:inventory")
                .permissionName("Inventory Menu")
                .permissionType("MENU")
                .parentId(null)
                .status("ACTIVE")
                .sortOrder(1)
                .build();

        childPermission = SysPermission.builder()
                .id(2L)
                .permissionCode("inventory:view")
                .permissionName("View Inventory")
                .permissionType("BUTTON")
                .parentId(1L)
                .status("ACTIVE")
                .sortOrder(1)
                .build();
    }

    // ========== createPermission ==========

    @Test
    @DisplayName("createPermission - success when code is unique")
    void testCreatePermission_Success() {
        PermissionDTO dto = PermissionDTO.builder()
                .permissionCode("menu:new")
                .permissionName("New Menu")
                .permissionType("MENU")
                .status("ACTIVE")
                .sortOrder(10)
                .build();

        when(permissionRepository.existsByPermissionCode("menu:new")).thenReturn(false);
        when(permissionRepository.save(any(SysPermission.class))).thenReturn(
                SysPermission.builder().id(10L).permissionCode("menu:new")
                        .permissionName("New Menu").permissionType("MENU")
                        .status("ACTIVE").sortOrder(10).build()
        );
        doNothing().when(cacheService).onPermissionChanged(any());

        PermissionDTO result = permissionService.createPermission(dto);

        assertThat(result).isNotNull();
        assertThat(result.getPermissionCode()).isEqualTo("menu:new");
    }

    @Test
    @DisplayName("createPermission - throws when code already exists")
    void testCreatePermission_DuplicateCode() {
        PermissionDTO dto = PermissionDTO.builder()
                .permissionCode("menu:inventory")
                .permissionName("Duplicate")
                .build();

        when(permissionRepository.existsByPermissionCode("menu:inventory")).thenReturn(true);

        assertThatThrownBy(() -> permissionService.createPermission(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ========== deletePermission ==========

    @Test
    @DisplayName("deletePermission - success when no children")
    void testDeletePermission_Success() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(menuPermission));
        when(permissionRepository.findByParentId(1L)).thenReturn(List.of());
        doNothing().when(cacheService).onPermissionChanged(any());

        permissionService.deletePermission(1L);

        verify(permissionRepository).delete(menuPermission);
    }

    @Test
    @DisplayName("deletePermission - throws when has child permissions")
    void testDeletePermission_HasChildren() {
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(menuPermission));
        when(permissionRepository.findByParentId(1L)).thenReturn(List.of(childPermission));

        assertThatThrownBy(() -> permissionService.deletePermission(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("child permissions");

        verify(permissionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deletePermission - throws when not found")
    void testDeletePermission_NotFound() {
        when(permissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.deletePermission(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== getPermissionsByType ==========

    @Test
    @DisplayName("getPermissionsByType MENU - returns only menu permissions")
    void testGetPermissionsByType_Menu() {
        when(permissionRepository.findByPermissionType("MENU"))
                .thenReturn(List.of(menuPermission));

        List<PermissionDTO> result = permissionService.getPermissionsByType("MENU");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPermissionType()).isEqualTo("MENU");
    }

    @Test
    @DisplayName("getPermissionsByType BUTTON - returns only button permissions")
    void testGetPermissionsByType_Button() {
        when(permissionRepository.findByPermissionType("BUTTON"))
                .thenReturn(List.of(childPermission));

        List<PermissionDTO> result = permissionService.getPermissionsByType("BUTTON");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPermissionType()).isEqualTo("BUTTON");
    }

    // ========== buildMenuTree ==========

    @Test
    @DisplayName("buildMenuTree - returns hierarchical structure for MENU type ACTIVE only")
    void testBuildMenuTree_Success() {
        SysPermission rootMenu = SysPermission.builder()
                .id(1L).permissionCode("menu:root").permissionName("Root")
                .permissionType("MENU").parentId(null).status("ACTIVE").sortOrder(1).build();
        SysPermission childMenu = SysPermission.builder()
                .id(2L).permissionCode("menu:child").permissionName("Child")
                .permissionType("MENU").parentId(1L).status("ACTIVE").sortOrder(1).build();

        when(permissionRepository.findByPermissionTypeAndStatus("MENU", "ACTIVE"))
                .thenReturn(List.of(rootMenu, childMenu));

        List<MenuTreeDTO> result = permissionService.buildMenuTree();

        assertThat(result).hasSize(1); // only root
        assertThat(result.get(0).getChildren()).hasSize(1); // child nested
    }

    @Test
    @DisplayName("buildMenuTree - returns empty when no menus")
    void testBuildMenuTree_Empty() {
        when(permissionRepository.findByPermissionTypeAndStatus("MENU", "ACTIVE"))
                .thenReturn(List.of());

        List<MenuTreeDTO> result = permissionService.buildMenuTree();

        assertThat(result).isEmpty();
    }

    // ========== getChildPermissions ==========

    @Test
    @DisplayName("getChildPermissions - returns children of given parent")
    void testGetChildPermissions() {
        when(permissionRepository.findByParentId(1L)).thenReturn(List.of(childPermission));

        List<PermissionDTO> result = permissionService.getChildPermissions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParentId()).isEqualTo(1L);
    }
}
