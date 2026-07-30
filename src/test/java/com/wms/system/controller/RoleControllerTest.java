package com.wms.system.controller;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.RoleDTO;
import com.wms.system.service.RolePermissionService;
import com.wms.system.service.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleService roleService;
    @Mock
    private RolePermissionService rolePermissionService;
    @InjectMocks
    private RoleController controller;

    @Test
    void listsAllRolesByDefault() {
        RoleDTO role = RoleDTO.builder().id(1L).roleCode("SUPER_ADMIN").build();
        when(roleService.getAllRoles()).thenReturn(List.of(role));

        var response = controller.getRoles(false);

        assertThat(response.getBody()).containsExactly(role);
        verify(roleService).getAllRoles();
    }

    @Test
    void listsOnlyActiveRolesWhenRequested() {
        RoleDTO role = RoleDTO.builder().id(2L).roleCode("WAREHOUSE_ADMIN").build();
        when(roleService.getAllActiveRoles()).thenReturn(List.of(role));

        var response = controller.getRoles(true);

        assertThat(response.getBody()).containsExactly(role);
        verify(roleService).getAllActiveRoles();
    }

    @Test
    void returnsRolePermissionsAfterRoleExistenceCheck() {
        RoleDTO role = RoleDTO.builder().id(2L).roleCode("WAREHOUSE_ADMIN").build();
        PermissionDTO permission = PermissionDTO.builder().id(10L).permissionCode("inventory:view").build();
        when(roleService.getRoleById(2L)).thenReturn(role);
        when(rolePermissionService.getRolePermissions(2L)).thenReturn(List.of(permission));

        var response = controller.getRolePermissions(2L);

        assertThat(response.getBody()).containsExactly(permission);
        verify(roleService).getRoleById(2L);
    }
}
