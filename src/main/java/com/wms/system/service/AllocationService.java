package com.wms.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.AllocationPolicy;
import com.wms.system.entity.enums.FulfillmentStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Allocation Service (智能分配服务)
 *
 * V3.7 Architecture: Core service for intelligent inventory allocation
 *
 * Core Responsibilities:
 * 1. Intelligent allocation algorithm (智能分配算法)
 * 2. FEFO sorting (First Expire First Out - 先过期先出)
 * 3. Strict box strategy (整箱策略)
 * 4. Near-expiry filtering (临期过滤)
 * 5. User-specified batch priority (用户指定批次优先)
 * 6. Generate outbound tasks (生成出库任务)
 *
 * Allocation Algorithm Priority (MUST follow this exact order):
 * a. User-specified priority: If specified_batch_ids has value, force deduct from specified batches
 * b. Strict Box Strategy: If quantity % per_pack_qty == 0, ONLY allocate from full box batches
 * c. Near-expiry filtering: If reject_near_expiry=true, filter out near-expiry batches
 * d. Stock sufficiency check: If SUM(available_quantity) < requested_quantity, throw exception
 * e. FEFO sorting: Sort by expiry_date ASC (first expire first out)
 * f. Box/piece separation: For remainders, prefer loose stock first, then break boxes if needed
 *
 * Technical Features:
 * - @Transactional: Ensures atomicity (all allocations must succeed or rollback)
 * - Error Key System: All exceptions use error keys for frontend i18n
 * - Logging: INFO for allocation decisions, DEBUG for algorithm steps, ERROR for failures
 *
 * Important Notes:
 * - This service only PLANS the allocation and creates PENDING tasks
 * - DO NOT actually deduct inventory (that happens in OutboundService when picking is confirmed)
 * - Use LocalDate.now() for current date calculations
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductSkuRepository productSkuRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final BackorderService backorderService;
    private final ObjectMapper objectMapper;

    /**
     * ⭐ Core Method: Allocate inventory for sales order
     *
     * Business Flow:
     * 1. Validate sales order exists
     * 2. Query all order items
     * 3. For each item, run intelligent allocation algorithm
     * 4. Generate outbound tasks for each allocated batch
     * 5. Return all generated tasks
     *
     * @param salesOrderId Sales order ID
     * @return List<OutboundTask> Generated outbound tasks (status = PENDING)
     * @throws BusinessException if order not found, product not found, or insufficient stock
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OutboundTask> allocateInventory(Long salesOrderId) {
        log.info("🚀 Starting intelligent allocation for sales order: salesOrderId={}", salesOrderId);

        // 1. Validate sales order exists
        SalesOrder salesOrder = salesOrderRepository.findById(salesOrderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", salesOrderId)
            ));

        log.info("📋 Sales order found: orderNo={}, customerId={}, status={}",
            salesOrder.getOrderNo(), salesOrder.getCustomerId(), salesOrder.getStatus());

        List<OutboundTask> existingTasks = outboundTaskRepository.findBySalesOrderId(salesOrderId);
        if (!existingTasks.isEmpty()) {
            log.info("Allocation already exists for sales order: salesOrderId={}, taskCount={}",
                salesOrderId, existingTasks.size());
            return existingTasks;
        }

        // 2. Query all order items
        List<SalesOrderItem> orderItems = salesOrderItemRepository.findBySalesOrderId(salesOrderId);

        if (orderItems.isEmpty()) {
            log.warn("⚠️ No order items found for sales order: salesOrderId={}", salesOrderId);
            return new ArrayList<>();
        }

        log.info("📦 Found {} order items to allocate", orderItems.size());

        // 3. Allocate each order item
        List<OutboundTask> allTasks = new ArrayList<>();

        for (SalesOrderItem item : orderItems) {
            log.info("🔄 Allocating order item: itemId={}, productSkuId={}, quantity={}",
                item.getId(), item.getProductSkuId(), item.getQuantity());

            List<OutboundTask> itemTasks = allocateOrderItem(item, salesOrder);
            allTasks.addAll(itemTasks);

            int allocatedQty = itemTasks.stream().mapToInt(OutboundTask::getPlanQty).sum();
            item.setRequestedQty(item.getQuantity());
            item.setAllocatedQty(allocatedQty);
            item.setBackorderQty(Math.max(0, item.getQuantity() - allocatedQty));
            item.setFulfillmentStatus(resolveItemFulfillmentStatus(allocatedQty, item.getBackorderQty()));
            salesOrderItemRepository.save(item);

            log.info("✅ Order item allocated: itemId={}, tasksGenerated={}", item.getId(), itemTasks.size());
        }

        log.info("✅ Intelligent allocation completed: salesOrderId={}, totalTasks={}", salesOrderId, allTasks.size());

        boolean hasBackorder = orderItems.stream()
            .anyMatch(item -> item.getBackorderQty() != null && item.getBackorderQty() > 0);
        boolean hasAllocation = allTasks.stream().anyMatch(task -> task.getPlanQty() != null && task.getPlanQty() > 0);
        salesOrder.setFulfillmentStatus(hasBackorder
            ? (hasAllocation ? FulfillmentStatus.PARTIALLY_ALLOCATED : FulfillmentStatus.WAITING_INBOUND)
            : FulfillmentStatus.RESERVED);
        salesOrder.setShortageReason(hasBackorder ? "Some quantities are waiting for inventory." : null);
        salesOrder.setFulfillmentVersion(
            salesOrder.getFulfillmentVersion() == null ? 1L : salesOrder.getFulfillmentVersion() + 1
        );
        salesOrderRepository.save(salesOrder);

        return allTasks;
    }

    /**
     * Allocate single order item (智能分配单个订单明细)
     *
     * Algorithm Steps:
     * 1. If has specified_batch_ids → Force deduct from specified batches
     * 2. Query available batches with filtering (near-expiry, active, quantity>0)
     * 3. Check stock sufficiency
     * 4. Apply strict box strategy if needed
     * 5. FEFO allocation (sort by expiry_date ASC)
     * 6. Generate outbound tasks
     *
     * @param item Sales order item
     * @return List<OutboundTask> Generated outbound tasks
     * @throws BusinessException if product not found or insufficient stock
     */
    private List<OutboundTask> allocateOrderItem(SalesOrderItem item, SalesOrder salesOrder) {
        log.debug("📊 Starting allocation for item: itemId={}, productSkuId={}, quantity={}",
            item.getId(), item.getProductSkuId(), item.getQuantity());

        // 1. Validate product exists
        ProductSku product = productSkuRepository.findById(item.getProductSkuId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                Map.of("productSkuId", item.getProductSkuId())
            ));

        log.debug("📦 ProductSku info: name={}, perPackQty={}, nearExpiryDays={}",
            product.getName(), product.getPerPackQty(), product.getNearExpiryDays());

        // 2. Check if user specified batches
        if (item.hasSpecifiedBatches()) {
            log.info("🎯 User-specified batches detected: specifiedBatchIds={}", item.getSpecifiedBatchIds());
            return allocateFromSpecifiedBatches(item, product);
        }

        // 3. Query available batches with filtering
        List<InventoryBatch> availableBatches = getAvailableBatches(
            item.getProductSkuId(),
            item.getRejectNearExpiry(),
            product.getNearExpiryDays()
        );

        log.debug("📋 Available batches found: count={}", availableBatches.size());

        // 4. Check stock sufficiency
        int totalAvailable = availableBatches.stream()
            .mapToInt(InventoryBatch::getAvailableQuantity)
            .sum();

        AllocationPolicy policy = salesOrder.getAllocationPolicy() == null
            ? AllocationPolicy.FULL_ONLY
            : salesOrder.getAllocationPolicy();

        if (totalAvailable < item.getQuantity() && policy == AllocationPolicy.FULL_ONLY) {
            log.error("❌ Insufficient stock: productSkuId={}, requested={}, available={}, shortage={}",
                item.getProductSkuId(), item.getQuantity(), totalAvailable, item.getQuantity() - totalAvailable);

            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productSkuId", item.getProductSkuId(),
                    "productName", product.getName(),
                    "requestedQuantity", item.getQuantity(),
                    "availableQuantity", totalAvailable,
                    "shortage", item.getQuantity() - totalAvailable
                )
            );
        }

        if (totalAvailable < item.getQuantity() && policy == AllocationPolicy.WAIT_FOR_COMPLETE) {
            backorderService.createBackorder(item, item.getQuantity(), 100, salesOrder.getPromisedShipDate());
            return new ArrayList<>();
        }

        if (totalAvailable < item.getQuantity() && policy == AllocationPolicy.PARTIAL_BACKORDER) {
            int allocatableQty = Math.max(0, totalAvailable);
            List<OutboundTask> tasks = allocatableQty == 0
                ? new ArrayList<>()
                : allocateFromBatchesUpTo(item, availableBatches, allocatableQty);
            backorderService.createBackorder(item, item.getQuantity() - allocatableQty, 100, salesOrder.getPromisedShipDate());
            return tasks;
        }

        List<BatchAllocation> allocationPlan = planPackAwareAllocation(item, product, availableBatches);
        return createOutboundTasks(item, allocationPlan);
    }

    private FulfillmentStatus resolveItemFulfillmentStatus(int allocatedQty, int backorderQty) {
        if (backorderQty > 0 && allocatedQty > 0) {
            return FulfillmentStatus.PARTIALLY_ALLOCATED;
        }
        if (backorderQty > 0) {
            return FulfillmentStatus.WAITING_INBOUND;
        }
        return FulfillmentStatus.RESERVED;
    }

    /**
     * Split each batch into its loose remainder and full-pack portion. This
     * avoids classifying the entire batch from its current quantity modulo.
     */
    private List<BatchAllocation> planPackAwareAllocation(
        SalesOrderItem item,
        ProductSku product,
        List<InventoryBatch> availableBatches
    ) {
        int packSize = product.getPerPackQty() == null || product.getPerPackQty() <= 0
            ? 1
            : product.getPerPackQty();

        List<InventoryBatch> fefoBatches = availableBatches.stream()
            .sorted(Comparator.comparing(
                InventoryBatch::getExpiryDate,
                Comparator.nullsLast(Comparator.naturalOrder())
            ))
            .collect(Collectors.toList());

        Map<Long, Integer> remainingByBatch = new LinkedHashMap<>();
        for (InventoryBatch batch : fefoBatches) {
            remainingByBatch.put(batch.getId(), batch.getAvailableQuantity());
        }

        Map<Long, BatchAllocation> allocations = new LinkedHashMap<>();
        int remainingRequest = item.getQuantity();
        boolean fullPackRequest = remainingRequest % packSize == 0;

        if (!fullPackRequest) {
            remainingRequest = allocateLooseRemainders(
                fefoBatches,
                remainingByBatch,
                allocations,
                remainingRequest,
                packSize
            );
        }

        remainingRequest = allocateFullPackPortions(
            fefoBatches,
            remainingByBatch,
            allocations,
            remainingRequest,
            packSize
        );

        if (!fullPackRequest && remainingRequest > 0) {
            remainingRequest = allocateByBreakingPack(
                fefoBatches,
                remainingByBatch,
                allocations,
                remainingRequest
            );
        }

        if (remainingRequest > 0) {
            int eligibleQuantity = allocations.values().stream()
                .mapToInt(BatchAllocation::quantity)
                .sum();
            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productSkuId", item.getProductSkuId(),
                    "productName", product.getName(),
                    "requestedQuantity", item.getQuantity(),
                    "availableQuantity", eligibleQuantity,
                    "shortage", remainingRequest
                )
            );
        }

        return new ArrayList<>(allocations.values());
    }

    private int allocateLooseRemainders(
        List<InventoryBatch> batches,
        Map<Long, Integer> remainingByBatch,
        Map<Long, BatchAllocation> allocations,
        int remainingRequest,
        int packSize
    ) {
        for (InventoryBatch batch : batches) {
            if (remainingRequest == 0) {
                break;
            }

            int looseQuantity = remainingByBatch.get(batch.getId()) % packSize;
            int allocated = Math.min(remainingRequest, looseQuantity);
            remainingRequest -= addAllocation(batch, allocated, remainingByBatch, allocations);
        }
        return remainingRequest;
    }

    private int allocateFullPackPortions(
        List<InventoryBatch> batches,
        Map<Long, Integer> remainingByBatch,
        Map<Long, BatchAllocation> allocations,
        int remainingRequest,
        int packSize
    ) {
        int fullPackDemand = (remainingRequest / packSize) * packSize;
        for (InventoryBatch batch : batches) {
            if (fullPackDemand == 0) {
                break;
            }

            int batchQuantity = remainingByBatch.get(batch.getId());
            int fullPackQuantity = (batchQuantity / packSize) * packSize;
            int allocated = Math.min(fullPackDemand, fullPackQuantity);
            int actualAllocated = addAllocation(batch, allocated, remainingByBatch, allocations);
            fullPackDemand -= actualAllocated;
            remainingRequest -= actualAllocated;
        }
        return remainingRequest;
    }

    private int allocateByBreakingPack(
        List<InventoryBatch> batches,
        Map<Long, Integer> remainingByBatch,
        Map<Long, BatchAllocation> allocations,
        int remainingRequest
    ) {
        for (InventoryBatch batch : batches) {
            if (remainingRequest == 0) {
                break;
            }

            int allocated = Math.min(remainingRequest, remainingByBatch.get(batch.getId()));
            remainingRequest -= addAllocation(batch, allocated, remainingByBatch, allocations);
        }
        return remainingRequest;
    }

    private int addAllocation(
        InventoryBatch batch,
        int quantity,
        Map<Long, Integer> remainingByBatch,
        Map<Long, BatchAllocation> allocations
    ) {
        if (quantity <= 0) {
            return 0;
        }

        BatchAllocation allocation = allocations.computeIfAbsent(
            batch.getId(),
            ignored -> new BatchAllocation(batch, 0)
        );
        allocation.add(quantity);
        remainingByBatch.compute(batch.getId(), (ignored, remaining) -> remaining - quantity);
        return quantity;
    }

    private List<OutboundTask> createOutboundTasks(
        SalesOrderItem item,
        List<BatchAllocation> allocationPlan
    ) {
        List<OutboundTask> tasks = new ArrayList<>();
        for (BatchAllocation allocation : allocationPlan) {
            InventoryBatch batch = allocation.batch();
            tasks.add(reserveAndCreateTask(item, batch, allocation.quantity()));
        }
        return tasks;
    }

    private OutboundTask reserveAndCreateTask(SalesOrderItem item, InventoryBatch batch, int quantity) {
        batch.reserveQuantity(quantity);
        InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

        InventoryReservation reservation = InventoryReservation.builder()
            .salesOrderId(item.getSalesOrderId())
            .salesOrderItemId(item.getId())
            .inventoryBatchId(savedBatch.getId())
            .productSkuId(item.getProductSkuId())
            .locationId(savedBatch.getLocation().getId())
            .reservedQty(quantity)
            .sourceType("SALES_ORDER")
            .build();
        reservation = inventoryReservationRepository.save(reservation);

        OutboundTask task = OutboundTask.builder()
            .salesOrderId(item.getSalesOrderId())
            .salesOrderItemId(item.getId())
            .assignedBatchId(savedBatch.getId())
            .reservationId(reservation.getId())
            .locationId(savedBatch.getLocation().getId())
            .planQty(quantity)
            .actualQty(0)
            .status(OutboundTaskStatus.PENDING)
            .remark(String.format("Reserved from batch %s (expiry: %s)",
                savedBatch.getBatchCode(), savedBatch.getExpiryDate()))
            .build();

        OutboundTask savedTask = outboundTaskRepository.save(task);
        log.info("Outbound task and reservation created: taskId={}, reservationId={}, batchCode={}, planQty={}",
            savedTask.getId(), reservation.getId(), savedBatch.getBatchCode(), quantity);
        return savedTask;
    }

    /**
     * Allocate from user-specified batches (用户指定批次分配)
     *
     * Business Rules:
     * - Force deduct from specified batches only
     * - Ignore near-expiry filtering
     * - Ignore box strategy
     * - Still check stock sufficiency
     *
     * @param item Sales order item
     * @param product ProductSku entity
     * @return List<OutboundTask> Generated outbound tasks
     * @throws BusinessException if batch not found or insufficient stock
     */
    private List<OutboundTask> allocateFromSpecifiedBatches(SalesOrderItem item, ProductSku product) {
        log.info("🎯 Allocating from specified batches: itemId={}, specifiedBatchIds={}",
            item.getId(), item.getSpecifiedBatchIds());

        // 1. Parse specified batch IDs
        List<Long> specifiedBatchIds = parseSpecifiedBatchIds(item.getSpecifiedBatchIds());

        if (specifiedBatchIds.isEmpty()) {
            log.error("❌ Failed to parse specified batch IDs: specifiedBatchIds={}", item.getSpecifiedBatchIds());
            throw new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("specifiedBatchIds", item.getSpecifiedBatchIds())
            );
        }

        log.debug("📋 Parsed batch IDs: {}", specifiedBatchIds);

        // 2. Query specified batches
        List<InventoryBatch> specifiedBatches = new ArrayList<>();
        for (Long batchId : specifiedBatchIds) {
            InventoryBatch batch = inventoryBatchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.BATCH_NOT_FOUND,
                    Map.of("batchId", batchId)
                ));

            // Validate batch is active and has quantity
            if (!batch.isActive()) {
                log.error("❌ Specified batch is inactive: batchId={}, batchCode={}", batchId, batch.getBatchCode());
                throw new BusinessException(
                    ErrorKeys.BATCH_INACTIVE,
                    Map.of("batchId", batchId, "batchCode", batch.getBatchCode())
                );
            }

            if (batch.getAvailableQuantity() <= 0) {
                log.error("❌ Specified batch has no stock: batchId={}, batchCode={}, quantity={}",
                    batchId, batch.getBatchCode(), batch.getAvailableQuantity());
                throw new BusinessException(
                    ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                    Map.of(
                        "batchId", batchId,
                        "batchCode", batch.getBatchCode(),
                        "availableQuantity", batch.getAvailableQuantity()
                    )
                );
            }

            // Validate batch belongs to the correct product
            if (!batch.getProductSku().getId().equals(item.getProductSkuId())) {
                log.error("❌ Specified batch belongs to different product: batchId={}, batchProductSkuId={}, expectedProductSkuId={}",
                    batchId, batch.getProductSku().getId(), item.getProductSkuId());
                throw new BusinessException(
                    ErrorKeys.BATCH_NOT_FOUND,
                    Map.of(
                        "batchId", batchId,
                        "expectedProductSkuId", item.getProductSkuId(),
                        "actualProductSkuId", batch.getProductSku().getId()
                    )
                );
            }

            specifiedBatches.add(batch);
        }

        // 3. Check stock sufficiency
        int totalAvailable = specifiedBatches.stream()
            .mapToInt(InventoryBatch::getAvailableQuantity)
            .sum();

        if (totalAvailable < item.getQuantity()) {
            log.error("❌ Insufficient stock in specified batches: requested={}, available={}, shortage={}",
                item.getQuantity(), totalAvailable, item.getQuantity() - totalAvailable);

            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productSkuId", item.getProductSkuId(),
                    "productName", product.getName(),
                    "requestedQuantity", item.getQuantity(),
                    "availableQuantity", totalAvailable,
                    "shortage", item.getQuantity() - totalAvailable
                )
            );
        }

        // 4. Sort by expiry date (FEFO)
        List<InventoryBatch> sortedBatches = specifiedBatches.stream()
            .sorted((b1, b2) -> b1.getExpiryDate().compareTo(b2.getExpiryDate()))
            .collect(Collectors.toList());

        // 5. Allocate from batches
        return allocateFromBatches(item, product, sortedBatches);
    }

    /**
     * Allocate from batches (FEFO allocation)
     *
     * Algorithm:
     * 1. Iterate through sorted batches (by expiry date ASC)
     * 2. Deduct from each batch until requested quantity is satisfied
     * 3. Generate outbound task for each batch allocation
     *
     * @param item Sales order item
     * @param product ProductSku entity
     * @param sortedBatches Sorted batches (by expiry date ASC)
     * @return List<OutboundTask> Generated outbound tasks
     */
    private List<OutboundTask> allocateFromBatches(
        SalesOrderItem item,
        ProductSku product,
        List<InventoryBatch> sortedBatches
    ) {
        log.debug("🔄 Starting FEFO allocation: itemId={}, requestedQty={}, batchCount={}",
            item.getId(), item.getQuantity(), sortedBatches.size());

        List<OutboundTask> tasks = new ArrayList<>();
        int remaining = item.getQuantity();

        for (InventoryBatch batch : sortedBatches) {
            if (remaining <= 0) {
                break;  // All quantity fulfilled
            }

            // Calculate allocation for this batch
            int toAllocate = Math.min(remaining, batch.getAvailableQuantity());

            log.debug("📦 Allocating from batch: batchCode={}, batchQty={}, toAllocate={}, expiryDate={}",
                batch.getBatchCode(), batch.getQuantity(), toAllocate, batch.getExpiryDate());

            OutboundTask savedTask = reserveAndCreateTask(item, batch, toAllocate);
            tasks.add(savedTask);

            log.info("✅ Outbound task created: taskId={}, batchCode={}, planQty={}, locationId={}",
                savedTask.getId(), batch.getBatchCode(), toAllocate, batch.getLocation().getId());

            remaining -= toAllocate;
        }

        // Verify all quantity allocated
        if (remaining > 0) {
            log.error("❌ Failed to allocate full quantity: itemId={}, remaining={}", item.getId(), remaining);
            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productSkuId", item.getProductSkuId(),
                    "productName", product.getName(),
                    "requestedQuantity", item.getQuantity(),
                    "allocatedQuantity", item.getQuantity() - remaining,
                    "shortage", remaining
                )
            );
        }

        log.info("✅ FEFO allocation completed: itemId={}, tasksGenerated={}", item.getId(), tasks.size());

        return tasks;
    }

    private List<OutboundTask> allocateFromBatchesUpTo(
        SalesOrderItem item,
        List<InventoryBatch> sortedBatches,
        int requestedQty
    ) {
        List<OutboundTask> tasks = new ArrayList<>();
        int remaining = requestedQty;

        List<InventoryBatch> fefoBatches = sortedBatches.stream()
            .sorted(Comparator.comparing(InventoryBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());

        for (InventoryBatch batch : fefoBatches) {
            if (remaining <= 0) {
                break;
            }
            int toAllocate = Math.min(remaining, batch.getAvailableQuantity());
            if (toAllocate <= 0) {
                continue;
            }
            tasks.add(reserveAndCreateTask(item, batch, toAllocate));
            remaining -= toAllocate;
        }

        return tasks;
    }

    /**
     * Get available batches with filtering
     *
     * Filters:
     * 1. product_sku_id = ?
     * 2. active = true
     * 3. quantity > 0
     * 4. If reject_near_expiry = true, filter out near-expiry batches
     *
     * @param productSkuId ProductSku ID
     * @param rejectNearExpiry Reject near-expiry flag
     * @param nearExpiryDays Near-expiry threshold (days)
     * @return List<InventoryBatch> Available batches
     */
    private List<InventoryBatch> getAvailableBatches(
        Long productSkuId,
        Boolean rejectNearExpiry,
        Integer nearExpiryDays
    ) {
        log.debug("🔍 Querying available batches: productSkuId={}, rejectNearExpiry={}, nearExpiryDays={}",
            productSkuId, rejectNearExpiry, nearExpiryDays);

        // Query all active batches for product
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByProductSkuIdAndActiveOrderByExpiryDateAsc(productSkuId, true);

        // Filter out batches with quantity = 0
        batches = batches.stream()
            .filter(batch -> batch.getAvailableQuantity() > 0)
            .collect(Collectors.toList());

        // Filter out near-expiry batches if needed
        if (Boolean.TRUE.equals(rejectNearExpiry)) {
            log.debug("🚫 Filtering near-expiry batches: nearExpiryDays={}", nearExpiryDays);

            LocalDate today = LocalDate.now();
            batches = batches.stream()
                .filter(batch -> !isNearExpiry(batch.getExpiryDate(), nearExpiryDays))
                .collect(Collectors.toList());

            log.debug("📋 After near-expiry filtering: remainingBatches={}", batches.size());
        }

        log.debug("✅ Available batches query completed: count={}", batches.size());

        return batches;
    }

    /**
     * Check if batch is near expiry
     *
     * Formula: CURRENT_DATE + near_expiry_days >= expiry_date
     *
     * @param expiryDate Batch expiry date
     * @param nearExpiryDays Near-expiry threshold (days)
     * @return true if near expiry
     */
    private boolean isNearExpiry(LocalDate expiryDate, Integer nearExpiryDays) {
        if (expiryDate == null || nearExpiryDays == null) {
            return false;
        }

        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(nearExpiryDays);

        return !threshold.isBefore(expiryDate);
    }

    private static final class BatchAllocation {
        private final InventoryBatch batch;
        private int quantity;

        private BatchAllocation(InventoryBatch batch, int quantity) {
            this.batch = batch;
            this.quantity = quantity;
        }

        private InventoryBatch batch() {
            return batch;
        }

        private int quantity() {
            return quantity;
        }

        private void add(int allocatedQuantity) {
            quantity += allocatedQuantity;
        }
    }

    /**
     * Parse specified batch IDs from JSON array string
     *
     * Format: "[123, 456, 789]"
     *
     * @param specifiedBatchIds JSON array string
     * @return List<Long> Parsed batch IDs
     */
    private List<Long> parseSpecifiedBatchIds(String specifiedBatchIds) {
        if (specifiedBatchIds == null || specifiedBatchIds.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(specifiedBatchIds, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("❌ Failed to parse specified batch IDs: specifiedBatchIds={}, error={}",
                specifiedBatchIds, e.getMessage());
            return new ArrayList<>();
        }
    }
}
