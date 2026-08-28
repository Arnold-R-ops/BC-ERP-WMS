package com.wms.system.dto.inbound;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class OtherInboundApplicationItemResponse {
    private Long id;
    private Long productSkuId;
    private String productName;
    private String productBarcode;
    private Integer plannedQuantity;
    private Integer receivedQuantity;
    private Integer closedQuantity;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String externalBatchCode;
    private String remark;
}
