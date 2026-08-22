package com.wms.system.controller;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only permission catalogue for IAM review and role assignment planning. */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SECURITY_ADMIN')")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<PermissionDTO>> getPermissions(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            @RequestParam(name = "type", required = false) String type) {
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(permissionService.getPermissionsByType(type.trim().toUpperCase()));
        }
        return ResponseEntity.ok(activeOnly
                ? permissionService.getAllActivePermissions()
                : permissionService.getAllPermissions());
    }
}
