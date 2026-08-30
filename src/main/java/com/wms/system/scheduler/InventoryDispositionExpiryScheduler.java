package com.wms.system.scheduler;

import com.wms.system.service.inventory.InventoryDispositionPolicyService;
import com.wms.system.service.inventory.InventoryDispositionService;
import com.wms.system.tenant.task.ActiveCompanyTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Runs hourly so every tenant is evaluated shortly after its own local midnight. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDispositionExpiryScheduler {
    private final ActiveCompanyTaskRunner companyTaskRunner;
    private final InventoryDispositionPolicyService policyService;
    private final InventoryDispositionService dispositionService;

    @Scheduled(cron = "${wms.inventory-disposition.expiry-cron:0 15 * * * *}")
    public void markExpiredInventory() {
        companyTaskRunner.forEachActiveCompany("INVENTORY_EXPIRY_MARK", tenant -> {
            if (!policyService.automaticExpiryEnabled()) return;
            LocalDate businessDate = LocalDate.now(ZoneId.of(policyService.businessTimezone()));
            for (Long batchId : dispositionService.dueBatchIds(businessDate)) {
                try {
                    dispositionService.markExpired(batchId, businessDate);
                } catch (RuntimeException exception) {
                    log.error("Automatic inventory expiry failed: companyId={}, batchId={}, businessDate={}",
                        tenant.getId(), batchId, businessDate, exception);
                }
            }
        });
    }
}
