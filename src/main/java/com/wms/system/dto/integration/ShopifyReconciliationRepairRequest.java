package com.wms.system.dto.integration;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ShopifyReconciliationRepairRequest {

    @NotEmpty(message = "At least one reconciliation event id is required")
    @Size(max = 50, message = "At most 50 reconciliation events can be repaired at once")
    private List<@jakarta.validation.constraints.NotNull(message = "Reconciliation event id must not be null") Long> rawEventIds;
}
