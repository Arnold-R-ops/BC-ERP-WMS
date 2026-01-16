package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Purchase Order Item Response DTO
 *
 * Represents a single line item in a purchase order response.
 *
 * Business Fields:
 * - orderedQuantity: Total quantity ordered
 * - receivedQuantity: Total quantity received (accumulates across multiple receipts)
 * - unitCost: Hidden for STAFF role
 * - expiryDate: Required at Stage 2
 * - batches: List of InventoryBatch records (generated in Stage 2)
 *
 * Privacy Protection:
 * - unitCost: masked for STAFF role
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemResponse {

    /**
     * Purchase order item ID
     */
    private Long id;

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
     * Ordered quantity
     */
    private Integer orderedQuantity;

    /**
     * Received quantity (accumulates across multiple receipts)
     */
    private Integer receivedQuantity;

    /**
     * Unit cost (hidden for STAFF role)
     */
    private BigDecimal unitCost;

    /**
     * Expiry date
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
     * Remark
     */
    private String remark;

    /**
     * Associated batches (generated in Stage 2)
     */
    private List<InventoryBatchResponse> batches;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Check if item is fully received
     *
     * @return true if receivedQuantity == orderedQuantity
     */
    public boolean isFullyReceived() {
        return receivedQuantity != null && receivedQuantity.equals(orderedQuantity);
    }

    /**
     * Get remaining quantity to receive
     *
     * @return orderedQuantity - receivedQuantity
     */
    public Integer getRemainingQuantity() {
        if (receivedQuantity == null) {
            return orderedQuantity;
        }
        return orderedQuantity - receivedQuantity;
    }
}
