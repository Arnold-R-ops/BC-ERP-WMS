package com.wms.system.dto.inventorydisposition;

import jakarta.validation.constraints.*;

public record InventoryDispositionDecisionRequest(
    @NotBlank @Size(max = 500) String comment
) { }
