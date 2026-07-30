package com.wms.system.service;

import com.wms.system.dto.stocktake.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stocktake Service
 *
 * V3.8 Architecture: Smart Stocktake System
 *
 * Core Responsibilities:
 * 1. Create cycle stocktake tasks with smart batch selection
 * 2. Manage stocktake workflow (CREATED → COUNTING → REVIEWING → COMPLETED)
 * 3. Submit count results (blind count)
 * 4. Review and approve stocktake results
 * 5. Adjust inventory based on differences
 *
 * Smart Selection Logic:
 * - MONTHLY: Recent 30 days active batches + high-value products (max 500)
 * - QUARTERLY/ANNUAL: All active batches
 * - ADHOC: All active batches in specified warehouse
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeService {

    private final StocktakeTaskRepository stocktakeTaskRepository;
    private final StocktakeItemRepository stocktakeItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ProductSkuRepository productSkuRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;

    private static final int MONTHLY_MAX_BATCHES = 500;
    private static final int RECENT_DAYS_THRESHOLD = 30;

    /**
     * Create cycle stocktake task
     *
     * Smart Selection Logic:
     * 1. MONTHLY: Query batches with stock transactions in last 30 days (max 500)
     * 2. QUARTERLY/ANNUAL: Query all active batches
     * 3. ADHOC: Query all active batches in warehouse
     *
     * @param request Create request
     * @param createdBy Creator user ID
     * @param createdByName Creator user name
     * @return StocktakeTaskResponse
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse createCycleTask(
        CreateStocktakeTaskRequest request,
        Long createdBy,
        String createdByName
    ) {
        log.info("Creating stocktake task: warehouseId={}, cycleType={}, createdBy={}",
            request.getWarehouseId(), request.getCycleType(), createdByName);

        // 1. Validate warehouse exists
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.WAREHOUSE_NOT_FOUND,
                Map.of("warehouseId", request.getWarehouseId())
            ));

        // 2. Parse cycle type
        StocktakeCycleType cycleType;
        try {
            cycleType = StocktakeCycleType.valueOf(request.getCycleType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "cycleType", "value", request.getCycleType())
            );
        }

        // 3. Generate task number
        String taskNo = generateTaskNo(cycleType);

        // 4. Create snapshot time
        LocalDateTime snapshotTime = LocalDateTime.now();

        // 5. Select batches based on cycle type
        List<InventoryBatch> selectedBatches = selectBatchesForStocktake(
            request.getWarehouseId(),
            cycleType
        );

        log.info("Selected {} batches for stocktake: taskNo={}, cycleType={}",
            selectedBatches.size(), taskNo, cycleType);

        // 6. Create stocktake task
        StocktakeTask task = StocktakeTask.builder()
            .taskNo(taskNo)
            .warehouseId(request.getWarehouseId())
            .cycleType(cycleType)
            .status(StocktakeStatus.CREATED)
            .snapshotTime(snapshotTime)
            .totalItems(selectedBatches.size())
            .countedItems(0)
            .differenceItems(0)
            .createdBy(createdBy)
            .createdByName(createdByName)
            .build();

        StocktakeTask savedTask = stocktakeTaskRepository.save(task);

        // 7. Create stocktake items
        List<StocktakeItem> items = new ArrayList<>();
        for (InventoryBatch batch : selectedBatches) {
            StocktakeItem item = StocktakeItem.builder()
                .taskId(savedTask.getId())
                .productSkuId(batch.getProductSku().getId())
                .batchId(batch.getId())
                .locationId(batch.getLocation().getId())
                .snapshotQty(batch.getQuantity())
                .countedQty(null)
                .differenceQty(0)
                .isCounted(false)
                .build();
            items.add(item);
        }

        stocktakeItemRepository.saveAll(items);

        log.info("Stocktake task created successfully: taskNo={}, totalItems={}",
            savedTask.getTaskNo(), savedTask.getTotalItems());

        return convertToTaskResponse(savedTask);
    }

    /**
     * Start counting
     *
     * @param taskId Task ID
     * @return StocktakeTaskResponse
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse startCounting(Long taskId) {
        log.info("Starting stocktake counting: taskId={}", taskId);

        StocktakeTask task = getTaskById(taskId);

        if (!task.canStartCounting()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_CANNOT_START,
                Map.of("taskId", taskId, "currentStatus", task.getStatus().name())
            );
        }

        task.setStatus(StocktakeStatus.COUNTING);
        StocktakeTask savedTask = stocktakeTaskRepository.save(task);

        log.info("Stocktake counting started: taskNo={}", savedTask.getTaskNo());

        return convertToTaskResponse(savedTask);
    }

    /**
     * Submit count result (blind count)
     *
     * @param taskId Task ID
     * @param itemId Item ID
     * @param request Submit count request
     * @param countedBy Counter user ID
     * @param countedByName Counter user name
     * @return StocktakeItemResponse (without snapshot qty)
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeItemResponse submitCount(
        Long taskId,
        Long itemId,
        SubmitCountRequest request,
        Long countedBy,
        String countedByName
    ) {
        log.info("Submitting count: taskId={}, itemId={}, countedQty={}, countedBy={}",
            taskId, itemId, request.getCountedQty(), countedByName);

        // 1. Validate task status
        StocktakeTask task = getTaskById(taskId);
        if (!task.canCount()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_CANNOT_COUNT,
                Map.of("taskId", taskId, "currentStatus", task.getStatus().name())
            );
        }

        // 2. Get stocktake item
        StocktakeItem item = stocktakeItemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.STOCKTAKE_ITEM_NOT_FOUND,
                Map.of("itemId", itemId, "taskId", taskId)
            ));

        // 3. Validate item belongs to task
        if (!item.getTaskId().equals(taskId)) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_ITEM_NOT_FOUND,
                Map.of("itemId", itemId, "taskId", taskId)
            );
        }

        // 4. Update item
        boolean wasNotCounted = !item.getIsCounted();
        item.setCountedQty(request.getCountedQty());
        item.setDifferenceQty(item.calculateDifference());
        item.setIsCounted(true);
        item.setCountedBy(countedBy);
        item.setCountedByName(countedByName);
        item.setCountedAt(LocalDateTime.now());
        item.setRemark(request.getRemark());

        StocktakeItem savedItem = stocktakeItemRepository.save(item);

        // 5. Update task statistics
        if (wasNotCounted) {
            task.setCountedItems(task.getCountedItems() + 1);
        }
        if (savedItem.hasDifference()) {
            // Recalculate difference items count
            long differenceCount = stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(taskId, 0);
            task.setDifferenceItems((int) differenceCount);
        }

        // 6. Auto transition to REVIEWING if all items counted
        if (task.getCountedItems().equals(task.getTotalItems())) {
            task.setStatus(StocktakeStatus.REVIEWING);
            log.info("All items counted, auto transitioning to REVIEWING: taskNo={}", task.getTaskNo());
        }

        stocktakeTaskRepository.save(task);

        log.info("Count submitted successfully: itemId={}, countedQty={}, differenceQty={}",
            savedItem.getId(), savedItem.getCountedQty(), savedItem.getDifferenceQty());

        return convertToItemResponse(savedItem);
    }

    /**
     * Finish counting (manual transition to REVIEWING)
     *
     * @param taskId Task ID
     * @return StocktakeTaskResponse
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse finishCounting(Long taskId) {
        log.info("Finishing stocktake counting: taskId={}", taskId);

        StocktakeTask task = getTaskById(taskId);

        // The final count submission can auto-transition the task before the
        // mobile client sends its explicit finish request. Treat REVIEWING as
        // an idempotent success so a delayed or retried confirmation cannot
        // report a false failure after the state change has committed.
        if (task.getStatus() == StocktakeStatus.REVIEWING) {
            log.info("Stocktake already finished counting: taskNo={}", task.getTaskNo());
            return convertToTaskResponse(task);
        }

        if (!task.canCount()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_CANNOT_COUNT,
                Map.of("taskId", taskId, "currentStatus", task.getStatus().name())
            );
        }

        task.setStatus(StocktakeStatus.REVIEWING);
        StocktakeTask savedTask = stocktakeTaskRepository.save(task);

        log.info("Stocktake counting finished: taskNo={}", savedTask.getTaskNo());

        return convertToTaskResponse(savedTask);
    }

    /**
     * Review stocktake result
     *
     * @param taskId Task ID
     * @param request Review request
     * @param reviewedBy Reviewer user ID
     * @param reviewedByName Reviewer user name
     * @return StocktakeTaskResponse
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse reviewStocktake(
        Long taskId,
        ReviewStocktakeRequest request,
        Long reviewedBy,
        String reviewedByName
    ) {
        log.info("Reviewing stocktake: taskId={}, approved={}, reviewedBy={}",
            taskId, request.getApproved(), reviewedByName);

        StocktakeTask task = getTaskById(taskId);

        if (!task.canReview()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_CANNOT_REVIEW,
                Map.of("taskId", taskId, "currentStatus", task.getStatus().name())
            );
        }

        // Update review info
        task.setReviewedBy(reviewedBy);
        task.setReviewedByName(reviewedByName);
        task.setReviewedAt(LocalDateTime.now());
        task.setReviewComment(request.getComment());

        if (request.getApproved()) {
            // Approve: adjust inventory and complete task
            adjustInventory(taskId, reviewedByName);
            task.setStatus(StocktakeStatus.COMPLETED);
            log.info("Stocktake approved and completed: taskNo={}", task.getTaskNo());
        } else {
            // Reject: return to COUNTING status
            task.setStatus(StocktakeStatus.COUNTING);
            log.info("Stocktake rejected, returned to COUNTING: taskNo={}", task.getTaskNo());
        }

        StocktakeTask savedTask = stocktakeTaskRepository.save(task);

        return convertToTaskResponse(savedTask);
    }

    /**
     * Adjust inventory based on stocktake differences (private method)
     *
     * @param taskId Task ID
     */
    private void adjustInventory(Long taskId, String reviewedByName) {
        log.info("Adjusting inventory for stocktake: taskId={}", taskId);

        StocktakeTask task = getTaskById(taskId);

        // Get all items with differences
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(taskId);
        List<StocktakeItem> differenceItems = items.stream()
            .filter(StocktakeItem::hasDifference)
            .collect(Collectors.toList());

        log.info("Found {} items with differences", differenceItems.size());

        for (StocktakeItem item : differenceItems) {
            // Get batch
            InventoryBatch batch = inventoryBatchRepository.findById(item.getBatchId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.BATCH_NOT_FOUND,
                    Map.of("batchId", item.getBatchId())
                ));

            Integer quantityBefore = batch.getQuantity();
            Integer adjustment = item.getDifferenceQty();

            // Adjust batch quantity
            if (adjustment > 0) {
                batch.increaseQuantity(adjustment);
            } else {
                batch.decreaseQuantity(Math.abs(adjustment));
            }

            // If quantity becomes zero or negative, mark as inactive
            if (batch.getQuantity() <= 0) {
                batch.setActive(false);
                log.info("Batch marked as inactive due to zero quantity: batchCode={}", batch.getBatchCode());
            }

            InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

            // Create stock transaction
            TransactionType transactionType = adjustment > 0 ? TransactionType.IN : TransactionType.OUT;
            SourceType sourceType = adjustment > 0 ? SourceType.INVENTORY_GAIN : SourceType.INVENTORY_LOSS;

            StockTransaction transaction = StockTransaction.builder()
                .productSku(batch.getProductSku())
                .location(batch.getLocation())
                .transactionType(transactionType)
                .sourceType(sourceType)
                .quantity(Math.abs(adjustment))
                .quantityBefore(quantityBefore)
                .quantityAfter(savedBatch.getQuantity())
                .sourceOrderId(task.getTaskNo())
                .operatorId(task.getReviewedBy())
                .operatorName(reviewedByName != null ? reviewedByName : task.getReviewedByName())
                .remark(String.format("盘点调整 - 任务号: %s, 差异: %+d", task.getTaskNo(), adjustment))
                .build();

            stockTransactionRepository.save(transaction);

            log.info("Inventory adjusted: batchCode={}, before={}, after={}, adjustment={}",
                savedBatch.getBatchCode(), quantityBefore, savedBatch.getQuantity(), adjustment);
        }

        log.info("Inventory adjustment completed: taskId={}, adjustedItems={}", taskId, differenceItems.size());
    }

    /**
     * Get stocktake task by ID
     *
     * @param taskId Task ID
     * @return StocktakeTaskResponse
     */
    @Transactional(readOnly = true)
    public StocktakeTaskResponse getStocktakeTask(Long taskId) {
        StocktakeTask task = getTaskById(taskId);
        return convertToTaskResponse(task);
    }

    /**
     * List stocktake tasks
     *
     * @param warehouseId Warehouse ID (optional)
     * @param status Status (optional)
     * @return List<StocktakeTaskResponse>
     */
    @Transactional(readOnly = true)
    public List<StocktakeTaskResponse> listStocktakeTasks(Long warehouseId, String status) {
        List<StocktakeTask> tasks;

        if (warehouseId != null && status != null) {
            StocktakeStatus stocktakeStatus = StocktakeStatus.valueOf(status.toUpperCase());
            tasks = stocktakeTaskRepository.findByWarehouseId(warehouseId).stream()
                .filter(t -> t.getStatus() == stocktakeStatus)
                .collect(Collectors.toList());
        } else if (warehouseId != null) {
            tasks = stocktakeTaskRepository.findByWarehouseId(warehouseId);
        } else if (status != null) {
            StocktakeStatus stocktakeStatus = StocktakeStatus.valueOf(status.toUpperCase());
            tasks = stocktakeTaskRepository.findByStatus(stocktakeStatus);
        } else {
            tasks = stocktakeTaskRepository.findAll();
        }

        return tasks.stream()
            .map(this::convertToTaskResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get stocktake items (blind count version - no snapshot qty)
     *
     * @param taskId Task ID
     * @return List<StocktakeItemResponse>
     */
    @Transactional(readOnly = true)
    public List<StocktakeItemResponse> getStocktakeItems(Long taskId) {
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(taskId);
        return items.stream()
            .map(this::convertToItemResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get stocktake items for review (with snapshot qty and difference)
     *
     * @param taskId Task ID
     * @return List<StocktakeItemDetailResponse>
     */
    @Transactional(readOnly = true)
    public List<StocktakeItemDetailResponse> getStocktakeItemsForReview(Long taskId) {
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(taskId);
        return items.stream()
            .map(this::convertToItemDetailResponse)
            .collect(Collectors.toList());
    }

    // ========== Private Helper Methods ==========

    /**
     * Get task by ID (internal method)
     */
    private StocktakeTask getTaskById(Long taskId) {
        return stocktakeTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_NOT_FOUND,
                Map.of("taskId", taskId)
            ));
    }

    /**
     * Generate task number
     *
     * Format: TK-yyyyMM-{M|Q|A|X}序号
     * Example: TK-202601-M01, TK-202601-Q01
     */
    private String generateTaskNo(StocktakeCycleType cycleType) {
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String typeCode = switch (cycleType) {
            case MONTHLY -> "M";
            case QUARTERLY -> "Q";
            case ANNUAL -> "A";
            case ADHOC -> "X";
        };

        // Find next sequence number for this month and type
        String prefix = "TK-" + yearMonth + "-" + typeCode;
        List<StocktakeTask> existingTasks = stocktakeTaskRepository.findAll().stream()
            .filter(t -> t.getTaskNo().startsWith(prefix))
            .collect(Collectors.toList());

        int nextSeq = existingTasks.size() + 1;
        return String.format("%s%02d", prefix, nextSeq);
    }

    /**
     * Select batches for stocktake based on cycle type
     */
    private List<InventoryBatch> selectBatchesForStocktake(Long warehouseId, StocktakeCycleType cycleType) {
        if (cycleType == StocktakeCycleType.MONTHLY) {
            return selectBatchesForMonthly(warehouseId);
        } else {
            // QUARTERLY, ANNUAL, ADHOC: all active batches
            return selectBatchesForFullInventory(warehouseId);
        }
    }

    /**
     * Select batches for monthly stocktake
     * Strategy: Recent 30 days active batches (max 500)
     */
    private List<InventoryBatch> selectBatchesForMonthly(Long warehouseId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(RECENT_DAYS_THRESHOLD);

        // Get batches with recent transactions
        List<StockTransaction> recentTransactions = stockTransactionRepository.findAll().stream()
            .filter(t -> t.getCreatedAt().isAfter(thirtyDaysAgo))
            .collect(Collectors.toList());

        // Extract unique batch IDs from transactions
        List<Long> recentBatchProductSkuIds = recentTransactions.stream()
            .map(t -> t.getProductSku().getId())
            .distinct()
            .collect(Collectors.toList());

        // Get active batches for these products in the warehouse
        List<InventoryBatch> batches = inventoryBatchRepository.findAll().stream()
            .filter(b -> b.getActive())
            .filter(b -> b.getLocation() != null && b.getLocation().getWarehouse() != null)
            .filter(b -> b.getLocation().getWarehouse().getId().equals(warehouseId))
            .filter(b -> recentBatchProductSkuIds.contains(b.getProductSku().getId()))
            .limit(MONTHLY_MAX_BATCHES)
            .collect(Collectors.toList());

        log.info("Selected {} batches for monthly stocktake (recent {} days)", batches.size(), RECENT_DAYS_THRESHOLD);

        return batches;
    }

    /**
     * Select batches for full inventory stocktake
     * Strategy: All active batches in warehouse
     */
    private List<InventoryBatch> selectBatchesForFullInventory(Long warehouseId) {
        List<InventoryBatch> batches = inventoryBatchRepository.findAll().stream()
            .filter(b -> b.getActive())
            .filter(b -> b.getLocation() != null && b.getLocation().getWarehouse() != null)
            .filter(b -> b.getLocation().getWarehouse().getId().equals(warehouseId))
            .collect(Collectors.toList());

        log.info("Selected {} batches for full inventory stocktake", batches.size());

        return batches;
    }

    /**
     * Convert StocktakeTask to StocktakeTaskResponse
     */
    private StocktakeTaskResponse convertToTaskResponse(StocktakeTask task) {
        Warehouse warehouse = warehouseRepository.findById(task.getWarehouseId()).orElse(null);

        return StocktakeTaskResponse.builder()
            .id(task.getId())
            .taskNo(task.getTaskNo())
            .warehouseId(task.getWarehouseId())
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .cycleType(task.getCycleType().name())
            .cycleTypeDescription(task.getCycleType().getDescription())
            .status(task.getStatus().name())
            .statusDescription(task.getStatus().getDescription())
            .snapshotTime(task.getSnapshotTime())
            .totalItems(task.getTotalItems())
            .countedItems(task.getCountedItems())
            .differenceItems(task.getDifferenceItems())
            .progress(task.getProgress())
            .reviewedBy(task.getReviewedBy())
            .reviewedByName(task.getReviewedByName())
            .reviewedAt(task.getReviewedAt())
            .reviewComment(task.getReviewComment())
            .createdBy(task.getCreatedBy())
            .createdByName(task.getCreatedByName())
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }

    /**
     * Backward-compatible alias for legacy method calls.
     */
    private StocktakeTaskResponse convertToResponse(StocktakeTask task) {
        return convertToTaskResponse(task);
    }

    /**
     * Convert StocktakeItem to StocktakeItemResponse (blind count - no snapshot qty)
     */
    private StocktakeItemResponse convertToItemResponse(StocktakeItem item) {
        ProductSku product = productSkuRepository.findById(item.getProductSkuId()).orElse(null);
        InventoryBatch batch = inventoryBatchRepository.findById(item.getBatchId()).orElse(null);
        Location location = locationRepository.findById(item.getLocationId()).orElse(null);

        return StocktakeItemResponse.builder()
            .id(item.getId())
            .taskId(item.getTaskId())
            .productSkuId(item.getProductSkuId())
            .productName(product != null ? product.getName() : null)
            .productBarcode(product != null ? product.getBarcode() : null)
            .batchId(item.getBatchId())
            .batchCode(batch != null ? batch.getBatchCode() : null)
            .locationId(item.getLocationId())
            .locationCode(location != null ? location.getLocationCode() : null)
            .countedQty(item.getCountedQty())
            .isCounted(item.getIsCounted())
            .countedBy(item.getCountedBy())
            .countedByName(item.getCountedByName())
            .countedAt(item.getCountedAt())
            .remark(item.getRemark())
            .build();
    }

    /**
     * Convert StocktakeItem to StocktakeItemDetailResponse (review version - with snapshot qty)
     */
    private StocktakeItemDetailResponse convertToItemDetailResponse(StocktakeItem item) {
        ProductSku product = productSkuRepository.findById(item.getProductSkuId()).orElse(null);
        InventoryBatch batch = inventoryBatchRepository.findById(item.getBatchId()).orElse(null);
        Location location = locationRepository.findById(item.getLocationId()).orElse(null);

        return StocktakeItemDetailResponse.detailBuilder()
            .id(item.getId())
            .taskId(item.getTaskId())
            .productSkuId(item.getProductSkuId())
            .productName(product != null ? product.getName() : null)
            .productBarcode(product != null ? product.getBarcode() : null)
            .batchId(item.getBatchId())
            .batchCode(batch != null ? batch.getBatchCode() : null)
            .locationId(item.getLocationId())
            .locationCode(location != null ? location.getLocationCode() : null)
            .countedQty(item.getCountedQty())
            .isCounted(item.getIsCounted())
            .countedBy(item.getCountedBy())
            .countedByName(item.getCountedByName())
            .countedAt(item.getCountedAt())
            .remark(item.getRemark())
            .snapshotQty(item.getSnapshotQty())
            .differenceQty(item.getDifferenceQty())
            .build();
    }

    // ========== Delete/Cancel/Void Operations ==========

    /**
     * Delete stocktake task (physical delete, CREATED only)
     *
     * Business Rule:
     * - CREATED status: physical DELETE from database
     * - Any other status: throws exception, use cancelStocktakeTask or voidStocktakeTask instead
     *
     * @param taskId 盘点任务ID
     * @param operatorId 操作人ID
     * @throws BusinessException if task not found or not in CREATED status
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteStocktakeTask(Long taskId, Long operatorId) {
        log.info("🗑️ Deleting stocktake task (physical): taskId={}, operatorId={}", taskId, operatorId);

        StocktakeTask task = stocktakeTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_NOT_FOUND,
                Map.of("taskId", taskId)
            ));

        if (!task.getStatus().canPhysicallyDelete()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_INVALID_STATUS,
                Map.of(
                    "taskId", taskId,
                    "currentStatus", task.getStatus().name(),
                    "reason", "只有已创建状态的盘点任务可以物理删除，已开始的任务请使用取消或作废"
                )
            );
        }

        stocktakeTaskRepository.deleteById(taskId);
        log.info("✅ Stocktake task physically deleted: taskId={}, taskNo={}", taskId, task.getTaskNo());
    }

    /**
     * Cancel stocktake task (business failure, kept for AI learning)
     *
     * Business Rule:
     * - CREATED status → physical delete
     * - COUNTING / REVIEWING → set status = CANCELLED
     * - reason is required
     * - Data preserved for AI learning
     *
     * @param taskId 盘点任务ID
     * @param reason 取消原因 (required)
     * @param operatorId 操作人ID
     * @return StocktakeTaskResponse
     * @throws BusinessException if task not found or cannot be cancelled
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse cancelStocktakeTask(Long taskId, String reason, Long operatorId) {
        log.info("🚫 Cancelling stocktake task: taskId={}, operatorId={}, reason={}", taskId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_INVALID_STATUS,
                Map.of("taskId", taskId, "reason", "业务取消必须填写取消原因")
            );
        }

        StocktakeTask task = stocktakeTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_NOT_FOUND,
                Map.of("taskId", taskId)
            ));

        // CREATED → physical delete
        if (task.getStatus() == StocktakeStatus.CREATED) {
            stocktakeTaskRepository.deleteById(taskId);
            log.info("✅ CREATED stocktake task physically deleted during cancel: taskId={}", taskId);
            return null;
        }

        if (!task.getStatus().canCancel()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_INVALID_STATUS,
                Map.of(
                    "taskId", taskId,
                    "currentStatus", task.getStatus().name(),
                    "reason", "当前状态不允许取消"
                )
            );
        }

        task.setStatus(StocktakeStatus.CANCELLED);
        task.setReviewComment("业务取消: " + reason);
        task = stocktakeTaskRepository.save(task);

        log.info("✅ Stocktake task cancelled: taskId={}, taskNo={}", taskId, task.getTaskNo());
        return convertToResponse(task);
    }

    /**
     * Void stocktake task (data noise, filtered from AI and statistics)
     *
     * Business Rule:
     * - CREATED status → physical delete
     * - COUNTING / REVIEWING → set status = VOIDED
     * - VOIDED tasks are excluded from AI training and business statistics
     * - Financial audit trail is preserved (task number retained)
     *
     * @param taskId 盘点任务ID
     * @param reason 作废原因 (required)
     * @param operatorId 操作人ID
     * @return StocktakeTaskResponse
     * @throws BusinessException if task not found or cannot be voided
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse voidStocktakeTask(Long taskId, String reason, Long operatorId) {
        log.info("🚫 Voiding stocktake task: taskId={}, operatorId={}, reason={}", taskId, operatorId, reason);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_INVALID_STATUS,
                Map.of("taskId", taskId, "reason", "系统作废必须填写作废原因")
            );
        }

        StocktakeTask task = stocktakeTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_NOT_FOUND,
                Map.of("taskId", taskId)
            ));

        // CREATED → physical delete
        if (task.getStatus() == StocktakeStatus.CREATED) {
            stocktakeTaskRepository.deleteById(taskId);
            log.info("✅ CREATED stocktake task physically deleted during void: taskId={}", taskId);
            return null;
        }

        if (!task.getStatus().canVoid()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_INVALID_STATUS,
                Map.of(
                    "taskId", taskId,
                    "currentStatus", task.getStatus().name(),
                    "reason", "当前状态不允许作废"
                )
            );
        }

        task.setStatus(StocktakeStatus.VOIDED);
        task.setReviewComment("系统作废: " + reason);
        task = stocktakeTaskRepository.save(task);

        log.info("✅ Stocktake task voided: taskId={}, taskNo={}", taskId, task.getTaskNo());
        return convertToResponse(task);
    }
}
