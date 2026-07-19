package com.wms.system.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create Purchase Order Item Request DTO
 *
 * Represents a single line item in a purchase order.
 *
 * Business Rules:
 * - productSkuId: required
 * - orderedQuantity: required, must be > 0
 * - unitCost: optional (can be null for non-financial staff)
 * - expiryDate: optional at Stage 1, required at Stage 2
 * - productionDate: optional
 * - externalBatchCode: optional (supplier's batch code)
 * - remark: optional
 *
 * Validation:
 * - productSkuId: required
 * - orderedQuantity: required, >= 1
 * - unitCost: optional, >= 0 if provided
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseOrderItemRequest {

    /**
     * ProductSku ID (required)
     */
    @NotNull(message = "ProductSku ID is required")
    private Long productSkuId;

    /**
     * Ordered quantity (required, must be > 0)
     */
    @NotNull(message = "Ordered quantity is required")
    @Min(value = 1, message = "Ordered quantity must be greater than 0")
    private Integer orderedQuantity;

    /**
     * Unit cost (optional, privacy field for STAFF role)
     */
    @DecimalMin(value = "0.00", message = "Unit cost cannot be negative")
    private BigDecimal unitCost;

    /**
     * Expiry date (optional at Stage 1, required at Stage 2)
     */
    private LocalDate expiryDate;

    /**
     * Production date (optional)
     */
    private LocalDate productionDate;

    /**
     * External batch code from supplier (optional)
     */
    private String externalBatchCode;

    /**
     * Remark (optional)
     */
    private String remark;
}
