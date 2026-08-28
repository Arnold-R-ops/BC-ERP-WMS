package com.wms.system.dto.receiving;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record SavePurchaseOrderAllocationsRequest(
    @NotNull Long purchaseOrderVersion,
    @NotEmpty List<@Valid Row> allocations
) {
    @Schema(name = "PurchaseOrderAllocationRowRequest")
    public record Row(
        @NotNull @Positive Long purchaseOrderItemId,
        @NotNull @Positive Long warehouseId,
        @NotNull @Positive Integer plannedQuantity
    ) {
    }
}
