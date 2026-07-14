package com.wms.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.ChannelRawEvent;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.entity.OutboundTask;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.repository.IntegrationConfigRepository;
import com.wms.system.repository.OutboundTaskRepository;
import com.wms.system.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Shopify Webhook 处理服务（P1 批次3）
 *
 * 安全与可靠性三道闸：
 * 1. HMAC-SHA256 验签（X-Shopify-Hmac-Sha256，密钥 = 应用 Client Secret）
 * 2. 幂等：X-Shopify-Webhook-Id 去重（接线 V4.4 休眠的 IdempotencyService）
 * 3. 快收慢处理：报文先落库（channel_raw_events, source=WEBHOOK），处理
 *    失败不影响 200 响应——orders/create 失败件由轮询兜底重试放行
 *
 * 事件策略（2026-07-11 用户决策）：
 * - orders/create    → 复用批次2 转单管道（SKU 漏斗/待映射队列/独立事务）
 * - orders/cancelled → 未拣货自动取消（释放预留）；拣货中/已发货 → 待人工
 * - orders/updated   → 只留底，标记待人工比对
 * - refunds/create   → 只留底（P2 做 RMA 时的数据积累）
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B3 (Webhook Intake)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyWebhookService {

    private final IntegrationConfigRepository integrationConfigRepository;
    private final ChannelRawEventService rawEventService;
    private final IdempotencyService idempotencyService;
    private final ShopifyIntegrationService shopifyIntegrationService;
    private final SalesOrderRepository salesOrderRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final SalesSubmissionService salesSubmissionService;
    private final ObjectMapper objectMapper;

    private static final String CHANNEL = "SHOPIFY";

    public static final String TOPIC_ORDER_CREATE = "orders/create";
    public static final String TOPIC_ORDER_CANCELLED = "orders/cancelled";
    public static final String TOPIC_ORDER_UPDATED = "orders/updated";
    public static final String TOPIC_REFUND_CREATE = "refunds/create";

    /**
     * 处理结果：控制器据此决定 HTTP 状态（验签失败 401，其余一律 200）
     */
    public enum WebhookOutcome { ACCEPTED, DUPLICATE, SIGNATURE_INVALID, UNKNOWN_SHOP }

    /**
     * Webhook 入口：验签 → 幂等 → 按主题分发
     *
     * 约定：除验签/店铺识别失败外一律返回 ACCEPTED——报文已留底，
     * 业务层面的失败走各自的重试/人工通道，不让 Shopify 无谓重发。
     */
    public WebhookOutcome handle(String shopDomain, String topic, String webhookId,
                                 String hmacHeader, String rawBody) {
        // 1. 店铺定位（Webhook 用 X-Shopify-Shop-Domain 声明来源）
        Optional<IntegrationConfig> configOpt = integrationConfigRepository
            .findFirstByPlatformAndStoreUrlAndIsActiveTrue(CHANNEL, shopDomain);
        if (configOpt.isEmpty()) {
            log.warn("Webhook 来自未配置的店铺，拒绝: shopDomain={}", shopDomain);
            return WebhookOutcome.UNKNOWN_SHOP;
        }
        IntegrationConfig config = configOpt.get();

        // 2. HMAC 验签（常数时间比较，防时序侧信道）
        if (!verifySignature(rawBody, hmacHeader, config.getClientSecret())) {
            log.warn("Webhook 验签失败，拒绝: shopDomain={}, topic={}", shopDomain, topic);
            return WebhookOutcome.SIGNATURE_INVALID;
        }

        // 3. 幂等：Webhook-Id 精确去重（Shopify 对未确认推送会重发）
        if (rawEventService.webhookAlreadyReceived(webhookId)) {
            log.info("Webhook 重发去重: webhookId={}, topic={}", webhookId, topic);
            return WebhookOutcome.DUPLICATE;
        }
        IdempotencyRequest idem = null;
        try {
            idem = idempotencyService.start(webhookId, "SHOPIFY_WEBHOOK:" + topic, rawBody);
            if (idem != null && idem.getStatus() == IdempotencyStatus.SUCCEEDED) {
                log.info("Webhook 幂等命中（已成功处理过）: webhookId={}", webhookId);
                return WebhookOutcome.DUPLICATE;
            }
        } catch (Exception e) {
            // 幂等键冲突（同 id 不同报文）：可疑但不值得让 Shopify 重发，记录后继续
            log.warn("Webhook 幂等键冲突: webhookId={}, error={}", webhookId, e.getMessage());
        }

        // 4. 按主题分发（处理失败不上抛——报文已留底，各走各的补救通道）
        try {
            dispatch(topic, webhookId, rawBody, config);
            idempotencyService.markSucceeded(idem, 200, "accepted");
        } catch (Exception e) {
            log.error("Webhook 处理异常（报文已留底，等待重试/人工）: topic={}, error={}", topic, e.getMessage(), e);
            idempotencyService.markFailed(idem, 500, e.getMessage());
        }
        return WebhookOutcome.ACCEPTED;
    }

    /**
     * HMAC-SHA256 验签：Base64(HmacSHA256(rawBody, clientSecret)) 与请求头比对
     */
    public boolean verifySignature(String rawBody, String hmacHeader, String clientSecret) {
        if (!StringUtils.hasText(hmacHeader) || !StringUtils.hasText(clientSecret)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] expected = Base64.getDecoder().decode(hmacHeader);
            return MessageDigest.isEqual(computed, expected);
        } catch (Exception e) {
            log.error("HMAC 计算失败: {}", e.getMessage());
            return false;
        }
    }

    private void dispatch(String topic, String webhookId, String rawBody, IntegrationConfig config)
            throws Exception {
        JsonNode node = objectMapper.readTree(rawBody);
        String externalId = node.path("id").asText(null);

        switch (topic == null ? "" : topic) {
            case TOPIC_ORDER_CREATE -> {
                // 复用批次2 管道；管道内部自建 TYPE_ORDER 留底（source=WEBHOOK）
                ShopifyIntegrationService.SyncResult result =
                    shopifyIntegrationService.processWebhookOrder(node, config);
                log.info("Webhook 新订单处理: externalId={}, 成功={}, 跳过={}, 失败={}",
                    externalId, result.getSuccessCount(), result.getSkippedCount(), result.getFailedCount());
            }
            case TOPIC_ORDER_CANCELLED -> handleCancelled(node, externalId, webhookId, rawBody, config);
            case TOPIC_ORDER_UPDATED -> {
                ChannelRawEvent event = rawEventService.recordWebhook(CHANNEL, config.getStoreUrl(),
                    ChannelRawEvent.TYPE_ORDER_UPDATED, externalId, rawBody, webhookId);
                rawEventService.markManualReview(event.getId(),
                    "渠道侧订单被修改，请人工比对（订单号 " + node.path("name").asText("?") + "）");
            }
            case TOPIC_REFUND_CREATE -> {
                ChannelRawEvent event = rawEventService.recordWebhook(CHANNEL, config.getStoreUrl(),
                    ChannelRawEvent.TYPE_REFUND, node.path("order_id").asText(null), rawBody, webhookId);
                rawEventService.markSkipped(event.getId(), "退款报文留底（P2 RMA 数据积累），暂不处理");
            }
            default -> {
                ChannelRawEvent event = rawEventService.recordWebhook(CHANNEL, config.getStoreUrl(),
                    "UNHANDLED:" + topic, externalId, rawBody, webhookId);
                rawEventService.markSkipped(event.getId(), "未订阅处理的主题: " + topic);
            }
        }
    }

    /**
     * 取消事件（用户决策：未拣货自动取消，拣货中/已发货待人工）
     */
    private void handleCancelled(JsonNode node, String externalId, String webhookId,
                                 String rawBody, IntegrationConfig config) {
        ChannelRawEvent event = rawEventService.recordWebhook(CHANNEL, config.getStoreUrl(),
            ChannelRawEvent.TYPE_ORDER_CANCELLED, externalId, rawBody, webhookId);

        Optional<SalesOrder> orderOpt = salesOrderRepository.findByExternalOrderId(externalId);
        if (orderOpt.isEmpty()) {
            rawEventService.markSkipped(event.getId(), "本地无对应订单（可能从未同步或已被清理）");
            return;
        }
        SalesOrder order = orderOpt.get();

        if (order.getStatus() == SalesOrderStatus.SHIPPED) {
            rawEventService.markManualReview(event.getId(),
                "订单已发货，无法自动取消（本地订单 " + order.getOrderNo() + "），请人工处理退货流程");
            return;
        }

        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(order.getId());
        boolean pickingStarted = tasks.stream().anyMatch(t ->
            t.getStatus() == OutboundTaskStatus.PICKING || t.getStatus() == OutboundTaskStatus.COMPLETED);
        if (pickingStarted) {
            rawEventService.markManualReview(event.getId(),
                "拣货已开始，不自动取消（本地订单 " + order.getOrderNo() + "），请人工确认后处理");
            return;
        }

        try {
            // 未拣货：自动业务取消（CANCELLED），释放预留库存
            salesSubmissionService.cancelSalesOrder(order.getId(),
                "Shopify 渠道顾客取消（webhook orders/cancelled）", 1L);
            rawEventService.markProcessed(event.getId());
            log.info("Webhook 自动取消订单成功: 本地订单={}, externalId={}", order.getOrderNo(), externalId);
        } catch (Exception e) {
            log.error("Webhook 自动取消失败，转人工: 本地订单={}, error={}", order.getOrderNo(), e.getMessage());
            rawEventService.markManualReview(event.getId(),
                "自动取消失败（" + e.getMessage() + "），请人工处理");
        }
    }
}
