package com.wms.system.dto.rma;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/** Commercial decision, deliberately separate from the warehouse quality disposition. */
public record SetRmaRefundEligibilityRequest(
    @NotBlank @Size(max = 500) String reason,
    @NotEmpty List<@Valid Line> lines
) {
    public record Line(@NotNull Long rmaLineId, @NotNull @Min(0) Integer eligibleQuantity) { }
}
