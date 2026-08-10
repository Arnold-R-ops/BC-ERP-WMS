package com.wms.system.controller;

import com.wms.system.dto.PermissionRequestAuditDTO;
import com.wms.system.dto.PermissionRequestCreateRequest;
import com.wms.system.dto.PermissionRequestDTO;
import com.wms.system.dto.PermissionRequestPageResponse;
import com.wms.system.dto.PermissionRequestReviewRequest;
import com.wms.system.dto.PermissionRequestRevokeRequest;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.PermissionRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permission-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SECURITY_ADMIN')")
public class PermissionRequestController {
    private final PermissionRequestService permissionRequestService;

    @GetMapping
    public ResponseEntity<PermissionRequestPageResponse> list(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "targetUserId", required = false) Long targetUserId,
        @RequestParam(name = "requestedRoleId", required = false) Long requestedRoleId,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size,
        Authentication authentication
    ) {
        return ResponseEntity.ok(permissionRequestService.list(
            status,
            targetUserId,
            requestedRoleId,
            page,
            size,
            AuthUserResolver.resolveCurrentRole(authentication)
        ));
    }

    @PostMapping
    public ResponseEntity<PermissionRequestDTO> create(
        @Valid @RequestBody PermissionRequestCreateRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(permissionRequestService.create(
            request,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication),
            AuthUserResolver.resolveCurrentRole(authentication)
        ));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<PermissionRequestDTO> review(
        @PathVariable("id") Long id,
        @Valid @RequestBody PermissionRequestReviewRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(permissionRequestService.review(
            id,
            request,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication),
            AuthUserResolver.resolveCurrentRole(authentication)
        ));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<PermissionRequestDTO> revoke(
        @PathVariable("id") Long id,
        @Valid @RequestBody PermissionRequestRevokeRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(permissionRequestService.revoke(
            id,
            request,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication),
            AuthUserResolver.resolveCurrentRole(authentication)
        ));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<PermissionRequestAuditDTO>> history(
        @PathVariable("id") Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(permissionRequestService.history(
            id,
            AuthUserResolver.resolveCurrentRole(authentication)
        ));
    }
}

