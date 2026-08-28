package com.wms.system.dto.purchase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PurchaseRemainderClosureActionRequest(
    @NotBlank @Size(max = 500) String comment
) {
}
