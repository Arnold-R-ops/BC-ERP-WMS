package com.wms.system.service.inventory;

import com.wms.system.entity.enums.InventoryQualityStatus;

/** Server-authored command; no client-facing generic transition endpoint exists. */
public record InventoryQualityTransitionCommand(
    Long inventoryBatchId,
    InventoryQualityStatus targetStatus,
    String sourceType,
    String sourceId,
    String reasonCode,
    String reasonDetail,
    Long actorId,
    String actorName,
    String idempotencyKey
) {
}
