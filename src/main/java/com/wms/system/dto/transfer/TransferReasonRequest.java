package com.wms.system.dto.transfer;

import jakarta.validation.constraints.*;

public record TransferReasonRequest(
    @NotNull @PositiveOrZero Long version,
    @NotBlank @Size(max = 500) String reason
) { }
