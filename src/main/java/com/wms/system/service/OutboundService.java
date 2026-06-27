package com.wms.system.service;

import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.FulfillmentStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Outbound Service (出库服务)
 *
 * V3.7 Architecture: Warehouse Picking Confirmation and Inventory Deduction
 *
 * Core Responsibilities:
 * 1. Confirm picking for single task (confirmPicking)
 * 2. Batch confirm picking for multiple tasks (batchConfirmPicking)
 * 3. Deduct inventory from batches (deductInventory)
 * 4. Record stock transactions (recordStockTransaction)
 * 5. Check order completion status (checkOrderCompletion)
 *
 * Business Flow:
 * 1. Validate task exists and status is valid (PENDING or PICKING)
 * 2. Validate actualQty <= planQty (cannot exceed planned quantity)
 * 3. Update task status to COMPLETED
 * 4. Deduct inventory from batch (with optimistic locking)
 * 5. Record stock transaction (source_type = SALE_OUT)
 * 6. Check if all tasks for the order are completed
 * 7. If all completed, update sales order status to SHIPPED
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
    private final StockTransactionRepository stockTransactionRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    /**
     * ⭐ Confirm Picking for Single Task (确认单个拣货任务)
     *
     * Business Flow:
     * 1. Validate task exists
     * 2. Validate task status = PENDING or PICKING (use canConfirmPicking())
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
     *    - If all tasks for the sales order are COMPLETED, update sales_order.status = SHIPPED
     * 8. Return OutboundTaskResponse
     *
     * @param taskId Task ID
     * @param actualQty Actual picked quantity
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     * @return OutboundTaskResponse Task response DTO
     * @throws BusinessException if task not found, invalid status, or insufficient stock
     */
    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        retryFor = {OptimisticLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    public OutboundTaskResponse confirmPicking(
        Long taskId,
        Integer actualQty,
        Long operatorId,
        String operatorName
    ) {
        log.info("Confirming picking: taskId={}, actualQty={}, operatorId={}, operatorName={}",
            taskId, actualQty, operatorId, operatorName);

        // 1. Validate task exists
        OutboundTask task = outboundTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.OUTBOUND_TASK_NOT_FOUND,
                Map.of("taskId", taskId)
            ));

        // 2. Validate task status (must be PENDING or PICKING)
        if (!task.canConfirmPicking()) {
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
                    "operationType", "PICKING_CONFIRMATION"
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

        // 4. Update task
        task.setActualQty(actualQty);
        task.setStatus(OutboundTaskStatus.COMPLETED);
        task.setPickedBy(operatorId);
        task.setPickedAt(LocalDateTime.now());

        OutboundTask savedTask = outboundTaskRepository.save(task);

        log.info("Task updated: taskId={}, status={}, actualQty={}, pickedBy={}, pickedAt={}",
            savedTask.getId(), savedTask.getStatus(), savedTask.getActualQty(),
            savedTask.getPickedBy(), savedTask.getPickedAt());

        // 5. Deduct inventory or consume the V4.5 reservation
        if (task.getReservationId() != null) {
            consumeReservation(task, actualQty);
        } else {
            deductInventory(task.getAssignedBatchId(), actualQty);
        }

        // 6. Record stock transaction
        recordStockTransaction(task, actualQty, operatorId, operatorName);

        updateSalesOrderItemShipment(task.getSalesOrderItemId(), actualQty);

        // 7. Check order completion
        checkOrderCompletion(task.getSalesOrderId());

        // 8. Return response
        return convertToResponse(savedTask);
    }

    /**
     * ⭐ Batch Confirm Picking (批量确认拣货)
     *
     * Business Flow:
     * - For each taskId, call confirmPicking with planQty as actualQty
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
    public List<OutboundTaskResponse> batchConfirmPicking(
        List<Long> taskIds,
        Long operatorId,
        String operatorName
    ) {
        log.info("Batch confirming picking: taskIds={}, operatorId={}, operatorName={}",
            taskIds, operatorId, operatorName);

        if (taskIds == null || taskIds.isEmpty()) {
            throw new BusinessException(
                ErrorKeys.PARAMETER_REQUIRED,
                Map.of("parameter", "taskIds")
            );
        }

        List<OutboundTaskResponse> responses = new ArrayList<>();

        for (Long taskId : taskIds) {
            // Get task to retrieve planQty
            OutboundTask task = outboundTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.OUTBOUND_TASK_NOT_FOUND,
                    Map.of("taskId", taskId)
                ));

            // Confirm picking with planQty as actualQty
            OutboundTaskResponse response = confirmPicking(
                taskId,
                task.getPlanQty(),
                operatorId,
                operatorName
            );

            responses.add(response);
        }

        log.info("Batch confirm completed: totalTasks={}, operatorId={}", responses.size(), operatorId);

        return responses;
    }

    /**
     * Deduct Inventory from Batch (扣减批次库存)
     *
     * Business Logic:
     * - Update inventory_batch.quantity -= quantity
     * - If batch quantity reaches 0, set active = false
     * - Use optimistic locking to prevent overselling
     *
     * @param batchId Batch ID
     * @param quantity Quantity to deduct
     * @throws BusinessException if batch not found or insufficient stock
     */
    private void deductInventory(Long batchId, Integer quantity) {
        log.info("Deducting inventory: batchId={}, quantity={}", batchId, quantity);

        // 1. Query batch
        InventoryBatch batch = inventoryBatchRepository.findById(batchId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", batchId)
            ));

        // 2. Validate batch is active
        if (!batch.getActive()) {
            log.error("Batch is inactive: batchId={}, batchCode={}", batchId, batch.getBatchCode());

            throw new BusinessException(
                ErrorKeys.BATCH_INACTIVE,
                Map.of(
                    "batchId", batchId,
                    "batchCode", batch.getBatchCode()
                )
            );
        }

        // 3. Validate sufficient stock
        if (batch.getQuantity() < quantity) {
            log.error("Insufficient batch stock: batchId={}, batchCode={}, available={}, requested={}",
                batchId, batch.getBatchCode(), batch.getQuantity(), quantity);

            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "batchId", batchId,
                    "batchCode", batch.getBatchCode(),
                    "availableQuantity", batch.getQuantity(),
                    "requestedQuantity", quantity,
                    "shortage", quantity - batch.getQuantity()
                )
            );
        }

        // 4. Deduct quantity
        Integer quantityBefore = batch.getQuantity();
        batch.decreaseQuantity(quantity);  // This method throws exception if insufficient

        // 5. If quantity reaches 0, set active = false
        if (batch.getQuantity() == 0) {
            batch.setActive(false);
            log.info("Batch exhausted, setting active=false: batchId={}, batchCode={}",
                batchId, batch.getBatchCode());
        }

        // 6. Save batch
        InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

        log.info("Inventory deducted: batchId={}, batchCode={}, before={}, after={}, deducted={}, active={}",
            savedBatch.getId(), savedBatch.getBatchCode(), quantityBefore,
            savedBatch.getQuantity(), quantity, savedBatch.getActive());
    }

    private void consumeReservation(OutboundTask task, Integer actualQty) {
        InventoryReservation reservation = inventoryReservationRepository.findById(task.getReservationId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.INVENTORY_RESERVATION_NOT_FOUND,
                Map.of("reservationId", task.getReservationId())
            ));

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

        InventoryBatch batch = inventoryBatchRepository.findById(reservation.getInventoryBatchId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", reservation.getInventoryBatchId())
            ));

        if (!batch.getActive()) {
            throw new BusinessException(
                ErrorKeys.BATCH_INACTIVE,
                Map.of("batchId", batch.getId(), "batchCode", batch.getBatchCode())
            );
        }

        Integer quantityBefore = batch.getQuantity();
        batch.consumeReservedQuantity(actualQty);
        if (batch.getQuantity() == 0) {
            batch.setActive(false);
        }

        reservation.consume(actualQty);
        inventoryBatchRepository.save(batch);
        inventoryReservationRepository.save(reservation);

        log.info("Reservation consumed: reservationId={}, batchId={}, before={}, after={}, consumed={}, status={}",
            reservation.getId(), batch.getId(), quantityBefore, batch.getQuantity(), actualQty,
            reservation.getStatus());
    }

    private void updateSalesOrderItemShipment(Long salesOrderItemId, Integer actualQty) {
        SalesOrderItem item = salesOrderItemRepository.findById(salesOrderItemId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_ITEM_NOT_FOUND,
                Map.of("itemId", salesOrderItemId)
            ));

        int shippedQty = (item.getShippedQty() == null ? 0 : item.getShippedQty()) + actualQty;
        item.setShippedQty(shippedQty);
        item.setFulfillmentStatus(shippedQty >= item.getQuantity()
            ? FulfillmentStatus.SHIPPED
            : FulfillmentStatus.PARTIALLY_SHIPPED);
        salesOrderItemRepository.save(item);
    }

    /**
     * Record Stock Transaction (记录库存流水)
     *
     * Business Logic:
     * - Create StockTransaction record
     * - Set source_type = "SALE_OUT"
     * - Set source_order_id = sales_order.order_no
     * - Set transaction_type = "OUT"
     * - Set quantity = actualQty
     * - Set operator_id and operator_name
     * - Set remark = "销售出库 - 订单号: {orderNo}"
     *
     * @param task Outbound task
     * @param actualQty Actual picked quantity
     * @param operatorId Operator user ID
     * @param operatorName Operator user name
     */
    private void recordStockTransaction(
        OutboundTask task,
        Integer actualQty,
        Long operatorId,
        String operatorName
    ) {
        log.info("Recording stock transaction: taskId={}, actualQty={}, operatorId={}",
            task.getId(), actualQty, operatorId);

        // 1. Get batch
        InventoryBatch batch = inventoryBatchRepository.findById(task.getAssignedBatchId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", task.getAssignedBatchId())
            ));

        // 2. Get sales order
        SalesOrder salesOrder = salesOrderRepository.findById(task.getSalesOrderId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", task.getSalesOrderId())
            ));

        // 3. Calculate quantity before and after
        // Note: At this point, batch quantity has already been deducted
        Integer quantityAfter = batch.getQuantity();
        Integer quantityBefore = quantityAfter + actualQty;

        // 4. Create stock transaction
        StockTransaction transaction = StockTransaction.builder()
            .product(batch.getProduct())
            .location(batch.getLocation())
            .transactionType(TransactionType.OUT)
            .sourceType(SourceType.SALE_OUT)
            .quantity(actualQty)
            .quantityBefore(quantityBefore)
            .quantityAfter(quantityAfter)
            .sourceOrderId(salesOrder.getOrderNo())
            .operatorId(operatorId)
            .operatorName(operatorName)
            .remark(String.format("销售出库 - 订单号: %s", salesOrder.getOrderNo()))
            .build();

        StockTransaction savedTransaction = stockTransactionRepository.save(transaction);

        log.info("Stock transaction recorded: transactionId={}, batchCode={}, quantity={}, sourceOrderId={}",
            savedTransaction.getId(), batch.getBatchCode(), actualQty, salesOrder.getOrderNo());
    }

    /**
     * Check Order Completion (检查订单是否完成)
     *
     * Business Logic:
     * - Query all tasks for the sales order
     * - If all tasks are COMPLETED, update sales_order.status = SHIPPED
     *
     * @param salesOrderId Sales order ID
     */
    private void checkOrderCompletion(Long salesOrderId) {
        log.info("Checking order completion: salesOrderId={}", salesOrderId);

        // 1. Get sales order
        SalesOrder salesOrder = salesOrderRepository.findById(salesOrderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", salesOrderId)
            ));

        // 2. Count total tasks and completed tasks
        long totalTasks = outboundTaskRepository.countTasksBySalesOrderId(salesOrderId);
        long completedTasks = outboundTaskRepository.countCompletedTasksBySalesOrderId(salesOrderId);

        log.info("Order completion status: salesOrderId={}, totalTasks={}, completedTasks={}",
            salesOrderId, totalTasks, completedTasks);

        // 3. If all tasks completed, update order status to SHIPPED
        if (totalTasks > 0 && completedTasks == totalTasks) {
            salesOrder.setStatus(SalesOrderStatus.SHIPPED);
            salesOrder.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
            salesOrder.setShippedAt(LocalDateTime.now());
            salesOrder.setFulfillmentVersion(
                salesOrder.getFulfillmentVersion() == null ? 1L : salesOrder.getFulfillmentVersion() + 1
            );
            SalesOrder savedOrder = salesOrderRepository.save(salesOrder);

            log.info("Order completed and shipped: salesOrderId={}, orderNo={}, status={}",
                savedOrder.getId(), savedOrder.getOrderNo(), savedOrder.getStatus());
        } else {
            if (completedTasks > 0) {
                salesOrder.setFulfillmentStatus(FulfillmentStatus.PARTIALLY_SHIPPED);
                salesOrder.setFulfillmentVersion(
                    salesOrder.getFulfillmentVersion() == null ? 1L : salesOrder.getFulfillmentVersion() + 1
                );
                salesOrderRepository.save(salesOrder);
            }
            log.info("Order not yet completed: salesOrderId={}, remaining tasks={}",
                salesOrderId, totalTasks - completedTasks);
        }
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

        Product product = batch.getProduct();
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
            .productId(product.getId())
            .productName(product.getName())
            .productBarcode(product.getBarcode())
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

        return tasks.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
}
