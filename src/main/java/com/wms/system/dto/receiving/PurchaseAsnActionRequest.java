package com.wms.system.dto.receiving;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PurchaseAsnActionRequest(
    @NotBlank @Size(max = 500) String reason
) {
}
