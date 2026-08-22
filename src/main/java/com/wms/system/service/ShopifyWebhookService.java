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
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.ChannelWebhookRoute;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import com.wms.system.tenant.repository.ChannelWebhookRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import com.wms.system.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Shopify webhook intake with signed global routing and tenant-scoped processing. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyWebhookService {

    private static final String CHANNEL = "SHOPIFY";

    public static final String TOPIC_ORDER_CREATE = "orders/create";
    public static final String TOPIC_ORDER_CANCELLED = "orders/cancelled";
    public static final String TOPIC_ORDER_UPDATED = "orders/updated";
    public static final String TOPIC_REFUND_CREATE = "refunds/create";

    private final IntegrationConfigRepository integrationConfigRepository;
    private final ChannelWebhookRouteRepository channelWebhookRouteRepository;
    private final TenantRepository tenantRepository;
    private final ChannelRawEventService rawEventService;
    private final IdempotencyService idempotencyService;
    private final ShopifyIntegrationService shopifyIntegrationService;
    private final SalesOrderRepository salesOrderRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final SalesSubmissionService salesSubmissionService;
    private final ObjectMapper objectMapper;

    public enum WebhookOutcome { ACCEPTED, DUPLICATE, SIGNATURE_INVALID, UNKNOWN_SHOP }

    public WebhookOutcome handle(
            String shopDomain,
            String topic,
            String webhookId,
            String hmacHeader,
            String rawBody) {
        String canonicalStore = IntegrationConfig.canonicalizeStoreIdentifier(shopDomain);
        if (!isValidShopifyDomain(canonicalStore)) {
            return WebhookOutcome.UNKNOWN_SHOP;
        }

        // Deliberate global routing query: returns no business entity and no
        // customer/order/inventory data.
        ChannelWebhookRoute route = channelWebhookRouteRepository
            .findByPlatformAndCanonicalStoreIdentifierAndActiveTrue(CHANNEL, canonicalStore)
            .orElse(null);
        if (route == null) {
            log.warn("Rejected webhook for an unknown shop: shop={}", canonicalStore);
            return WebhookOutcome.UNKNOWN_SHOP;
        }
        if (!verifySignature(rawBody, hmacHeader, route.getSigningSecret())) {
            log.warn("Rejected webhook with invalid signature: shop={}, topic={}", canonicalStore, topic);
            return WebhookOutcome.SIGNATURE_INVALID;
        }

        Tenant tenant = tenantRepository.findById(route.getTenantId())
            .filter(candidate -> candidate.getStatus() == TenantStatus.ACTIVE)
            .orElse(null);
        if (tenant == null) {
            return WebhookOutcome.UNKNOWN_SHOP;
        }

        TenantContext context = new TenantContext(
            tenant.getId(),
            tenant.getSlug(),
            canonicalStore,
            RequestSurface.TENANT,
            tenant.getStatus()
        );
        return TenantContextHolder.runWithResolvedTenant(
            context,
            () -> handleForCompany(route, topic, webhookId, rawBody)
        );
    }

    private WebhookOutcome handleForCompany(
            ChannelWebhookRoute route,
            String topic,
            String webhookId,
            String rawBody) {
        IntegrationConfig config = integrationConfigRepository.findById(route.getIntegrationConfigId())
            .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
            .filter(candidate -> route.getCanonicalStoreIdentifier()
                .equals(candidate.getCanonicalStoreIdentifier()))
            .orElse(null);
        if (config == null) {
            return WebhookOutcome.UNKNOWN_SHOP;
        }
        if (!StringUtils.hasText(webhookId)) {
            return WebhookOutcome.SIGNATURE_INVALID;
        }

        if (rawEventService.webhookAlreadyReceived(webhookId)) {
            return WebhookOutcome.DUPLICATE;
        }

        IdempotencyRequest idempotency = null;
        try {
            idempotency = idempotencyService.start(
                webhookId,
                "SHOPIFY_WEBHOOK:" + topic,
                rawBody
            );
            if (idempotency != null && idempotency.getStatus() == IdempotencyStatus.SUCCEEDED) {
                return WebhookOutcome.DUPLICATE;
            }
        } catch (DataIntegrityViolationException | BusinessException conflict) {
            log.warn("Webhook idempotency conflict: webhookId={}", webhookId);
            return WebhookOutcome.DUPLICATE;
        }

        try {
            dispatch(topic, webhookId, rawBody, config);
            idempotencyService.markSucceeded(idempotency, 200, "accepted");
        } catch (Exception exception) {
            log.error("Webhook processing failed and remains available for replay: topic={}",
                topic, exception);
            idempotencyService.markFailed(idempotency, 500, exception.getMessage());
        }
        return WebhookOutcome.ACCEPTED;
    }

    public boolean verifySignature(String rawBody, String hmacHeader, String clientSecret) {
        if (!StringUtils.hasText(hmacHeader) || !StringUtils.hasText(clientSecret) || rawBody == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] expected = Base64.getDecoder().decode(hmacHeader);
            return MessageDigest.isEqual(computed, expected);
        } catch (Exception exception) {
            return false;
        }
    }

    private void dispatch(
            String topic,
            String webhookId,
            String rawBody,
            IntegrationConfig config) throws Exception {
        JsonNode node = objectMapper.readTree(rawBody);
        String externalId = node.path("id").asText(null);

        switch (topic == null ? "" : topic) {
            case TOPIC_ORDER_CREATE -> shopifyIntegrationService.processWebhookOrder(node, config);
            case TOPIC_ORDER_CANCELLED ->
                handleCancelled(node, externalId, webhookId, rawBody, config);
            case TOPIC_ORDER_UPDATED -> {
                ChannelRawEvent event = rawEventService.recordWebhook(
                    CHANNEL, config.getStoreUrl(), ChannelRawEvent.TYPE_ORDER_UPDATED,
                    externalId, rawBody, webhookId);
                rawEventService.markManualReview(
                    event.getId(), "Channel order changed; manual comparison required");
            }
            case TOPIC_REFUND_CREATE -> {
                ChannelRawEvent event = rawEventService.recordWebhook(
                    CHANNEL, config.getStoreUrl(), ChannelRawEvent.TYPE_REFUND,
                    node.path("order_id").asText(null), rawBody, webhookId);
                rawEventService.markSkipped(event.getId(), "Refund retained for future RMA processing");
            }
            default -> {
                ChannelRawEvent event = rawEventService.recordWebhook(
                    CHANNEL, config.getStoreUrl(), "UNHANDLED:" + topic,
                    externalId, rawBody, webhookId);
                rawEventService.markSkipped(event.getId(), "Unhandled webhook topic: " + topic);
            }
        }
    }

    private void handleCancelled(
            JsonNode node,
            String externalId,
            String webhookId,
            String rawBody,
            IntegrationConfig config) {
        ChannelRawEvent event = rawEventService.recordWebhook(
            CHANNEL, config.getStoreUrl(), ChannelRawEvent.TYPE_ORDER_CANCELLED,
            externalId, rawBody, webhookId);

        Optional<SalesOrder> order = salesOrderRepository.findByExternalOrderId(externalId);
        if (order.isEmpty()) {
            rawEventService.markSkipped(event.getId(), "No matching local order");
            return;
        }
        SalesOrder salesOrder = order.get();
        if (salesOrder.getStatus() == SalesOrderStatus.SHIPPED) {
            rawEventService.markManualReview(event.getId(), "Order already shipped");
            return;
        }

        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(salesOrder.getId());
        boolean pickingStarted = tasks.stream().anyMatch(task ->
            task.getStatus() == OutboundTaskStatus.PICKING
                || task.getStatus() == OutboundTaskStatus.COMPLETED);
        if (pickingStarted) {
            rawEventService.markManualReview(event.getId(), "Picking already started");
            return;
        }

        try {
            salesSubmissionService.cancelSalesOrder(
                salesOrder.getId(),
                "Shopify customer cancellation (orders/cancelled webhook)",
                1L
            );
            rawEventService.markProcessed(event.getId());
        } catch (RuntimeException exception) {
            rawEventService.markManualReview(
                event.getId(), "Automatic cancellation failed: " + exception.getMessage());
        }
    }

    private boolean isValidShopifyDomain(String value) {
        return value != null
            && value.matches("^[a-z0-9][a-z0-9-]{0,62}\\.myshopify\\.com$");
    }
}
