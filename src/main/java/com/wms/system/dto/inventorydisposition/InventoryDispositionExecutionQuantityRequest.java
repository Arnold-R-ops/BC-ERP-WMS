package com.wms.system.dto.inventorydisposition;

import jakarta.validation.constraints.*;

public record InventoryDispositionExecutionQuantityRequest(
    @NotNull @Positive Long lineId,
    @NotNull @Positive Integer quantity
) { }
