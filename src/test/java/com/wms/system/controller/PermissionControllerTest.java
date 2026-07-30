package com.wms.system.controller;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.service.PermissionService;
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
class PermissionControllerTest {

    @Mock
    private PermissionService permissionService;
    @InjectMocks
    private PermissionController controller;

    @Test
    void listsAllPermissionsByDefault() {
        PermissionDTO permission = PermissionDTO.builder().id(1L).permissionCode("inventory:view").build();
        when(permissionService.getAllPermissions()).thenReturn(List.of(permission));

        var response = controller.getPermissions(false, null);

        assertThat(response.getBody()).containsExactly(permission);
        verify(permissionService).getAllPermissions();
    }

    @Test
    void listsActivePermissionsWhenRequested() {
        when(permissionService.getAllActivePermissions()).thenReturn(List.of());

        controller.getPermissions(true, null);

        verify(permissionService).getAllActivePermissions();
    }

    @Test
    void filtersByNormalisedPermissionType() {
        PermissionDTO permission = PermissionDTO.builder().permissionType("API").build();
        when(permissionService.getPermissionsByType("API")).thenReturn(List.of(permission));

        var response = controller.getPermissions(false, " api ");

        assertThat(response.getBody()).containsExactly(permission);
        verify(permissionService).getPermissionsByType("API");
    }
}
