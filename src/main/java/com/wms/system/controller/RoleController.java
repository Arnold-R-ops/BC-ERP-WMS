package com.wms.system.controller;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.RoleDTO;
import com.wms.system.service.RolePermissionService;
import com.wms.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only role catalogue used by the IAM administration workspace.
 *
 * Role and permission mutation stays behind the existing service boundary until
 * the historical permission catalogue has been audited and normalised.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    @GetMapping
    public ResponseEntity<List<RoleDTO>> getRoles(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        List<RoleDTO> roles = activeOnly
                ? roleService.getAllActiveRoles()
                : roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDTO> getRole(@PathVariable("id") Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<PermissionDTO>> getRolePermissions(@PathVariable("id") Long id) {
        roleService.getRoleById(id);
        return ResponseEntity.ok(rolePermissionService.getRolePermissions(id));
    }
}
