package com.wms.system.dto.purchase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PurchaseRemainderClosureCreateRequest(
    @NotBlank @Size(max = 500) String reason
) {
}
