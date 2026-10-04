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
 * 验签失败一律 401；只有可信报文可靠落库后才返回 200。
 * 容量不足或存储失败不确认接收；业务处理由专用后台消费者执行。
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
    private final com.wms.system.tenant.config.TenantSecurityProperties properties;

    @PostMapping("/shopify")
    public ResponseEntity<String> receive(
        @RequestHeader(value = "X-Shopify-Shop-Domain", required = false) String shopDomain,
        @RequestHeader(value = "X-Shopify-Topic", required = false) String topic,
        @RequestHeader(value = "X-Shopify-Webhook-Id", required = false) String webhookId,
        @RequestHeader(value = "X-Shopify-Hmac-Sha256", required = false) String hmac,
        jakarta.servlet.http.HttpServletRequest request
    ) throws java.io.IOException {
        int maximum=properties.getWebhookMaxBodyBytes();
        if(request.getContentLengthLong()>maximum) { webhookService.rejectOversizedIngress(shopDomain); return ResponseEntity.status(413).body("payload too large"); }
        byte[] bytes=request.getInputStream().readNBytes(maximum+1);
        if(bytes.length>maximum) { webhookService.rejectOversizedIngress(shopDomain); return ResponseEntity.status(413).body("payload too large"); }
        String rawBody=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        log.info("收到 Shopify Webhook: shop={}, topic={}, webhookId={}", shopDomain, topic, webhookId);

        ShopifyWebhookService.WebhookOutcome outcome =
            webhookService.handle(shopDomain, topic, webhookId, hmac, rawBody);

        return switch (outcome) {
            case SIGNATURE_INVALID, UNKNOWN_SHOP ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
            case DUPLICATE, ACCEPTED -> ResponseEntity.ok("ok");
            case OVER_LIMIT -> ResponseEntity.status(429).header("Retry-After","60").body("capacity unavailable");
            case CONFLICT -> ResponseEntity.status(409).body("event identity conflict");
            case UNAVAILABLE, PROCESSING_FAILED -> ResponseEntity.status(503).body("receipt not confirmed");
        };
    }
}
