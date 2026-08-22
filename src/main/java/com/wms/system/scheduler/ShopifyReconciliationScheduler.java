package com.wms.system.scheduler;

import com.wms.system.service.ShopifyReconciliationService;
import com.wms.system.tenant.task.ActiveCompanyTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "wms.integration.shopify.reconcile-enabled",
    havingValue = "true"
)
public class ShopifyReconciliationScheduler {

    private final ShopifyReconciliationService reconciliationService;
    private final ActiveCompanyTaskRunner activeCompanyTaskRunner;

    @Value("${wms.integration.shopify.reconcile-days:3}")
    private int days;

    /** Report-only scheduled reconciliation. Automatic repair is forbidden. */
    @Scheduled(cron = "${wms.integration.shopify.reconcile-cron:0 30 2 * * ?}")
    public void reconcile() {
        activeCompanyTaskRunner.forEachActiveCompany(
            "SHOPIFY_RECONCILIATION",
            tenant -> reconciliationService.reconcileAllActive(days));
    }
}
