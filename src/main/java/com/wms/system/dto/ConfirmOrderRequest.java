package com.wms.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Confirm Order Request DTO (Stage 2: ASN Confirmation)
 *
 * Used for Stage 2: Confirm ASN and generate batch codes.
 *
 * Business Flow:
 * 1. User clicks "Confirm/Generate Batch Codes" button
 * 2. Submit this request with item expiry date updates
 * 3. System validates expiry_date for all items (required at Stage 2)
 * 4. Generate Hashids batch codes atomically
 * 5. Update PO status to IN_TRANSIT
 *
 * Validation:
 * - items: required, at least one item with expiry date
 * - Each item must have itemId and expiryDate
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management - Stage 2)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmOrderRequest {

    /**
     * List of item updates with expiry dates
     */
    @NotEmpty(message = "At least one item update is required")
    @Valid
    private List<ItemExpiryUpdate> items;

    /**
     * Item expiry date update
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemExpiryUpdate {

        /**
         * Purchase order item ID (required)
         */
        @NotNull(message = "Item ID is required")
        private Long itemId;

        /**
         * Expiry date (required at Stage 2)
         */
        @NotNull(message = "Expiry date is required for batch generation")
        private LocalDate expiryDate;

        /**
         * Production date (optional)
         */
        private LocalDate productionDate;
    }
}
