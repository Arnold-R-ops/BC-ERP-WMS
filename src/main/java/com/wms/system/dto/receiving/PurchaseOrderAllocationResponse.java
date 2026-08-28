package com.wms.system.dto.receiving;

import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;

public record PurchaseOrderAllocationResponse(
    Long id,
    Long purchaseOrderItemId,
    Long productSkuId,
    String skuCode,
    Long warehouseId,
    String warehouseCode,
    Integer plannedQuantity,
    Integer shippedQuantity,
    Integer receivedQuantity,
    Integer closedQuantity,
    Integer availableToShipQuantity,
    PurchaseOrderAllocationStatus status
) {
}
