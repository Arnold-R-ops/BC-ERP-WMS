package com.wms.system.controller;

import com.wms.system.dto.receiving.CreatePurchaseAsnRequest;
import com.wms.system.dto.receiving.PurchaseAsnActionRequest;
import com.wms.system.dto.receiving.PurchaseAsnResponse;
import com.wms.system.dto.receiving.UpdatePurchaseAsnDraftRequest;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.PurchaseAsnService;
import com.wms.system.service.WarehouseScopeService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PurchaseAsnController {

    private final PurchaseAsnService purchaseAsnService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping("/purchase-orders/{purchaseOrderId}/asns")
    @PreAuthorize("hasAnyAuthority('purchase:asn:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<PurchaseAsnResponse>> list(@PathVariable Long purchaseOrderId) {
        return ResponseEntity.ok(purchaseAsnService.list(purchaseOrderId));
    }

    @PostMapping("/purchase-orders/{purchaseOrderId}/asns")
    @PreAuthorize("hasAnyAuthority('purchase:asn:create', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> create(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody CreatePurchaseAsnRequest request,
        Authentication authentication
    ) {
        requireWarehouse(authentication, request.warehouseId(), purchaseOrderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseAsnService.create(
            purchaseOrderId, request, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/purchase-orders/{purchaseOrderId}/asns/quick-confirm")
    @PreAuthorize("hasAnyAuthority('purchase:asn:confirm', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> quickConfirm(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody CreatePurchaseAsnRequest request,
        Authentication authentication
    ) {
        requireWarehouse(authentication, request.warehouseId(), purchaseOrderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseAsnService.quickConfirm(
            purchaseOrderId, request, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @GetMapping("/purchase-asns/{id}")
    @PreAuthorize("hasAnyAuthority('purchase:asn:view', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> get(
        @PathVariable Long id,
        Authentication authentication
    ) {
        PurchaseAsnResponse response = purchaseAsnService.get(id);
        requireWarehouse(authentication, response.warehouseId(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/purchase-asns/{id}")
    @PreAuthorize("hasAnyAuthority('purchase:asn:update', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> updateDraft(
        @PathVariable Long id,
        @Valid @RequestBody UpdatePurchaseAsnDraftRequest request,
        Authentication authentication
    ) {
        requireExistingWarehouse(authentication, id);
        requireWarehouse(authentication, request.warehouseId(), id);
        return ResponseEntity.ok(purchaseAsnService.updateDraft(id, request));
    }

    @PostMapping("/purchase-asns/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('purchase:asn:confirm', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> confirm(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireExistingWarehouse(authentication, id);
        return ResponseEntity.ok(purchaseAsnService.confirm(
            id, AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/purchase-asns/{id}/correct")
    @PreAuthorize("hasAnyAuthority('purchase:asn:create', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> correct(
        @PathVariable Long id,
        @Valid @RequestBody PurchaseAsnActionRequest request,
        Authentication authentication
    ) {
        requireExistingWarehouse(authentication, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseAsnService.correct(
            id, request.reason(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/purchase-asns/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('purchase:asn:cancel', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> cancel(
        @PathVariable Long id,
        @Valid @RequestBody PurchaseAsnActionRequest request,
        Authentication authentication
    ) {
        requireExistingWarehouse(authentication, id);
        return ResponseEntity.ok(purchaseAsnService.cancel(
            id, request.reason(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/purchase-asns/{id}/close")
    @PreAuthorize("hasAnyAuthority('purchase:asn:close', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseAsnResponse> close(
        @PathVariable Long id,
        @Valid @RequestBody PurchaseAsnActionRequest request,
        Authentication authentication
    ) {
        requireExistingWarehouse(authentication, id);
        return ResponseEntity.ok(purchaseAsnService.closeRemainder(
            id, request.reason(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    private void requireExistingWarehouse(Authentication authentication, Long asnId) {
        requireWarehouse(authentication, purchaseAsnService.get(asnId).warehouseId(), asnId);
    }

    private void requireWarehouse(Authentication authentication, Long warehouseId, Object resourceId) {
        warehouseScopeService.requireAccess(
            authentication, List.of(warehouseId), "PurchaseAsn", resourceId);
    }
}
