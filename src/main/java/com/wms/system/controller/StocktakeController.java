package com.wms.system.controller;

import com.wms.system.dto.stocktake.*;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.StocktakeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Stocktake Controller
 *
 * V3.8 Architecture: Smart Stocktake System RESTful API
 *
 * Provides endpoints for:
 * - Create stocktake tasks
 * - Start counting
 * - Submit count results (blind count)
 * - Finish counting
 * - Review and approve stocktake results
 * - Query stocktake tasks and items
 *
 * Business Flow:
 * 1. Create task → Select batches → Generate snapshot
 * 2. Start counting → Warehouse staff count items (blind count)
 * 3. Submit counts → Calculate differences
 * 4. Finish counting → Transition to review
 * 5. Manager reviews → Approve/Reject
 * 6. If approved → Adjust inventory → Complete
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Slf4j
@RestController
@RequestMapping("/api/stocktake")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    /**
     * Create stocktake task
     *
     * POST /api/stocktake/tasks
     *
     * Permission: stocktake:create
     *
     * Request Body: CreateStocktakeTaskRequest
     * Returns: StocktakeTaskResponse
     */
    @PostMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('stocktake:create', 'SUPER_ADMIN')")
    public ResponseEntity<StocktakeTaskResponse> createStocktakeTask(
        @Valid @RequestBody CreateStocktakeTaskRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("API调用: createStocktakeTask - 用户: {}, 仓库ID: {}, 周期类型: {}",
            user.getUsername(), request.getWarehouseId(), request.getCycleType());

        StocktakeTaskResponse response = stocktakeService.createCycleTask(
            request,
            user.getId(),
            user.getUsername()
        );

        log.info("API响应: createStocktakeTask - 任务号: {}, 明细数: {}",
            response.getTaskNo(), response.getTotalItems());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List stocktake tasks
     *
     * GET /api/stocktake/tasks
     *
     * Permission: stocktake:view
     *
     * Optional Parameters:
     * - warehouseId: Warehouse ID
     * - status: Task status
     *
     * Returns: List<StocktakeTaskResponse>
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('stocktake:view', 'SUPER_ADMIN')")
    public ResponseEntity<List<StocktakeTaskResponse>> listStocktakeTasks(
        @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) String status
    ) {
        log.info("API调用: listStocktakeTasks - warehouseId: {}, status: {}", warehouseId, status);

        List<StocktakeTaskResponse> responses = stocktakeService.listStocktakeTasks(warehouseId, status);

        log.info("API响应: listStocktakeTasks - 任务数: {}", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get stocktake task by ID
     *
     * GET /api/stocktake/tasks/{id}
     *
     * Permission: stocktake:view
     *
     * Path Variable: id (Task ID)
     * Returns: StocktakeTaskResponse
     */
    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAnyAuthority('stocktake:view', 'SUPER_ADMIN')")
    public ResponseEntity<StocktakeTaskResponse> getStocktakeTask(@PathVariable Long id) {
        log.info("API调用: getStocktakeTask - id: {}", id);

        StocktakeTaskResponse response = stocktakeService.getStocktakeTask(id);

        log.info("API响应: getStocktakeTask - 任务号: {}, 状态: {}",
            response.getTaskNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Start counting
     *
     * POST /api/stocktake/tasks/{id}/start
     *
     * Permission: stocktake:count
     *
     * Path Variable: id (Task ID)
     * Returns: StocktakeTaskResponse
     */
    @PostMapping("/tasks/{id}/start")
    @PreAuthorize("hasAnyAuthority('stocktake:count', 'SUPER_ADMIN')")
    public ResponseEntity<StocktakeTaskResponse> startCounting(@PathVariable Long id) {
        log.info("API调用: startCounting - id: {}", id);

        StocktakeTaskResponse response = stocktakeService.startCounting(id);

        log.info("API响应: startCounting - 任务号: {}, 状态: {}",
            response.getTaskNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Get stocktake items (blind count version - no snapshot qty)
     *
     * GET /api/stocktake/tasks/{id}/items
     *
     * Permission: stocktake:count
     *
     * Path Variable: id (Task ID)
     * Returns: List<StocktakeItemResponse>
     */
    @GetMapping("/tasks/{id}/items")
    @PreAuthorize("hasAnyAuthority('stocktake:count', 'SUPER_ADMIN')")
    public ResponseEntity<List<StocktakeItemResponse>> getStocktakeItems(@PathVariable Long id) {
        log.info("API调用: getStocktakeItems - taskId: {}", id);

        List<StocktakeItemResponse> responses = stocktakeService.getStocktakeItems(id);

        log.info("API响应: getStocktakeItems - 明细数: {}", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Submit count result
     *
     * POST /api/stocktake/tasks/{taskId}/items/{itemId}/count
     *
     * Permission: stocktake:count
     *
     * Path Variables:
     * - taskId: Task ID
     * - itemId: Item ID
     *
     * Request Body: SubmitCountRequest
     * Returns: StocktakeItemResponse
     */
    @PostMapping("/tasks/{taskId}/items/{itemId}/count")
    @PreAuthorize("hasAnyAuthority('stocktake:count', 'SUPER_ADMIN')")
    public ResponseEntity<StocktakeItemResponse> submitCount(
        @PathVariable Long taskId,
        @PathVariable Long itemId,
        @Valid @RequestBody SubmitCountRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("API调用: submitCount - taskId: {}, itemId: {}, countedQty: {}, 用户: {}",
            taskId, itemId, request.getCountedQty(), user.getUsername());

        StocktakeItemResponse response = stocktakeService.submitCount(
            taskId,
            itemId,
            request,
            user.getId(),
            user.getUsername()
        );

        log.info("API响应: submitCount - itemId: {}, countedQty: {}",
            response.getId(), response.getCountedQty());

        return ResponseEntity.ok(response);
    }

    /**
     * Finish counting
     *
     * POST /api/stocktake/tasks/{id}/finish
     *
     * Permission: stocktake:count
     *
     * Path Variable: id (Task ID)
     * Returns: StocktakeTaskResponse
     */
    @PostMapping("/tasks/{id}/finish")
    @PreAuthorize("hasAnyAuthority('stocktake:count', 'SUPER_ADMIN')")
    public ResponseEntity<StocktakeTaskResponse> finishCounting(@PathVariable Long id) {
        log.info("API调用: finishCounting - id: {}", id);

        StocktakeTaskResponse response = stocktakeService.finishCounting(id);

        log.info("API响应: finishCounting - 任务号: {}, 状态: {}",
            response.getTaskNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Get stocktake items for review (with snapshot qty and difference)
     *
     * GET /api/stocktake/tasks/{id}/review-items
     *
     * Permission: stocktake:review
     *
     * Path Variable: id (Task ID)
     * Returns: List<StocktakeItemDetailResponse>
     */
    @GetMapping("/tasks/{id}/review-items")
    @PreAuthorize("hasAnyAuthority('stocktake:review', 'SUPER_ADMIN')")
    public ResponseEntity<List<StocktakeItemDetailResponse>> getStocktakeItemsForReview(@PathVariable Long id) {
        log.info("API调用: getStocktakeItemsForReview - taskId: {}", id);

        List<StocktakeItemDetailResponse> responses = stocktakeService.getStocktakeItemsForReview(id);

        log.info("API响应: getStocktakeItemsForReview - 明细数: {}", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Review stocktake result
     *
     * POST /api/stocktake/tasks/{id}/review
     *
     * Permission: stocktake:review
     *
     * Path Variable: id (Task ID)
     * Request Body: ReviewStocktakeRequest
     * Returns: StocktakeTaskResponse
     */
    @PostMapping("/tasks/{id}/review")
    @PreAuthorize("hasAnyAuthority('stocktake:review', 'SUPER_ADMIN')")
    public ResponseEntity<StocktakeTaskResponse> reviewStocktake(
        @PathVariable Long id,
        @Valid @RequestBody ReviewStocktakeRequest request,
        Authentication authentication
    ) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        log.info("API调用: reviewStocktake - id: {}, approved: {}, 审核人: {}",
            id, request.getApproved(), user.getUsername());

        StocktakeTaskResponse response = stocktakeService.reviewStocktake(
            id,
            request,
            user.getId(),
            user.getUsername()
        );

        log.info("API响应: reviewStocktake - 任务号: {}, 状态: {}",
            response.getTaskNo(), response.getStatus());

        return ResponseEntity.ok(response);
    }
}
