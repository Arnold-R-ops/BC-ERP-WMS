package com.wms.system.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformSessionCleanupScheduler {
    private final PlatformSessionService sessions;
    @Scheduled(initialDelay = 3_600_000, fixedDelay = 3_600_000)
    public void cleanup() {
        try { sessions.cleanup(); }
        catch (Exception exception) {
            // Cleanup failure never changes authentication; retry on the next hourly run.
            log.error("Platform session cleanup failed; next hourly run will retry: {}", exception.getClass().getSimpleName());
        }
    }
}
