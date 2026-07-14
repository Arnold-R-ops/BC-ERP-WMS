package com.wms.system.controller;

import com.wms.system.service.ShopifyWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Shopify Webhook 接收端点（P1 批次3）
 *
 * 免 JWT 认证（Shopify 不会携带本系统令牌），安全由 HMAC-SHA256 验签保证：
 * 验签失败一律 401；验签通过后报文立即留底并尽快返回 200（Shopify 要求
 * 5 秒内响应，超时判定失败并重发——重发由 Webhook-Id 幂等去重兜住）。
 *
 * 启用步骤（部署公网后）：在 Shopify dev dashboard 应用配置的 Webhooks
 * 段订阅 orders/create、orders/cancelled、orders/updated、refunds/create，
 * 回调地址填 https://你的域名/api/webhooks/shopify —— 代码零改动。
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B3 (Webhook Intake)
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class ShopifyWebhookController {

    private final ShopifyWebhookService webhookService;

    @PostMapping("/shopify")
    public ResponseEntity<String> receive(
        @RequestHeader(value = "X-Shopify-Shop-Domain", required = false) String shopDomain,
        @RequestHeader(value = "X-Shopify-Topic", required = false) String topic,
        @RequestHeader(value = "X-Shopify-Webhook-Id", required = false) String webhookId,
        @RequestHeader(value = "X-Shopify-Hmac-Sha256", required = false) String hmac,
        @RequestBody String rawBody
    ) {
        log.info("收到 Shopify Webhook: shop={}, topic={}, webhookId={}", shopDomain, topic, webhookId);

        ShopifyWebhookService.WebhookOutcome outcome =
            webhookService.handle(shopDomain, topic, webhookId, hmac, rawBody);

        return switch (outcome) {
            case SIGNATURE_INVALID, UNKNOWN_SHOP ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
            case DUPLICATE, ACCEPTED -> ResponseEntity.ok("ok");
        };
    }
}
