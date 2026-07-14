package com.wms.system.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShopifyWebhookService 单元测试（P1 批次3）
 *
 * 签名用与生产相同的 HMAC-SHA256 真实计算，非桩。
 */
@ExtendWith(MockitoExtension.class)
class ShopifyWebhookServiceTest {

    @Mock
    private IntegrationConfigRepository integrationConfigRepository;

    @Mock
    private ChannelRawEventService rawEventService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ShopifyIntegrationService shopifyIntegrationService;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private OutboundTaskRepository outboundTaskRepository;

    @Mock
    private SalesSubmissionService salesSubmissionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ShopifyWebhookService webhookService;

    private static final String SECRET = "shpss_test_secret";
    private static final String SHOP = "test-store.myshopify.com";
    private static final String ORDER_BODY = "{\"id\":12345,\"name\":\"#1001\",\"email\":\"a@b.com\",\"line_items\":[]}";

    private IntegrationConfig config;
    private ChannelRawEvent event;

    @BeforeEach
    void setUp() {
        config = IntegrationConfig.builder()
            .id(1L).platform("SHOPIFY").storeUrl(SHOP)
            .clientId("cid").clientSecret(SECRET).isActive(true)
            .build();
        event = ChannelRawEvent.builder()
            .id(7L).channel("SHOPIFY").status(ChannelRawEvent.STATUS_RECEIVED)
            .build();
    }

    /**
     * 与生产实现一致的签名计算（测试端独立实现，交叉验证）
     */
    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private void stubKnownShop() {
        when(integrationConfigRepository.findFirstByPlatformAndStoreUrlAndIsActiveTrue("SHOPIFY", SHOP))
            .thenReturn(Optional.of(config));
    }

    // ===== 验签 =====

    @Test
    void verifySignature_ValidAndTampered() throws Exception {
        String valid = sign(ORDER_BODY, SECRET);

        assertThat(webhookService.verifySignature(ORDER_BODY, valid, SECRET)).isTrue();
        assertThat(webhookService.verifySignature(ORDER_BODY + "x", valid, SECRET)).isFalse();
        assertThat(webhookService.verifySignature(ORDER_BODY, valid, "wrong-secret")).isFalse();
        assertThat(webhookService.verifySignature(ORDER_BODY, null, SECRET)).isFalse();
        assertThat(webhookService.verifySignature(ORDER_BODY, "not-base64!!!", SECRET)).isFalse();
    }

    @Test
    void handle_UnknownShop_Rejected() {
        when(integrationConfigRepository.findFirstByPlatformAndStoreUrlAndIsActiveTrue(anyString(), anyString()))
            .thenReturn(Optional.empty());

        var outcome = webhookService.handle("stranger.myshopify.com", "orders/create", "wh-1", "sig", ORDER_BODY);

        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.UNKNOWN_SHOP);
        verify(shopifyIntegrationService, never()).processWebhookOrder(any(), any());
    }

    @Test
    void handle_InvalidSignature_Rejected() {
        stubKnownShop();

        var outcome = webhookService.handle(SHOP, "orders/create", "wh-1", "bad-signature", ORDER_BODY);

        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.SIGNATURE_INVALID);
        verify(shopifyIntegrationService, never()).processWebhookOrder(any(), any());
    }

    // ===== 幂等 =====

    @Test
    void handle_DuplicateWebhookId_Deduped() throws Exception {
        stubKnownShop();
        when(rawEventService.webhookAlreadyReceived("wh-1")).thenReturn(true);

        var outcome = webhookService.handle(SHOP, "orders/create", "wh-1", sign(ORDER_BODY, SECRET), ORDER_BODY);

        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.DUPLICATE);
        verify(shopifyIntegrationService, never()).processWebhookOrder(any(), any());
    }

    @Test
    void handle_IdempotencySucceededHit_Deduped() throws Exception {
        stubKnownShop();
        when(rawEventService.webhookAlreadyReceived(anyString())).thenReturn(false);
        when(idempotencyService.start(anyString(), anyString(), anyString()))
            .thenReturn(IdempotencyRequest.builder().status(IdempotencyStatus.SUCCEEDED).build());

        var outcome = webhookService.handle(SHOP, "orders/create", "wh-1", sign(ORDER_BODY, SECRET), ORDER_BODY);

        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.DUPLICATE);
        verify(shopifyIntegrationService, never()).processWebhookOrder(any(), any());
    }

    // ===== orders/create =====

    @Test
    void handle_OrderCreate_DispatchesToPipeline() throws Exception {
        stubKnownShop();
        when(rawEventService.webhookAlreadyReceived(anyString())).thenReturn(false);
        when(shopifyIntegrationService.processWebhookOrder(any(), eq(config)))
            .thenReturn(new ShopifyIntegrationService.SyncResult());

        var outcome = webhookService.handle(SHOP, "orders/create", "wh-1", sign(ORDER_BODY, SECRET), ORDER_BODY);

        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.ACCEPTED);
        verify(shopifyIntegrationService).processWebhookOrder(any(), eq(config));
        verify(idempotencyService).markSucceeded(any(), eq(200), anyString());
    }

    @Test
    void handle_ProcessingException_StillAccepted() throws Exception {
        stubKnownShop();
        when(rawEventService.webhookAlreadyReceived(anyString())).thenReturn(false);
        when(shopifyIntegrationService.processWebhookOrder(any(), any()))
            .thenThrow(new RuntimeException("boom"));

        var outcome = webhookService.handle(SHOP, "orders/create", "wh-1", sign(ORDER_BODY, SECRET), ORDER_BODY);

        // 处理失败不让 Shopify 重发——报文已留底，走各自补救通道
        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.ACCEPTED);
        verify(idempotencyService).markFailed(any(), eq(500), anyString());
    }

    // ===== orders/cancelled =====

    private void stubCancelledCommon() throws Exception {
        stubKnownShop();
        when(rawEventService.webhookAlreadyReceived(anyString())).thenReturn(false);
        when(rawEventService.recordWebhook(anyString(), anyString(), eq(ChannelRawEvent.TYPE_ORDER_CANCELLED),
            anyString(), anyString(), anyString())).thenReturn(event);
    }

    @Test
    void cancelled_NotPicked_AutoCancelled() throws Exception {
        stubCancelledCommon();
        SalesOrder order = SalesOrder.builder()
            .id(100L).orderNo("SO-1").status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT).build();
        when(salesOrderRepository.findByExternalOrderId("12345")).thenReturn(Optional.of(order));

        OutboundTask pendingTask = mock(OutboundTask.class);
        when(pendingTask.getStatus()).thenReturn(OutboundTaskStatus.PENDING);
        when(outboundTaskRepository.findBySalesOrderId(100L)).thenReturn(List.of(pendingTask));

        var outcome = webhookService.handle(SHOP, "orders/cancelled", "wh-2", sign(ORDER_BODY, SECRET), ORDER_BODY);

        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.ACCEPTED);
        verify(salesSubmissionService).cancelSalesOrder(eq(100L), anyString(), eq(1L));
        verify(rawEventService).markProcessed(7L);
    }

    @Test
    void cancelled_PickingStarted_ManualReview() throws Exception {
        stubCancelledCommon();
        SalesOrder order = SalesOrder.builder()
            .id(100L).orderNo("SO-1").status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT).build();
        when(salesOrderRepository.findByExternalOrderId("12345")).thenReturn(Optional.of(order));

        OutboundTask pickingTask = mock(OutboundTask.class);
        when(pickingTask.getStatus()).thenReturn(OutboundTaskStatus.PICKING);
        when(outboundTaskRepository.findBySalesOrderId(100L)).thenReturn(List.of(pickingTask));

        webhookService.handle(SHOP, "orders/cancelled", "wh-2", sign(ORDER_BODY, SECRET), ORDER_BODY);

        verify(salesSubmissionService, never()).cancelSalesOrder(anyLong(), anyString(), anyLong());
        verify(rawEventService).markManualReview(eq(7L), anyString());
    }

    @Test
    void cancelled_Shipped_ManualReview() throws Exception {
        stubCancelledCommon();
        SalesOrder order = SalesOrder.builder()
            .id(100L).orderNo("SO-1").status(SalesOrderStatus.SHIPPED).build();
        when(salesOrderRepository.findByExternalOrderId("12345")).thenReturn(Optional.of(order));

        webhookService.handle(SHOP, "orders/cancelled", "wh-2", sign(ORDER_BODY, SECRET), ORDER_BODY);

        verify(salesSubmissionService, never()).cancelSalesOrder(anyLong(), anyString(), anyLong());
        verify(rawEventService).markManualReview(eq(7L), anyString());
    }

    @Test
    void cancelled_NoLocalOrder_Skipped() throws Exception {
        stubCancelledCommon();
        when(salesOrderRepository.findByExternalOrderId("12345")).thenReturn(Optional.empty());

        webhookService.handle(SHOP, "orders/cancelled", "wh-2", sign(ORDER_BODY, SECRET), ORDER_BODY);

        verify(salesSubmissionService, never()).cancelSalesOrder(anyLong(), anyString(), anyLong());
        verify(rawEventService).markSkipped(eq(7L), anyString());
    }

    @Test
    void cancelled_AutoCancelFails_FallsBackToManualReview() throws Exception {
        stubCancelledCommon();
        SalesOrder order = SalesOrder.builder()
            .id(100L).orderNo("SO-1").status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT).build();
        when(salesOrderRepository.findByExternalOrderId("12345")).thenReturn(Optional.of(order));
        when(outboundTaskRepository.findBySalesOrderId(100L)).thenReturn(List.of());
        when(salesSubmissionService.cancelSalesOrder(anyLong(), anyString(), anyLong()))
            .thenThrow(new RuntimeException("state conflict"));

        var outcome = webhookService.handle(SHOP, "orders/cancelled", "wh-2", sign(ORDER_BODY, SECRET), ORDER_BODY);

        assertThat(outcome).isEqualTo(ShopifyWebhookService.WebhookOutcome.ACCEPTED);
        verify(rawEventService).markManualReview(eq(7L), anyString());
    }

    // ===== orders/updated 与 refunds/create =====

    @Test
    void updated_RecordedForManualReview() throws Exception {
        stubKnownShop();
        when(rawEventService.webhookAlreadyReceived(anyString())).thenReturn(false);
        when(rawEventService.recordWebhook(anyString(), anyString(), eq(ChannelRawEvent.TYPE_ORDER_UPDATED),
            anyString(), anyString(), anyString())).thenReturn(event);

        webhookService.handle(SHOP, "orders/updated", "wh-3", sign(ORDER_BODY, SECRET), ORDER_BODY);

        verify(rawEventService).markManualReview(eq(7L), anyString());
        verify(shopifyIntegrationService, never()).processWebhookOrder(any(), any());
    }

    @Test
    void refund_RecordedOnly() throws Exception {
        stubKnownShop();
        when(rawEventService.webhookAlreadyReceived(anyString())).thenReturn(false);
        String refundBody = "{\"id\":555,\"order_id\":12345}";
        when(rawEventService.recordWebhook(anyString(), anyString(), eq(ChannelRawEvent.TYPE_REFUND),
            anyString(), anyString(), anyString())).thenReturn(event);

        webhookService.handle(SHOP, "refunds/create", "wh-4", sign(refundBody, SECRET), refundBody);

        verify(rawEventService).markSkipped(eq(7L), anyString());
        verify(salesSubmissionService, never()).cancelSalesOrder(anyLong(), anyString(), anyLong());
    }
}
