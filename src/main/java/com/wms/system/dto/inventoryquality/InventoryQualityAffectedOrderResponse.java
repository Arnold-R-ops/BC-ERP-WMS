package com.wms.system.dto.inventoryquality;

public record InventoryQualityAffectedOrderResponse(
    Long salesOrderId,
    Long salesOrderItemId,
    Integer quantity
) {
}
