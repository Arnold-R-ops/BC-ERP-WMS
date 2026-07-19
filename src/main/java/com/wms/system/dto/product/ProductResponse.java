package com.wms.system.dto.product;

import java.time.LocalDateTime;

public record ProductResponse(
    Long id,
    Integer version,
    String productCode,
    String productName,
    Long categoryId,
    String categoryCode,
    String categoryName,
    Long parentCategoryId,
    String parentCategoryName,
    String brand,
    String description,
    Boolean enabled,
    long skuCount,
    long enabledSkuCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
