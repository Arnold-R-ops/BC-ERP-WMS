package com.wms.system.dto.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SalesOrderShipmentRequest {

    @NotBlank(message = "Tracking number is required")
    @Size(max = 100, message = "Tracking number must not exceed 100 characters")
    private String trackingNo;

    @Size(max = 50, message = "Carrier must not exceed 50 characters")
    private String carrier;

    @Size(max = 500, message = "Tracking URL must not exceed 500 characters")
    private String trackingUrl;

    private LocalDateTime shippedAt;

    @Size(max = 500, message = "Remark must not exceed 500 characters")
    private String remark;
}
