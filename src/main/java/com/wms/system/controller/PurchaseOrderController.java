package com.wms.system.controller;

import com.wms.system.dto.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.ExcelImportService;
import com.wms.system.service.ExcelTemplateService;
import com.wms.system.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Purchase Order Management Controller
 *
 * Provides RESTful APIs for purchase order operations:
 * - Create and edit purchase-order drafts
 * - Query commercial workflow status
 * - Import draft lines from the controlled spreadsheet template
 *
 * Shipment notifications and physical receipts are intentionally exposed by
 * PurchaseAsnController and ReceivingTaskController, not by this controller.
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

    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('purchase:create', 'TENANT_ADMIN')")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        byte[] excelBytes = excelImportService.downloadPurchaseOrderTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(ExcelTemplateService.PURCHASE_FILE)
            .build());
        return ResponseEntity.ok().headers(headers).body(excelBytes);
    }

    /**
     * Create purchase-order draft
     *
     * API Endpoint:
     * POST /api/purchase-orders
     *
     * Request Body Example:
     * <pre>
     * {
     *   "supplierId": 1,
     *   "items": [
     *     {
     *       "productSkuId": 1,
     *       "orderedQuantity": 100,
     *       "unitCost": 10.50,
     *       "expiryDate": "2027-12-31",
     *       "externalBatchCode": "BATCH001"
     *     }
     *   ],
     *   "expectedDate": "2026-09-20",
     *   "remark": "Urgent order"
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * Returns PurchaseOrderResponse with DRAFT status. The authoritative actor
     * is always resolved from the authenticated account and is never accepted
     * from this request body.
     *
     * @param request Create purchase order request
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Created purchase order
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('purchase:create', 'TENANT_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(
        @Valid @RequestBody CreatePurchaseOrderRequest request,
        Authentication authentication
    ) {
        Long actorId = AuthUserResolver.resolveUserId(authentication);
        String actorName = AuthUserResolver.resolveUsername(authentication);
        log.info("API: Create purchase order - supplierId={}, itemCount={}, actorId={}",
            request.getSupplierId(), request.getItems().size(), actorId);

        // Convert DTO to Service input format
        List<PurchaseOrderService.PurchaseOrderItemData> itemsData = request.getItems().stream()
            .map(item -> PurchaseOrderService.PurchaseOrderItemData.builder()
                .productSkuId(item.getProductSkuId())
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
            request.getSupplierId(),
            itemsData,
            request.getExpectedDate(),
            actorId,
            actorName,
            request.getRemark()
        );

        // Convert to response DTO with privacy masking
        String userRoleCode = getUserRoleCode(authentication);
        PurchaseOrderResponse response = mapToResponse(purchaseOrder, userRoleCode);

        log.info("API: Purchase order created - poNumber={}, status={}, totalQuantity={}",
            response.getPoNumber(), response.getStatus(), response.getTotalQuantity());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Edit an existing purchase-order draft. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('purchase:update', 'TENANT_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<PurchaseOrderResponse> updateOrderingPurchaseOrder(
        @PathVariable("id") Long id,
        @Valid @RequestBody UpdatePurchaseOrderRequest request,
        Authentication authentication
    ) {
        List<PurchaseOrderService.PurchaseOrderItemUpdateData> items = request.getItems().stream()
            .map(item -> PurchaseOrderService.PurchaseOrderItemUpdateData.builder()
                .id(item.getId())
                .productSkuId(item.getProductSkuId())
                .orderedQuantity(item.getOrderedQuantity())
                .unitCost(item.getUnitCost())
                .expiryDate(item.getExpiryDate())
                .productionDate(item.getProductionDate())
                .externalBatchCode(item.getExternalBatchCode())
                .remark(item.getRemark())
                .build())
            .collect(Collectors.toList());

        PurchaseOrder purchaseOrder = purchaseOrderService.updateOrderingPurchaseOrder(
            id,
            request.getVersion(),
            request.getSupplierId(),
            items,
            request.getExpectedDate(),
            AuthUserResolver.resolveUserId(authentication),
            AuthUserResolver.resolveUsername(authentication),
            request.getRemark()
        );

        return ResponseEntity.ok(mapToResponse(purchaseOrder, getUserRoleCode(authentication)));
    }

    /**
     * ⭐ Stage 1: Upload Excel to Create Purchase Order
     *
     * API Endpoint:
     * POST /api/purchase-orders/upload
     *
     * Form Data Parameters:
     * - file: Excel file (XLSX format, required)
     * - supplierId: Supplier master-data ID (required)
     * - expectedDate: Expected delivery date (optional, format: yyyy-MM-dd)
     *
     * Excel Format:
     * | productSkuId | quantity | unitCost | expiryDate | productionDate | externalBatchCode | remark |
     *
     * Success Response (201 Created):
     * Returns PurchaseOrderResponse with ORDERING status
     *
     * @param file Excel file
     * @param supplierId Supplier master-data ID
     * @param authentication Current user authentication
     * @return ResponseEntity<PurchaseOrderResponse> Created purchase order
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('purchase:create', 'TENANT_ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<PurchaseOrderResponse> uploadExcel(
        @RequestParam("file") MultipartFile file,
        @RequestParam("supplierId") Long supplierId,
        @RequestParam(value = "expectedDate", required = false) String expectedDate,
        Authentication authentication
    ) {
        Long actorId = AuthUserResolver.resolveUserId(authentication);
        String actorName = AuthUserResolver.resolveUsername(authentication);
        log.info("API: Upload Excel - filename={}, supplierId={}, actorId={}",
            file.getOriginalFilename(), supplierId, actorId);

        // Parse Excel file
        List<PurchaseOrderService.PurchaseOrderItemData> itemsData =
            excelImportService.importPurchaseOrderFromExcel(file);

        log.info("Excel parsed successfully: itemCount={}", itemsData.size());

        // Call service
        PurchaseOrder purchaseOrder = purchaseOrderService.createPurchaseOrder(
            supplierId,
            itemsData,
            expectedDate != null ? java.time.LocalDate.parse(expectedDate) : null,
            actorId,
            actorName,
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
    @PreAuthorize("hasAnyAuthority('purchase:view', 'TENANT_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrderById(
        @PathVariable("id") Long id,
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
     * GET /api/purchase-orders?status=DRAFT
     *
     * Success Response (200 OK):
     * Returns List<PurchaseOrderResponse> with privacy masking applied
     *
     * @param status Purchase order status (optional)
     * @param pageable Pagination parameters (optional)
     * @param authentication Current user authentication
     * @return ResponseEntity<List<PurchaseOrderResponse>> Purchase orders
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('purchase:view', 'TENANT_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PurchaseOrderResponse>> getPurchaseOrders(
        @RequestParam(value = "status", required = false) PurchaseOrderStatus status,
        @PageableDefault(size = 20, page = 0) Pageable pageable,
        Authentication authentication
    ) {
        log.info("API: Query purchase orders - status={}, page={}, size={}",
            status, pageable.getPageNumber(), pageable.getPageSize());

        List<PurchaseOrder> purchaseOrders;
        if (status != null) {
            purchaseOrders = purchaseOrderService.findByStatus(status, pageable);
        } else {
            purchaseOrders = purchaseOrderService.findAll(pageable);
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
     * @return String Role code (e.g., "TENANT_ADMIN", "WAREHOUSE_ADMIN", "STAFF")
     *         Returns "STAFF" as default (most restrictive)
     */
    private String getUserRoleCode(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            // Extract role from authorities (format: "ROLE_TENANT_ADMIN" → "TENANT_ADMIN")
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
     * - TENANT_ADMIN/WAREHOUSE_ADMIN/PURCHASER roles: all fields visible
     *
     * @param purchaseOrder Purchase order entity
     * @param userRoleCode Current user's role code (e.g., "STAFF", "TENANT_ADMIN")
     * @return PurchaseOrderResponse Response DTO
     */
    private PurchaseOrderResponse mapToResponse(PurchaseOrder purchaseOrder, String userRoleCode) {
        // Mask sensitive data for restricted roles
        boolean maskSensitiveData = "STAFF".equals(userRoleCode) ||
                                     "SALESPERSON".equals(userRoleCode);

        return PurchaseOrderResponse.builder()
            .id(purchaseOrder.getId())
            .version(purchaseOrder.getVersion())
            .poNumber(purchaseOrder.getPoNumber())
            .supplier(maskSensitiveData ? "***" : purchaseOrder.getSupplier())  // Privacy masking
            .supplierId(purchaseOrder.getSupplierReference() == null
                ? null
                : purchaseOrder.getSupplierReference().getId())
            .supplierCode(purchaseOrder.getSupplierReference() == null
                ? null
                : purchaseOrder.getSupplierReference().getCode())
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
            .productSkuId(item.getProductSku().getId())
            .productName(item.getProductSku().getName())
            .productBarcode(item.getProductSku().getBarcode())
            .orderedQuantity(item.getOrderedQuantity())
            .receivedQuantity(item.getReceivedQuantity())
            .unitCost(isStaff ? null : item.getUnitCost())  // Privacy masking
            .expiryDate(item.getExpiryDate())
            .productionDate(item.getProductionDate())
            .externalBatchCode(item.getExternalBatchCode())
            .remark(item.getRemark())
            .batches(item.getBatches().stream()
                .filter(batch -> Boolean.TRUE.equals(batch.getActive()))
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
