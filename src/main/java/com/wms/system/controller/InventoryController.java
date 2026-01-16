package com.wms.system.controller;

import com.wms.system.dto.StockAdjustmentRequest;
import com.wms.system.dto.StockTransactionResponse;
import com.wms.system.entity.StockTransaction;
import com.wms.system.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inventory Management Controller
 *
 * Provides RESTful APIs for inventory operations:
 * - Stock adjustment (inbound/outbound/adjustment)
 * - Stock inquiry
 * - Low stock alerts
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
     * Adjust stock (inbound/outbound/adjustment)
     *
     * API Endpoint:
     * POST /api/inventory/adjust
     *
     * Request Body Example:
     * <pre>
     * {
     *   "productId": 123,
     *   "locationId": 456,
     *   "transactionType": "OUT",
     *   "sourceType": "SALE_OUT",
     *   "quantity": 50,
     *   "sourceOrderId": "SO202501110001",
     *   "operatorId": 1,
     *   "operatorName": "John Doe",
     *   "remark": "Sale to customer ABC"
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "id": 1001,
     *   "productId": 123,
     *   "productName": "Coca-Cola 500ml",
     *   "locationId": 456,
     *   "locationCode": "WH01-ZONE_A-A-01-001",
     *   "transactionType": "OUT",
     *   "sourceType": "SALE_OUT",
     *   "quantity": 50,
     *   "quantityBefore": 200,
     *   "quantityAfter": 150,
     *   "sourceOrderId": "SO202501110001",
     *   "operatorId": 1,
     *   "operatorName": "John Doe",
     *   "createdAt": "2025-01-11T10:30:00+00:00"
     * }
     * </pre>
     *
     * Error Response (400 Bad Request - Insufficient Stock):
     * <pre>
     * {
     *   "errorKey": "STOCK_INSUFFICIENT",
     *   "params": {
     *     "productId": 123,
     *     "currentStock": 50,
     *     "requestedQuantity": 100,
     *     "shortage": 50
     *   },
     *   "timestamp": "2025-01-11T10:30:00+00:00",
     *   "path": "/api/inventory/adjust",
     *   "status": 400
     * }
     * </pre>
     *
     * Error Response (404 Not Found - Product Not Found):
     * <pre>
     * {
     *   "errorKey": "PRODUCT_NOT_FOUND",
     *   "params": {
     *     "productId": 999
     *   },
     *   "timestamp": "2025-01-11T10:30:00+00:00",
     *   "path": "/api/inventory/adjust",
     *   "status": 404
     * }
     * </pre>
     *
     * @param request Stock adjustment request (validated)
     * @return ResponseEntity<StockTransactionResponse> Transaction result
     * @throws com.wms.system.exception.BusinessException if validation or business rules fail
     */
    @PostMapping("/adjust")
    public ResponseEntity<StockTransactionResponse> adjustStock(
        @Valid @RequestBody StockAdjustmentRequest request
    ) {
        log.info("API: Adjust stock - productId={}, locationId={}, type={}, quantity={}",
            request.getProductId(), request.getLocationId(),
            request.getTransactionType(), request.getQuantity());

        // Call service layer
        StockTransaction transaction = inventoryService.adjustStock(request);

        // Convert Entity to DTO (NEVER expose Entity directly)
        StockTransactionResponse response = mapToResponse(transaction);

        log.info("API: Stock adjustment completed - transactionId={}, quantityBefore={}, quantityAfter={}",
            response.getId(), response.getQuantityBefore(), response.getQuantityAfter());

        return ResponseEntity.ok(response);
    }

    /**
     * Query total stock for a product
     *
     * API Endpoint:
     * GET /api/inventory/total-stock/{productId}
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "productId": 123,
     *   "totalStock": 350
     * }
     * </pre>
     *
     * @param productId Product ID
     * @return ResponseEntity with total stock
     */
    @GetMapping("/total-stock/{productId}")
    public ResponseEntity<?> getTotalStock(@PathVariable Long productId) {
        log.info("API: Query total stock - productId={}", productId);

        Integer totalStock = inventoryService.getTotalStock(productId);

        return ResponseEntity.ok(java.util.Map.of(
            "productId", productId,
            "totalStock", totalStock
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

    /**
     * Map StockTransaction Entity to StockTransactionResponse DTO
     *
     * IMPORTANT:
     * - NEVER expose Entity classes directly in Controller responses
     * - Use DTO to decouple API contract from database schema
     * - Allows flexible API evolution without breaking database
     *
     * @param transaction StockTransaction entity
     * @return StockTransactionResponse DTO
     */
    private StockTransactionResponse mapToResponse(StockTransaction transaction) {
        return StockTransactionResponse.builder()
            .id(transaction.getId())
            .productId(transaction.getProduct().getId())
            .productName(transaction.getProduct().getName())
            .productBarcode(transaction.getProduct().getBarcode())
            .locationId(transaction.getLocation().getId())
            .locationCode(transaction.getLocation().getLocationCode())
            .transactionType(transaction.getTransactionType())
            .sourceType(transaction.getSourceType())
            .quantity(transaction.getQuantity())
            .quantityBefore(transaction.getQuantityBefore())
            .quantityAfter(transaction.getQuantityAfter())
            .sourceOrderId(transaction.getSourceOrderId())
            .operatorId(transaction.getOperatorId())
            .operatorName(transaction.getOperatorName())
            .remark(transaction.getRemark())
            .createdAt(transaction.getCreatedAt())
            .updatedAt(transaction.getUpdatedAt())
            .build();
    }
}
