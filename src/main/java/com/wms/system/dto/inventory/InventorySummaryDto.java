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
    private Long productId;
    private SkuInfoDto skuInfo;
    private List<String> warehouseNames;
    private String displayQuantity;
    private Integer totalQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private String displayAvailableQuantity;
    private StockStatus stockStatus;
    private LocalDate furthestExpiryDate;
}
