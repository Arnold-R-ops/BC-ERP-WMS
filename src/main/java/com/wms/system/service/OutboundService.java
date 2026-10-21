package com.wms.system.service;

import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.BatchTrackingMode;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.inventory.InventoryPostingCommand;
import com.wms.system.service.inventory.InventoryPostingService;
import com.wms.system.service.inventory.PostingContext;
import com.wms.system.tenant.context.CompanyScope;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outbound Service (出库服务)
 *
 * V3.7 Architecture: Physical Dispatch Confirmation and Inventory Deduction
 *
 * Core Responsibilities:
 * 1. Confirm physical dispatch for one task (confirmDispatch)
 * 2. Atomically confirm dispatch for multiple tasks (batchConfirmDispatch)
 * 3. Consume any sales reservation
 * 4. Post inventory, ledger and Outbox through InventoryPostingService
 * 5. Check order completion status (checkOrderCompletion)
 *
 * Business Flow:
 * 1. Validate task exists and status is valid (PENDING or PICKING)
 * 2. Validate actualQty <= planQty (cannot exceed planned quantity)
 * 3. Update task status to COMPLETED
 * 4. Deduct inventory from batch (with optimistic locking)
 * 5. Record stock transaction (source_type = SALE_OUT)
 * 6. Check if all tasks for the order are completed
 * 7. Rebuild the order's warehouse and commercial projections
 *
 * Important Notes:
 * - This is where actual inventory deduction happens (not in AllocationService)
 * - Use optimistic locking on InventoryBatch to prevent overselling
 * - All operations are transactional and atomic
 * - Stock transactions are recorded for audit trail
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundService {

    private final OutboundTaskRepository outboundTaskRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final ProductSkuRepository productSkuRepository;
    private final LocationRepository locationRepository;
    private final LocationOccupancyService locationOccupancyService;
    private final SalesOrderStatusProjectionService statusProjectionService;
    private final InventoryPostingService inventoryPostingService;

    /**
     * ⭐ Confirm Physical Dispatch for Single Task (确认单个任务发货)
     *
     * Business Flow:
     * 1. Validate task exists
     * 2. Validate task status = PENDING or PICKING (use canConfirmDispatch())
     * 3. Validate actualQty <= planQty (cannot exceed planned quantity)
     * 4. Update task:
     *    - Set actual_qty = actualQty
     *    - Set status = COMPLETED
     *    - Set picked_by = operatorId
     *    - Set picked_at = LocalDateTime.now()
     * 5. Deduct Inventory:
     *    - Update inventory_batch.quantity -= actualQty
     *    - If batch quantity reaches 0, set active = false
     * 6. Record Stock Transaction:
     *    - Create StockTransaction record
     *    - Set source_type = "SALE_OUT"
     *    - Set source_order_id = sales_order_no
     *    - Set transaction_type = "OUT"
     *    - Set quantity = actualQty
     *    - Set operator_id and operator_name
     * 7. Check Order Completion:
     *    - Rebuild warehouse status and complete the commercial order when all effective quantity shipped
     * 8. Return OutboundTaskResponse
     *
     * @param taskId Task ID
     * @param actualQty Actual picked quantity
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @return OutboundTaskResponse Task response DTO
     * @throws BusinessException if task not found, invalid status, or insufficient stock
     */
    public OutboundTaskResponse confirmDispatch(
        Long taskId,
        Integer actualQty,
        Long operatorId,
        String operatorName
    ) {
        return confirmDispatch(taskId, actualQty, null, null, null, operatorId, operatorName);
    }

    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        retryFor = {OptimisticLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public OutboundTaskResponse confirmDispatch(
        Long taskId,
        Integer actualQty,
        Long scannedLocationId,
        String skuCode,
        String scannedBatchCode,
        Long operatorId,
        String operatorName
    ) {
        return confirmDispatch(taskId, actualQty, scannedLocationId, skuCode, scannedBatchCode,
            operatorId, operatorName, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public OutboundTaskResponse confirmDispatch(Long taskId, Integer actualQty, Long scannedLocationId,
        String skuCode, String scannedBatchCode, Long operatorId, String operatorName,
        StockPackaging.Selection packagingSelection) {
        log.info("Confirming dispatch: taskId={}, actualQty={}, operatorId={}, operatorName={}",
            taskId, actualQty, operatorId, operatorName);

        // 1. Validate task exists
        OutboundTask task = outboundTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.OUTBOUND_TASK_NOT_FOUND,
                Map.of("taskId", taskId)
            ));

        // 2. Validate task status (must be PENDING or PICKING)
        if (!task.canConfirmDispatch()) {
            log.error("Invalid task status: taskId={}, currentStatus={}", taskId, task.getStatus());

            if (task.getStatus() == OutboundTaskStatus.COMPLETED) {
                throw new BusinessException(
                    ErrorKeys.OUTBOUND_TASK_ALREADY_COMPLETED,
                    Map.of("taskId", taskId)
                );
            }

            throw new BusinessException(
                ErrorKeys.OUTBOUND_TASK_INVALID_STATUS,
                Map.of(
                    "taskId", taskId,
                    "currentStatus", task.getStatus().name(),
                    "requiredStatus", "PENDING or PICKING"
                )
            );
        }

        // 3. Validate actualQty <= planQty
        if (actualQty == null || actualQty <= 0) {
            throw new BusinessException(
                ErrorKeys.STOCK_INVALID_QUANTITY,
                Map.of(
                    "quantity", actualQty == null ? "null" : actualQty,
                    "operationType", "DISPATCH_CONFIRMATION"
                )
            );
        }

        if (actualQty > task.getPlanQty()) {
            log.error("Actual quantity exceeds plan quantity: taskId={}, planQty={}, actualQty={}",
                taskId, task.getPlanQty(), actualQty);

            throw new BusinessException(
                ErrorKeys.OUTBOUND_ACTUAL_QTY_EXCEEDS_PLAN,
                Map.of(
                    "taskId", taskId,
                    "planQty", task.getPlanQty(),
                    "actualQty", actualQty
                )
            );
        }

        LockedDispatch locked = lockDispatchResources(task);
        task = locked.task();

        // Revalidate after acquiring the authoritative batch/reservation/task locks.
        if (!task.canConfirmDispatch()) {
            if (task.getStatus() == OutboundTaskStatus.COMPLETED) {
                throw new BusinessException(
                    ErrorKeys.OUTBOUND_TASK_ALREADY_COMPLETED,
                    Map.of("taskId", taskId)
                );
            }
            throw new BusinessException(
                ErrorKeys.OUTBOUND_TASK_INVALID_STATUS,
                Map.of(
                    "taskId", taskId,
                    "currentStatus", task.getStatus().name(),
                    "requiredStatus", "PENDING or PICKING"
                )
            );
        }
        verifyPickingIdentity(task, locked.batch(), scannedLocationId, skuCode, scannedBatchCode);
        requireDispatchableQuantity(locked.batch(), actualQty);
        if (locked.batch().getStocktakeTaskId() != null) throw new BusinessException("PACKAGING_STOCKTAKE_LOCKED");
        StockPackaging packagingAfter = null;
        if (locked.batch().getPackaging() != null) {
            if (packagingSelection == null && actualQty.equals(locked.batch().getQuantity())) {
                packagingAfter = StockPackaging.empty(locked.batch().getPackaging().packSize());
            } else {
                packagingAfter = locked.batch().getPackaging()
                    .take(packagingSelection, locked.batch().getVersion(), actualQty).remaining();
            }
        } else if (packagingSelection != null) {
            throw new BusinessException("PACKAGING_VERIFY_REQUIRED");
        }

        // 4. Update task
        task.setActualQty(actualQty);
        task.setStatus(OutboundTaskStatus.COMPLETED);
        task.setPickedBy(operatorId);
        task.setPickedAt(LocalDateTime.now());

        OutboundTask savedTask = outboundTaskRepository.save(task);

        log.info("Task updated: taskId={}, status={}, actualQty={}, pickedBy={}, pickedAt={}",
            savedTask.getId(), savedTask.getStatus(), savedTask.getActualQty(),
            savedTask.getPickedBy(), savedTask.getPickedAt());

        // 5-6. Consume any reservation and post quantity/ledger/Outbox atomically.
        postDispatch(locked, actualQty, operatorId, operatorName, packagingAfter);

        updateSalesOrderItemShipment(task.getSalesOrderItemId(), actualQty);

        // 7. Check order completion
        checkOrderCompletion(task.getSalesOrderId());

        // 8. Return response
        return convertToResponse(savedTask);
    }

    private void verifyPickingIdentity(
        OutboundTask task,
        InventoryBatch batch,
        Long scannedLocationId,
        String skuCode,
        String scannedBatchCode
    ) {
        ProductSku product = batch.getProductSku();
        if (product.getBatchTrackingMode() == BatchTrackingMode.LOCATION_VISUAL) {
            if (scannedLocationId != null && !scannedLocationId.equals(task.getLocationId())) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                        "field", "locationId",
                        "expectedLocationId", task.getLocationId(),
                        "actualLocationId", scannedLocationId
                    )
                );
            }

            if (skuCode != null && !skuCode.isBlank()
                && !skuCode.equals(product.getSkuCode())) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                        "field", "skuCode",
                        "value", skuCode,
                        "expectedSkuCode", product.getSkuCode(),
                        "expectedProductSkuId", product.getId()
                    )
                );
            }

            Long locationId = scannedLocationId == null ? task.getLocationId() : scannedLocationId;
            InventoryBatch resolved = locationOccupancyService.resolveSingleVisualBatch(locationId, product.getId());
            if (!resolved.getId().equals(task.getAssignedBatchId())) {
                throw new BusinessException(
                    ErrorKeys.LOCATION_VISUAL_BATCH_AMBIGUOUS,
                    Map.of(
                        "locationId", locationId,
                        "resolvedBatchId", resolved.getId(),
                        "taskBatchId", task.getAssignedBatchId()
                    )
                );
            }
            return;
        }

        if (scannedBatchCode != null && !scannedBatchCode.isBlank()
            && !scannedBatchCode.equals(batch.getBatchCode())) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "batchCode", "value", scannedBatchCode, "expectedBatchCode", batch.getBatchCode())
            );
        }
    }

    /**
     * ⭐ Batch Confirm Dispatch (批量确认发货)
     *
     * Business Flow:
     * - For each taskId, call confirmDispatch with planQty as actualQty
     * - Use @Transactional to ensure atomicity
     * - Return List<OutboundTaskResponse>
     *
     * @param taskIds List of task IDs
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @return List<OutboundTaskResponse> List of task responses
     * @throws BusinessException if any task fails validation
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OutboundTaskResponse> batchConfirmDispatch(
        List<Long> taskIds,
        Long operatorId,
        String operatorName
    ) {
        log.info("Batch confirming dispatch: taskIds={}, operatorId={}, operatorName={}",
            taskIds, operatorId, operatorName);

        if (taskIds == null || taskIds.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.PARAMETER_REQUIRED,
                Map.of("parameter", "taskIds")
            );
        }

        List<BatchDispatchTarget> targets = new ArrayList<>();
        for (int index = 0; index < taskIds.size(); index++) {
            Long taskId = taskIds.get(index);
            OutboundTask task = outboundTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.OUTBOUND_TASK_NOT_FOUND,
                    Map.of("taskId", taskId)
                ));
            targets.add(new BatchDispatchTarget(
                index,
                task.getId(),
                task.getAssignedBatchId(),
                task.getReservationId(),
                task.getSalesOrderId(),
                task.getPlanQty()
            ));
        }

        // Serialize every affected sales order first. This keeps dispatch in
        // the same leading lock order as order cancellation/closure and avoids
        // an order-vs-inventory deadlock across multi-order batches.
        targets.stream()
            .map(BatchDispatchTarget::salesOrderId)
            .distinct()
            .sorted()
            .forEach(orderId -> salesOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.SALES_ORDER_NOT_FOUND,
                    Map.of("salesOrderId", orderId)
                )));

        // After the order locks, every batch request acquires remaining locks
        // in the same batch/reservation/task order.
        targets.sort(Comparator
            .comparing(BatchDispatchTarget::batchId)
            .thenComparing(BatchDispatchTarget::reservationId,
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(BatchDispatchTarget::taskId));
        OutboundTaskResponse[] orderedResponses = new OutboundTaskResponse[taskIds.size()];

        for (BatchDispatchTarget target : targets) {
            OutboundTaskResponse response = confirmDispatch(
                target.taskId(),
                target.planQty(),
                operatorId,
                operatorName
            );
            orderedResponses[target.originalIndex()] = response;
        }

        List<OutboundTaskResponse> responses = java.util.Arrays.asList(orderedResponses);

        log.info("Batch confirm completed: totalTasks={}, operatorId={}", responses.size(), operatorId);

        return responses;
    }

    private void consumeReservation(
        OutboundTask task,
        InventoryBatch batch,
        InventoryReservation reservation,
        Integer actualQty
    ) {
        if (!reservation.hasOpenQuantity() || reservation.getOpenQty() < actualQty) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_RESERVATION_INVALID_STATUS,
                Map.of(
                    "reservationId", reservation.getId(),
                    "status", reservation.getStatus().name(),
                    "openQty", reservation.getOpenQty(),
                    "requestedQty", actualQty
                )
            );
        }

        if (!reservation.getInventoryBatchId().equals(task.getAssignedBatchId())
            || !reservation.getSalesOrderId().equals(task.getSalesOrderId())
            || !reservation.getSalesOrderItemId().equals(task.getSalesOrderItemId())
            || !reservation.getProductSkuId().equals(batch.getProductSku().getId())
            || !reservation.getLocationId().equals(task.getLocationId())) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_RESERVATION_INVALID_STATUS,
                Map.of("reservationId", reservation.getId(), "taskId", task.getId())
            );
        }

        batch.releaseReservedQuantity(actualQty);
        inventoryBatchRepository.saveAndFlush(batch);
        reservation.consume(actualQty);
        inventoryReservationRepository.save(reservation);

        log.info("Reservation prepared for dispatch posting: reservationId={}, batchId={}, consumed={}, status={}",
            reservation.getId(), batch.getId(), actualQty, reservation.getStatus());
    }

    private LockedDispatch lockDispatchResources(OutboundTask snapshot) {
        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(snapshot.getSalesOrderId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", snapshot.getSalesOrderId())
            ));
        InventoryBatch batch = inventoryBatchRepository.findByIdForPosting(snapshot.getAssignedBatchId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", snapshot.getAssignedBatchId())
            ));

        InventoryReservation reservation = null;
        if (snapshot.getReservationId() != null) {
            reservation = inventoryReservationRepository.findByIdForUpdate(snapshot.getReservationId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.INVENTORY_RESERVATION_NOT_FOUND,
                    Map.of("reservationId", snapshot.getReservationId())
                ));
        }

        OutboundTask task = outboundTaskRepository.findByIdForUpdate(snapshot.getId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.OUTBOUND_TASK_NOT_FOUND,
                Map.of("taskId", snapshot.getId())
            ));
        if (!task.getAssignedBatchId().equals(batch.getId())
            || !java.util.Objects.equals(task.getReservationId(), snapshot.getReservationId())) {
            throw new BusinessException(
                ErrorKeys.STOCK_CONCURRENCY_CONFLICT,
                Map.of("taskId", task.getId(), "reason", "DISPATCH_ASSIGNMENT_CHANGED")
            );
        }
        return new LockedDispatch(task, batch, reservation, salesOrder);
    }

    private void requireDispatchableQuantity(InventoryBatch batch, Integer quantity) {
        if (!batch.getActive()) {
            throw new BusinessException(
                ErrorKeys.BATCH_INACTIVE,
                Map.of("batchId", batch.getId(), "batchCode", batch.getBatchCode())
            );
        }
        requireSaleable(batch);
        if (batch.getQuantity() < quantity) {
            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "batchId", batch.getId(),
                    "batchCode", batch.getBatchCode(),
                    "availableQuantity", batch.getQuantity(),
                    "requestedQuantity", quantity,
                    "shortage", quantity - batch.getQuantity()
                )
            );
        }
    }

    private void postDispatch(
        LockedDispatch locked,
        Integer actualQty,
        Long operatorId,
        String operatorName,
        StockPackaging packagingAfter
    ) {
        OutboundTask task = locked.task();
        InventoryBatch batch = locked.batch();
        if (locked.reservation() != null) {
            consumeReservation(task, batch, locked.reservation(), actualQty);
        }

        SalesOrder salesOrder = locked.salesOrder();
        Long warehouseId = batch.getLocation() == null || batch.getLocation().getWarehouse() == null
            ? null
            : batch.getLocation().getWarehouse().getId();
        if (warehouseId == null) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_NOT_FOUND,
                Map.of("batchId", batch.getId())
            );
        }

        String postingId = "SALE-OUT:" + task.getId();
        inventoryPostingService.post(new InventoryPostingCommand(
            batch.getId(),
            batch.getProductSku().getId(),
            task.getLocationId(),
            -actualQty,
            new PostingContext(
                CompanyScope.currentCompanyId(),
                warehouseId,
                SourceType.SALE_OUT,
                salesOrder.getOrderNo(),
                "SALE_DISPATCH",
                String.format("销售出库 - 订单号: %s", salesOrder.getOrderNo()),
                operatorId,
                operatorName,
                postingId,
                "sale-out-task:" + task.getId()
            ), packagingAfter
        ));
        locationOccupancyService.refreshLocationStatus(batch.getLocation().getId());
    }

    private void requireSaleable(InventoryBatch batch) {
        if (!batch.isSaleable()) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_BATCH_NOT_SALEABLE,
                Map.of(
                    "batchId", batch.getId(),
                    "batchCode", batch.getBatchCode(),
                    "qualityStatus", batch.getQualityStatus().name(),
                    "expired", batch.isExpired()
                )
            );
        }
    }

    private void updateSalesOrderItemShipment(Long salesOrderItemId, Integer actualQty) {
        SalesOrderItem item = salesOrderItemRepository.findById(salesOrderItemId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_ITEM_NOT_FOUND,
                Map.of("itemId", salesOrderItemId)
            ));

        int shippedQty = (item.getShippedQty() == null ? 0 : item.getShippedQty()) + actualQty;
        item.setShippedQty(shippedQty);
        salesOrderItemRepository.save(item);
    }

    /**
     * Check Order Completion (检查订单是否完成)
     *
     * Business Logic:
     * - Query all tasks for the sales order
     * - Rebuild all derived sales status layers from authoritative facts
     *
     * @param salesOrderId Sales order ID
     */
    private void checkOrderCompletion(Long salesOrderId) {
        log.info("Checking order completion: salesOrderId={}", salesOrderId);

        SalesOrder projected = statusProjectionService.refresh(salesOrderId);
        log.info("Sales order projection refreshed after dispatch: salesOrderId={}, commercialStatus={}, warehouseStatus={}",
            salesOrderId, projected.getCommercialStatus(), projected.getWarehouseStatus());
    }

    /**
     * Convert Entity to Response DTO (实体转换为响应DTO)
     *
     * @param task Outbound task entity
     * @return OutboundTaskResponse Response DTO
     */
    private OutboundTaskResponse convertToResponse(OutboundTask task) {
        // Fetch related entities
        InventoryBatch batch = inventoryBatchRepository.findById(task.getAssignedBatchId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", task.getAssignedBatchId())
            ));

        SalesOrder salesOrder = salesOrderRepository.findById(task.getSalesOrderId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", task.getSalesOrderId())
            ));

        ProductSku product = batch.getProductSku();
        Location location = batch.getLocation();

        return OutboundTaskResponse.builder()
            .id(task.getId())
            .salesOrderId(task.getSalesOrderId())
            .salesOrderNo(salesOrder.getOrderNo())
            .salesOrderItemId(task.getSalesOrderItemId())
            .assignedBatchId(task.getAssignedBatchId())
            .reservationId(task.getReservationId())
            .batchCode(batch.getBatchCode())
            .locationId(task.getLocationId())
            .locationCode(location.getLocationCode())
            .warehouseId(location.getWarehouse() == null ? null : location.getWarehouse().getId())
            .productSkuId(product.getId())
            .productSkuCode(product.getSkuCode())
            .productName(product.getName())
            .productBarcode(product.getBarcode())
            .batchTrackingMode(product.getBatchTrackingMode() == null
                ? null
                : product.getBatchTrackingMode().name())
            .planQty(task.getPlanQty())
            .actualQty(task.getActualQty())
            .status(task.getStatus().name())
            .statusDescription(task.getStatus().getDescription())
            .pickedBy(task.getPickedBy())
            .pickedAt(task.getPickedAt())
            .remark(task.getRemark())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }

    /**
     * Get outbound task by ID
     *
     * @param id Task ID
     * @return OutboundTaskResponse
     * @throws BusinessException if task not found
     */
    @Transactional(readOnly = true)
    public OutboundTaskResponse getOutboundTask(Long id) {
        log.debug("Getting outbound task by id: {}", id);

        OutboundTask task = outboundTaskRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.OUTBOUND_TASK_NOT_FOUND,
                Map.of("taskId", id)
            ));

        return convertToResponse(task);
    }

    /**
     * List outbound tasks with optional filters
     *
     * @param salesOrderId Sales order ID (optional)
     * @param status Task status (optional)
     * @return List of outbound tasks
     */
    @Transactional(readOnly = true)
    public List<OutboundTaskResponse> listOutboundTasks(Long salesOrderId, String status) {
        log.debug("Listing outbound tasks: salesOrderId={}, status={}", salesOrderId, status);

        List<OutboundTask> tasks;

        if (salesOrderId != null && status != null) {
            // Filter by both salesOrderId and status
            OutboundTaskStatus taskStatus = OutboundTaskStatus.valueOf(status);
            tasks = outboundTaskRepository.findBySalesOrderIdAndStatus(salesOrderId, taskStatus);
        } else if (salesOrderId != null) {
            // Filter by salesOrderId only
            tasks = outboundTaskRepository.findBySalesOrderId(salesOrderId);
        } else if (status != null) {
            // Filter by status only
            OutboundTaskStatus taskStatus = OutboundTaskStatus.valueOf(status);
            tasks = outboundTaskRepository.findByStatus(taskStatus);
        } else {
            // No filter, return all
            tasks = outboundTaskRepository.findAll();
        }

        return sortTasksForPickingRoute(tasks).stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    private List<OutboundTask> sortTasksForPickingRoute(List<OutboundTask> tasks) {
        if (tasks.size() <= 1) {
            return tasks;
        }

        Set<Long> locationIds = tasks.stream()
            .map(OutboundTask::getLocationId)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(HashSet::new));

        Map<Long, Location> locations = new HashMap<>();
        locationRepository.findAllById(locationIds).forEach(location -> locations.put(location.getId(), location));

        List<OutboundTask> remaining = new ArrayList<>(tasks);
        List<OutboundTask> sorted = new ArrayList<>();
        int currentX = 0;
        int currentY = 0;

        while (!remaining.isEmpty()) {
            final int x = currentX;
            final int y = currentY;
            OutboundTask next = remaining.stream()
                .min(Comparator
                    .comparingInt((OutboundTask task) -> distanceFrom(locations.get(task.getLocationId()), x, y))
                    .thenComparing(OutboundTask::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(remaining.get(0));

            remaining.remove(next);
            sorted.add(next);

            Location nextLocation = locations.get(next.getLocationId());
            currentX = nextLocation == null || nextLocation.getPosX() == null ? currentX : nextLocation.getPosX();
            currentY = nextLocation == null || nextLocation.getPosY() == null ? currentY : nextLocation.getPosY();
        }

        return sorted;
    }

    private int distanceFrom(Location location, int x, int y) {
        if (location == null) {
            return Integer.MAX_VALUE;
        }
        int locationX = location.getPosX() == null ? 0 : location.getPosX();
        int locationY = location.getPosY() == null ? 0 : location.getPosY();
        return Math.abs(locationX - x) + Math.abs(locationY - y);
    }

    private record LockedDispatch(
        OutboundTask task,
        InventoryBatch batch,
        InventoryReservation reservation,
        SalesOrder salesOrder
    ) {
    }

    private record BatchDispatchTarget(
        int originalIndex,
        Long taskId,
        Long batchId,
        Long reservationId,
        Long salesOrderId,
        Integer planQty
    ) {
    }
}
