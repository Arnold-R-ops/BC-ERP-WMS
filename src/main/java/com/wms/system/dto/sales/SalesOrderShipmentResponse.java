package com.wms.system.dto.sales;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SalesOrderShipmentResponse {
    private Long id;
    private Long salesOrderId;
    private String trackingNo;
    private String carrier;
    private String trackingUrl;
    private String status;
    private LocalDateTime shippedAt;
    private Long createdBy;
    private String createdByName;
    private Long voidedBy;
    private String voidedByName;
    private LocalDateTime voidedAt;
    private String remark;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
