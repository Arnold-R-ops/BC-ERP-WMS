package com.wms.system.dto.inventorydisposition;

import java.util.List;

public record InventoryDamagePreviewResponse(
    boolean confirmationRequired,
    Integer totalReallocatedQuantity,
    Integer totalBackorderedQuantity,
    Integer totalQualityExceptionQuantity,
    List<InventoryDamagePreviewLineResponse> lines
) { }
