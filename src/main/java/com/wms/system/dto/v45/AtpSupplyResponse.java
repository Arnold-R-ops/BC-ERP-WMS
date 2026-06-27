package com.wms.system.dto.v45;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AtpSupplyResponse {
    private Long purchaseOrderId;
    private String poNumber;
    private Long purchaseOrderItemId;
    private Long productId;
    private String productName;
    private LocalDate expectedDate;
    private Integer orderedQty;
    private Integer receivedQty;
    private Integer committedQty;
    private Integer availableToPromiseQty;
    private BigDecimal supplierReliabilityScore;
}
