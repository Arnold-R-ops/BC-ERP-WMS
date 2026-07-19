package com.wms.system.dto.v45;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmergencyStockCorrectionRequest {
    @NotNull
    private Long productSkuId;

    @NotNull
    private Long locationId;

    private Long inventoryBatchId;
    private String batchCode;
    private LocalDate productionDate;
    private LocalDate expiryDate;

    @NotNull
    @Min(0)
    private Integer countedQty;

    @NotBlank
    private String reasonCode;

    private String reasonDetail;
    private String evidenceUrl;
    private Long relatedSalesOrderId;
}
