package com.wms.system.dto.inventorydisposition;

import jakarta.validation.constraints.*;

public record InventoryDispositionQuantityRequest(
    @NotNull @Positive Long batchId,
    @NotNull @Positive Integer quantity
) { }
