package com.wms.system.dto.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
    @Size(min = 1, max = 100)
    String categoryName,

    @Min(0)
    Integer sortOrder,

    @Size(max = 500)
    String description
) {}
