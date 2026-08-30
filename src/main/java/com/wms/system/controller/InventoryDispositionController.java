package com.wms.system.controller;

import com.wms.system.dto.inventorydisposition.*;
import com.wms.system.entity.enums.InventoryDispositionStatus;
import com.wms.system.entity.enums.InventoryDispositionType;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.WarehouseScopeService;
import com.wms.system.service.inventory.InventoryDispositionPolicyService;
import com.wms.system.service.inventory.InventoryDispositionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Validated
@RestController
@RequestMapping("/api/inventory-dispositions")
@RequiredArgsConstructor
public class InventoryDispositionController {
    private final InventoryDispositionService dispositionService;
    private final InventoryDispositionPolicyService policyService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:view', 'inventory_disposition:report', 'inventory_disposition:create', 'inventory_disposition:execute', 'TENANT_ADMIN')")
    public ResponseEntity<List<InventoryDispositionCandidateResponse>> candidates(
        @RequestParam(defaultValue = "") @Size(max = 100) String search,
        Authentication authentication
    ) {
        return ResponseEntity.ok(dispositionService.candidates(
            search, allowedWarehouses(authentication)));
    }

    @PostMapping("/damage-preview")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:report', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDamagePreviewResponse> damagePreview(
        @Valid @RequestBody ReportInventoryDamageRequest request,
        Authentication authentication
    ) {
        requireBatchAccess(authentication, request.lines(), "InventoryDamagePreview");
        return ResponseEntity.ok(dispositionService.previewDamage(
            request, allowedWarehouses(authentication)));
    }

    @PostMapping("/damage-reports")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:report', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> reportDamage(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ReportInventoryDamageRequest request,
        Authentication authentication
    ) {
        requireBatchAccess(authentication, request.lines(), "InventoryDamageReport");
        return ResponseEntity.status(HttpStatus.CREATED).body(dispositionService.reportDamage(
            request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication), allowedWarehouses(authentication)));
    }

    @PostMapping("/scrap-orders")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:create', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> createScrapOrder(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateInventoryScrapOrderRequest request,
        Authentication authentication
    ) {
        requireBatchAccess(authentication, request.lines(), "InventoryScrapOrder");
        return ResponseEntity.status(HttpStatus.CREATED).body(dispositionService.createScrapOrder(
            request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication), allowedWarehouses(authentication)));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:view', 'inventory_disposition:report', 'inventory_disposition:create', 'inventory_disposition:execute', 'inventory_disposition:review', 'TENANT_ADMIN')")
    public ResponseEntity<List<InventoryDispositionResponse>> list(
        @RequestParam(required = false) InventoryDispositionType type,
        @RequestParam(required = false) InventoryDispositionStatus status,
        Authentication authentication
    ) {
        return ResponseEntity.ok(warehouseScopeService.filterAccessible(
            authentication,
            dispositionService.list(type, status),
            order -> order.lines().stream()
                .map(InventoryDispositionLineResponse::warehouseId).toList()
        ));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:view', 'inventory_disposition:report', 'inventory_disposition:create', 'inventory_disposition:execute', 'inventory_disposition:review', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> get(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireOrderAccess(authentication, id);
        return ResponseEntity.ok(dispositionService.get(id));
    }

    @PostMapping("/orders/{id}/approve")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:review', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> approve(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody InventoryDispositionDecisionRequest request,
        Authentication authentication
    ) {
        requireOrderAccess(authentication, id);
        return ResponseEntity.ok(dispositionService.approve(
            id, request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/orders/{id}/submit")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:create', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> submit(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        Authentication authentication
    ) {
        requireOrderAccess(authentication, id);
        return ResponseEntity.ok(dispositionService.submit(
            id, idempotencyKey, AuthUserResolver.resolveUserId(authentication)));
    }

    @PostMapping("/orders/{id}/reject")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:review', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> reject(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody InventoryDispositionDecisionRequest request,
        Authentication authentication
    ) {
        requireOrderAccess(authentication, id);
        return ResponseEntity.ok(dispositionService.reject(
            id, request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:create', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> cancel(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody InventoryDispositionDecisionRequest request,
        Authentication authentication
    ) {
        requireOrderAccess(authentication, id);
        return ResponseEntity.ok(dispositionService.cancel(
            id, request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/orders/{id}/executions")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:execute', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionResponse> execute(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ExecuteInventoryDispositionRequest request,
        Authentication authentication
    ) {
        requireOrderAccess(authentication, id);
        return ResponseEntity.ok(dispositionService.execute(
            id, request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:view', 'inventory_disposition:policy', 'inventory_disposition:review', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionPolicyResponse> policy() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyAuthority('inventory_disposition:policy', 'TENANT_ADMIN')")
    public ResponseEntity<InventoryDispositionPolicyResponse> updatePolicy(
        @Valid @RequestBody UpdateInventoryDispositionPolicyRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(policyService.update(
            request, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    private void requireBatchAccess(
        Authentication authentication,
        List<InventoryDispositionQuantityRequest> lines,
        String resourceType
    ) {
        warehouseScopeService.requireAccess(
            authentication,
            dispositionService.warehouseIdsForBatches(lines),
            resourceType,
            lines.stream().map(InventoryDispositionQuantityRequest::batchId).toList()
        );
    }

    private void requireOrderAccess(Authentication authentication, Long orderId) {
        warehouseScopeService.requireAccess(
            authentication,
            dispositionService.warehouseIdsForOrder(orderId),
            "InventoryDispositionOrder", orderId
        );
    }

    private Set<Long> allowedWarehouses(Authentication authentication) {
        return warehouseScopeService.isWarehouseStaff(authentication)
            ? warehouseScopeService.getAssignedWarehouseIds(authentication)
            : null;
    }
}
