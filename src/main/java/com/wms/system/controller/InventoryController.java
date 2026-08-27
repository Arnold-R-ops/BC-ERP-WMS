package com.wms.system.controller;

import com.wms.system.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inventory Management Controller
 *
 * Provides read-only REST APIs for inventory inquiry and health checks.
 *
 * API Conventions:
 * - Base path: /api/inventory
 * - Request body validation: @Valid annotation
 * - Error handling: GlobalExceptionHandler converts exceptions to ErrorResponse
 * - Response format: JSON (DTO, NOT Entity)
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Controller Layer)
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Query total stock for a product
     *
     * API Endpoint:
     * GET /api/inventory/total-stock/{productSkuId}
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "productSkuId": 123,
     *   "totalStock": 350
     * }
     * </pre>
     *
     * @param productSkuId ProductSku ID
     * @return ResponseEntity with total stock
     */
    @GetMapping("/total-stock/{productSkuId}")
    public ResponseEntity<?> getTotalStock(@PathVariable("productSkuId") Long productSkuId) {
        log.info("API: Query total stock - productSkuId={}", productSkuId);

        Integer totalStock = inventoryService.getTotalStock(productSkuId);
        Integer reservedStock = inventoryService.getReservedStock(productSkuId);
        Integer availableStock = inventoryService.getAvailableStock(productSkuId);

        return ResponseEntity.ok(java.util.Map.of(
            "productSkuId", productSkuId,
            "totalStock", totalStock,
            "reservedStock", reservedStock,
            "availableStock", availableStock
        ));
    }

    /**
     * Health check for inventory API
     *
     * API Endpoint:
     * GET /api/inventory/health
     *
     * @return ResponseEntity with status
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(java.util.Map.of(
            "status", "UP",
            "service", "InventoryService",
            "timestamp", java.time.LocalDateTime.now()
        ));
    }

}
