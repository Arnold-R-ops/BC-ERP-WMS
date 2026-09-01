package com.wms.system.dto.transfer;

import com.wms.system.entity.enums.TransferReceiptDisposition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record ReceiveTransferRequest(
    @NotNull @PositiveOrZero Long version,
    @Size(max = 500) String remark,
    @NotEmpty @Size(max = 500) List<@Valid Line> lines
) {
    @Schema(name = "ReceiveTransferLineRequest")
    public record Line(
        @NotNull @Positive Long dispatchLineId,
        @NotNull @Positive Long locationId,
        @NotNull TransferReceiptDisposition disposition,
        @NotNull @Positive Integer quantity
    ) { }
}
