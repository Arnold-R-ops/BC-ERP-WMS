package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Inventory Batch Response DTO
 *
 * Used for returning batch data to frontend.
 *
 * Business Fields:
 * - batchCode: Hashids generated code (e.g., R7M4K9)
 * - quantity: Current stock quantity (V3.0: single source of truth)
 * - initialQuantity: Initial quantity (for turnover calculation)
 * - expiryDate: For FIFO sorting
 * - entryDate: Physical receipt time (Stage 3)
 * - location: Assigned location (Stage 3)
 * - active: Batch validity flag
 *
 * Time Fields Distinction:
 * - createdAt: Batch code generation time (Stage 2)
 * - entryDate: Physical receipt time (Stage 3)
 *
 * V3.0 Architecture:
 * - This DTO represents the ONLY inventory data source
 * - Total stock = SUM(quantity) WHERE active = true
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 3.0 (Single Source of Truth Architecture)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryBatchResponse {

    /**
     * Batch ID
     */
    private Long id;

    /**
     * Batch code (Hashids generated, e.g., R7M4K9)
     */
    private String batchCode;

    /**
     * Product ID
     */
    private Long productId;

    /**
     * Product name (for display)
     */
    private String productName;

    /**
     * Product barcode (for display)
     */
    private String productBarcode;

    /**
     * Current quantity (V3.0: single source of truth)
     */
    private Integer quantity;

    /**
     * Quantity reserved by approved sales orders.
     */
    private Integer reservedQuantity;

    /**
     * Quantity currently available for new allocations.
     */
    private Integer availableQuantity;

    /**
     * Initial quantity (for turnover calculation)
     */
    private Integer initialQuantity;

    /**
     * Expiry date (for FIFO sorting)
     */
    private LocalDate expiryDate;

    /**
     * Production date
     */
    private LocalDate productionDate;

    /**
     * External batch code from supplier
     */
    private String externalBatchCode;

    /**
     * Location ID (assigned in Stage 3)
     */
    private Long locationId;

    /**
     * Location code (for display)
     */
    private String locationCode;

    /**
     * Physical entry date (Stage 3)
     */
    private LocalDateTime entryDate;

    /**
     * Batch validity flag
     */
    private Boolean active;

    /**
     * Remark (rollback reason if inactive)
     */
    private String remark;

    /**
     * Batch code generation time (Stage 2)
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Check if batch is exhausted
     *
     * @return true if quantity == 0
     */
    public boolean isExhausted() {
        return quantity != null && quantity == 0;
    }

    /**
     * Check if batch is expired
     *
     * @return true if expiryDate < today
     */
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    /**
     * Check if batch is expiring soon (within 7 days)
     *
     * @return true if expiryDate is within 7 days
     */
    public boolean isExpiringSoon() {
        if (expiryDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        return !expiryDate.isBefore(today) && expiryDate.isBefore(sevenDaysLater);
    }

    /**
     * Get days until expiry
     *
     * @return days until expiry (negative if expired)
     */
    public long getDaysUntilExpiry() {
        if (expiryDate == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /**
     * Get usage rate (percentage of stock used)
     *
     * @return usage rate (0.0 to 1.0)
     */
    public double getUsageRate() {
        if (initialQuantity == null || initialQuantity == 0) {
            return 0.0;
        }
        if (quantity == null) {
            return 1.0;
        }
        return 1.0 - ((double) quantity / initialQuantity);
    }
}
