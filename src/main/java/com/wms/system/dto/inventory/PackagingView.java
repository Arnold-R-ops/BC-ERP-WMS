package com.wms.system.dto.inventory;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.StockPackaging;

public record PackagingView(Long batchId, String batchCode, Long productSkuId, Long warehouseId,
                            Long locationId, String locationCode, Integer quantity, Integer version,
                            Integer reservedQuantity, Long stocktakeTaskId, String baseUnit,
                            Integer standardPackSize, StockPackaging packaging) {
    public static PackagingView of(InventoryBatch batch) {
        var product = batch.getProductSku();
        int size = batch.getPackaging() != null ? batch.getPackaging().packSize()
            : product.getConversionRate() == null || product.getConversionRate() < 1 ? 1 : product.getConversionRate();
        return new PackagingView(batch.getId(), batch.getBatchCode(), product.getId(),
            batch.getLocation().getWarehouse().getId(), batch.getLocation().getId(), batch.getLocationCode(),
            batch.getQuantity(), batch.getVersion(), batch.getReservedQuantity(), batch.getStocktakeTaskId(),
            product.getBaseUnit(), size, batch.getPackaging());
    }
}
