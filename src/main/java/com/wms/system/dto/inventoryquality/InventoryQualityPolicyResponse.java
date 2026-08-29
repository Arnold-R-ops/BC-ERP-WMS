package com.wms.system.dto.inventoryquality;

import com.wms.system.entity.enums.InventoryQualityReleaseMode;

import java.util.List;

public record InventoryQualityPolicyResponse(
    InventoryQualityReleaseMode releaseMode,
    List<InventoryQualityReleaseMode> availableModes
) {
}
