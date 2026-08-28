package com.wms.system.controller;

import com.wms.system.dto.v45.EmergencyStockCorrectionActionRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionApplyRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionImpactResponse;
import com.wms.system.dto.v45.EmergencyStockCorrectionPolicyResponse;
import com.wms.system.dto.v45.EmergencyStockCorrectionPolicyUpdateRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionResponse;
import com.wms.system.dto.InventoryBatchResponse;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.EmergencyStockCorrectionService;
import com.wms.system.service.InventoryBatchService;
import com.wms.system.service.InventoryCorrectionPolicyService;
import com.wms.system.service.WarehouseScopeService;
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
    private final InventoryBatchService inventoryBatchService;
    private final InventoryCorrectionPolicyService policyService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping("/policy")
    @PreAuthorize("hasAnyAuthority('inventory:correction:view', 'inventory:correction:execute', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionPolicyResponse> getPolicy() {
        return ResponseEntity.ok(policyService.getPolicy());
    }

    @PutMapping("/policy")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionPolicyResponse> updatePolicy(
        @Valid @RequestBody EmergencyStockCorrectionPolicyUpdateRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(policyService.updatePolicy(
            request.getMode(),
            request.getReason(),
            AuthUserResolver.resolveUserId(authentication),
            authentication.getName()
        ));
    }

    @GetMapping("/batch-options")
    @PreAuthorize("hasAnyAuthority('inventory:correction:execute', 'TENANT_ADMIN')")
    public ResponseEntity<List<InventoryBatchResponse>> listBatchOptions(
        @RequestParam("productSkuId") Long productSkuId,
        Authentication authentication
    ) {
        List<InventoryBatch> accessibleBatches = warehouseScopeService.filterAccessible(
            authentication,
            inventoryBatchService.getCorrectionBatchOptions(productSkuId),
            batch -> batch.getLocation() == null || batch.getLocation().getWarehouse() == null
                ? List.of()
                : List.of(batch.getLocation().getWarehouse().getId())
        );
        return ResponseEntity.ok(accessibleBatches.stream()
            .map(this::toBatchResponse)
            .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('inventory:correction:execute', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> create(
        @Valid @RequestBody EmergencyStockCorrectionRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        Long warehouseId = correctionService.resolveWarehouseId(request);
        warehouseScopeService.requireAccess(
            authentication, List.of(warehouseId), "EmergencyStockCorrection", "new");
        return ResponseEntity.status(HttpStatus.CREATED).body(
            correctionService.create(request, userId, warehouseId)
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('inventory:correction:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<EmergencyStockCorrectionResponse>> list(
        @RequestParam(value = "status", required = false) EmergencyCorrectionStatus status,
        Authentication authentication
    ) {
        return ResponseEntity.ok(warehouseScopeService.filterAccessible(
            authentication,
            correctionService.list(status),
            correction -> List.of(correction.getWarehouseId())
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('inventory:correction:view', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> get(
        @PathVariable("id") Long id,
        Authentication authentication
    ) {
        requireWarehouseAccess(authentication, id);
        return ResponseEntity.ok(correctionService.get(id));
    }

    @GetMapping("/{id}/impact")
    @PreAuthorize("hasAnyAuthority('inventory:correction:view', 'inventory:correction:execute', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionImpactResponse> impact(
        @PathVariable("id") Long id,
        Authentication authentication
    ) {
        requireWarehouseAccess(authentication, id);
        return ResponseEntity.ok(correctionService.impact(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('inventory:correction:execute', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> submit(
        @PathVariable("id") Long id,
        @Valid @RequestBody EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        requireWarehouseAccess(authentication, id);
        return ResponseEntity.ok(correctionService.submit(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyAuthority('inventory:correction:review', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> review(
        @PathVariable("id") Long id,
        @Valid @RequestBody EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        requireWarehouseAccess(authentication, id);
        return ResponseEntity.ok(correctionService.review(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('inventory:correction:approve', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> approve(
        @PathVariable("id") Long id,
        @Valid @RequestBody EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        requireWarehouseAccess(authentication, id);
        return ResponseEntity.ok(correctionService.approve(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('inventory:correction:reject', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> reject(
        @PathVariable("id") Long id,
        @Valid @RequestBody EmergencyStockCorrectionActionRequest request,
        Authentication authentication
    ) {
        requireWarehouseAccess(authentication, id);
        return ResponseEntity.ok(correctionService.reject(id, AuthUserResolver.resolveUserId(authentication), comment(request)));
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAnyAuthority('inventory:correction:execute', 'TENANT_ADMIN')")
    public ResponseEntity<EmergencyStockCorrectionResponse> apply(
        @PathVariable("id") Long id,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody EmergencyStockCorrectionApplyRequest request,
        Authentication authentication
    ) {
        requireWarehouseAccess(authentication, id);
        return ResponseEntity.ok(correctionService.apply(
            id,
            AuthUserResolver.resolveUserId(authentication),
            authentication.getName(),
            idempotencyKey,
            request.getPreviewToken()
        ));
    }

    private String comment(EmergencyStockCorrectionActionRequest request) {
        return request == null ? null : request.getComment();
    }

    private InventoryBatchResponse toBatchResponse(InventoryBatch batch) {
        return InventoryBatchResponse.builder()
            .id(batch.getId())
            .batchCode(batch.getBatchCode())
            .productSkuId(batch.getProductSku().getId())
            .productName(batch.getProductSku().getName())
            .productBarcode(batch.getProductSku().getBarcode())
            .quantity(batch.getQuantity())
            .reservedQuantity(batch.getReservedQuantity())
            .availableQuantity(batch.getAvailableQuantity())
            .qualityStatus(batch.getQualityStatus())
            .initialQuantity(batch.getInitialQuantity())
            .expiryDate(batch.getExpiryDate())
            .productionDate(batch.getProductionDate())
            .externalBatchCode(batch.getExternalBatchCode())
            .locationId(batch.getLocation().getId())
            .locationCode(batch.getLocation().getLocationCode())
            .entryDate(batch.getEntryDate())
            .active(batch.getActive())
            .remark(batch.getRemark())
            .createdAt(batch.getCreatedAt())
            .updatedAt(batch.getUpdatedAt())
            .build();
    }

    private void requireWarehouseAccess(Authentication authentication, Long correctionId) {
        warehouseScopeService.requireAccess(
            authentication,
            List.of(correctionService.getWarehouseId(correctionId)),
            "EmergencyStockCorrection",
            correctionId
        );
    }
}
