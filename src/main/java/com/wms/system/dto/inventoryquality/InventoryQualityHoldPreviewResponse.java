package com.wms.system.dto.inventoryquality;

import java.util.List;

public record InventoryQualityHoldPreviewResponse(
    boolean executable,
    boolean confirmationRequired,
    Integer totalReservationQuantityToReallocate,
    Integer totalShortageQuantity,
    List<InventoryQualityHoldPreviewLineResponse> lines
) {
}
