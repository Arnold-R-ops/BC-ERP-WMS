package com.wms.system.controller;

import com.wms.system.service.ShopifyIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Shopify 同步手动触发（P1 批次2）
 *
 * 定时调度默认关闭（wms.integration.shopify.scheduler-enabled），此端点
 * 提供受控的手动触发：SKU 映射确认后立即放行被卡订单、联调验证等场景，
 * 不必等待或开启调度器。
 *
 * ⚠️ 会真实拉取店铺订单并转入 WMS（与定时任务同一逻辑），仅管理员可用。
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Slf4j
@RestController
@RequestMapping("/api/integration/shopify")
@RequiredArgsConstructor
public class ShopifySyncController {

    private final ShopifyIntegrationService shopifyIntegrationService;

    @PostMapping("/sync")
    @PreAuthorize("hasAnyAuthority('system:admin', 'TENANT_ADMIN')")
    public ResponseEntity<Map<String, Integer>> syncNow() {
        log.info("手动触发 Shopify 订单同步");
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();
        return ResponseEntity.ok(Map.of(
            "success", result.getSuccessCount(),
            "skipped", result.getSkippedCount(),
            "failed", result.getFailedCount()
        ));
    }
}
