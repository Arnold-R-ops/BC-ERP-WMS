package com.wms.system.dto.inventoryquality;

import com.wms.system.entity.enums.InventoryQualityStatus;

public record InventoryQualityActionLineResponse(
    Long id,
    Long sourceBatchId,
    Long resultBatchId,
    String sourceBatchCode,
    String resultBatchCode,
    Long productSkuId,
    String productSkuCode,
    String productName,
    Long warehouseId,
    String warehouseName,
    Long locationId,
    String locationCode,
    Integer quantity,
    Integer reallocatedReservedQuantity,
    InventoryQualityStatus fromStatus,
    InventoryQualityStatus toStatus
) {
}
