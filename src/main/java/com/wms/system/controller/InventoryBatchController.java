package com.wms.system.controller;

import com.wms.system.dto.InventoryBatchResponse;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.service.InventoryBatchService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inventory Batch Management Controller
 *
 * Provides RESTful APIs for batch-based inventory operations:
 * - FIFO outbound (先进先出出库)
 * - Batch stock queries (V3.0: Real-time aggregation)
 * - Expiry warnings (过期预警)
 * - Batch lifecycle management
 *
 * V3.0 Architecture:
 * - InventoryBatch is the ONLY inventory data source
 * - No Inventory aggregation table
 * - All stock queries aggregate from InventoryBatch WHERE active = true
 *
 * API Conventions:
 * - Base path: /api/inventory/batches
 * - Request body validation: @Valid annotation
 * - Error handling: GlobalExceptionHandler converts exceptions to ErrorResponse
 * - Response format: JSON (DTO, NOT Entity)
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 3.0 (Single Source of Truth Architecture + Batch Management)
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory/batches")
@RequiredArgsConstructor
@Validated
public class InventoryBatchController {

    private final InventoryBatchService inventoryBatchService;

    /**
     * ⭐ FIFO Outbound (先进先出出库)
     *
     * API Endpoint:
     * POST /api/inventory/batches/outbound
     *
     * Request Body Example:
     * <pre>
     * {
     *   "productSkuId": 1,
     *   "quantity": 150,
     *   "sourceType": "SALE_OUT",
     *   "sourceOrderId": "SO-20250113-001",
     *   "operatorId": 1,
     *   "operatorName": "warehouse_staff"
     * }
     * </pre>
     *
     * Business Flow:
     * 1. Query active batches for product (ORDER BY expiryDate ASC)
     * 2. Deduct from earliest expiring batches first
     * 3. Generate stock transactions for each batch deduction
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "success": true,
     *   "deductedBatches": [
     *     {
     *       "batchCode": "R7M4K9",
     *       "deductedQuantity": 100,
     *       "remainingQuantity": 0
     *     },
     *     {
     *       "batchCode": "X8N2P5",
     *       "deductedQuantity": 50,
     *       "remainingQuantity": 50
     *     }
     *   ],
     *   "transactionIds": [1001, 1002]
     * }
     * </pre>
     *
     * @param productSkuId ProductSku ID (required)
     * @param quantity Outbound quantity (required, >= 1)
     * @param sourceType Source type (required, e.g., SALE_OUT, PRODUCTION_OUT)
     * @param sourceOrderId Source order ID (required)
     * @param operatorId Operator user ID (required)
     * @param operatorName Operator user name (required)
     * @return ResponseEntity with outbound result
     */
    @PostMapping("/outbound")
    public ResponseEntity<Map<String, Object>> fifoOutbound(
        @RequestParam("productSkuId") @NotNull(message = "ProductSku ID is required") Long productSkuId,
        @RequestParam("quantity") @NotNull(message = "Quantity is required") @Min(value = 1, message = "Quantity must be >= 1") Integer quantity,
        @RequestParam("sourceType") @NotNull(message = "Source type is required") SourceType sourceType,
        @RequestParam("sourceOrderId") @NotBlank(message = "Source order ID is required") String sourceOrderId,
        @RequestParam("operatorId") @NotNull(message = "Operator ID is required") Long operatorId,
        @RequestParam("operatorName") @NotBlank(message = "Operator name is required") String operatorName
    ) {
        log.info("API: FIFO outbound - productSkuId={}, quantity={}, sourceType={}, sourceOrder={}",
            productSkuId, quantity, sourceType, sourceOrderId);

        // Call service (FIFO logic)
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            productSkuId, quantity, sourceType, sourceOrderId, operatorId, operatorName
        );

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("transactionCount", transactions.size());
        response.put("transactionIds", transactions.stream()
            .map(StockTransaction::getId)
            .collect(Collectors.toList()));

        log.info("API: FIFO outbound completed - productSkuId={}, quantity={}, transactionCount={}",
            productSkuId, quantity, transactions.size());

        return ResponseEntity.ok(response);
    }

    /**
     * ⭐ Get Total Stock for ProductSku (V3.0: Real-time Aggregation)
     *
     * API Endpoint:
     * GET /api/inventory/batches/total-stock/{productSkuId}
     *
     * V3.0 Architecture:
     * - Query: SELECT SUM(quantity) FROM inventory_batch WHERE product_sku_id = ? AND active = true
     * - No Inventory table lookup
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "productSkuId": 1,
     *   "totalStock": 250
     * }
     * </pre>
     *
     * @param productSkuId ProductSku ID
     * @return ResponseEntity with total stock
     */
    @GetMapping("/total-stock/{productSkuId}")
    public ResponseEntity<Map<String, Object>> getTotalStock(@PathVariable("productSkuId") Long productSkuId) {
        log.info("API: Get total stock - productSkuId={}", productSkuId);

        Integer totalStock = inventoryBatchService.getTotalStock(productSkuId);

        Map<String, Object> response = new HashMap<>();
        response.put("productSkuId", productSkuId);
        response.put("totalStock", totalStock);

        return ResponseEntity.ok(response);
    }

    /**
     * Query Active Batches by ProductSku
     *
     * API Endpoint:
     * GET /api/inventory/batches/product/{productSkuId}
     *
     * Returns all active batches for a product, sorted by expiry date (FIFO order).
     *
     * Success Response (200 OK):
     * Returns List<InventoryBatchResponse>
     *
     * @param productSkuId ProductSku ID
     * @return ResponseEntity<List<InventoryBatchResponse>> Active batches
     */
    @GetMapping("/product/{productSkuId}")
    public ResponseEntity<List<InventoryBatchResponse>> getActiveBatchesByProductSku(
        @PathVariable("productSkuId") Long productSkuId
    ) {
        log.info("API: Get active batches by product - productSkuId={}", productSkuId);

        List<InventoryBatch> batches = inventoryBatchService.getActiveBatchesByProductSku(productSkuId);

        List<InventoryBatchResponse> responses = batches.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Query Active Batches by Location
     *
     * API Endpoint:
     * GET /api/inventory/batches/location/{locationId}
     *
     * Returns all active batches at a specific location.
     *
     * Success Response (200 OK):
     * Returns List<InventoryBatchResponse>
     *
     * @param locationId Location ID
     * @return ResponseEntity<List<InventoryBatchResponse>> Active batches
     */
    @GetMapping("/location/{locationId}")
    public ResponseEntity<List<InventoryBatchResponse>> getActiveBatchesByLocation(
        @PathVariable("locationId") Long locationId
    ) {
        log.info("API: Get active batches by location - locationId={}", locationId);

        List<InventoryBatch> batches = inventoryBatchService.getActiveBatchesByLocation(locationId);

        List<InventoryBatchResponse> responses = batches.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Query Batch by Batch Code
     *
     * API Endpoint:
     * GET /api/inventory/batches/{batchCode}
     *
     * Returns batch details by batch code.
     *
     * Success Response (200 OK):
     * Returns InventoryBatchResponse
     *
     * @param batchCode Batch code
     * @return ResponseEntity<InventoryBatchResponse> Batch details
     */
    @GetMapping("/{batchCode}")
    public ResponseEntity<InventoryBatchResponse> getBatchByCode(
        @PathVariable("batchCode") String batchCode
    ) {
        log.info("API: Get batch by code - batchCode={}", batchCode);

        InventoryBatch batch = inventoryBatchService.findByBatchCode(batchCode);

        InventoryBatchResponse response = mapToResponse(batch);

        return ResponseEntity.ok(response);
    }

    /**
     * ⚠️ Find Expired Batches (过期批次查询)
     *
     * API Endpoint:
     * GET /api/inventory/batches/expired
     *
     * Query batches where expiryDate < today AND active = true
     *
     * Success Response (200 OK):
     * Returns List<InventoryBatchResponse>
     *
     * @return ResponseEntity<List<InventoryBatchResponse>> Expired batches
     */
    @GetMapping("/expired")
    public ResponseEntity<List<InventoryBatchResponse>> getExpiredBatches() {
        log.info("API: Get expired batches");

        List<InventoryBatch> batches = inventoryBatchService.findExpiredBatches();

        List<InventoryBatchResponse> responses = batches.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        log.info("API: Found {} expired batches", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * ⚠️ Find Soon-to-Expire Batches (即将过期批次查询)
     *
     * API Endpoint:
     * GET /api/inventory/batches/expiring-soon?days=7
     *
     * Query batches where expiryDate is within N days
     *
     * Success Response (200 OK):
     * Returns List<InventoryBatchResponse>
     *
     * @param days Days threshold (default: 7)
     * @return ResponseEntity<List<InventoryBatchResponse>> Soon-to-expire batches
     */
    @GetMapping("/expiring-soon")
    public ResponseEntity<List<InventoryBatchResponse>> getSoonToExpireBatches(
        @RequestParam(value = "days", defaultValue = "7") int days
    ) {
        log.info("API: Get soon-to-expire batches - days={}", days);

        List<InventoryBatch> batches = inventoryBatchService.findSoonToExpireBatches(days);

        List<InventoryBatchResponse> responses = batches.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        log.info("API: Found {} soon-to-expire batches (within {} days)",
            responses.size(), days);

        return ResponseEntity.ok(responses);
    }

    /**
     * Find Exhausted Batches (quantity = 0 AND active = true)
     *
     * API Endpoint:
     * GET /api/inventory/batches/exhausted
     *
     * Used for cleanup or archival.
     *
     * Success Response (200 OK):
     * Returns List<InventoryBatchResponse>
     *
     * @return ResponseEntity<List<InventoryBatchResponse>> Exhausted batches
     */
    @GetMapping("/exhausted")
    public ResponseEntity<List<InventoryBatchResponse>> getExhaustedBatches() {
        log.info("API: Get exhausted batches");

        List<InventoryBatch> batches = inventoryBatchService.findExhaustedBatches();

        List<InventoryBatchResponse> responses = batches.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());

        log.info("API: Found {} exhausted batches", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * ⭐ Manual Stock Adjustment for Specific Batch
     *
     * API Endpoint:
     * PUT /api/inventory/batches/{batchCode}/adjust
     *
     * Use Cases:
     * - Inventory reconciliation (盘点调整)
     * - Damage/loss recording
     * - Manual corrections
     *
     * Request Parameters:
     * - adjustmentQuantity: Adjustment quantity (positive = increase, negative = decrease)
     * - sourceType: Source type (e.g., INVENTORY_GAIN, INVENTORY_LOSS)
     * - sourceOrderId: Source order ID (e.g., ST-20250113-001)
     * - operatorId: Operator user ID
     * - operatorName: Operator user name
     * - remark: Adjustment reason
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "success": true,
     *   "transactionId": 1001,
     *   "batchCode": "R7M4K9",
     *   "quantityBefore": 100,
     *   "quantityAfter": 120,
     *   "adjustment": 20
     * }
     * </pre>
     *
     * @param batchCode Batch code
     * @param adjustmentQuantity Adjustment quantity
     * @param sourceType Source type
     * @param sourceOrderId Source order ID
     * @param operatorId Operator ID
     * @param operatorName Operator name
     * @param remark Adjustment reason
     * @return ResponseEntity with adjustment result
     */
    @PutMapping("/{batchCode}/adjust")
    public ResponseEntity<Map<String, Object>> adjustBatchStock(
        @PathVariable("batchCode") String batchCode,
        @RequestParam("adjustmentQuantity") @NotNull(message = "Adjustment quantity is required") Integer adjustmentQuantity,
        @RequestParam("sourceType") @NotNull(message = "Source type is required") SourceType sourceType,
        @RequestParam("sourceOrderId") @NotBlank(message = "Source order ID is required") String sourceOrderId,
        @RequestParam("operatorId") @NotNull(message = "Operator ID is required") Long operatorId,
        @RequestParam("operatorName") @NotBlank(message = "Operator name is required") String operatorName,
        @RequestParam(value = "remark", required = false) String remark
    ) {
        log.info("API: Adjust batch stock - batchCode={}, adjustment={}, sourceType={}",
            batchCode, adjustmentQuantity, sourceType);

        // Call service
        StockTransaction transaction = inventoryBatchService.adjustBatchStock(
            batchCode, adjustmentQuantity, sourceType, sourceOrderId,
            operatorId, operatorName, remark
        );

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("transactionId", transaction.getId());
        response.put("batchCode", batchCode);
        response.put("quantityBefore", transaction.getQuantityBefore());
        response.put("quantityAfter", transaction.getQuantityAfter());
        response.put("adjustment", adjustmentQuantity);

        log.info("API: Batch adjustment completed - transactionId={}, batchCode={}, adjustment={}",
            transaction.getId(), batchCode, adjustmentQuantity);

        return ResponseEntity.ok(response);
    }

    // ========== Helper Methods ==========

    /**
     * Map InventoryBatch entity to Response DTO
     *
     * @param batch Inventory batch entity
     * @return InventoryBatchResponse Response DTO
     */
    private InventoryBatchResponse mapToResponse(InventoryBatch batch) {
        return InventoryBatchResponse.builder()
            .id(batch.getId())
            .batchCode(batch.getBatchCode())
            .productSkuId(batch.getProductSku().getId())
            .productName(batch.getProductSku().getName())
            .productBarcode(batch.getProductSku().getBarcode())
            .quantity(batch.getQuantity())
            .reservedQuantity(batch.getReservedQuantity())
            .availableQuantity(batch.getAvailableQuantity())
            .initialQuantity(batch.getInitialQuantity())
            .expiryDate(batch.getExpiryDate())
            .productionDate(batch.getProductionDate())
            .externalBatchCode(batch.getExternalBatchCode())
            .locationId(batch.getLocation() != null ? batch.getLocation().getId() : null)
            .locationCode(batch.getLocation() != null ? batch.getLocation().getLocationCode() : null)
            .entryDate(batch.getEntryDate())
            .active(batch.getActive())
            .remark(batch.getRemark())
            .createdAt(batch.getCreatedAt())
            .updatedAt(batch.getUpdatedAt())
            .build();
    }
}
