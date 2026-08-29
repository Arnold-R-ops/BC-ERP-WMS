package com.wms.system.dto.receivinginspection;

import jakarta.validation.constraints.*;

public record CreateReceivingReinspectionRequest(
    @NotNull @Positive Long originalResultId,
    @NotNull @Positive Long inventoryBatchId,
    @NotNull @Positive Integer quantity,
    @NotBlank @Size(max = 500) String reason,
    boolean confirmReservationReallocation
) {
}
