package com.wms.system.dto.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomerFactSummaryResponse {
    private Long customerId;
    private Long totalOrderCount;
    private BigDecimal totalAmount;
    private LocalDate lastOrderDate;
    private BigDecimal averageIntervalDays;
    private LocalDateTime refreshedAt;
}
