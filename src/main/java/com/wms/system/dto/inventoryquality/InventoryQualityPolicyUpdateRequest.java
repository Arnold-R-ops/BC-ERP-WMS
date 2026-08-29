package com.wms.system.dto.inventoryquality;

import com.wms.system.entity.enums.InventoryQualityReleaseMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryQualityPolicyUpdateRequest(
    @NotNull InventoryQualityReleaseMode releaseMode,
    @NotBlank @Size(max = 500) String reason,
    @AssertTrue boolean confirmed
) {
}
