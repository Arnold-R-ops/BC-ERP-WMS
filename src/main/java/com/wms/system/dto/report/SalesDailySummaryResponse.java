package com.wms.system.dto.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SalesDailySummaryResponse {
    private LocalDate summaryDate;
    private Long totalOrderCount;
    private BigDecimal totalAmount;
    private Long draftCount;
    private Long pendingApprovalCount;
    private Long approvedAwaitingShipmentCount;
    private Long shippedCount;
    private Long rejectedCount;
    private Long cancelledCount;
    private Long voidedCount;
    private LocalDateTime refreshedAt;
}
