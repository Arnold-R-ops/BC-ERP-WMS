package com.wms.system.controller;

import com.wms.system.dto.inventory.InventoryReservationResponse;
import com.wms.system.service.InventoryReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inventory/reservations")
@RequiredArgsConstructor
public class InventoryReservationController {

    private final InventoryReservationService reservationService;

    @GetMapping
    public ResponseEntity<List<InventoryReservationResponse>> listBySalesOrder(
        @RequestParam("salesOrderId") Long salesOrderId
    ) {
        log.info("API: List inventory reservations by salesOrderId={}", salesOrderId);
        return ResponseEntity.ok(reservationService.listBySalesOrder(salesOrderId));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<InventoryReservationResponse>> listByBatch(
        @PathVariable("batchId") Long batchId
    ) {
        log.info("API: List inventory reservations by batchId={}", batchId);
        return ResponseEntity.ok(reservationService.listByBatch(batchId));
    }

    @PostMapping("/{reservationId}/release")
    public ResponseEntity<InventoryReservationResponse> release(
        @PathVariable("reservationId") Long reservationId
    ) {
        log.info("API: Release inventory reservation reservationId={}", reservationId);
        return ResponseEntity.ok(reservationService.releaseReservation(reservationId));
    }
}
