package com.wms.system.dto;

import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stock Adjustment Request DTO
 *
 * Used for inbound/outbound/adjustment operations.
 *
 * Use Cases:
 * 1. Purchase inbound: transactionType=IN, sourceType=PURCHASE_IN
 * 2. Sale outbound: transactionType=OUT, sourceType=SALE_OUT
 * 3. Inventory adjustment: transactionType=ADJUST, sourceType=MANUAL_ADJUST
 *
 * Validation:
 * - All required fields validated with @NotNull/@NotBlank
 * - Quantity must be >= 1
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (English Comments)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequest {

    /**
     * ProductSku ID (required)
     */
    @NotNull(message = "ProductSku ID is required")
    private Long productSkuId;

    /**
     * Location ID (required)
     */
    @NotNull(message = "Location ID is required")
    private Long locationId;

    /**
     * Transaction type (required)
     * - IN: Inbound
     * - OUT: Outbound
     * - ADJUST: Adjustment
     */
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    /**
     * Source type (required)
     * - PURCHASE_IN: Purchase inbound
     * - SALE_OUT: Sale outbound
     * - RETURN_IN: Return inbound
     * - PRODUCTION_OUT: Production outbound
     * - TRANSFER_OUT: Transfer outbound
     * - INVENTORY_GAIN: Inventory gain
     * - INVENTORY_LOSS: Inventory loss
     * - MANUAL_ADJUST: Manual adjustment
     */
    @NotNull(message = "Source type is required")
    private SourceType sourceType;

    /**
     * Adjustment quantity (required, must be > 0)
     */
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantity;

    /**
     * Source order ID (required)
     * Examples:
     * - Purchase order: PO202501110001
     * - Sale order: SO202501110002
     * - Transfer order: TR202501110003
     * - Stock take order: ST202501110004
     */
    @NotBlank(message = "Source order ID is required")
    private String sourceOrderId;

    /**
     * Operator ID (optional, for future extension)
     */
    private Long operatorId;

    /**
     * Operator name (optional, for future extension)
     */
    private String operatorName;

    /**
     * Remark (optional)
     */
    private String remark;

    /**
     * Check if this is an inbound operation
     *
     * @return true if transaction type is IN
     */
    public boolean isInbound() {
        return transactionType == TransactionType.IN;
    }

    /**
     * Check if this is an outbound operation
     *
     * @return true if transaction type is OUT
     */
    public boolean isOutbound() {
        return transactionType == TransactionType.OUT;
    }

    /**
     * Check if this is an adjustment operation
     *
     * @return true if transaction type is ADJUST
     */
    public boolean isAdjustment() {
        return transactionType == TransactionType.ADJUST;
    }
}
