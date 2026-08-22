package com.wms.system.controller;

import com.wms.system.dto.v45.BackorderLineResponse;
import com.wms.system.service.BackorderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backorders")
@RequiredArgsConstructor
public class BackorderController {

    private final BackorderService backorderService;

    @GetMapping("/sales-order/{salesOrderId}")
    @PreAuthorize("hasAnyAuthority('sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<BackorderLineResponse>> listByOrder(@PathVariable("salesOrderId") Long salesOrderId) {
        return ResponseEntity.ok(backorderService.listByOrder(salesOrderId));
    }

    @GetMapping("/product/{productSkuId}")
    @PreAuthorize("hasAnyAuthority('inventory:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<BackorderLineResponse>> listByProductSku(@PathVariable("productSkuId") Long productSkuId) {
        return ResponseEntity.ok(backorderService.listByProductSku(productSkuId));
    }

    @PostMapping("/wake/product/{productSkuId}")
    @PreAuthorize("hasAnyAuthority('inventory:adjust', 'TENANT_ADMIN')")
    public ResponseEntity<?> wakeProduct(@PathVariable("productSkuId") Long productSkuId) {
        int createdTasks = backorderService.wakeProduct(productSkuId);
        return ResponseEntity.ok(Map.of("productSkuId", productSkuId, "createdTaskGroups", createdTasks));
    }
}
