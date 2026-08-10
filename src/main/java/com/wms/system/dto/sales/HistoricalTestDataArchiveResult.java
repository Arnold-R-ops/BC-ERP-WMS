package com.wms.system.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalTestDataArchiveResult {
    private Long salesOrderId;
    private String orderNo;
    private String orderStatus;
    private List<Long> archivedTaskIds;
    private Long auditId;
    private LocalDateTime archivedAt;
    private boolean inventoryChanged;
    private boolean reservationsChanged;
    private boolean stockTransactionsCreated;
}
