package com.wms.system.service.inventory;

/**
 * Delta-based command for an existing inventory batch.
 *
 * <p>The expected SKU and location are mandatory guard values. The posting
 * service never relocates a batch and never creates a missing batch.</p>
 */
public record InventoryPostingCommand(
    Long inventoryBatchId,
    Long productSkuId,
    Long locationId,
    Integer delta,
    PostingContext context,
    com.wms.system.entity.StockPackaging packagingAfter
) {
    public InventoryPostingCommand(Long inventoryBatchId, Long productSkuId, Long locationId,
                                   Integer delta, PostingContext context) {
        this(inventoryBatchId, productSkuId, locationId, delta, context, null);
    }
}
