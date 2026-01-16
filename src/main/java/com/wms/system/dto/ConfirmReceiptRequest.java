package com.wms.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Confirm Receipt Request DTO (Stage 3: Physical Receipt)
 *
 * Used for Stage 3: Physical receipt with location assignment.
 *
 * Business Flow:
 * 1. Warehouse staff receives physical goods
 * 2. Scan batch codes and assign locations
 * 3. Submit this request to confirm receipt
 * 4. System updates batch locations, records entryDate
 * 5. Generate stock transactions
 * 6. Update PO status to PARTIALLY_RECEIVED or COMPLETED
 *
 * Supports Partial Receiving:
 * - Same PurchaseOrderItem can have multiple InventoryBatch records
 * - Can call this endpoint multiple times for same PO
 * - receivedQuantity accumulates across multiple receipts
 *
 * Validation:
 * - receiveItems: required, at least one item
 * - Each item must have at least one batch location assignment
 * - operatorId: required
 * - operatorName: required
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management - Stage 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmReceiptRequest {

    /**
     * List of items to receive (required)
     */
    @NotEmpty(message = "At least one receive item is required")
    @Valid
    private List<ReceiveItem> receiveItems;

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
     * Receive item (contains batch location assignments)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveItem {

        /**
         * Purchase order item ID (required)
         */
        @NotNull(message = "Item ID is required")
        private Long itemId;

        /**
         * Batch location assignments (required, at least one)
         */
        @NotEmpty(message = "At least one batch location assignment is required")
        @Valid
        private List<BatchLocationAssignment> batches;
    }

    /**
     * Batch location assignment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchLocationAssignment {

        /**
         * Batch code (generated in Stage 2, required)
         */
        @NotBlank(message = "Batch code is required")
        private String batchCode;

        /**
         * Location ID to assign (required)
         */
        @NotNull(message = "Location ID is required")
        private Long locationId;
    }
}
