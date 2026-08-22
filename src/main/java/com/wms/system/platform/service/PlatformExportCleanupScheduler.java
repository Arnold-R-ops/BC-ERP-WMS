package com.wms.system.platform.service;

import com.wms.system.platform.config.PlatformAccessProperties;
import com.wms.system.platform.repository.PlatformExportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class PlatformExportCleanupScheduler {
    private final PlatformExportJobRepository repository;
    private final PlatformAccessProperties properties;

    @Scheduled(cron = "${wms.platform-access.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void removeExpiredFiles() {
        Path root = properties.getExportDirectory().toAbsolutePath().normalize();
        repository.findByStatusAndExpiresAtBefore("COMPLETED", OffsetDateTime.now()).forEach(job -> {
            try {
                if (job.getFilePath() != null) {
                    Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
                    if (path.getParent().equals(root)) Files.deleteIfExists(path);
                }
                job.setStatus("EXPIRED");
                job.setFilePath(null);
            } catch (java.io.IOException ignored) {
                // A later run retries; the audit and job metadata remain intact.
            }
        });
    }
}
