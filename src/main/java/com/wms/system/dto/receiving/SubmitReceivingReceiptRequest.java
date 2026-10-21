package com.wms.system.dto.receiving;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SubmitReceivingReceiptRequest(
    @Size(max = 500) String remark,
    @NotEmpty List<@Valid Line> lines
) {
    @Schema(name = "ReceivingReceiptLineRequest")
    public record Line(
        @NotNull @Positive Long receivingTaskItemId,
        @NotNull @Positive Long locationId,
        @NotNull @Positive Integer quantity,
        LocalDate productionDate,
        LocalDate expiryDate,
        @Size(max = 100) String externalBatchCode,
        @Valid com.wms.system.entity.StockPackaging packaging
    ) {
        public Line(Long receivingTaskItemId, Long locationId, Integer quantity, LocalDate productionDate,
                    LocalDate expiryDate, String externalBatchCode) {
            this(receivingTaskItemId, locationId, quantity, productionDate, expiryDate, externalBatchCode, null);
        }
    }
}
