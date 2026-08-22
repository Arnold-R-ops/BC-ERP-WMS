package com.wms.system.platform.service;

import com.wms.system.platform.repository.PlatformMfaChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class PlatformMfaChallengeCleanupScheduler {
    private final PlatformMfaChallengeRepository repository;

    @Scheduled(cron = "${wms.platform-mfa.challenge-cleanup-cron:0 10 * * * *}")
    @Transactional
    public void cleanup() {
        repository.deleteConsumedOrExpired(OffsetDateTime.now());
    }
}
