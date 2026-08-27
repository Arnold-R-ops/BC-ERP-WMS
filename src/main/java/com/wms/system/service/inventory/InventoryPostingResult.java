package com.wms.system.service.inventory;

/**
 * Stable result of applying an inventory delta to one existing batch.
 */
public record InventoryPostingResult(
    Long inventoryBatchId,
    String batchCode,
    Long productSkuId,
    Long locationId,
    Long warehouseId,
    Integer delta,
    Integer quantityBefore,
    Integer quantityAfter,
    boolean changed,
    Long stockTransactionId,
    String postingId,
    String idempotencyKey
) {
}
