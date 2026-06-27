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
    @PreAuthorize("hasAnyAuthority('sales:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<BackorderLineResponse>> listByOrder(@PathVariable("salesOrderId") Long salesOrderId) {
        return ResponseEntity.ok(backorderService.listByOrder(salesOrderId));
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyAuthority('inventory:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<BackorderLineResponse>> listByProduct(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(backorderService.listByProduct(productId));
    }

    @PostMapping("/wake/product/{productId}")
    @PreAuthorize("hasAnyAuthority('inventory:adjust', 'SUPER_ADMIN')")
    public ResponseEntity<?> wakeProduct(@PathVariable("productId") Long productId) {
        int createdTasks = backorderService.wakeProduct(productId);
        return ResponseEntity.ok(Map.of("productId", productId, "createdTaskGroups", createdTasks));
    }
}
