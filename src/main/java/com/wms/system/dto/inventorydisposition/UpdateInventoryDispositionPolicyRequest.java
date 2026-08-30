package com.wms.system.dto.inventorydisposition;

import com.wms.system.entity.enums.InventoryDispositionReviewMode;
import jakarta.validation.constraints.*;

public record UpdateInventoryDispositionPolicyRequest(
    @NotNull InventoryDispositionReviewMode reviewMode,
    boolean automaticExpiryEnabled,
    @NotBlank @Size(max = 60) String businessTimezone,
    @NotBlank @Size(max = 500) String reason
) { }
