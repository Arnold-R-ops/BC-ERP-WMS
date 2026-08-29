package com.wms.system.dto.inventoryquality;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryQualityQuantityRequest(
    @NotNull Long batchId,
    @NotNull @Min(1) Integer quantity
) {
}
