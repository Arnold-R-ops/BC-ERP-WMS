package com.wms.system.service;

import com.wms.system.dto.ReorderSuggestion;
import com.wms.system.entity.Product;
import com.wms.system.entity.StockTransaction;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.StockTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stock Prediction Service
 *
 * Core Responsibilities:
 * 1. Analyze historical outbound data to calculate daily average
 * 2. Generate intelligent reorder suggestions based on lead time and safety stock
 * 3. Predict stockout time
 * 4. Assess reorder urgency
 *
 * Prediction Formula:
 * - Daily Average Outbound = Total outbound in last N days / N days
 * - Suggested Reorder Quantity = (Daily Average × Lead Time) + Safety Stock
 * - Estimated Days Until Stockout = Current Stock / Daily Average
 *
 * Urgency Levels:
 * - CRITICAL: Current stock < Safety stock
 * - HIGH: Estimated stockout days < Lead time
 * - MEDIUM: Estimated stockout days ≈ Lead time (±3 days)
 * - LOW: Stock sufficient
 *
 * IMPORTANT - Timezone Conversion Logic (CLAUDE.md Compliance):
 * =========================================================
 * Problem:
 * - Database stores UTC time (e.g., 2025-01-01T23:00:00+00:00)
 * - This is 2025-01-01 in London (UTC+0)
 * - But 2025-01-02 in China (UTC+8)
 *
 * Solution:
 * - When calculating "last 30 days outbound", convert UTC to Europe/London timezone
 * - Group transactions by London date (not UTC date)
 * - This ensures accurate daily statistics for UK business operations
 *
 * Example:
 * - UTC: 2025-01-01T23:00:00+00:00
 * - London: 2025-01-01T23:00:00 (same date - counted as Jan 1st)
 * - China: 2025-01-02T07:00:00 (different date - but NOT relevant for UK business)
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Timezone Conversion + Error Key System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPredictionService {

    private final ProductRepository productRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final StockTransactionRepository stockTransactionRepository;

    /**
     * Default calculation period (days)
     * Use last 30 days of outbound data for prediction
     */
    private static final int DEFAULT_CALCULATION_PERIOD = 30;

    /**
     * Business timezone: Europe/London
     * All date-based calculations use London timezone (not UTC)
     */
    private static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("Europe/London");

    /**
     * ⭐ Core Method: Get reorder suggestion for a single product
     *
     * Business Flow:
     * 1. Query product information (minStock, leadTime, unitPrice, supplier)
     * 2. Query current total stock (sum across all locations)
     * 3. Query outbound transactions (last N days in London timezone)
     * 4. Calculate daily average outbound
     * 5. Apply prediction formula:
     *    - Suggested Reorder Quantity = (Daily Average × Lead Time) + Safety Stock
     *    - Estimated Days Until Stockout = Current Stock / Daily Average
     * 6. Calculate reorder cost and urgency level
     * 7. Return ReorderSuggestion object
     *
     * Timezone Conversion Logic:
     * ========================
     * - Database stores UTC timestamps (e.g., 2025-01-01T23:00:00+00:00)
     * - Convert to London timezone before grouping by date
     * - Ensures accurate daily statistics for UK business
     *
     * @param productId Product ID
     * @param calculationPeriod Calculation period in days (e.g., 7/14/30/60/90)
     * @return ReorderSuggestion Reorder suggestion
     * @throws BusinessException with error key if product not found
     */
    @Transactional(readOnly = true)
    public ReorderSuggestion getReorderSuggestion(Long productId, Integer calculationPeriod) {
        log.info("Generating reorder suggestion: productId={}, calculationPeriod={} days",
            productId, calculationPeriod);

        // 1. Query product information
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_NOT_FOUND,
                Map.of("productId", productId)
            ));

        // 2. Query current total stock (sum across all locations)
        // Single source of truth is inventory_batch (V3.0 architecture decision);
        // the legacy Inventory table is no longer maintained by inbound/outbound flows.
        Integer currentStock = inventoryBatchRepository.sumQuantityByProduct(productId);
        if (currentStock == null) {
            currentStock = 0;
        }

        // 3. Query historical outbound transactions (last N days in London timezone)
        // IMPORTANT: Define time range in London timezone, then convert to UTC for database query
        ZonedDateTime londonNow = ZonedDateTime.now(BUSINESS_TIMEZONE);
        ZonedDateTime londonStartDate = londonNow.minusDays(calculationPeriod);

        // Convert London time to UTC (LocalDateTime, since JVM timezone is UTC)
        LocalDateTime utcEndDate = londonNow.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        LocalDateTime utcStartDate = londonStartDate.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

        log.debug("Querying outbound transactions: londonPeriod=[{} to {}], utcPeriod=[{} to {}]",
            londonStartDate.toLocalDate(), londonNow.toLocalDate(),
            utcStartDate, utcEndDate);

        List<StockTransaction> outboundTransactions = stockTransactionRepository
            .findMovementHistory(productId, utcStartDate, utcEndDate);

        log.info("Product {} has {} outbound transactions in last {} days (London timezone)",
            product.getName(), outboundTransactions.size(), calculationPeriod);

        // 4. Calculate daily average outbound (with timezone conversion)
        double dailyAverageOutbound = calculateDailyAverageWithTimezoneConversion(
            outboundTransactions, calculationPeriod
        );

        // 5. Calculate suggested reorder quantity
        // Formula: (Daily Average × Lead Time) + Safety Stock
        int suggestedQuantity = (int) Math.ceil(
            (dailyAverageOutbound * product.getLeadTime()) + product.getMinStock()
        );

        // 6. Calculate estimated days until stockout
        double estimatedDaysUntilStockout = 0.0;
        if (dailyAverageOutbound > 0) {
            estimatedDaysUntilStockout = currentStock / dailyAverageOutbound;
        } else {
            // No outbound activity - stock will not run out
            estimatedDaysUntilStockout = Double.MAX_VALUE;
        }

        // 7. Build ReorderSuggestion object
        ReorderSuggestion suggestion = ReorderSuggestion.builder()
            .productId(product.getId())
            .barcode(product.getBarcode())
            .productName(product.getName())
            .specification(product.getSpecification())
            .unitPrice(product.getUnitPrice())
            .supplier(product.getSupplier())
            .currentStock(currentStock)
            .minStock(product.getMinStock())
            .leadTime(product.getLeadTime())
            .dailyAverageOutbound(dailyAverageOutbound)
            .calculationPeriod(calculationPeriod)
            .suggestedReorderQuantity(suggestedQuantity)
            .estimatedDaysUntilStockout(estimatedDaysUntilStockout)
            .build();

        // 8. Calculate urgency level
        suggestion.calculateUrgencyLevel();

        // 9. Calculate reorder cost
        suggestion.calculateEstimatedCost();

        log.info("Reorder suggestion generated: product={}, currentStock={}, dailyAverage={}, " +
                "suggestedReorder={}, urgency={}, estimatedStockout={} days",
            product.getName(),
            currentStock,
            String.format("%.2f", dailyAverageOutbound),
            suggestedQuantity,
            suggestion.getUrgencyLevel().getDescription(),
            String.format("%.1f", estimatedDaysUntilStockout)
        );

        return suggestion;
    }

    /**
     * Get reorder suggestion using default calculation period (30 days)
     *
     * @param productId Product ID
     * @return ReorderSuggestion Reorder suggestion
     */
    @Transactional(readOnly = true)
    public ReorderSuggestion getReorderSuggestion(Long productId) {
        return getReorderSuggestion(productId, DEFAULT_CALCULATION_PERIOD);
    }

    /**
     * ⭐ Batch generate reorder suggestions: Get all low stock products
     *
     * Business Scenarios:
     * 1. Stock alert page: Display products that need reordering
     * 2. Automatic procurement system: Scheduled task to generate purchase orders
     * 3. Inventory analysis report: Analyze product turnover
     *
     * Sorting Rules:
     * - Primary: Urgency level (CRITICAL > HIGH > MEDIUM > LOW)
     * - Secondary: Estimated stockout days (ascending)
     *
     * @param calculationPeriod Calculation period in days
     * @return List<ReorderSuggestion> Reorder suggestions (sorted by urgency)
     */
    @Transactional(readOnly = true)
    public List<ReorderSuggestion> getAllReorderSuggestions(Integer calculationPeriod) {
        log.info("Generating batch reorder suggestions: calculationPeriod={} days", calculationPeriod);

        // 1. Query all low stock products
        List<Product> lowStockProducts = productRepository.findLowStockProducts();
        log.info("Found {} low stock products", lowStockProducts.size());

        // 2. Generate reorder suggestion for each product
        List<ReorderSuggestion> suggestions = lowStockProducts.stream()
            .map(product -> {
                try {
                    return getReorderSuggestion(product.getId(), calculationPeriod);
                } catch (Exception e) {
                    log.error("Failed to generate reorder suggestion: productId={}, productName={}",
                        product.getId(), product.getName(), e);
                    return null;
                }
            })
            .filter(suggestion -> suggestion != null)  // Filter failed suggestions
            .filter(ReorderSuggestion::needsReorder)  // Filter products that don't need reordering
            .sorted(Comparator
                .comparing((ReorderSuggestion s) -> s.getUrgencyLevel().getPriority())  // Sort by urgency
                .thenComparing(ReorderSuggestion::getEstimatedDaysUntilStockout)  // Then by stockout days
            )
            .collect(Collectors.toList());

        log.info("Batch reorder suggestions completed: {} suggestions generated", suggestions.size());

        return suggestions;
    }

    /**
     * Get all reorder suggestions using default period (30 days)
     *
     * @return List<ReorderSuggestion> Reorder suggestions
     */
    @Transactional(readOnly = true)
    public List<ReorderSuggestion> getAllReorderSuggestions() {
        return getAllReorderSuggestions(DEFAULT_CALCULATION_PERIOD);
    }

    /**
     * Get high priority reorder suggestions (CRITICAL and HIGH only)
     *
     * @param calculationPeriod Calculation period in days
     * @return List<ReorderSuggestion> High priority suggestions
     */
    @Transactional(readOnly = true)
    public List<ReorderSuggestion> getHighPriorityReorderSuggestions(Integer calculationPeriod) {
        List<ReorderSuggestion> allSuggestions = getAllReorderSuggestions(calculationPeriod);

        return allSuggestions.stream()
            .filter(s -> s.getUrgencyLevel() == ReorderSuggestion.UrgencyLevel.CRITICAL
                      || s.getUrgencyLevel() == ReorderSuggestion.UrgencyLevel.HIGH)
            .collect(Collectors.toList());
    }

    /**
     * Calculate total reorder cost (for budget planning)
     *
     * @param suggestions List of reorder suggestions
     * @return BigDecimal Total cost
     */
    public BigDecimal calculateTotalReorderCost(List<ReorderSuggestion> suggestions) {
        return suggestions.stream()
            .map(ReorderSuggestion::getEstimatedCost)
            .filter(cost -> cost != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get outbound trend for a product (for chart display)
     *
     * @param productId Product ID
     * @param days Number of days
     * @return List<StockTransaction> Historical outbound records
     */
    @Transactional(readOnly = true)
    public List<StockTransaction> getOutboundTrend(Long productId, Integer days) {
        // Define time range in London timezone
        ZonedDateTime londonNow = ZonedDateTime.now(BUSINESS_TIMEZONE);
        ZonedDateTime londonStartDate = londonNow.minusDays(days);

        // Convert to UTC (LocalDateTime, since JVM timezone is UTC)
        LocalDateTime utcEndDate = londonNow.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        LocalDateTime utcStartDate = londonStartDate.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();

        return stockTransactionRepository.findMovementHistory(productId, utcStartDate, utcEndDate);
    }

    /**
     * ⭐ Calculate daily average outbound with timezone conversion
     *
     * CRITICAL - Timezone Conversion Logic:
     * =====================================
     * Problem:
     * - Database stores UTC time: 2025-01-01T23:00:00+00:00
     * - London time: 2025-01-01T23:00:00 (same date)
     * - China time: 2025-01-02T07:00:00 (different date)
     *
     * Solution:
     * - Convert each transaction's UTC timestamp to London timezone
     * - Group by London date (not UTC date)
     * - Calculate daily average based on London business days
     *
     * Example:
     * Transaction A: createdAt = 2025-01-01T23:00:00+00:00 (UTC)
     *   → London: 2025-01-01T23:00:00 → Date: 2025-01-01 ✅
     *   → China:  2025-01-02T07:00:00 → Date: 2025-01-02 ❌ (not used)
     *
     * This ensures accurate statistics for UK business operations.
     *
     * @param outboundTransactions Outbound transaction list
     * @param calculationPeriod Calculation period in days
     * @return double Daily average outbound quantity
     */
    private double calculateDailyAverageWithTimezoneConversion(
        List<StockTransaction> outboundTransactions,
        Integer calculationPeriod
    ) {
        if (outboundTransactions.isEmpty()) {
            log.info("No outbound transactions found, daily average = 0");
            return 0.0;
        }

        // Group transactions by London date (NOT UTC date)
        Map<LocalDate, List<StockTransaction>> groupedByLondonDate = outboundTransactions.stream()
            .collect(Collectors.groupingBy(transaction -> {
                // Convert UTC LocalDateTime to London timezone
                // Since database stores UTC time and JVM timezone is UTC
                ZonedDateTime utcTime = transaction.getCreatedAt().atZone(ZoneId.of("UTC"));
                ZonedDateTime londonTime = utcTime.withZoneSameInstant(BUSINESS_TIMEZONE);

                // Extract date in London timezone
                LocalDate londonDate = londonTime.toLocalDate();

                log.debug("Transaction: UTC={}, London={}, Date={}",
                    transaction.getCreatedAt(),
                    londonTime,
                    londonDate
                );

                return londonDate;
            }));

        // Calculate total outbound (sum all quantities)
        int totalOutbound = outboundTransactions.stream()
            .mapToInt(StockTransaction::getQuantity)
            .sum();

        // Calculate daily average
        double dailyAverage = (double) totalOutbound / calculationPeriod;

        log.debug("Daily average calculation: totalOutbound={}, calculationPeriod={} days, " +
                "dailyAverage={}, londonDatesWithActivity={}",
            totalOutbound,
            calculationPeriod,
            String.format("%.2f", dailyAverage),
            groupedByLondonDate.size()
        );

        // Log date distribution for debugging
        groupedByLondonDate.forEach((date, transactions) -> {
            int dailyQuantity = transactions.stream()
                .mapToInt(StockTransaction::getQuantity)
                .sum();
            log.debug("London date: {}, transactions={}, quantity={}",
                date, transactions.size(), dailyQuantity);
        });

        return dailyAverage;
    }
}
