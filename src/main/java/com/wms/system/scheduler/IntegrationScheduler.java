package com.wms.system.scheduler;

import com.wms.system.service.ShopifyIntegrationService;
import com.wms.system.tenant.task.ActiveCompanyTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polling fallback for Shopify orders, executed independently per company. */
@Slf4j
@Component
@ConditionalOnProperty(name = "wms.integration.shopify.scheduler-enabled", havingValue = "true")
@RequiredArgsConstructor
public class IntegrationScheduler {

    private final ShopifyIntegrationService shopifyIntegrationService;
    private final ActiveCompanyTaskRunner activeCompanyTaskRunner;

    @Scheduled(cron = "${wms.integration.shopify.poll-cron:0 0/5 * * * ?}")
    public void syncShopifyOrders() {
        activeCompanyTaskRunner.forEachActiveCompany("SHOPIFY_ORDER_SYNC", tenant -> {
            ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();
            log.info("Shopify order sync completed: companyId={}, success={}, skipped={}, failed={}",
                tenant.getId(), result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount());
        });
    }
}
