package com.wms.system.dto.receiving;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseAsnRequest(
    @NotNull @Positive Long warehouseId,
    @Size(max = 100) String supplierShipmentNo,
    @Size(max = 100) String carrier,
    @Size(max = 100) String trackingNo,
    LocalDate expectedArrivalDate,
    @Size(max = 500) String remark,
    @NotEmpty List<@Valid Item> items
) {
    @Schema(name = "CreatePurchaseAsnItemRequest")
    public record Item(
        @NotNull @Positive Long purchaseOrderItemId,
        @NotNull @Positive Integer shippedQuantity,
        LocalDate productionDate,
        LocalDate expiryDate,
        @Size(max = 100) String externalBatchCode,
        @Size(max = 500) String remark
    ) {
    }
}
