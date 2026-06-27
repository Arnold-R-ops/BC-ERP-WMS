package com.wms.system.controller;

import com.wms.system.dto.sales.*;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.SalesEntryService;
import com.wms.system.service.SalesSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Sales Order Controller
 *
 * V3.7 Architecture: Sales Order Management RESTful API
 *
 * Provides endpoints for:
 * - Excel template download and upload
 * - Batch options pre-check
 * - Sales order CRUD operations
 * - Sales order approval workflow
 * - Sales order cancellation
 *
 * Business Flow:
 * 1. Download template → Upload Excel → Pre-check batches
 * 2. Create sales order → Risk control check
 * 3. If requires approval: Manager approves/rejects
 * 4. If approved: Allocate inventory → Generate outbound tasks
 * 5. Warehouse picks goods → Ship order
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@RestController
@RequestMapping("/api/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesEntryService salesEntryService;
    private final SalesSubmissionService salesSubmissionService;

    /**
     * Download Excel template
     *
     * GET /api/sales-orders/template
     *
     * Permission: sales:create
     *
     * Returns: Excel file (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)
     */
    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('sales:create', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadTemplate() {
        log.info("API调用: downloadTemplate");

        byte[] excelBytes = salesEntryService.downloadExcelTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "sales_order_template.xlsx");

        log.info("API响应: downloadTemplate - 文件大小: {} bytes", excelBytes.length);

        return ResponseEntity.ok()
            .headers(headers)
            .body(excelBytes);
    }

    /**
     * Upload Excel file
     *
     * POST /api/sales-orders/upload
     *
     * Permission: sales:create
     *
     * Request: MultipartFile (form-data)
     * Returns: List<SalesOrderItemData>
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('sales:create', 'SUPER_ADMIN')")
    public ResponseEntity<List<CreateSalesOrderRequest.SalesOrderItemData>> uploadExcel(
        @RequestParam("file") MultipartFile file
    ) {
        log.info("API调用: uploadExcel - 文件名: {}", file.getOriginalFilename());

        List<CreateSalesOrderRequest.SalesOrderItemData> items = salesEntryService.importSalesOrderFromExcel(file);

        log.info("API响应: uploadExcel - 导入明细数: {}", items.size());

        return ResponseEntity.ok(items);
    }

    /**
     * Get batch options for pre-check
     *
     * POST /api/sales-orders/batch-options
     *
     * Permission: sales:create
     *
     * Parameters:
     * - productId: Product ID
     * - quantity: Required quantity
     * - rejectNearExpiry: Reject near expiry batches (optional)
     *
     * Returns: List<BatchOptionDto>
     */
    @PostMapping("/batch-options")
    @PreAuthorize("hasAnyAuthority('sales:create', 'SUPER_ADMIN')")
    public ResponseEntity<List<BatchOptionDto>> getBatchOptions(
        @RequestParam("productId") Long productId,
        @RequestParam("quantity") Integer quantity,
        @RequestParam(value = "rejectNearExpiry", required = false) Boolean rejectNearExpiry
    ) {
        log.info("API调用: getBatchOptions - productId: {}, quantity: {}, rejectNearExpiry: {}",
            productId, quantity, rejectNearExpiry);

        List<BatchOptionDto> options = salesEntryService.getBatchOptions(productId, quantity, rejectNearExpiry);

        log.info("API响应: getBatchOptions - 批次选项数: {}", options.size());

        return ResponseEntity.ok(options);
    }

    /**
     * Create sales order
     *
     * POST /api/sales-orders
     *
     * Permission: sales:create
     *
     * Request Body: CreateSalesOrderRequest
     * Returns: SalesOrderResponse
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('sales:create', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderResponse> createSalesOrder(
        @Valid @RequestBody CreateSalesOrderRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);
        log.info("API调用: createSalesOrder - 用户: {}, 客户ID: {}, 明细数: {}",
            username, request.getCustomerId(), request.getItems().size());

        SalesOrderResponse response = salesSubmissionService.createSalesOrder(
            request,
            userId,
            username
        );

        log.info("API响应: createSalesOrder - 订单号: {}, 状态: {}", response.getOrderNo(), response.getStatus());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all sales orders
     *
     * GET /api/sales-orders
     *
     * Permission: sales:view
     *
     * Optional Parameters:
     * - status: Order status
     * - customerId: Customer ID
     *
     * Returns: List<SalesOrderResponse>
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('sales:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<SalesOrderResponse>> listSalesOrders(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "customerId", required = false) Long customerId
    ) {
        log.info("API调用: listSalesOrders - status: {}, customerId: {}", status, customerId);

        List<SalesOrderResponse> responses = salesSubmissionService.listSalesOrders(status, customerId);

        log.info("API响应: listSalesOrders - 订单数: {}", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get sales order by ID
     *
     * GET /api/sales-orders/{id}
     *
     * Permission: sales:view
     *
     * Path Variable: id (Sales order ID)
     * Returns: SalesOrderResponse
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('sales:view', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderResponse> getSalesOrder(@PathVariable("id") Long id) {
        log.info("API调用: getSalesOrder - id: {}", id);

        SalesOrderResponse response = salesSubmissionService.getSalesOrder(id);

        log.info("API响应: getSalesOrder - 订单号: {}, 状态: {}", response.getOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Update sales order
     *
     * PUT /api/sales-orders/{id}
     *
     * Permission: sales:edit
     *
     * Path Variable: id (Sales order ID)
     * Request Body: UpdateSalesOrderRequest
     * Returns: SalesOrderResponse
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('sales:edit', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderResponse> updateSalesOrder(
        @PathVariable("id") Long id,
        @Valid @RequestBody UpdateSalesOrderRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);
        log.info("API调用: updateSalesOrder - id: {}, 用户: {}, 明细数: {}",
            id, username, request.getItems().size());

        SalesOrderResponse response = salesSubmissionService.updateSalesOrder(id, request, userId);

        log.info("API响应: updateSalesOrder - 订单号: {}, 状态: {}", response.getOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Approve sales order
     *
     * POST /api/sales-orders/{id}/approve
     *
     * Permission: sales:approve
     *
     * Path Variable: id (Sales order ID)
     * Request Body: ApprovalRequest
     * Returns: SalesOrderResponse
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('sales:approve', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderResponse> approveSalesOrder(
        @PathVariable("id") Long id,
        @Valid @RequestBody ApprovalRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);
        log.info("API调用: approveSalesOrder - id: {}, 审批人: {}, 意见: {}",
            id, username, request.getComment());

        SalesOrderResponse response = salesSubmissionService.approveSalesOrder(
            id,
            userId,
            username,
            request.getComment(),
            request.getAllocationPolicy(),
            request.getRequestedShipDate(),
            request.getPromisedShipDate()
        );

        log.info("API响应: approveSalesOrder - 订单号: {}, 状态: {}", response.getOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Reject sales order
     *
     * POST /api/sales-orders/{id}/reject
     *
     * Permission: sales:approve
     *
     * Path Variable: id (Sales order ID)
     * Request Body: ApprovalRequest
     * Returns: SalesOrderResponse
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('sales:approve', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderResponse> rejectSalesOrder(
        @PathVariable("id") Long id,
        @Valid @RequestBody ApprovalRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);
        log.info("API调用: rejectSalesOrder - id: {}, 审批人: {}, 原因: {}",
            id, username, request.getReason());

        SalesOrderResponse response = salesSubmissionService.rejectSalesOrder(
            id,
            userId,
            username,
            request.getReason()
        );

        log.info("API响应: rejectSalesOrder - 订单号: {}, 状态: {}", response.getOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel sales order
     *
     * POST /api/sales-orders/{id}/cancel
     *
     * Permission: sales:cancel
     *
     * Path Variable: id (Sales order ID)
     * Request Body: CancelOrderRequest
     * Returns: SalesOrderResponse
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('sales:cancel', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderResponse> cancelSalesOrder(
        @PathVariable("id") Long id,
        @Valid @RequestBody CancelOrderRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);
        log.info("API调用: cancelSalesOrder - id: {}, 操作人: {}, 原因: {}",
            id, username, request.getReason());

        SalesOrderResponse response = salesSubmissionService.cancelSalesOrder(
            id,
            request.getReason(),
            userId
        );

        log.info("API响应: cancelSalesOrder - 订单号: {}, 状态: {}", response.getOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Void sales order (AI data cleaning - mark as data noise)
     *
     * POST /api/sales-orders/{id}/void
     *
     * Permission: sales:cancel
     *
     * Path Variable: id (Sales order ID)
     * Request Body: CancelOrderRequest
     * Returns: SalesOrderResponse
     */
    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyAuthority('sales:cancel', 'SUPER_ADMIN')")
    public ResponseEntity<SalesOrderResponse> voidSalesOrder(
        @PathVariable("id") Long id,
        @Valid @RequestBody CancelOrderRequest request,
        Authentication authentication
    ) {
        Long userId = AuthUserResolver.resolveUserId(authentication);
        String username = AuthUserResolver.resolveUsername(authentication);
        log.info("API调用: voidSalesOrder - id: {}, 操作人: {}, 原因: {}",
            id, username, request.getReason());

        SalesOrderResponse response = salesSubmissionService.voidSalesOrder(
            id,
            request.getReason(),
            userId
        );

        log.info("API响应: voidSalesOrder - 订单号: {}, 状态: {}", response.getOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }
}
