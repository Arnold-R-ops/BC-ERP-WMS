package com.wms.system.dto.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record DispatchTransferRequest(
    @NotNull @PositiveOrZero Long version,
    @Size(max = 120) String handoverReference,
    LocalDateTime estimatedArrivalAt,
    @Size(max = 500) String warehouseRemark,
    @NotEmpty @Size(max = 500) List<@Valid Line> lines
) {
    @Schema(name = "DispatchTransferLineRequest")
    public record Line(
        @NotNull @Positive Long allocationId,
        @NotNull @Positive Integer quantity
    ) { }
}
