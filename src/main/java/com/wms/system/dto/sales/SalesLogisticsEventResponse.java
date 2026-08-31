package com.wms.system.dto.sales;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SalesLogisticsEventResponse {
    private Long id;
    private String fromStatus;
    private String toStatus;
    private String toStatusDescription;
    private String source;
    private String reason;
    private Long changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
}
