package com.wms.system.service;

import com.wms.system.dto.stocktake.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.inventory.InventoryPostingCommand;
import com.wms.system.service.inventory.InventoryPostingService;
import com.wms.system.service.inventory.PostingContext;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
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
    private final OutboundTaskRepository outboundTaskRepository;
    private final ProductSkuRepository productSkuRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryPostingService inventoryPostingService;
    private final LocationOccupancyService locationOccupancyService;

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

        // The task row is the leading lock for every count mutation. Review
        // takes the same lock before reading items, so an already-started
        // count can neither be omitted from posting nor overwrite COMPLETED
        // after approval commits.
        StocktakeTask task = getTaskByIdForUpdate(taskId);

        if (!task.canStartCounting()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_CANNOT_START,
                Map.of("taskId", taskId, "currentStatus", task.getStatus().name())
            );
        }

        List<StocktakeItem> countItems = stocktakeItemRepository.findByTaskId(taskId);
        List<Long> countBatchIds = countItems.stream().map(StocktakeItem::getBatchId).distinct().sorted().toList();
        List<InventoryBatch> countBatches = countBatchIds.isEmpty() ? List.of()
            : inventoryBatchRepository.findAllByIdForPosting(countBatchIds);
        if (countBatches.size() != countBatchIds.size()) throw new BusinessException(ErrorKeys.BATCH_NOT_FOUND);
        if (!countBatchIds.isEmpty() && outboundTaskRepository.existsByAssignedBatchIdInAndStatusIn(countBatchIds,
            List.of(OutboundTaskStatus.PENDING, OutboundTaskStatus.PICKING, OutboundTaskStatus.QUALITY_EXCEPTION)))
            throw new BusinessException("PACKAGING_RESERVED");
        for (InventoryBatch batch : countBatches) {
            if (batch.getStocktakeTaskId() != null) throw new BusinessException("PACKAGING_STOCKTAKE_LOCKED");
            if (batch.getReservedQuantity() != null && batch.getReservedQuantity() > 0)
                throw new BusinessException("PACKAGING_RESERVED");
            batch.setStocktakeTaskId(taskId);
        }
        inventoryBatchRepository.saveAllAndFlush(countBatches);
        for (StocktakeItem item : countItems) {
            InventoryBatch batch = countBatches.stream().filter(b -> b.getId().equals(item.getBatchId())).findFirst().orElseThrow();
            item.setSnapshotQty(batch.getQuantity());
            item.setSnapshotBatchVersion(batch.getVersion());
        }
        stocktakeItemRepository.saveAll(countItems);
        task.setSnapshotTime(LocalDateTime.now());
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
        StocktakeTask task = getTaskByIdForUpdate(taskId);
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
        InventoryBatch countedBatch = inventoryBatchRepository.findByIdForPosting(item.getBatchId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.BATCH_NOT_FOUND));
        if (request.getPackaging() != null) {
            request.getPackaging().requireTotal(request.getCountedQty());
            if (!java.util.Objects.equals(request.getPackaging().packSize(), com.wms.system.dto.inventory.PackagingView.of(countedBatch).standardPackSize()))
                throw new BusinessException("PACKAGING_INVALID");
        } else if (countedBatch.getPackaging() != null) {
            throw new BusinessException("PACKAGING_SELECTION_REQUIRED");
        }
        item.setCountedPackaging(request.getPackaging());
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
        // Always recalculate: a recount can also clear an existing difference.
        long differenceCount = stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(taskId, 0);
        task.setDifferenceItems((int) differenceCount);

        // Saving (including recounting after rejection) never submits for review.
        // The explicit finish command is the single transition boundary.

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

        StocktakeTask task = getTaskByIdForUpdate(taskId);

        // A repeated finish request after a committed transition is idempotent.
        if (task.getStatus() == StocktakeStatus.REVIEWING) {
            requireAllItemsCounted(
                task,
                ErrorKeys.STOCKTAKE_TASK_CANNOT_COUNT,
                "NOT_ALL_ITEMS_COUNTED"
            );
            log.info("Stocktake already finished counting: taskNo={}", task.getTaskNo());
            return convertToTaskResponse(task);
        }

        if (!task.canCount()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_CANNOT_COUNT,
                Map.of("taskId", taskId, "currentStatus", task.getStatus().name())
            );
        }

        requireAllItemsCounted(
            task,
            ErrorKeys.STOCKTAKE_TASK_CANNOT_COUNT,
            "NOT_ALL_ITEMS_COUNTED"
        );

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
        return reviewStocktake(
            taskId,
            request,
            reviewedBy,
            reviewedByName,
            "stocktake-review-task:" + taskId
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public StocktakeTaskResponse reviewStocktake(
        Long taskId,
        ReviewStocktakeRequest request,
        Long reviewedBy,
        String reviewedByName,
        String idempotencyKey
    ) {
        log.info("Reviewing stocktake: taskId={}, approved={}, reviewedBy={}",
            taskId, request.getApproved(), reviewedByName);

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        StocktakeTask task = getTaskByIdForUpdate(taskId);

        if (!task.canReview()) {
            throw new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_CANNOT_REVIEW,
                Map.of("taskId", taskId, "currentStatus", task.getStatus().name())
            );
        }

        if (Boolean.TRUE.equals(request.getApproved())) {
            requireAllItemsCounted(
                task,
                ErrorKeys.STOCKTAKE_TASK_CANNOT_REVIEW,
                "NOT_ALL_ITEMS_COUNTED"
            );
        }

        // Update review info
        task.setReviewedBy(reviewedBy);
        task.setReviewedByName(reviewedByName);
        task.setReviewedAt(LocalDateTime.now());
        task.setReviewComment(request.getComment());

        if (request.getApproved()) {
            // Approve: adjust inventory and complete task
            adjustInventory(
                task,
                reviewedBy,
                reviewedByName,
                normalizedIdempotencyKey
            );
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
     * @param task Locked stocktake task
     */
    private void adjustInventory(
        StocktakeTask task,
        Long reviewedBy,
        String reviewedByName,
        String idempotencyKey
    ) {
        Long taskId = task.getId();
        log.info("Adjusting inventory for stocktake: taskId={}", taskId);

        // Get all items with differences
        List<StocktakeItem> items = stocktakeItemRepository.findByTaskId(taskId);
        List<StocktakeItem> differenceItems = items.stream()
            .filter(item -> item.hasDifference() || item.getCountedPackaging() != null || item.getSnapshotBatchVersion() != null)
            .sorted(Comparator.comparing(StocktakeItem::getBatchId)
                .thenComparing(StocktakeItem::getId))
            .collect(Collectors.toList());

        log.info("Found {} items with differences", differenceItems.size());

        List<Long> batchIds = differenceItems.stream()
            .map(StocktakeItem::getBatchId)
            .distinct()
            .sorted()
            .toList();
        List<InventoryBatch> lockedBatches = batchIds.isEmpty()
            ? List.of()
            : inventoryBatchRepository.findAllByIdForPosting(batchIds);
        if (lockedBatches.size() != batchIds.size()) {
            Long missingBatchId = batchIds.stream()
                .filter(id -> lockedBatches.stream().noneMatch(batch -> id.equals(batch.getId())))
                .findFirst()
                .orElse(null);
            throw new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", missingBatchId == null ? -1L : missingBatchId)
            );
        }

        for (StocktakeItem item : differenceItems) {
            InventoryBatch batch = lockedBatches.stream().filter(b -> b.getId().equals(item.getBatchId())).findFirst().orElseThrow();
            if (item.getSnapshotBatchVersion() != null && (!java.util.Objects.equals(batch.getStocktakeTaskId(), taskId)
                || !java.util.Objects.equals(batch.getVersion(), item.getSnapshotBatchVersion())
                || !java.util.Objects.equals(batch.getQuantity(), item.getSnapshotQty())))
                throw new BusinessException("PACKAGING_STALE");
            batch.setStocktakeTaskId(null);
            Integer adjustment = item.getDifferenceQty();
            SourceType sourceType = adjustment > 0 ? SourceType.INVENTORY_GAIN : SourceType.INVENTORY_LOSS;
            String postingId = "STOCKTAKE:" + task.getTaskNo() + ":" + item.getId();
            String childKey = childPostingKey(idempotencyKey, item.getId());
            inventoryPostingService.post(new InventoryPostingCommand(
                item.getBatchId(),
                item.getProductSkuId(),
                item.getLocationId(),
                adjustment,
                new PostingContext(
                    CompanyScope.currentCompanyId(),
                    task.getWarehouseId(),
                    sourceType,
                    task.getTaskNo(),
                    adjustment > 0 ? "STOCKTAKE_GAIN" : "STOCKTAKE_LOSS",
                    String.format("盘点调整 - 任务号: %s, 差异: %+d", task.getTaskNo(), adjustment),
                    reviewedBy,
                    reviewedByName,
                    postingId,
                    childKey
                ),
                item.getCountedPackaging()
            ));
            locationOccupancyService.refreshLocationStatus(item.getLocationId());

            log.info("Inventory posting completed: taskId={}, itemId={}, batchId={}, adjustment={}",
                taskId, item.getId(), item.getBatchId(), adjustment);
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

    private StocktakeTask getTaskByIdForUpdate(Long taskId) {
        return stocktakeTaskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.STOCKTAKE_TASK_NOT_FOUND,
                Map.of("taskId", taskId)
            ));
    }

    private void requireAllItemsCounted(
        StocktakeTask task,
        String errorKey,
        String reason
    ) {
        long persistedUncounted = stocktakeItemRepository.countByTaskIdAndIsCounted(
            task.getId(),
            false
        );
        int remainingByTask = Math.max(
            0,
            task.getTotalItems() - task.getCountedItems()
        );
        long remainingItems = Math.max(persistedUncounted, remainingByTask);
        if (remainingItems > 0) {
            throw new BusinessException(
                errorKey,
                Map.of(
                    "taskId", task.getId(),
                    "currentStatus", task.getStatus().name(),
                    "reason", reason,
                    "remainingItems", remainingItems
                )
            );
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_KEY_REQUIRED);
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "Idempotency-Key", "maxLength", 120)
            );
        }
        return normalized;
    }

    private String childPostingKey(String idempotencyKey, Long itemId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (idempotencyKey + ":" + itemId).getBytes(StandardCharsets.UTF_8)
            );
            return "stocktake-" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
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
        List<InventoryBatch> batches = inventoryBatchRepository.findMonthlyStocktakeCandidates(
            CompanyScope.currentCompanyId(),
            warehouseId,
            thirtyDaysAgo,
            PageRequest.of(0, MONTHLY_MAX_BATCHES)
        );

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
            .countedPackaging(item.getCountedPackaging())
            .packagingRequired(batch != null && batch.getPackaging() != null)
            .standardPackSize(batch == null ? 1 : com.wms.system.dto.inventory.PackagingView.of(batch).standardPackSize())
            .baseUnit(product == null ? "件" : product.getDisplayBaseUnit())
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

        StocktakeItemDetailResponse response = StocktakeItemDetailResponse.detailBuilder()
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
        response.setCountedPackaging(item.getCountedPackaging());
        response.setStandardPackSize(batch == null ? 1 : com.wms.system.dto.inventory.PackagingView.of(batch).standardPackSize());
        response.setBaseUnit(product == null ? "件" : product.getDisplayBaseUnit());
        return response;
    }

    // ========== Delete/Cancel/Void Operations ==========

    private void releaseCountLocks(Long taskId) {
        List<Long> ids = stocktakeItemRepository.findByTaskId(taskId).stream().map(StocktakeItem::getBatchId).distinct().sorted().toList();
        if (ids.isEmpty()) return;
        for (InventoryBatch batch : inventoryBatchRepository.findAllByIdForPosting(ids)) {
            if (java.util.Objects.equals(batch.getStocktakeTaskId(), taskId)) {
                batch.setStocktakeTaskId(null);
                // A cancelled count may already have physically repacked goods; require a fresh verification.
                batch.setPackaging(null);
            }
        }
    }

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

        StocktakeTask task = stocktakeTaskRepository.findByIdForUpdate(taskId)
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

        releaseCountLocks(taskId);
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

        StocktakeTask task = stocktakeTaskRepository.findByIdForUpdate(taskId)
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

        releaseCountLocks(taskId);
        task.setStatus(StocktakeStatus.VOIDED);
        task.setReviewComment("系统作废: " + reason);
        task = stocktakeTaskRepository.save(task);

        log.info("✅ Stocktake task voided: taskId={}, taskNo={}", taskId, task.getTaskNo());
        return convertToResponse(task);
    }
}
