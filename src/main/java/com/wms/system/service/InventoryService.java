package com.wms.system.service;

import com.wms.system.dto.StockAdjustmentRequest;
import com.wms.system.entity.Inventory;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.StockTransaction;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.StockTransactionRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Inventory Management Service
 *
 * Core Responsibilities:
 * 1. Stock adjustment (inbound/outbound/adjustment)
 * 2. Automatic stock transaction recording
 * 3. Concurrency control (optimistic locking + retry mechanism)
 * 4. Exception handling (insufficient stock + lock conflicts)
 *
 * Technical Features:
 * - @Transactional: Ensures data consistency (stock update + transaction creation)
 * - @Retryable: Auto-retry on optimistic lock conflicts (max 3 attempts)
 * - Error Key System: All exceptions use error keys for frontend i18n
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Error Key System + Audit Fields)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductSkuRepository productSkuRepository;
    private final LocationRepository locationRepository;
    private final StockTransactionRepository stockTransactionRepository;

    /**
     * ⭐ Core Method: Adjust stock (inbound/outbound/adjustment)
     *
     * Business Flow:
     * 1. Validation: Check if product and location exist
     * 2. Query or create inventory record
     * 3. Update stock quantity (IN: +, OUT: -, ADJUST: ±)
     * 4. Generate stock transaction (with quantityBefore and quantityAfter for audit)
     * 5. Commit transaction
     *
     * Concurrency Control:
     * - Optimistic locking (@Version) prevents dirty writes
     * - Conflicts trigger auto-retry (max 3 attempts, 1 second delay)
     *
     * Exception Handling (Error Key System):
     * - PRODUCT_SKU_NOT_FOUND: ProductSku does not exist
     * - LOCATION_NOT_FOUND: Location does not exist
     * - STOCK_INSUFFICIENT: Outbound quantity exceeds current stock
     * - STOCK_CONCURRENCY_CONFLICT: Optimistic lock conflict (after 3 retries)
     *
     * @param request Stock adjustment request
     * @return StockTransaction Generated transaction record
     * @throws BusinessException with error key if operation fails
     */
    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        retryFor = {OptimisticLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)  // Retry after 1 second delay
    )
    public StockTransaction adjustStock(StockAdjustmentRequest request) {
        log.info("Starting stock adjustment: productSkuId={}, locationId={}, type={}, quantity={}",
            request.getProductSkuId(),
            request.getLocationId(),
            request.getTransactionType(),
            request.getQuantity()
        );

        try {
            // 1. Validation: Query product and location
            ProductSku product = productSkuRepository.findById(request.getProductSkuId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                    Map.of("productSkuId", request.getProductSkuId())
                ));

            Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.LOCATION_NOT_FOUND,
                    Map.of("locationId", request.getLocationId())
                ));

            // 2. Query or create inventory record
            Inventory inventory = inventoryRepository
                .findByProductSkuAndLocation(product, location)
                .orElseGet(() -> createNewInventory(product, location));

            // Record quantity BEFORE adjustment (for audit trail)
            Integer quantityBefore = inventory.getQuantity();

            // 3. Update stock quantity based on transaction type
            switch (request.getTransactionType()) {
                case IN -> {
                    // Inbound: Increase stock
                    inventory.increaseQuantity(request.getQuantity());
                    log.info("Inbound success: product={}, location={}, quantity={}, before={}, after={}",
                        product.getName(), location.getLocationCode(),
                        request.getQuantity(), quantityBefore, inventory.getQuantity());
                }
                case OUT -> {
                    // Outbound: Decrease stock (check if sufficient)
                    if (inventory.getQuantity() < request.getQuantity()) {
                        // Insufficient stock - throw error with parameters
                        log.error("Outbound failed: Insufficient stock. productSkuId={}, locationId={}, " +
                                "currentStock={}, requestedQuantity={}",
                            product.getId(), location.getId(),
                            inventory.getQuantity(), request.getQuantity());

                        throw new BusinessException(
                            ErrorKeys.STOCK_INSUFFICIENT,
                            Map.of(
                                "productSkuId", product.getId(),
                                "productName", product.getName(),
                                "locationId", location.getId(),
                                "locationCode", location.getLocationCode(),
                                "currentStock", inventory.getQuantity(),
                                "requestedQuantity", request.getQuantity(),
                                "shortage", request.getQuantity() - inventory.getQuantity()
                            )
                        );
                    }

                    inventory.decreaseQuantity(request.getQuantity());
                    log.info("Outbound success: product={}, location={}, quantity={}, before={}, after={}",
                        product.getName(), location.getLocationCode(),
                        request.getQuantity(), quantityBefore, inventory.getQuantity());
                }
                case ADJUST -> {
                    // Adjustment: Directly set quantity (can be positive or negative)
                    inventory.setQuantity(inventory.getQuantity() + request.getQuantity());
                    log.info("Stock adjustment success: product={}, location={}, adjustment={}, before={}, after={}",
                        product.getName(), location.getLocationCode(),
                        request.getQuantity(), quantityBefore, inventory.getQuantity());
                }
            }

            // 4. Save inventory record (may trigger OptimisticLockException)
            Inventory savedInventory = inventoryRepository.save(inventory);

            // 5. Generate stock transaction (with audit fields: quantityBefore and quantityAfter)
            StockTransaction transaction = createStockTransaction(
                request, product, location, quantityBefore, savedInventory.getQuantity()
            );
            StockTransaction savedTransaction = stockTransactionRepository.save(transaction);

            log.info("Stock adjustment completed: transactionId={}, product={}, location={}, type={}, quantity={}",
                savedTransaction.getId(),
                product.getName(),
                location.getLocationCode(),
                request.getTransactionType(),
                request.getQuantity()
            );

            return savedTransaction;

        } catch (OptimisticLockException e) {
            // Optimistic lock conflict: Another user modified this inventory
            log.error("Optimistic lock conflict: productSkuId={}, locationId={}, operationType={}",
                request.getProductSkuId(), request.getLocationId(), request.getTransactionType(), e);

            // Convert to BusinessException (will be retried by @Retryable)
            throw new BusinessException(
                ErrorKeys.STOCK_CONCURRENCY_CONFLICT,
                Map.of(
                    "productSkuId", request.getProductSkuId(),
                    "locationId", request.getLocationId(),
                    "operationType", request.getTransactionType().name(),
                    "retryAttempts", 3
                )
            );
        }
    }

    /**
     * Create new inventory record (when product is first stored in a location)
     *
     * @param product ProductSku entity
     * @param location Location entity
     * @return Inventory New inventory record (initial quantity = 0)
     */
    private Inventory createNewInventory(ProductSku product, Location location) {
        log.info("Creating new inventory record: product={}, location={}",
            product.getName(), location.getLocationCode());

        return Inventory.builder()
            .productSku(product)
            .location(location)
            .quantity(0)  // Initial stock = 0
            .build();
    }

    /**
     * Create stock transaction record (with audit fields)
     *
     * IMPORTANT (Audit Requirements):
     * - quantityBefore: Stock before adjustment
     * - quantityAfter: Stock after adjustment
     * - These fields enable audit trail and historical analysis
     *
     * @param request Stock adjustment request
     * @param product ProductSku entity
     * @param location Location entity
     * @param quantityBefore Stock before adjustment
     * @param quantityAfter Stock after adjustment
     * @return StockTransaction Transaction record
     */
    private StockTransaction createStockTransaction(StockAdjustmentRequest request,
                                                     ProductSku product,
                                                     Location location,
                                                     Integer quantityBefore,
                                                     Integer quantityAfter) {
        return StockTransaction.builder()
            .productSku(product)
            .location(location)
            .transactionType(request.getTransactionType())
            .sourceType(request.getSourceType())
            .quantity(request.getQuantity())
            .quantityBefore(quantityBefore)   // Audit: before
            .quantityAfter(quantityAfter)     // Audit: after
            .sourceOrderId(request.getSourceOrderId())
            .operatorId(request.getOperatorId())
            .operatorName(request.getOperatorName())
            .remark(request.getRemark())
            .build();
    }

    /**
     * Query total stock for a product (sum across all locations)
     *
     * @param productSkuId ProductSku ID
     * @return Integer Total stock (returns 0 if no inventory records found)
     */
    @Transactional(readOnly = true)
    public Integer getTotalStock(Long productSkuId) {
        Integer totalStock = inventoryBatchRepository.sumQuantityByProductSku(productSkuId);
        return totalStock != null ? totalStock : 0;
    }

    @Transactional(readOnly = true)
    public Integer getReservedStock(Long productSkuId) {
        Integer reservedStock = inventoryBatchRepository.sumReservedQuantityByProductSku(productSkuId);
        return reservedStock != null ? reservedStock : 0;
    }

    @Transactional(readOnly = true)
    public Integer getAvailableStock(Long productSkuId) {
        Integer availableStock = inventoryBatchRepository.sumAvailableQuantityByProductSku(productSkuId);
        return availableStock != null ? availableStock : 0;
    }

    /**
     * Query stock distribution for a product (which locations have stock)
     *
     * @param productSkuId ProductSku ID
     * @return List<Inventory> Inventory records
     */
    @Transactional(readOnly = true)
    public List<Inventory> getStockDistribution(Long productSkuId) {
        return inventoryRepository.findByProductSku_Id(productSkuId);
    }

    /**
     * Query inventory records for a location (which products are stored)
     *
     * @param locationId Location ID
     * @return List<Inventory> Inventory records
     */
    @Transactional(readOnly = true)
    public List<Inventory> getStockByLocation(Long locationId) {
        return inventoryRepository.findByLocation_Id(locationId);
    }

    /**
     * Query low stock inventory records (for stock alerts)
     *
     * @return List<Inventory> Low stock records
     */
    @Transactional(readOnly = true)
    public List<Inventory> getLowStockInventories() {
        return inventoryRepository.findLowStockInventories();
    }
}
