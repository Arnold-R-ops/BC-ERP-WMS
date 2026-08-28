package com.wms.system.service.inventory;

import com.wms.system.entity.enums.InventoryQualityStatus;

public record InventoryQualityTransitionResult(
    Long eventId,
    Long inventoryBatchId,
    InventoryQualityStatus fromStatus,
    InventoryQualityStatus toStatus,
    boolean replayed
) {
}
