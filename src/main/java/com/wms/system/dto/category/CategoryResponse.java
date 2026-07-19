package com.wms.system.dto.category;

import java.time.LocalDateTime;
import java.util.List;

public record CategoryResponse(
    Long id,
    Long companyId,
    Long parentId,
    String categoryCode,
    String categoryName,
    Integer level,
    Integer sortOrder,
    Boolean enabled,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<CategoryResponse> children
) {}
