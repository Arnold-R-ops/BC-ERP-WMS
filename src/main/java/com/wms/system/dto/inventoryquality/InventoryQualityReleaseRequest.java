package com.wms.system.dto.inventoryquality;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InventoryQualityReleaseRequest(
    @NotEmpty List<@Valid InventoryQualityQuantityRequest> lines,
    @NotBlank @Size(max = 80) String reasonCode,
    @Size(max = 500) String reasonDetail,
    @Size(max = 120) String externalReference
) {
}
