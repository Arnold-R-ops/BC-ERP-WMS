package com.wms.system.dto.supplier;

import java.time.LocalDateTime;

public record SupplierResponse(
    Long id,
    Long companyId,
    String code,
    String name,
    String contact,
    String address,
    String email,
    String phone,
    String remark,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
