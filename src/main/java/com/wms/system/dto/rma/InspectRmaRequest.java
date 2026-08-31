package com.wms.system.dto.rma;

import com.wms.system.entity.enums.RmaDisposition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record InspectRmaRequest(
    @Size(max = 500) String reasonDetail,
    @NotEmpty List<@Valid Line> lines
) {
    public record Line(
        @NotNull Long receiptLineId,
        @NotNull RmaDisposition disposition,
        @NotNull @Min(1) Integer quantity
    ) { }
}
