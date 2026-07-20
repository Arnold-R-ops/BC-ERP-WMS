package com.wms.system.dto;

import com.wms.system.entity.enums.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Purchase Order Response DTO
 *
 * Used for returning purchase order data to frontend.
 *
 * Privacy Protection:
 * - For STAFF role:
 *   - supplier: masked as "***"
 *   - totalCost: set to null
 *   - unitCost in items: set to null
 * - For ADMIN/MANAGER roles:
 *   - All fields visible
 *
 * Business States:
 * - ORDERING: Stage 1 (created, batch codes not generated)
 * - IN_TRANSIT: Stage 2 (batch codes generated, awaiting physical receipt)
 * - PARTIALLY_RECEIVED: Stage 3 (some items received)
 * - COMPLETED: Stage 3 (all items fully received)
 * - CANCELLED: Cancelled by user
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {

    /**
     * Purchase order ID
     */
    private Long id;

    /**
     * Purchase order number (format: PO-YYYYMMDD-XXX)
     */
    private String poNumber;

    /**
     * Supplier name (masked for STAFF role)
     */
    private String supplier;

    /**
     * Supplier master-data ID. Null only for unmatched legacy records.
     */
    private Long supplierId;

    /**
     * Current immutable supplier code from master data.
     */
    private String supplierCode;

    /**
     * Purchase order status
     */
    private PurchaseOrderStatus status;

    /**
     * Total ordered quantity
     */
    private Integer totalQuantity;

    /**
     * Total cost (hidden for STAFF role)
     */
    private BigDecimal totalCost;

    /**
     * Expected delivery date
     */
    private LocalDate expectedDate;

    /**
     * Actual entry date (Stage 3 completion)
     */
    private LocalDateTime actualEntryDate;

    /**
     * Operator user ID
     */
    private Long operatorId;

    /**
     * Operator user name
     */
    private String operatorName;

    /**
     * Remark
     */
    private String remark;

    /**
     * Audit log (state change history)
     */
    private String auditLog;

    /**
     * Purchase order items
     */
    private List<PurchaseOrderItemResponse> items;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;
}
