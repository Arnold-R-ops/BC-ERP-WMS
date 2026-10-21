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
     * ProductSku ID
     */
    private Long productSkuId;

    /**
     * ProductSku name
     */
    private String productName;

    /**
     * ProductSku barcode
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

    private com.wms.system.entity.StockPackaging countedPackaging;
    private Integer standardPackSize;
    private String baseUnit;
    private Boolean packagingRequired;

    public StocktakeItemResponse(Long id, Long taskId, Long productSkuId, String productName,
        String productBarcode, Long batchId, String batchCode, Long locationId, String locationCode,
        Integer countedQty, Boolean isCounted, Long countedBy, String countedByName,
        LocalDateTime countedAt, String remark) {
        this.id = id; this.taskId = taskId; this.productSkuId = productSkuId; this.productName = productName;
        this.productBarcode = productBarcode; this.batchId = batchId; this.batchCode = batchCode;
        this.locationId = locationId; this.locationCode = locationCode; this.countedQty = countedQty;
        this.isCounted = isCounted; this.countedBy = countedBy; this.countedByName = countedByName;
        this.countedAt = countedAt; this.remark = remark;
    }
}
