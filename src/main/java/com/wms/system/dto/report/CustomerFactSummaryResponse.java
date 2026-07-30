package com.wms.system.dto.report;

import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CustomerFactSummaryResponse {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private CustomerType customerType;
    private CustomerSource source;
    private Long totalOrderCount;
    private BigDecimal totalAmount;
    private BigDecimal averageOrderValue;
    private LocalDate lastOrderDate;
    private BigDecimal averageIntervalDays;
    private LocalDateTime refreshedAt;
    private List<CustomerProductSummaryResponse> topProducts;
}
