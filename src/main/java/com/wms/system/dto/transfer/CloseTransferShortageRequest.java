package com.wms.system.dto.transfer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CloseTransferShortageRequest(
    @NotNull @PositiveOrZero Long version,
    @NotBlank @Size(max = 500) String reason,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "true")
    @AssertTrue boolean confirmed
) { }
