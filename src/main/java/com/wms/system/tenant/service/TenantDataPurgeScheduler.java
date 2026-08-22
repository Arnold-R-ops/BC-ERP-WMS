package com.wms.system.tenant.service;

import com.wms.system.tenant.repository.TenantRepository;
import com.wms.system.tenant.model.TenantStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantDataPurgeScheduler {
    private final TenantRepository tenantRepository;
    private final TenantDataPurgeService purgeService;

    @Scheduled(cron = "${wms.tenancy.purge-cron:0 20 * * * *}")
    public void purgeExpiredCompanies() {
        for (Long tenantId : tenantRepository.findIdsDueForPurge(OffsetDateTime.now(),
                TenantStatus.CLOSED, TenantStatus.PURGE_PENDING)) {
            try { purgeService.purgeDueCompany(tenantId); }
            catch (RuntimeException exception) {
                log.error("Company data purge failed for tenantId={}", tenantId, exception);
            }
        }
    }
}
