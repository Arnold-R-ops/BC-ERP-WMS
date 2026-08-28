package com.wms.system.dto.inbound;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class OtherInboundApplicationItemRequest {
    @NotNull
    private Long productSkuId;

    @NotNull
    @Min(1)
    private Integer plannedQuantity;

    private LocalDate productionDate;
    private LocalDate expiryDate;

    @Size(max = 100)
    private String externalBatchCode;

    @Size(max = 500)
    private String remark;
}
