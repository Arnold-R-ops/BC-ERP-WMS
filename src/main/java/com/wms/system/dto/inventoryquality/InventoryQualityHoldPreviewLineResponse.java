package com.wms.system.dto.inventoryquality;

import java.util.List;

public record InventoryQualityHoldPreviewLineResponse(
    Long batchId,
    String batchCode,
    String productSkuCode,
    String productName,
    Long warehouseId,
    String warehouseName,
    String locationCode,
    Integer onHandQuantity,
    Integer reservedQuantity,
    Integer requestedHoldQuantity,
    Integer reservationQuantityToReallocate,
    Integer alternateSaleableQuantity,
    Integer shortageQuantity,
    List<InventoryQualityAffectedOrderResponse> affectedSalesOrders
) {
}
