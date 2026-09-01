package com.wms.system.dto.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record CreateTransferRequest(
    @NotNull @Positive Long sourceWarehouseId,
    @NotNull @Positive Long destinationWarehouseId,
    @NotBlank @Size(max = 500) String reason,
    LocalDateTime estimatedArrivalAt,
    @NotEmpty @Size(max = 200) List<@Valid Line> lines
) {
    @Schema(name = "CreateTransferLineRequest")
    public record Line(
        @NotNull @Positive Long productSkuId,
        @NotNull @Positive Integer quantity,
        @Positive Long preferredBatchId,
        @Positive Long backorderLineId
    ) { }
}
