package com.wms.system.dto.transfer;

import jakarta.validation.constraints.*;

public record TransferDecisionRequest(
    @NotNull @PositiveOrZero Long version,
    @NotBlank @Size(max = 500) String comment
) { }
