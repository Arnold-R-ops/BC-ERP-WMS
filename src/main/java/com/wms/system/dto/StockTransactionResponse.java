package com.wms.system.dto;

import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stock Transaction Response DTO
 *
 * Returned by inventory adjustment API.
 * Contains transaction details with audit fields (quantityBefore, quantityAfter).
 *
 * IMPORTANT:
 * - DO NOT expose Entity classes directly in Controller
 * - Use DTO to decouple API contract from database schema
 * - Allows flexible API evolution without breaking changes
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Response DTO)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransactionResponse {

    /**
     * Transaction ID
     */
    private Long id;

    /**
     * Product ID
     */
    private Long productId;

    /**
     * Product name (for display convenience)
     */
    private String productName;

    /**
     * Product barcode
     */
    private String productBarcode;

    /**
     * Location ID
     */
    private Long locationId;

    /**
     * Location code (for display convenience)
     */
    private String locationCode;

    /**
     * Transaction type (IN/OUT/ADJUST)
     */
    private TransactionType transactionType;

    /**
     * Source type (PURCHASE_IN, SALE_OUT, etc.)
     */
    private SourceType sourceType;

    /**
     * Adjustment quantity
     */
    private Integer quantity;

    /**
     * Stock quantity BEFORE adjustment (audit field)
     */
    private Integer quantityBefore;

    /**
     * Stock quantity AFTER adjustment (audit field)
     */
    private Integer quantityAfter;

    /**
     * Source order ID
     */
    private String sourceOrderId;

    /**
     * Operator ID
     */
    private Long operatorId;

    /**
     * Operator name
     */
    private String operatorName;

    /**
     * Remark
     */
    private String remark;

    /**
     * Transaction creation timestamp (UTC, JVM timezone is UTC)
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;
}
