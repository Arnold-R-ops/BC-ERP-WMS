package com.wms.system.dto.inventorydisposition;

import java.util.List;

public record InventoryDamagePreviewLineResponse(
    Long batchId,
    String batchCode,
    String productSkuCode,
    String productName,
    Long warehouseId,
    String warehouseName,
    String locationCode,
    Integer onHandQuantity,
    Integer reservedQuantity,
    Integer requestedDamageQuantity,
    Integer reallocatedQuantity,
    Integer backorderedQuantity,
    Integer qualityExceptionQuantity,
    List<InventoryDamageAffectedOrderResponse> affectedSalesOrders
) { }
