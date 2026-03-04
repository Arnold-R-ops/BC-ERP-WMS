package com.wms.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.*;
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
    private final ProductRepository productRepository;
    private final OutboundTaskRepository outboundTaskRepository;
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
            log.info("🔄 Allocating order item: itemId={}, productId={}, quantity={}",
                item.getId(), item.getProductId(), item.getQuantity());

            List<OutboundTask> itemTasks = allocateOrderItem(item);
            allTasks.addAll(itemTasks);

            log.info("✅ Order item allocated: itemId={}, tasksGenerated={}", item.getId(), itemTasks.size());
        }

        log.info("✅ Intelligent allocation completed: salesOrderId={}, totalTasks={}", salesOrderId, allTasks.size());

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
    private List<OutboundTask> allocateOrderItem(SalesOrderItem item) {
        log.debug("📊 Starting allocation for item: itemId={}, productId={}, quantity={}",
            item.getId(), item.getProductId(), item.getQuantity());

        // 1. Validate product exists
        Product product = productRepository.findById(item.getProductId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_NOT_FOUND,
                Map.of("productId", item.getProductId())
            ));

        log.debug("📦 Product info: name={}, perPackQty={}, nearExpiryDays={}",
            product.getName(), product.getPerPackQty(), product.getNearExpiryDays());

        // 2. Check if user specified batches
        if (item.hasSpecifiedBatches()) {
            log.info("🎯 User-specified batches detected: specifiedBatchIds={}", item.getSpecifiedBatchIds());
            return allocateFromSpecifiedBatches(item, product);
        }

        // 3. Query available batches with filtering
        List<InventoryBatch> availableBatches = getAvailableBatches(
            item.getProductId(),
            item.getRejectNearExpiry(),
            product.getNearExpiryDays()
        );

        log.debug("📋 Available batches found: count={}", availableBatches.size());

        // 4. Check stock sufficiency
        int totalAvailable = availableBatches.stream()
            .mapToInt(InventoryBatch::getQuantity)
            .sum();

        if (totalAvailable < item.getQuantity()) {
            log.error("❌ Insufficient stock: productId={}, requested={}, available={}, shortage={}",
                item.getProductId(), item.getQuantity(), totalAvailable, item.getQuantity() - totalAvailable);

            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productId", item.getProductId(),
                    "productName", product.getName(),
                    "requestedQuantity", item.getQuantity(),
                    "availableQuantity", totalAvailable,
                    "shortage", item.getQuantity() - totalAvailable
                )
            );
        }

        // 5. Apply strict box strategy if needed
        List<InventoryBatch> sortedBatches;
        if (isFullBox(item.getQuantity(), product.getPerPackQty())) {
            log.info("📦 Strict box strategy: Requested quantity is full box, filtering for full box batches only");
            sortedBatches = availableBatches.stream()
                .filter(batch -> isFullBox(batch.getQuantity(), product.getPerPackQty()))
                .sorted((b1, b2) -> b1.getExpiryDate().compareTo(b2.getExpiryDate()))
                .collect(Collectors.toList());

            // Check if full box batches are sufficient
            int fullBoxAvailable = sortedBatches.stream()
                .mapToInt(InventoryBatch::getQuantity)
                .sum();

            if (fullBoxAvailable < item.getQuantity()) {
                log.error("❌ Insufficient full box stock: productId={}, requested={}, fullBoxAvailable={}, shortage={}",
                    item.getProductId(), item.getQuantity(), fullBoxAvailable, item.getQuantity() - fullBoxAvailable);

                throw new BusinessException(
                    ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                    Map.of(
                        "productId", item.getProductId(),
                        "productName", product.getName(),
                        "requestedQuantity", item.getQuantity(),
                        "availableQuantity", fullBoxAvailable,
                        "shortage", item.getQuantity() - fullBoxAvailable
                    )
                );
            }
        } else {
            // 6. Box/piece separation: Prefer loose stock first, then break boxes
            log.info("📦 Box/piece separation: Prefer loose stock first");
            List<InventoryBatch> looseBatches = availableBatches.stream()
                .filter(batch -> !isFullBox(batch.getQuantity(), product.getPerPackQty()))
                .sorted((b1, b2) -> b1.getExpiryDate().compareTo(b2.getExpiryDate()))
                .collect(Collectors.toList());

            List<InventoryBatch> fullBoxBatches = availableBatches.stream()
                .filter(batch -> isFullBox(batch.getQuantity(), product.getPerPackQty()))
                .sorted((b1, b2) -> b1.getExpiryDate().compareTo(b2.getExpiryDate()))
                .collect(Collectors.toList());

            sortedBatches = new ArrayList<>();
            sortedBatches.addAll(looseBatches);
            sortedBatches.addAll(fullBoxBatches);

            log.debug("📊 Batch separation: looseBatches={}, fullBoxBatches={}", looseBatches.size(), fullBoxBatches.size());
        }

        // 7. FEFO allocation: Deduct batch by batch
        return allocateFromBatches(item, product, sortedBatches);
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
     * @param product Product entity
     * @return List<OutboundTask> Generated outbound tasks
     * @throws BusinessException if batch not found or insufficient stock
     */
    private List<OutboundTask> allocateFromSpecifiedBatches(SalesOrderItem item, Product product) {
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

            if (batch.getQuantity() <= 0) {
                log.error("❌ Specified batch has no stock: batchId={}, batchCode={}, quantity={}",
                    batchId, batch.getBatchCode(), batch.getQuantity());
                throw new BusinessException(
                    ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                    Map.of(
                        "batchId", batchId,
                        "batchCode", batch.getBatchCode(),
                        "availableQuantity", batch.getQuantity()
                    )
                );
            }

            // Validate batch belongs to the correct product
            if (!batch.getProduct().getId().equals(item.getProductId())) {
                log.error("❌ Specified batch belongs to different product: batchId={}, batchProductId={}, expectedProductId={}",
                    batchId, batch.getProduct().getId(), item.getProductId());
                throw new BusinessException(
                    ErrorKeys.BATCH_NOT_FOUND,
                    Map.of(
                        "batchId", batchId,
                        "expectedProductId", item.getProductId(),
                        "actualProductId", batch.getProduct().getId()
                    )
                );
            }

            specifiedBatches.add(batch);
        }

        // 3. Check stock sufficiency
        int totalAvailable = specifiedBatches.stream()
            .mapToInt(InventoryBatch::getQuantity)
            .sum();

        if (totalAvailable < item.getQuantity()) {
            log.error("❌ Insufficient stock in specified batches: requested={}, available={}, shortage={}",
                item.getQuantity(), totalAvailable, item.getQuantity() - totalAvailable);

            throw new BusinessException(
                ErrorKeys.BATCH_STOCK_INSUFFICIENT,
                Map.of(
                    "productId", item.getProductId(),
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
     * @param product Product entity
     * @param sortedBatches Sorted batches (by expiry date ASC)
     * @return List<OutboundTask> Generated outbound tasks
     */
    private List<OutboundTask> allocateFromBatches(
        SalesOrderItem item,
        Product product,
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
            int toAllocate = Math.min(remaining, batch.getQuantity());

            log.debug("📦 Allocating from batch: batchCode={}, batchQty={}, toAllocate={}, expiryDate={}",
                batch.getBatchCode(), batch.getQuantity(), toAllocate, batch.getExpiryDate());

            // Generate outbound task
            OutboundTask task = OutboundTask.builder()
                .salesOrderId(item.getSalesOrderId())
                .salesOrderItemId(item.getId())
                .assignedBatchId(batch.getId())
                .locationId(batch.getLocation().getId())
                .planQty(toAllocate)
                .actualQty(0)
                .status(OutboundTaskStatus.PENDING)
                .remark(String.format("Allocated from batch %s (expiry: %s)",
                    batch.getBatchCode(), batch.getExpiryDate()))
                .build();

            OutboundTask savedTask = outboundTaskRepository.save(task);
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
                    "productId", item.getProductId(),
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

    /**
     * Get available batches with filtering
     *
     * Filters:
     * 1. product_id = ?
     * 2. active = true
     * 3. quantity > 0
     * 4. If reject_near_expiry = true, filter out near-expiry batches
     *
     * @param productId Product ID
     * @param rejectNearExpiry Reject near-expiry flag
     * @param nearExpiryDays Near-expiry threshold (days)
     * @return List<InventoryBatch> Available batches
     */
    private List<InventoryBatch> getAvailableBatches(
        Long productId,
        Boolean rejectNearExpiry,
        Integer nearExpiryDays
    ) {
        log.debug("🔍 Querying available batches: productId={}, rejectNearExpiry={}, nearExpiryDays={}",
            productId, rejectNearExpiry, nearExpiryDays);

        // Query all active batches for product
        List<InventoryBatch> batches = inventoryBatchRepository
            .findByProductIdAndActiveOrderByExpiryDateAsc(productId, true);

        // Filter out batches with quantity = 0
        batches = batches.stream()
            .filter(batch -> batch.getQuantity() > 0)
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

    /**
     * Check if quantity is full box
     *
     * Formula: quantity % per_pack_qty == 0
     *
     * @param quantity Quantity to check
     * @param perPackQty Box size
     * @return true if full box
     */
    private boolean isFullBox(Integer quantity, Integer perPackQty) {
        if (quantity == null || perPackQty == null || perPackQty <= 0) {
            return false;
        }

        return quantity % perPackQty == 0;
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
