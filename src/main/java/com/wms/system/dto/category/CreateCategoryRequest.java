package com.wms.system.dto.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,49}$")
    String categoryCode,

    @NotBlank
    @Size(max = 100)
    String categoryName,

    Long parentId,

    @Min(0)
    Integer sortOrder,

    @Size(max = 500)
    String description
) {}
