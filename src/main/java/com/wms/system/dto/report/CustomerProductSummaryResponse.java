package com.wms.system.dto.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CustomerProductSummaryResponse {
    private Long productSkuId;
    private String skuCode;
    private String skuName;
    private String productName;
    private String barcode;
    private Long totalOrderCount;
    private Long totalQuantity;
    private BigDecimal totalAmount;
    private LocalDate firstOrderDate;
    private LocalDate lastOrderDate;
    private BigDecimal averageIntervalDays;
}
