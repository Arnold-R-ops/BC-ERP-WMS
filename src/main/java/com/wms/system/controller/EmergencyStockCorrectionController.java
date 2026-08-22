package com.wms.system.controller;

import com.wms.system.dto.v45.EmergencyStockCorrectionActionRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionResponse;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.EmergencyStockCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emergency-stock-corrections")
@RequiredArgsConstructor
public class EmergencyStockCorrectionController {

    private final EmergencyStockCorrectionService correctionService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('inventory:adjust', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> create(
        @Valid @RequestBody EmergencyStockCorrectionRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(correctionService.create(request, userId));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('inventory:correction:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<EmergencyStockCorrectionResponse>> list(
        @RequestParam(value = "status", required = false) EmergencyCorrectionStatus status
    ) {
        return ResponseEntity.ok(correctionService.list(status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('inventory:correction:view', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(correctionService.get(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('inventory:adjust', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> submit(
        @PathVariable("id") Long id,
        @RequestBody(required = false) EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(correctionService.submit(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('inventory:correction:review', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> review(
        @PathVariable("id") Long id,
        @RequestBody(required = false) EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(correctionService.review(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('inventory:correction:approve', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> approve(
        @PathVariable("id") Long id,
        @RequestBody(required = false) EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(correctionService.approve(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('inventory:correction:reject', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> reject(
        @PathVariable("id") Long id,
        @RequestBody(required = false) EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(correctionService.reject(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAnyAuthority('inventory:adjust', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> apply(
        @PathVariable("id") Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(correctionService.apply(id, AuthUserResolver.resolveUserId(authentication)));
    }

    private String comment(EmergencyStockCorrectionActionRequest request) {
        return request == null ? null : request.getComment();
    }
}
