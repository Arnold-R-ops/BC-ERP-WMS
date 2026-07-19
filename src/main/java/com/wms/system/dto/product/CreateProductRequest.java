package com.wms.system.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
    @NotBlank @Size(max = 50) String productCode,
    @NotBlank @Size(max = 200) String productName,
    @NotNull Long categoryId,
    @Size(max = 100) String brand,
    @Size(max = 2000) String description,
    Boolean enabled
) {
}
