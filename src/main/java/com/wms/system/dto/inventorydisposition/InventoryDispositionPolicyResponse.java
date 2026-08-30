package com.wms.system.dto.inventorydisposition;

import com.wms.system.entity.enums.InventoryDispositionReviewMode;
import java.util.List;

public record InventoryDispositionPolicyResponse(
    InventoryDispositionReviewMode reviewMode,
    boolean automaticExpiryEnabled,
    String businessTimezone,
    List<InventoryDispositionReviewMode> supportedReviewModes
) { }
