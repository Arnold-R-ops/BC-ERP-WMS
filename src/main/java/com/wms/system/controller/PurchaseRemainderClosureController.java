package com.wms.system.controller;

import com.wms.system.dto.purchase.PurchaseRemainderClosureActionRequest;
import com.wms.system.dto.purchase.PurchaseRemainderClosureCreateRequest;
import com.wms.system.dto.purchase.PurchaseRemainderClosureResponse;
import com.wms.system.entity.enums.PurchaseRemainderClosureStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.PurchaseAsnService;
import com.wms.system.service.PurchaseRemainderClosureService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PurchaseRemainderClosureController {

    private final PurchaseRemainderClosureService closureService;
    private final PurchaseAsnService purchaseAsnService;
    private final WarehouseScopeService warehouseScopeService;

    @PostMapping("/purchase-asns/{purchaseAsnId}/remainder-closure-requests")
    @PreAuthorize("hasAnyAuthority('purchase:request_close_remaining', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseRemainderClosureResponse> request(
        @PathVariable Long purchaseAsnId,
        @Valid @RequestBody PurchaseRemainderClosureCreateRequest request,
        Authentication authentication
    ) {
        requireAsnAccess(authentication, purchaseAsnId);
        return ResponseEntity.status(HttpStatus.CREATED).body(closureService.request(
            purchaseAsnId,
            request.reason(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }

    @GetMapping("/purchase-remainder-closure-requests")
    @PreAuthorize("hasAnyAuthority('purchase:request_close_remaining', 'purchase:approve_close_remaining', 'TENANT_ADMIN')")
    public ResponseEntity<List<PurchaseRemainderClosureResponse>> list(
        @RequestParam(required = false) PurchaseRemainderClosureStatus status,
        Authentication authentication
    ) {
        return ResponseEntity.ok(warehouseScopeService.filterAccessible(
            authentication,
            closureService.list(status),
            response -> List.of(response.warehouseId())
        ));
    }

    @GetMapping("/purchase-remainder-closure-requests/{id}")
    @PreAuthorize("hasAnyAuthority('purchase:request_close_remaining', 'purchase:approve_close_remaining', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseRemainderClosureResponse> get(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireRequestAccess(authentication, id);
        return ResponseEntity.ok(closureService.get(id));
    }

    @PostMapping("/purchase-remainder-closure-requests/{id}/approve")
    @PreAuthorize("hasAnyAuthority('purchase:approve_close_remaining', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseRemainderClosureResponse> approve(
        @PathVariable Long id,
        @Valid @RequestBody PurchaseRemainderClosureActionRequest request,
        Authentication authentication
    ) {
        requireRequestAccess(authentication, id);
        return ResponseEntity.ok(closureService.approve(
            id, request.comment(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/purchase-remainder-closure-requests/{id}/reject")
    @PreAuthorize("hasAnyAuthority('purchase:approve_close_remaining', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseRemainderClosureResponse> reject(
        @PathVariable Long id,
        @Valid @RequestBody PurchaseRemainderClosureActionRequest request,
        Authentication authentication
    ) {
        requireRequestAccess(authentication, id);
        return ResponseEntity.ok(closureService.reject(
            id, request.comment(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    @PostMapping("/purchase-remainder-closure-requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('purchase:request_close_remaining', 'TENANT_ADMIN')")
    public ResponseEntity<PurchaseRemainderClosureResponse> cancel(
        @PathVariable Long id,
        @Valid @RequestBody PurchaseRemainderClosureActionRequest request,
        Authentication authentication
    ) {
        requireRequestAccess(authentication, id);
        return ResponseEntity.ok(closureService.cancel(
            id, request.comment(), AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)));
    }

    private void requireAsnAccess(Authentication authentication, Long purchaseAsnId) {
        warehouseScopeService.requireAccess(
            authentication,
            List.of(purchaseAsnService.get(purchaseAsnId).warehouseId()),
            "PurchaseAsn",
            purchaseAsnId
        );
    }

    private void requireRequestAccess(Authentication authentication, Long requestId) {
        warehouseScopeService.requireAccess(
            authentication,
            List.of(closureService.getWarehouseId(requestId)),
            "PurchaseRemainderClosureRequest",
            requestId
        );
    }
}
