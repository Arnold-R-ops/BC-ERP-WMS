package com.wms.system.controller;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.RoleCopyPreviewResponse;
import com.wms.system.dto.RoleCopyRequest;
import com.wms.system.dto.RoleCopyResult;
import com.wms.system.dto.RoleDTO;
import com.wms.system.dto.RoleGovernanceAuditDTO;
import com.wms.system.dto.RoleGovernanceResult;
import com.wms.system.dto.RolePackageCreateRequest;
import com.wms.system.dto.RolePackageDraftRequest;
import com.wms.system.dto.RoleReviewRequest;
import com.wms.system.dto.RoleReviewSubmitRequest;
import com.wms.system.dto.RoleRuntimeStatusRequest;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.RoleCopyService;
import com.wms.system.service.RoleGovernanceService;
import com.wms.system.service.RolePermissionService;
import com.wms.system.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECURITY_ADMIN')")
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final RoleCopyService roleCopyService;
    private final RoleGovernanceService roleGovernanceService;

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

    @PostMapping("/drafts")
    public ResponseEntity<RoleGovernanceResult> createDraft(
            @Valid @RequestBody RolePackageCreateRequest request,
            Authentication authentication
    ) {
        RoleGovernanceResult result = roleGovernanceService.createDraft(
                request,
                AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/copy-preview")
    public ResponseEntity<RoleCopyPreviewResponse> previewCopy(@PathVariable("id") Long id) {
        return ResponseEntity.ok(roleCopyService.preview(id));
    }

    @PostMapping("/{id}/copies")
    public ResponseEntity<RoleCopyResult> copyRole(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoleCopyRequest request,
            Authentication authentication
    ) {
        RoleCopyResult result = roleCopyService.copy(
                id,
                request,
                AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}/draft")
    public ResponseEntity<RoleGovernanceResult> updateDraft(
            @PathVariable("id") Long id,
            @Valid @RequestBody RolePackageDraftRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleGovernanceService.updateDraft(
                id,
                request,
                AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @PostMapping("/{id}/submit-review")
    public ResponseEntity<RoleGovernanceResult> submitReview(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoleReviewSubmitRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleGovernanceService.submitForReview(
                id,
                request,
                AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<RoleGovernanceResult> review(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoleReviewRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleGovernanceService.review(
                id,
                request,
                AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication),
                AuthUserResolver.resolveCurrentRole(authentication)
        ));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<RoleGovernanceResult> activate(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoleRuntimeStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleGovernanceService.activate(
                id,
                request,
                AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<RoleGovernanceResult> deactivate(
            @PathVariable("id") Long id,
            @Valid @RequestBody RoleRuntimeStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleGovernanceService.deactivate(
                id,
                request,
                AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @GetMapping("/{id}/governance-history")
    public ResponseEntity<List<RoleGovernanceAuditDTO>> history(@PathVariable("id") Long id) {
        return ResponseEntity.ok(roleGovernanceService.history(id));
    }
}
