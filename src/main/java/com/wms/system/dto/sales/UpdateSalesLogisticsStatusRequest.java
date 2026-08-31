package com.wms.system.dto.sales;

import com.wms.system.entity.enums.SalesLogisticsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSalesLogisticsStatusRequest(
    @NotNull SalesLogisticsStatus status,
    @NotBlank @Size(max = 500) String reason
) {
}
