package com.wms.system.event;

import java.time.LocalDate;

/** Requests a targeted report-fact refresh after a sales-order transaction commits. */
public record SalesOrderFactsChangedEvent(
    Long companyId,
    Long salesOrderId,
    LocalDate summaryDate,
    String reason
) {
}
