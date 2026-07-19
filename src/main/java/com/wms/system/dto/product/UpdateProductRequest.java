package com.wms.system.dto.product;

import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
    @Size(max = 200) String productName,
    Long categoryId,
    @Size(max = 100) String brand,
    @Size(max = 2000) String description
) {
}
