package com.wms.system.dto.inventorydisposition;

public record InventoryDispositionExecutionLineResponse(
    Long id,
    Long dispositionLineId,
    Long inventoryBatchId,
    Integer quantity,
    Integer quantityBefore,
    Integer quantityAfter,
    Long stockTransactionId,
    String postingId
) { }
