package com.wms.system.controller;

import com.wms.system.dto.receiving.PurchaseOrderAllocationResponse;
import com.wms.system.dto.receiving.SavePurchaseOrderAllocationsRequest;
import com.wms.system.service.PurchaseOrderAllocationService;
import com.wms.system.service.WarehouseScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders/{purchaseOrderId}/allocations")
@RequiredArgsConstructor
public class PurchaseAllocationController {

    private final PurchaseOrderAllocationService allocationService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('purchase:allocation:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<PurchaseOrderAllocationResponse>> list(
        @PathVariable Long purchaseOrderId
    ) {
        return ResponseEntity.ok(allocationService.list(purchaseOrderId));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('purchase:allocation:edit', 'TENANT_ADMIN')")
    public ResponseEntity<List<PurchaseOrderAllocationResponse>> replace(
        @PathVariable Long purchaseOrderId,
        @Valid @RequestBody SavePurchaseOrderAllocationsRequest request,
        Authentication authentication
    ) {
        warehouseScopeService.requireAccess(
            authentication,
            request.allocations().stream().map(SavePurchaseOrderAllocationsRequest.Row::warehouseId).toList(),
            "PurchaseOrderAllocation",
            purchaseOrderId
        );
        return ResponseEntity.ok(allocationService.replace(purchaseOrderId, request));
    }
}
