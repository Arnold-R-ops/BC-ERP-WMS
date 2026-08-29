package com.wms.system.controller;

import com.wms.system.dto.receivinginspection.*;
import com.wms.system.entity.enums.ReceivingInspectionTaskStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.WarehouseScopeService;
import com.wms.system.service.inspection.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/receiving-inspections")
@RequiredArgsConstructor
public class ReceivingInspectionController {
    private final ReceivingInspectionService inspectionService;
    private final ReceivingInspectionPolicyService policyService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('receiving_inspection:view', 'receiving_inspection:execute', 'receiving_inspection:review', 'receiving_inspection:correct', 'receiving_inspection:policy', 'TENANT_ADMIN')")
    public ResponseEntity<List<ReceivingInspectionTaskResponse>> list(
        @RequestParam(required = false) ReceivingInspectionTaskStatus status,
        Authentication authentication
    ) {
        return ResponseEntity.ok(inspectionService.list(status, allowedWarehouses(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('receiving_inspection:view', 'receiving_inspection:execute', 'receiving_inspection:review', 'receiving_inspection:correct', 'receiving_inspection:policy', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingInspectionTaskResponse> get(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireTaskAccess(authentication, id);
        return ResponseEntity.ok(inspectionService.get(id));
    }

    @PostMapping("/{id}/results")
    @PreAuthorize("hasAnyAuthority('receiving_inspection:execute', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingInspectionResultResponse> submit(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody SubmitReceivingInspectionRequest request,
        Authentication authentication
    ) {
        requireTaskAccess(authentication, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.submit(
            id, request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @PostMapping("/results/{id}/approve")
    @PreAuthorize("hasAnyAuthority('receiving_inspection:review', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingInspectionResultResponse> approve(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ReceivingInspectionReviewRequest request,
        Authentication authentication
    ) {
        requireResultAccess(authentication, id);
        return ResponseEntity.ok(inspectionService.approve(
            id, request.comment(), idempotencyKey,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @PostMapping("/results/{id}/reject")
    @PreAuthorize("hasAnyAuthority('receiving_inspection:review', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingInspectionResultResponse> reject(
        @PathVariable Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody ReceivingInspectionReviewRequest request,
        Authentication authentication
    ) {
        requireResultAccess(authentication, id);
        return ResponseEntity.ok(inspectionService.reject(
            id, request.comment(), idempotencyKey,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @PostMapping("/reinspections")
    @PreAuthorize("hasAnyAuthority('receiving_inspection:correct', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingInspectionTaskResponse> reinspection(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateReceivingReinspectionRequest request,
        Authentication authentication
    ) {
        warehouseScopeService.requireAccess(
            authentication,
            List.of(inspectionService.warehouseIdForBatch(request.inventoryBatchId())),
            "ReceivingReinspection",
            request.inventoryBatchId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
            inspectionService.createReinspection(
                request, idempotencyKey, AuthUserResolver.resolveUserId(authentication),
                AuthUserResolver.resolveUsername(authentication),
                allowedWarehouses(authentication)
            )
        );
    }

    @GetMapping("/policy")
    @PreAuthorize("hasAnyAuthority('receiving_inspection:view', 'receiving_inspection:execute', 'receiving_inspection:review', 'receiving_inspection:correct', 'receiving_inspection:policy', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingInspectionPolicyResponse> policy() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    @PutMapping("/policy")
    @PreAuthorize("hasAnyAuthority('receiving_inspection:policy', 'TENANT_ADMIN')")
    public ResponseEntity<ReceivingInspectionPolicyResponse> updatePolicy(
        @Valid @RequestBody UpdateReceivingInspectionPolicyRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(policyService.update(
            request, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    private void requireTaskAccess(Authentication authentication, Long id) {
        warehouseScopeService.requireAccess(
            authentication, List.of(inspectionService.warehouseIdForTask(id)),
            "ReceivingInspectionTask", id);
    }

    private void requireResultAccess(Authentication authentication, Long id) {
        warehouseScopeService.requireAccess(
            authentication, List.of(inspectionService.warehouseIdForResult(id)),
            "ReceivingInspectionResult", id);
    }

    private Set<Long> allowedWarehouses(Authentication authentication) {
        return warehouseScopeService.isWarehouseStaff(authentication)
            ? warehouseScopeService.getAssignedWarehouseIds(authentication)
            : null;
    }
}
