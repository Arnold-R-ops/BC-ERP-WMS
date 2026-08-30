package com.wms.system.dto.inventorydisposition;

public record InventoryDamageAffectedOrderResponse(
    Long salesOrderId,
    Long salesOrderItemId,
    Integer quantity,
    String impact
) { }
