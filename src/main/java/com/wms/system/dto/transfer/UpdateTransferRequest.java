package com.wms.system.dto.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateTransferRequest(
    @NotNull @PositiveOrZero Long version,
    @NotNull @Positive Long sourceWarehouseId,
    @NotNull @Positive Long destinationWarehouseId,
    @NotBlank @Size(max = 500) String reason,
    LocalDateTime estimatedArrivalAt,
    @NotEmpty @Size(max = 200) @Valid List<CreateTransferRequest.Line> lines
) { }
