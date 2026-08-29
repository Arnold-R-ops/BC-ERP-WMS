package com.wms.system.controller;

import com.wms.system.dto.inventoryquality.*;
import com.wms.system.entity.enums.InventoryQualityActionStatus;
import com.wms.system.entity.enums.InventoryQualityActionType;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.WarehouseScopeService;
import com.wms.system.service.inventory.InventoryQualityActionService;
import com.wms.system.service.inventory.InventoryQualityPolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/api/inventory-quality")
@RequiredArgsConstructor
public class InventoryQualityController {
    private final InventoryQualityActionService actionService;
    private final InventoryQualityPolicyService policyService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyAuthority('inventory_quality:view', 'inventory_quality:hold', 'inventory_quality:release', 'TENANT_ADMIN')")
    public ResponseEntity<List<InventoryQualityCandidateResponse>> candidates(
        @RequestParam(defaultValue = "") @Size(max = 100) String search,
        Authentication authentication
    ) {
        return ResponseEntity.ok(actionService.candidates(search, allowedWarehouses(authentication)));
    }

    @PostMapping("/hold-preview")
    @PreAuthorize("hasAnyAuthority('inventory_quality:hold', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityHoldPreviewResponse> previewHold(
        @Valid @RequestBody InventoryQualityHoldRequest request,
        Authentication authentication
    ) {
        requireBatchAccess(authentication, request.lines(), "InventoryQualityHoldPreview");
        return ResponseEntity.ok(actionService.previewHold(request, allowedWarehouses(authentication)));
    }

    @PostMapping("/holds")
    @PreAuthorize("hasAnyAuthority('inventory_quality:hold', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityActionResponse> hold(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody InventoryQualityHoldRequest request,
        Authentication authentication
    ) {
        requireBatchAccess(authentication, request.lines(), "InventoryQualityHold");
        return ResponseEntity.status(HttpStatus.CREATED).body(actionService.hold(
            request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication), allowedWarehouses(authentication)
        ));
    }

    @PostMapping("/releases")
    @PreAuthorize("hasAnyAuthority('inventory_quality:release', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityActionResponse> release(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody InventoryQualityReleaseRequest request,
        Authentication authentication
    ) {
        requireBatchAccess(authentication, request.lines(), "InventoryQualityRelease");
        return ResponseEntity.status(HttpStatus.CREATED).body(actionService.release(
            request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @GetMapping("/actions")
    @PreAuthorize("hasAnyAuthority('inventory_quality:view', 'inventory_quality:hold', 'inventory_quality:release', 'inventory_quality:review', 'TENANT_ADMIN')")
    public ResponseEntity<List<InventoryQualityActionResponse>> list(
        @RequestParam(required = false) InventoryQualityActionType type,
        @RequestParam(required = false) InventoryQualityActionStatus status,
        Authentication authentication
    ) {
        return ResponseEntity.ok(warehouseScopeService.filterAccessible(
            authentication,
            actionService.list(type, status),
            action -> action.lines().stream().map(InventoryQualityActionLineResponse::warehouseId).toList()
        ));
    }

    @GetMapping("/actions/{id}")
    @PreAuthorize("hasAnyAuthority('inventory_quality:view', 'inventory_quality:hold', 'inventory_quality:release', 'inventory_quality:review', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityActionResponse> get(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireActionAccess(authentication, id);
        return ResponseEntity.ok(actionService.get(id));
    }

    @PostMapping("/actions/{id}/approve")
    @PreAuthorize("hasAnyAuthority('inventory_quality:review', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityActionResponse> approve(
        @PathVariable Long id,
        @Valid @RequestBody InventoryQualityActionRequest request,
        Authentication authentication
    ) {
        requireActionAccess(authentication, id);
        return ResponseEntity.ok(actionService.approve(
            id, request.comment(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/actions/{id}/reject")
    @PreAuthorize("hasAnyAuthority('inventory_quality:review', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityActionResponse> reject(
        @PathVariable Long id,
        @Valid @RequestBody InventoryQualityActionRequest request,
        Authentication authentication
    ) {
        requireActionAccess(authentication, id);
        return ResponseEntity.ok(actionService.reject(
            id, request.comment(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/actions/{id}/withdraw")
    @PreAuthorize("hasAnyAuthority('inventory_quality:release', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityActionResponse> withdraw(
        @PathVariable Long id,
        @Valid @RequestBody InventoryQualityActionRequest request,
        Authentication authentication
    ) {
        requireActionAccess(authentication, id);
        return ResponseEntity.ok(actionService.withdraw(
            id, request.comment(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyAuthority('inventory_quality:view', 'inventory_quality:release', 'inventory_quality:review', 'inventory_quality:policy', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityPolicyResponse> policy() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyAuthority('inventory_quality:policy', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryQualityPolicyResponse> updatePolicy(
        @Valid @RequestBody InventoryQualityPolicyUpdateRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(policyService.update(
            request.releaseMode(), request.reason(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    private void requireBatchAccess(
        Authentication authentication,
        List<InventoryQualityQuantityRequest> lines,
        String resourceType
    ) {
        warehouseScopeService.requireAccess(
            authentication,
            actionService.warehouseIdsForBatches(lines),
            resourceType,
            lines.stream().map(InventoryQualityQuantityRequest::batchId).toList()
        );
    }

    private void requireActionAccess(Authentication authentication, Long actionId) {
        warehouseScopeService.requireAccess(
            authentication,
            actionService.warehouseIdsForAction(actionId),
            "InventoryQualityAction",
            actionId
        );
    }

    private Set<Long> allowedWarehouses(Authentication authentication) {
        return warehouseScopeService.isWarehouseStaff(authentication)
            ? warehouseScopeService.getAssignedWarehouseIds(authentication)
            : null;
    }
}
