package com.wms.system.controller;

import com.wms.system.dto.inventory.InventoryReservationResponse;
import com.wms.system.service.InventoryReservationService;
import com.wms.system.service.WarehouseScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inventory/reservations")
@RequiredArgsConstructor
public class InventoryReservationController {

    private final InventoryReservationService reservationService;
    private final WarehouseScopeService warehouseScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('inventory:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<InventoryReservationResponse>> listBySalesOrder(
        @RequestParam("salesOrderId") Long salesOrderId,
        Authentication authentication
    ) {
        log.info("API: List inventory reservations by salesOrderId={}", salesOrderId);
        return ResponseEntity.ok(filterByWarehouseScope(
            authentication,
            reservationService.listBySalesOrder(salesOrderId)
        ));
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasAnyAuthority('inventory:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<InventoryReservationResponse>> listByBatch(
        @PathVariable("batchId") Long batchId,
        Authentication authentication
    ) {
        log.info("API: List inventory reservations by batchId={}", batchId);
        return ResponseEntity.ok(filterByWarehouseScope(
            authentication,
            reservationService.listByBatch(batchId)
        ));
    }

    private List<InventoryReservationResponse> filterByWarehouseScope(
        Authentication authentication,
        List<InventoryReservationResponse> responses
    ) {
        return warehouseScopeService.filterAccessible(
            authentication,
            responses,
            response -> java.util.Collections.singletonList(response.getWarehouseId())
        );
    }
}
