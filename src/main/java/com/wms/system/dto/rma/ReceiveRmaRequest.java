package com.wms.system.dto.rma;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record ReceiveRmaRequest(
    @Size(max = 500) String remark,
    @NotEmpty List<@Valid Line> lines
) {
    @Schema(name = "ReceiveRmaLineRequest")
    public record Line(
        @NotNull Long rmaLineId,
        @NotNull Long locationId,
        @NotNull @Min(1) Integer quantity
    ) { }
}
