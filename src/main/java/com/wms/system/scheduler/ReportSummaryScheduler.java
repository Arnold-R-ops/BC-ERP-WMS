package com.wms.system.scheduler;

import com.wms.system.service.ReportSummaryRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportSummaryScheduler {

    private final ReportSummaryRefreshService reportSummaryRefreshService;

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void refreshNightlyReportSummaries() {
        log.info("Starting nightly report fact buffer refresh");

        try {
            ReportSummaryRefreshService.RefreshResult result = reportSummaryRefreshService.refreshAll();
            log.info("Nightly report fact buffer refresh completed: {}", result);
        } catch (Exception e) {
            log.error("Nightly report fact buffer refresh failed", e);
        }
    }
}
