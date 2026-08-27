package com.wms.system.dto.v45;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmergencyStockCorrectionRequest {
    @NotNull
    private Long productSkuId;

    @NotNull
    private Long locationId;

    private Long inventoryBatchId;
    @Size(max = 50)
    private String batchCode;
    private LocalDate productionDate;
    private LocalDate expiryDate;

    @NotNull
    @Min(0)
    private Integer countedQty;

    @NotBlank
    @Size(max = 50)
    private String reasonCode;

    @NotBlank
    @Size(max = 1000)
    private String reasonDetail;
    @Size(max = 500)
    private String evidenceUrl;
    private Long relatedSalesOrderId;
}
