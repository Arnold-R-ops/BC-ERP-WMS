package com.wms.system.scheduler;

import com.wms.system.service.ShopifyIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 集成调度器
 *
 * V3.9 架构：Shopify 集成
 *
 * 功能：
 * - 定时同步 Shopify 订单
 *
 * 调度规则：
 * - Shopify 订单同步：每 5 分钟执行一次
 *
 * 使用说明：
 * 1. 在 application.yml 中启用调度：
 *    spring:
 *      task:
 *        scheduling:
 *          enabled: true
 *
 * 2. 配置 Shopify 集成（在 integration_configs 表中）：
 *    - platform: SHOPIFY
 *    - store_url: mystore.myshopify.com
 *    - access_token: shpat_xxxxx
 *    - is_active: true
 *
 * 3. 如果不需要自动调度，可以在配置中禁用：
 *    spring:
 *      task:
 *        scheduling:
 *          enabled: false
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "wms.integration.shopify.scheduler-enabled", havingValue = "true")
@RequiredArgsConstructor
public class IntegrationScheduler {

    private final ShopifyIntegrationService shopifyIntegrationService;

    /**
     * 同步 Shopify 订单
     *
     * 执行频率：默认每 5 分钟一次，可经配置覆盖（P1-B3）
     *
     * wms.integration.shopify.poll-cron：Webhook 启用后（部署公网并在
     * dev dashboard 配好回调地址），把此值改为如 "0 0/30 * * * ?" 将
     * 轮询降为每 30 分钟的兜底对账——无需改代码。
     */
    @Scheduled(cron = "${wms.integration.shopify.poll-cron:0 0/5 * * * ?}")
    public void syncShopifyOrders() {
        log.info("========== 开始执行 Shopify 订单同步任务 ==========");

        try {
            ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

            log.info("========== Shopify 订单同步任务完成 ========== 成功={}, 跳过={}, 失败={}",
                    result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount());

        } catch (Exception e) {
            log.error("========== Shopify 订单同步任务失败 ==========", e);
        }
    }
}
