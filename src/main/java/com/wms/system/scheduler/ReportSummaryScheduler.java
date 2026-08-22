package com.wms.system.scheduler;

import com.wms.system.service.ReportSummaryRefreshService;
import com.wms.system.tenant.task.ActiveCompanyTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportSummaryScheduler {

    private final ReportSummaryRefreshService reportSummaryRefreshService;
    private final ActiveCompanyTaskRunner activeCompanyTaskRunner;

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void refreshNightlyReportSummaries() {
        log.info("Starting nightly report fact buffer refresh");

        activeCompanyTaskRunner.forEachActiveCompany("REPORT_SUMMARY", tenant -> {
            ReportSummaryRefreshService.RefreshResult result = reportSummaryRefreshService.refreshAll();
            log.info("Nightly report fact buffer refresh completed: companyId={}, result={}",
                tenant.getId(), result);
        });
    }
}
