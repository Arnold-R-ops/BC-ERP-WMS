package com.wms.system.dto.stocktake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Stocktake Item Detail Response DTO (Review Version - With Snapshot Qty)
 *
 * V3.8 Architecture: Smart Stocktake System
 *
 * This DTO extends StocktakeItemResponse and adds snapshotQty and differenceQty.
 * It is used for review interface where managers need to see the differences.
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class StocktakeItemDetailResponse extends StocktakeItemResponse {

    /**
     * Snapshot quantity (book quantity - immutable)
     */
    private Integer snapshotQty;

    /**
     * Difference quantity (countedQty - snapshotQty)
     * Positive: surplus, Negative: shortage, Zero: match
     */
    private Integer differenceQty;

    @Builder(builderMethodName = "detailBuilder")
    public StocktakeItemDetailResponse(
        Long id,
        Long taskId,
        Long productSkuId,
        String productName,
        String productBarcode,
        Long batchId,
        String batchCode,
        Long locationId,
        String locationCode,
        Integer countedQty,
        Boolean isCounted,
        Long countedBy,
        String countedByName,
        java.time.LocalDateTime countedAt,
        String remark,
        Integer snapshotQty,
        Integer differenceQty
    ) {
        super(id, taskId, productSkuId, productName, productBarcode, batchId, batchCode,
            locationId, locationCode, countedQty, isCounted, countedBy, countedByName,
            countedAt, remark);
        this.snapshotQty = snapshotQty;
        this.differenceQty = differenceQty;
    }
}
