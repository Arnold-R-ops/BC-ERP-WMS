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
     * 执行频率：每 5 分钟执行一次
     * Cron 表达式：0 0/5 * * * ?
     * - 秒：0（每分钟的第 0 秒）
     * - 分：0/5（每 5 分钟）
     * - 时：*（每小时）
     * - 日：*（每天）
     * - 月：*（每月）
     * - 周：?（不指定）
     */
    @Scheduled(cron = "0 0/5 * * * ?")
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
