package com.wms.system.controller;

import com.wms.system.dto.receiving.InboundReferenceSkuResponse;
import com.wms.system.dto.receiving.InboundReferenceWarehouseResponse;
import com.wms.system.service.InboundReferenceService;
import com.wms.system.service.WarehouseScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InboundReferenceController {

    private final InboundReferenceService referenceService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping("/purchase-orders/reference/skus")
    @PreAuthorize("hasAnyAuthority('purchase:view', 'purchase:create', 'TENANT_ADMIN')")
    public ResponseEntity<List<InboundReferenceSkuResponse>> purchaseSkus() {
        return ResponseEntity.ok(referenceService.listOperationalSkus());
    }

    @GetMapping("/purchase-orders/reference/warehouses")
    @PreAuthorize("hasAnyAuthority('purchase:view', 'purchase:create', 'TENANT_ADMIN')")
    public ResponseEntity<List<InboundReferenceWarehouseResponse>> purchaseWarehouses(
        Authentication authentication
    ) {
        return ResponseEntity.ok(accessibleWarehouses(authentication));
    }

    @GetMapping("/other-inbound-applications/reference/skus")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<List<InboundReferenceSkuResponse>> otherInboundSkus() {
        return ResponseEntity.ok(referenceService.listOperationalSkus());
    }

    @GetMapping("/other-inbound-applications/reference/warehouses")
    @PreAuthorize("hasAnyAuthority('other_inbound:manage', 'TENANT_ADMIN')")
    public ResponseEntity<List<InboundReferenceWarehouseResponse>> otherInboundWarehouses(
        Authentication authentication
    ) {
        return ResponseEntity.ok(accessibleWarehouses(authentication));
    }

    private List<InboundReferenceWarehouseResponse> accessibleWarehouses(
        Authentication authentication
    ) {
        return warehouseScopeService.filterAccessible(
            authentication,
            referenceService.listActiveWarehouses(),
            warehouse -> List.of(warehouse.id())
        );
    }
}
