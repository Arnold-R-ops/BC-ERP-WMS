package com.wms.system.dto.inventoryquality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InventoryQualityActionRequest(
    @NotBlank @Size(max = 500) String comment
) {
}
