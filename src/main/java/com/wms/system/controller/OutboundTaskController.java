package com.wms.system.controller;

import com.wms.system.dto.outbound.ConfirmPickingRequest;
import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.OutboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Outbound Task Controller
 *
 * V3.7 Architecture: Outbound Task Management RESTful API
 *
 * Provides endpoints for:
 * - List outbound tasks (with filters)
 * - Get outbound task details
 * - Confirm picking (single task)
 * - Batch confirm picking (multiple tasks)
 *
 * Business Flow:
 * 1. Sales order approved → Inventory allocated → Outbound tasks created
 * 2. Warehouse staff queries pending tasks
 * 3. Warehouse staff picks goods and confirms picking
 * 4. System deducts inventory and records transaction
 * 5. When all tasks completed → Sales order status changes to SHIPPED
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@RestController
@RequestMapping("/api/outbound-tasks")
@RequiredArgsConstructor
public class OutboundTaskController {

    private final OutboundService outboundService;

    /**
     * List outbound tasks
     *
     * GET /api/outbound-tasks
     *
     * Permission: outbound:view
     *
     * Optional Parameters:
     * - salesOrderId: Sales order ID
     * - status: Task status (PENDING, PICKING, COMPLETED)
     *
     * Returns: List<OutboundTaskResponse>
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('outbound:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<OutboundTaskResponse>> listOutboundTasks(
        @RequestParam(required = false) Long salesOrderId,
        @RequestParam(required = false) String status
    ) {
        log.info("API调用: listOutboundTasks - salesOrderId: {}, status: {}", salesOrderId, status);

        List<OutboundTaskResponse> responses = outboundService.listOutboundTasks(salesOrderId, status);

        log.info("API响应: listOutboundTasks - 任务数: {}", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get outbound task by ID
     *
     * GET /api/outbound-tasks/{id}
     *
     * Permission: outbound:view
     *
     * Path Variable: id (Outbound task ID)
     * Returns: OutboundTaskResponse
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('outbound:view', 'SUPER_ADMIN')")
    public ResponseEntity<OutboundTaskResponse> getOutboundTask(@PathVariable Long id) {
        log.info("API调用: getOutboundTask - id: {}", id);

        OutboundTaskResponse response = outboundService.getOutboundTask(id);

        log.info("API响应: getOutboundTask - 任务ID: {}, 订单号: {}, 状态: {}",
            response.getId(), response.getSalesOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Confirm picking
     *
     * POST /api/outbound-tasks/{id}/confirm
     *
     * Permission: outbound:pick
     *
     * Path Variable: id (Outbound task ID)
     * Request Body: ConfirmPickingRequest
     * Returns: OutboundTaskResponse
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('outbound:pick', 'SUPER_ADMIN')")
    public ResponseEntity<OutboundTaskResponse> confirmPicking(
        @PathVariable Long id,
        @Valid @RequestBody ConfirmPickingRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("API调用: confirmPicking - taskId: {}, actualQty: {}, 操作人: {}",
            id, request.getActualQty(), user.getUsername());

        OutboundTaskResponse response = outboundService.confirmPicking(
            id,
            request.getActualQty(),
            user.getId(),
            user.getUsername()
        );

        log.info("API响应: confirmPicking - 任务ID: {}, 订单号: {}, 状态: {}",
            response.getId(), response.getSalesOrderNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Batch confirm picking
     *
     * POST /api/outbound-tasks/batch-confirm
     *
     * Permission: outbound:pick
     *
     * Request Body: List<Long> (Task IDs)
     * Returns: List<OutboundTaskResponse>
     */
    @PostMapping("/batch-confirm")
    @PreAuthorize("hasAnyAuthority('outbound:pick', 'SUPER_ADMIN')")
    public ResponseEntity<List<OutboundTaskResponse>> batchConfirmPicking(
        @RequestBody List<Long> taskIds,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("API调用: batchConfirmPicking - taskIds: {}, 操作人: {}", taskIds, user.getUsername());

        List<OutboundTaskResponse> responses = outboundService.batchConfirmPicking(
            taskIds,
            user.getId(),
            user.getUsername()
        );

        log.info("API响应: batchConfirmPicking - 完成任务数: {}", responses.size());

        return ResponseEntity.ok(responses);
    }
}
