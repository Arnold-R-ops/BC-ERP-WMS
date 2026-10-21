package com.wms.system.dto.inventory;

import com.wms.system.entity.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * SKU-level inventory summary view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySummaryDto {
    private Long productSkuId;
    private SkuInfoDto skuInfo;
    private List<String> warehouseNames;
    private String displayQuantity;
    private Integer sealedBoxes;
    private Integer openedBoxes;
    private Integer unverifiedQuantity;
    private Integer totalQuantity;
    /** Dispatched between warehouses and not yet received or closed as shortage. */
    private Integer inTransitQuantity;
    /** Company-owned physical stock: warehouse OnHand plus in-transit quantity. */
    private Integer companyOwnedQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private String displayAvailableQuantity;
    private Integer pendingInspectionQuantity;
    private Integer quarantinedQuantity;
    private Integer damagedQuantity;
    private Integer expiredQuantity;
    private Integer recallHoldQuantity;
    private Integer returnInspectionQuantity;
    private StockStatus stockStatus;
    private LocalDate furthestExpiryDate;
}
