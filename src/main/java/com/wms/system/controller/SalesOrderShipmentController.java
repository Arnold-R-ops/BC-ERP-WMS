package com.wms.system.controller;

import com.wms.system.dto.sales.SalesOrderShipmentRequest;
import com.wms.system.dto.sales.SalesOrderShipmentResponse;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.SalesOrderShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales-orders/{salesOrderId}/shipments")
@RequiredArgsConstructor
public class SalesOrderShipmentController {

    private final SalesOrderShipmentService shipmentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('sales:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<SalesOrderShipmentResponse>> list(@PathVariable Long salesOrderId) {
        return ResponseEntity.ok(shipmentService.list(salesOrderId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('sales:edit', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderShipmentResponse> create(
        @PathVariable Long salesOrderId,
        @Valid @RequestBody SalesOrderShipmentRequest request,
        Authentication authentication
    ) {
        SalesOrderShipmentResponse response = shipmentService.create(
            salesOrderId,
            request,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{shipmentId}")
    @PreAuthorize("hasAnyAuthority('sales:edit', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderShipmentResponse> update(
        @PathVariable Long salesOrderId,
        @PathVariable Long shipmentId,
        @Valid @RequestBody SalesOrderShipmentRequest request
    ) {
        return ResponseEntity.ok(shipmentService.update(salesOrderId, shipmentId, request));
    }

    @PostMapping("/{shipmentId}/void")
    @PreAuthorize("hasAnyAuthority('sales:edit', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderShipmentResponse> voidShipment(
        @PathVariable Long salesOrderId,
        @PathVariable Long shipmentId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(shipmentService.voidShipment(
            salesOrderId,
            shipmentId,
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication)
        ));
    }
}
