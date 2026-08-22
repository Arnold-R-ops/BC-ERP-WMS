package com.wms.system.service;

import com.wms.system.event.SalesOrderFactsChangedEvent;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderFactsChangedListener {

    private final ReportSummaryRefreshService reportSummaryRefreshService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshSalesDailySummary(SalesOrderFactsChangedEvent event) {
        try {
            TenantContext context = new TenantContext(
                event.companyId(), null, "event.background",
                RequestSurface.TENANT, TenantStatus.ACTIVE);
            int rows = TenantContextHolder.current().isPresent()
                ? reportSummaryRefreshService.refreshSalesDailySummary(
                    event.companyId(), event.summaryDate())
                : TenantContextHolder.runWithTenant(context, () ->
                    reportSummaryRefreshService.refreshSalesDailySummary(
                        event.companyId(), event.summaryDate()));
            log.info(
                "Sales daily summary refreshed after commit: companyId={}, salesOrderId={}, "
                    + "summaryDate={}, reason={}, rows={}",
                event.companyId(),
                event.salesOrderId(),
                event.summaryDate(),
                event.reason(),
                rows
            );
        } catch (RuntimeException exception) {
            // The source transaction has already committed. Preserve the successful
            // archive response and let the scheduled full refresh repair any transient failure.
            log.error(
                "Unable to refresh sales daily summary after commit: companyId={}, "
                    + "salesOrderId={}, summaryDate={}, reason={}",
                event.companyId(),
                event.salesOrderId(),
                event.summaryDate(),
                event.reason(),
                exception
            );
        }
    }
}
