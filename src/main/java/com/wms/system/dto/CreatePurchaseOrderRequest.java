package com.wms.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Create Purchase Order Request DTO
 *
 * Used for Stage 1: Creating purchase order from Excel or manual input.
 *
 * Business Flow:
 * 1. Upload Excel or manually create purchase order
 * 2. Submit this request to create PO with ORDERING status
 * 3. Generate PO number (PO-YYYYMMDD-XXX format)
 * 4. expiry_date is optional at this stage
 *
 * Validation:
 * - supplierId: required; supplier name is resolved from master data
 * - items: required, at least one item
 * - operatorId: required
 * - operatorName: required
 * - expectedDate: optional
 * - remark: optional
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseOrderRequest {

    /**
     * Supplier master-data ID (required)
     */
    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    /**
     * Purchase order items (required, at least one item)
     */
    @NotEmpty(message = "At least one purchase order item is required")
    @Valid
    private List<CreatePurchaseOrderItemRequest> items;

    /**
     * Expected delivery date (optional)
     */
    private LocalDate expectedDate;

    /**
     * Operator user ID (required)
     */
    @NotNull(message = "Operator ID is required")
    private Long operatorId;

    /**
     * Operator user name (required)
     */
    @NotBlank(message = "Operator name is required")
    private String operatorName;

    /**
     * Remark (optional)
     */
    private String remark;
}
