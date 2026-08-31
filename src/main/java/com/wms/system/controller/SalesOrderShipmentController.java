package com.wms.system.controller;

import com.wms.system.dto.sales.SalesOrderShipmentRequest;
import com.wms.system.dto.sales.SalesOrderShipmentResponse;
import com.wms.system.dto.sales.UpdateSalesLogisticsStatusRequest;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.SalesOrderShipmentService;
import com.wms.system.service.SalesCommandIdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-orders/{salesOrderId}/shipments")
@RequiredArgsConstructor
public class SalesOrderShipmentController {

    private final SalesOrderShipmentService shipmentService;
    private final SalesCommandIdempotencyService commandService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('sales:view', 'TENANT_ADMIN')")
    public ResponseEntity<List<SalesOrderShipmentResponse>> list(@PathVariable Long salesOrderId) {
        return ResponseEntity.ok(shipmentService.list(salesOrderId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('sales:shipment:edit', 'TENANT_ADMIN')")
    public ResponseEntity<SalesOrderShipmentResponse> create(
        @PathVariable Long salesOrderId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody SalesOrderShipmentRequest request,
        Authentication authentication
    ) {
        Long actorId = AuthUserResolver.resolveUserId(authentication);
        String actorName = AuthUserResolver.resolveUsername(authentication);
        SalesOrderShipmentResponse response = commandService.execute(idempotencyKey,
            "SALES_SHIPMENT_CREATE", Map.of("salesOrderId", salesOrderId, "request", request),
            actorId, actorName, SalesOrderShipmentResponse.class,
            () -> shipmentService.create(salesOrderId, request, actorId, actorName));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{shipmentId}")
    @PreAuthorize("hasAnyAuthority('sales:shipment:edit', 'TENANT_ADMIN')")
    public ResponseEntity<SalesOrderShipmentResponse> update(
        @PathVariable Long salesOrderId,
        @PathVariable Long shipmentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody SalesOrderShipmentRequest request,
        Authentication authentication
    ) {
        Long actorId = AuthUserResolver.resolveUserId(authentication);
        String actorName = AuthUserResolver.resolveUsername(authentication);
        return ResponseEntity.ok(commandService.execute(idempotencyKey, "SALES_SHIPMENT_UPDATE",
            Map.of("salesOrderId", salesOrderId, "shipmentId", shipmentId, "request", request),
            actorId, actorName, SalesOrderShipmentResponse.class,
            () -> shipmentService.update(salesOrderId, shipmentId, request, actorId, actorName)));
    }

    @PostMapping("/{shipmentId}/void")
    @PreAuthorize("hasAnyAuthority('sales:shipment:edit', 'TENANT_ADMIN')")
    public ResponseEntity<SalesOrderShipmentResponse> voidShipment(
        @PathVariable Long salesOrderId,
        @PathVariable Long shipmentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        Authentication authentication
    ) {
        Long actorId = AuthUserResolver.resolveUserId(authentication);
        String actorName = AuthUserResolver.resolveUsername(authentication);
        return ResponseEntity.ok(commandService.execute(idempotencyKey, "SALES_SHIPMENT_VOID",
            Map.of("salesOrderId", salesOrderId, "shipmentId", shipmentId),
            actorId, actorName, SalesOrderShipmentResponse.class,
            () -> shipmentService.voidShipment(salesOrderId, shipmentId, actorId, actorName)));
    }

    @PostMapping("/{shipmentId}/logistics-status")
    @PreAuthorize("hasAnyAuthority('sales:logistics:update', 'TENANT_ADMIN')")
    public ResponseEntity<SalesOrderShipmentResponse> updateLogisticsStatus(
        @PathVariable Long salesOrderId,
        @PathVariable Long shipmentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody UpdateSalesLogisticsStatusRequest request,
        Authentication authentication
    ) {
        Long actorId = AuthUserResolver.resolveUserId(authentication);
        String actorName = AuthUserResolver.resolveUsername(authentication);
        return ResponseEntity.ok(commandService.execute(idempotencyKey, "SALES_LOGISTICS_STATUS_UPDATE",
            Map.of("salesOrderId", salesOrderId, "shipmentId", shipmentId, "request", request),
            actorId, actorName, SalesOrderShipmentResponse.class,
            () -> shipmentService.updateLogisticsStatus(
                salesOrderId, shipmentId, request, actorId, actorName)));
    }
}
