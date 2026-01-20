package com.wms.system.controller;

import com.wms.system.dto.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.ExcelImportService;
import com.wms.system.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Purchase Order Management Controller
 *
 * Provides RESTful APIs for purchase order operations:
 * - Stage 1: Create PO (ORDERING)
 * - Stage 2: Confirm ASN and generate batch codes (IN_TRANSIT)
 * - Stage 3: Physical receipt (PARTIALLY_RECEIVED/COMPLETED)
 * - State rollback (IN_TRANSIT → ORDERING)
 * - Excel import
 * - Query operations
 *
 * Privacy Protection:
 * - STAFF role: supplier masked as "***", totalCost/unitCost hidden
 * - ADMIN/MANAGER roles: all fields visible
 *
 * API Conventions:
 * - Base path: /api/purchase-orders
 * - Request body validation: @Valid annotation
 * - Error handling: GlobalExceptionHandler converts exceptions to ErrorResponse
 * - Response format: JSON (DTO, NOT Entity)
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management + Batch Management)
 */
@Slf4j
@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final ExcelImportService excelImportService;

    /**
     * ⭐ Stage 1: Create Purchase Order
     *
     * API Endpoint:
     * POST /api/purchase-orders
     *
     * Request Body Example:
     * <pre>
     * {
     *   "supplier": "XX Supplier",
     *   "items": [
     *     {
     *       "productId": 1,
     *       "orderedQuantity": 100,
     *       "unitCost": 10.50,
     *       "expiryDate": "2025-12-31",
     *       "externalBatchCode": "BATCH001"
     *     }
     *   ],
     *   "expectedDate": "2025-01-20",
     *   "operatorId": 1,
     *   "operatorName": "admin",
     *   "remark": "Urgent order"
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * Returns PurchaseOrderResponse with ORDERING status
     *
     * @param request Create purchase order request
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Created purchase order
     */
    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(
        @Valid @RequestBody CreatePurchaseOrderRequest request,
        Authentication authentication
    ) {
        log.info("API: Create purchase order - supplier={}, itemCount={}, operator={}",
            request.getSupplier(), request.getItems().size(), request.getOperatorName());

        // Convert DTO to Service input format
        List<PurchaseOrderService.PurchaseOrderItemData> itemsData = request.getItems().stream()
            .map(item -> PurchaseOrderService.PurchaseOrderItemData.builder()
                .productId(item.getProductId())
                .orderedQuantity(item.getOrderedQuantity())
                .unitCost(item.getUnitCost())
                .expiryDate(item.getExpiryDate())
                .productionDate(item.getProductionDate())
                .externalBatchCode(item.getExternalBatchCode())
                .remark(item.getRemark())
                .build())
            .collect(Collectors.toList());

        // Call service
        PurchaseOrder purchaseOrder = purchaseOrderService.createPurchaseOrder(
            request.getSupplier(),
            itemsData,
            request.getExpectedDate(),
            request.getOperatorId(),
            request.getOperatorName(),
            request.getRemark()
        );

        // Convert to response DTO with privacy masking
        String userRoleCode = getUserRoleCode(authentication);
        PurchaseOrderResponse response = mapToResponse(purchaseOrder, userRoleCode);

        log.info("API: Purchase order created - poNumber={}, status={}, totalQuantity={}",
            response.getPoNumber(), response.getStatus(), response.getTotalQuantity());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ⭐ Stage 1: Upload Excel to Create Purchase Order
     *
     * API Endpoint:
     * POST /api/purchase-orders/upload
     *
     * Form Data Parameters:
     * - file: Excel file (XLSX format, required)
     * - supplier: Supplier name (required)
     * - operatorId: Operator user ID (required)
     * - operatorName: Operator user name (required)
     * - expectedDate: Expected delivery date (optional, format: yyyy-MM-dd)
     *
     * Excel Format:
     * | productId | quantity | unitCost | expiryDate | productionDate | externalBatchCode | remark |
     *
     * Success Response (201 Created):
     * Returns PurchaseOrderResponse with ORDERING status
     *
     * @param file Excel file
     * @param supplier Supplier name
     * @param operatorId Operator ID
     * @param operatorName Operator name
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Created purchase order
     */
    @PostMapping("/upload")
    public ResponseEntity<PurchaseOrderResponse> uploadExcel(
        @RequestParam("file") MultipartFile file,
        @RequestParam("supplier") String supplier,
        @RequestParam("operatorId") Long operatorId,
        @RequestParam("operatorName") String operatorName,
        @RequestParam(value = "expectedDate", required = false) String expectedDate,
        Authentication authentication
    ) {
        log.info("API: Upload Excel - filename={}, supplier={}, operator={}",
            file.getOriginalFilename(), supplier, operatorName);

        // Parse Excel file
        List<PurchaseOrderService.PurchaseOrderItemData> itemsData =
            excelImportService.importPurchaseOrderFromExcel(file);

        log.info("Excel parsed successfully: itemCount={}", itemsData.size());

        // Call service
        PurchaseOrder purchaseOrder = purchaseOrderService.createPurchaseOrder(
            supplier,
            itemsData,
            expectedDate != null ? java.time.LocalDate.parse(expectedDate) : null,
            operatorId,
            operatorName,
            "Imported from Excel: " + file.getOriginalFilename()
        );

        // Convert to response DTO with privacy masking
        String userRoleCode = getUserRoleCode(authentication);
        PurchaseOrderResponse response = mapToResponse(purchaseOrder, userRoleCode);

        log.info("API: Excel import completed - poNumber={}, itemCount={}",
            response.getPoNumber(), response.getItems().size());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ⭐ Stage 2: Confirm ASN and Generate Batch Codes
     *
     * API Endpoint:
     * PUT /api/purchase-orders/{id}/confirm
     *
     * Request Body Example:
     * <pre>
     * {
     *   "items": [
     *     {
     *       "itemId": 1,
     *       "expiryDate": "2025-12-31",
     *       "productionDate": "2025-01-01"
     *     }
     *   ]
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * Returns PurchaseOrderResponse with IN_TRANSIT status and generated batch codes
     *
     * @param id Purchase order ID
     * @param request Confirm order request
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Updated purchase order
     */
    @PutMapping("/{id}/confirm")
    public ResponseEntity<PurchaseOrderResponse> confirmAndGenerateBatchCodes(
        @PathVariable Long id,
        @Valid @RequestBody ConfirmOrderRequest request,
        Authentication authentication
    ) {
        log.info("API: Confirm ASN and generate batch codes - purchaseOrderId={}, itemCount={}",
            id, request.getItems().size());

        // Convert DTO to Service input format
        List<PurchaseOrderService.ItemExpiryUpdate> updates = request.getItems().stream()
            .map(item -> PurchaseOrderService.ItemExpiryUpdate.builder()
                .itemId(item.getItemId())
                .expiryDate(item.getExpiryDate())
                .productionDate(item.getProductionDate())
                .build())
            .collect(Collectors.toList());

        // Call service
        PurchaseOrder purchaseOrder = purchaseOrderService.confirmAndGenerateBatchCodes(id, updates);

        // Convert to response DTO with privacy masking
        String userRoleCode = getUserRoleCode(authentication);
        PurchaseOrderResponse response = mapToResponse(purchaseOrder, userRoleCode);

        log.info("API: Batch codes generated - poNumber={}, status={}, batchCount={}",
            response.getPoNumber(), response.getStatus(),
            response.getItems().stream().mapToLong(item -> item.getBatches().size()).sum());

        return ResponseEntity.ok(response);
    }

    /**
     * ⭐ Stage 3: Confirm Physical Receipt
     *
     * API Endpoint:
     * PUT /api/purchase-orders/{id}/receive
     *
     * Request Body Example:
     * <pre>
     * {
     *   "receiveItems": [
     *     {
     *       "itemId": 1,
     *       "batches": [
     *         {
     *           "batchCode": "R7M4K9",
     *           "locationId": 101
     *         }
     *       ]
     *     }
     *   ],
     *   "operatorId": 1,
     *   "operatorName": "warehouse_staff"
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * Returns PurchaseOrderResponse with PARTIALLY_RECEIVED or COMPLETED status
     *
     * Supports Partial Receiving:
     * - Can be called multiple times for same PO
     * - receivedQuantity accumulates
     *
     * @param id Purchase order ID
     * @param request Confirm receipt request
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Updated purchase order
     */
    @PutMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrderResponse> receiveGoods(
        @PathVariable Long id,
        @Valid @RequestBody ConfirmReceiptRequest request,
        Authentication authentication
    ) {
        log.info("API: Confirm physical receipt - purchaseOrderId={}, batchCount={}, operator={}",
            id, request.getReceiveItems().stream().mapToLong(item -> item.getBatches().size()).sum(),
            request.getOperatorName());

        // Convert DTO to Service input format
        List<PurchaseOrderService.BatchReceiptData> receiptData = request.getReceiveItems().stream()
            .flatMap(item -> item.getBatches().stream()
                .map(batch -> PurchaseOrderService.BatchReceiptData.builder()
                    .batchCode(batch.getBatchCode())
                    .locationId(batch.getLocationId())
                    .build()))
            .collect(Collectors.toList());

        // Call service
        PurchaseOrder purchaseOrder = purchaseOrderService.receiveGoods(
            id,
            receiptData,
            request.getOperatorId(),
            request.getOperatorName()
        );

        // Convert to response DTO with privacy masking
        String userRoleCode = getUserRoleCode(authentication);
        PurchaseOrderResponse response = mapToResponse(purchaseOrder, userRoleCode);

        log.info("API: Physical receipt completed - poNumber={}, status={}, receivedQty={}/{}",
            response.getPoNumber(), response.getStatus(),
            response.getItems().stream().mapToInt(PurchaseOrderItemResponse::getReceivedQuantity).sum(),
            response.getTotalQuantity());

        return ResponseEntity.ok(response);
    }

    /**
     * ⭐ Rollback to ORDERING
     *
     * API Endpoint:
     * PUT /api/purchase-orders/{id}/rollback
     *
     * Request Body Example:
     * <pre>
     * {
     *   "reason": "Supplier changed, need to re-verify data"
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * Returns PurchaseOrderResponse with ORDERING status
     *
     * ⚠️ Note: Only IN_TRANSIT → ORDERING rollback allowed
     *
     * @param id Purchase order ID
     * @param reason Rollback reason
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Updated purchase order
     */
    @PutMapping("/{id}/rollback")
    public ResponseEntity<PurchaseOrderResponse> rollbackToOrdering(
        @PathVariable Long id,
        @RequestParam("reason") String reason,
        Authentication authentication
    ) {
        log.info("API: Rollback to ORDERING - purchaseOrderId={}, reason={}", id, reason);

        // Call service
        PurchaseOrder purchaseOrder = purchaseOrderService.rollbackToOrdering(id, reason);

        // Convert to response DTO with privacy masking
        String userRoleCode = getUserRoleCode(authentication);
        PurchaseOrderResponse response = mapToResponse(purchaseOrder, userRoleCode);

        log.info("API: Rollback completed - poNumber={}, status={}",
            response.getPoNumber(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Query Purchase Order by ID
     *
     * API Endpoint:
     * GET /api/purchase-orders/{id}
     *
     * Success Response (200 OK):
     * Returns PurchaseOrderResponse with privacy masking applied
     *
     * @param id Purchase order ID
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Purchase order
     */
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrderById(
        @PathVariable Long id,
        Authentication authentication
    ) {
        log.info("API: Query purchase order by ID - id={}", id);

        PurchaseOrder purchaseOrder = purchaseOrderService.findById(id);

        String userRoleCode = getUserRoleCode(authentication);
        PurchaseOrderResponse response = mapToResponse(purchaseOrder, userRoleCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Query Purchase Orders by Status
     *
     * API Endpoint:
     * GET /api/purchase-orders?status=ORDERING
     *
     * Success Response (200 OK):
     * Returns List<PurchaseOrderResponse> with privacy masking applied
     *
     * @param status Purchase order status (optional)
     * @param authentication Current user authentication
     * @return ResponseEntity<List<PurchaseOrderResponse>> Purchase orders
     */
    @GetMapping
    public ResponseEntity<List<PurchaseOrderResponse>> getPurchaseOrders(
        @RequestParam(value = "status", required = false) PurchaseOrderStatus status,
        Authentication authentication
    ) {
        log.info("API: Query purchase orders - status={}", status);

        List<PurchaseOrder> purchaseOrders;
        if (status != null) {
            purchaseOrders = purchaseOrderService.findByStatus(status);
        } else {
            // Return all (implement pagination in future)
            purchaseOrders = List.of();  // TODO: Implement findAll with pagination
        }

        String userRoleCode = getUserRoleCode(authentication);
        List<PurchaseOrderResponse> responses = purchaseOrders.stream()
            .map(po -> mapToResponse(po, userRoleCode))
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ========== Helper Methods ==========

    /**
     * Get current user's role code from authentication
     *
     * v3.3 Multi-Role System:
     * - Role extracted from JWT token's current_role claim
     * - JwtAuthenticationFilter sets authorities as "ROLE_{roleCode}"
     * - This method extracts the role code from authorities
     *
     * @param authentication Authentication object
     * @return String Role code (e.g., "SUPER_ADMIN", "WAREHOUSE_ADMIN", "STAFF")
     *         Returns "STAFF" as default (most restrictive)
     */
    private String getUserRoleCode(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            // Extract role from authorities (format: "ROLE_SUPER_ADMIN" → "SUPER_ADMIN")
            return authentication.getAuthorities().stream()
                .findFirst()
                .map(auth -> auth.getAuthority())
                .map(authority -> authority.startsWith("ROLE_") ?
                     authority.substring(5) : authority)
                .orElse("STAFF");  // Default to most restrictive role
        }
        return "STAFF";
    }

    /**
     * Map PurchaseOrder entity to Response DTO with privacy masking
     *
     * Privacy Rules (v3.3 Multi-Role System):
     * - STAFF/SALESPERSON roles: supplier = "***", totalCost = null, unitCost = null
     * - SUPER_ADMIN/WAREHOUSE_ADMIN/PURCHASER roles: all fields visible
     *
     * @param purchaseOrder Purchase order entity
     * @param userRoleCode Current user's role code (e.g., "STAFF", "SUPER_ADMIN")
     * @return PurchaseOrderResponse Response DTO
     */
    private PurchaseOrderResponse mapToResponse(PurchaseOrder purchaseOrder, String userRoleCode) {
        // Mask sensitive data for restricted roles
        boolean maskSensitiveData = "STAFF".equals(userRoleCode) ||
                                     "SALESPERSON".equals(userRoleCode);

        return PurchaseOrderResponse.builder()
            .id(purchaseOrder.getId())
            .poNumber(purchaseOrder.getPoNumber())
            .supplier(maskSensitiveData ? "***" : purchaseOrder.getSupplier())  // Privacy masking
            .status(purchaseOrder.getStatus())
            .totalQuantity(purchaseOrder.getTotalQuantity())
            .totalCost(maskSensitiveData ? null : purchaseOrder.getTotalCost())  // Privacy masking
            .expectedDate(purchaseOrder.getExpectedDate())
            .actualEntryDate(purchaseOrder.getActualEntryDate())
            .operatorId(purchaseOrder.getOperatorId())
            .operatorName(purchaseOrder.getOperatorName())
            .remark(purchaseOrder.getRemark())
            .auditLog(purchaseOrder.getAuditLog())
            .items(purchaseOrder.getItems().stream()
                .map(item -> mapToItemResponse(item, maskSensitiveData))
                .collect(Collectors.toList()))
            .createdAt(purchaseOrder.getCreatedAt())
            .updatedAt(purchaseOrder.getUpdatedAt())
            .build();
    }

    /**
     * Map PurchaseOrderItem entity to Response DTO
     *
     * @param item Purchase order item entity
     * @param isStaff Is user STAFF role
     * @return PurchaseOrderItemResponse Response DTO
     */
    private PurchaseOrderItemResponse mapToItemResponse(PurchaseOrderItem item, boolean isStaff) {
        return PurchaseOrderItemResponse.builder()
            .id(item.getId())
            .productId(item.getProduct().getId())
            .productName(item.getProduct().getName())
            .productBarcode(item.getProduct().getBarcode())
            .orderedQuantity(item.getOrderedQuantity())
            .receivedQuantity(item.getReceivedQuantity())
            .unitCost(isStaff ? null : item.getUnitCost())  // Privacy masking
            .expiryDate(item.getExpiryDate())
            .productionDate(item.getProductionDate())
            .externalBatchCode(item.getExternalBatchCode())
            .remark(item.getRemark())
            .batches(item.getBatches().stream()
                .map(this::mapToBatchResponse)
                .collect(Collectors.toList()))
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }

    /**
     * Map InventoryBatch entity to Response DTO
     *
     * @param batch Inventory batch entity
     * @return InventoryBatchResponse Response DTO
     */
    private InventoryBatchResponse mapToBatchResponse(InventoryBatch batch) {
        return InventoryBatchResponse.builder()
            .id(batch.getId())
            .batchCode(batch.getBatchCode())
            .productId(batch.getProduct().getId())
            .productName(batch.getProduct().getName())
            .productBarcode(batch.getProduct().getBarcode())
            .quantity(batch.getQuantity())
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
