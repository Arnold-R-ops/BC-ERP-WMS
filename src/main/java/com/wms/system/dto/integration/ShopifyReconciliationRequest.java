package com.wms.system.dto.integration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShopifyReconciliationRequest {

    @NotNull(message = "Integration config id is required")
    private Long configId;

    @Min(value = 1, message = "Reconciliation window must be at least 1 day")
    @Max(value = 30, message = "Reconciliation window must not exceed 30 days")
    private Integer days = 3;
}
