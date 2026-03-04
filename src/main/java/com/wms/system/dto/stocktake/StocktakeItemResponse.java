package com.wms.system.dto.stocktake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stocktake Item Response DTO (Blind Count Version - No Snapshot Qty)
 *
 * V3.8 Architecture: Smart Stocktake System
 *
 * This DTO is used for counting interface (blind count).
 * It does NOT include snapshotQty and differenceQty to prevent cheating.
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StocktakeItemResponse {

    /**
     * Item ID
     */
    private Long id;

    /**
     * Task ID
     */
    private Long taskId;

    /**
     * Product ID
     */
    private Long productId;

    /**
     * Product name
     */
    private String productName;

    /**
     * Product barcode
     */
    private String productBarcode;

    /**
     * Batch ID
     */
    private Long batchId;

    /**
     * Batch code
     */
    private String batchCode;

    /**
     * Location ID
     */
    private Long locationId;

    /**
     * Location code
     */
    private String locationCode;

    /**
     * Counted quantity (actual count)
     */
    private Integer countedQty;

    /**
     * Is counted flag
     */
    private Boolean isCounted;

    /**
     * Counted by user ID
     */
    private Long countedBy;

    /**
     * Counted by user name
     */
    private String countedByName;

    /**
     * Counted at timestamp
     */
    private LocalDateTime countedAt;

    /**
     * Remark
     */
    private String remark;
}
