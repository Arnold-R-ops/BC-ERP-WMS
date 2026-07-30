package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.SalesOrderResponse;
import com.wms.system.dto.shopify.ShopifyAddressDto;
import com.wms.system.dto.shopify.ShopifyCustomerDto;
import com.wms.system.dto.shopify.ShopifyLineItemDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.dto.shopify.ShopifyOrdersResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.integration.ShopifyApiClient;
import com.wms.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ShopifyIntegrationService 单元测试
 *
 * P1 批次1 重写：同步编排改为"原始报文先落库 → 逐单独立事务 → 状态回写"，
 * 测试对应调整为 fetchOrdersRaw 原始 JSON + 报文留底桩 + 事务模板直通。
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version P1-B1
 */
@ExtendWith(MockitoExtension.class)
class ShopifyIntegrationServiceTest {

    @Mock
    private IntegrationConfigRepository integrationConfigRepository;

    @Mock
    private ShopifyApiClient shopifyApiClient;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private ShopifyConsumerIdentityService consumerIdentityService;

    @Mock
    private ChannelSkuResolver skuResolver;

    @Mock
    private PendingSkuMappingService pendingSkuMappingService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private SalesSubmissionService salesSubmissionService;

    @Mock
    private ChannelRawEventService rawEventService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ShopifyIntegrationService shopifyIntegrationService;

    private IntegrationConfig config;
    private ShopifyOrderDto shopifyOrder;
    private Customer customer;
    private ProductSku product;
    private SalesOrder salesOrder;
    private ChannelRawEvent rawEvent;

    @BeforeEach
    void setUp() {
        config = IntegrationConfig.builder()
                .id(1L)
                .platform("SHOPIFY")
                .storeUrl("test-store.myshopify.com")
                .clientId("cid")
                .clientSecret("shpss_x")
                .isActive(true)
                .build();

        ShopifyCustomerDto shopifyCustomer = new ShopifyCustomerDto();
        shopifyCustomer.setId(100L);
        shopifyCustomer.setEmail("test@example.com");
        shopifyCustomer.setFirstName("John");
        shopifyCustomer.setLastName("Doe");
        shopifyCustomer.setPhone("1234567890");

        ShopifyLineItemDto lineItem = new ShopifyLineItemDto();
        lineItem.setId(1L);
        lineItem.setSku("TEST-SKU-001");
        lineItem.setName("Test ProductSku");
        lineItem.setQuantity(10);
        lineItem.setPrice("99.99");

        shopifyOrder = new ShopifyOrderDto();
        shopifyOrder.setId(12345L);
        shopifyOrder.setName("#1001");
        shopifyOrder.setEmail("test@example.com");
        shopifyOrder.setFinancialStatus("paid");
        shopifyOrder.setCustomer(shopifyCustomer);
        shopifyOrder.setLineItems(Collections.singletonList(lineItem));

        customer = Customer.builder()
                .id(1L)
                .code("CUST001")
                .name("John Doe")
                .email("test@example.com")
                .isActive(true)
                .build();

        product = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .id(1L)
                .barcode("TEST-SKU-001")
                .name("Test ProductSku")
                .minSalesPrice(BigDecimal.valueOf(50.00))
                .build();

        salesOrder = SalesOrder.builder()
                .id(1L)
                .orderNo("SO20260205001")
                .customerId(customer.getId())
                .status(SalesOrderStatus.DRAFT)
                .totalAmount(BigDecimal.valueOf(999.90))
                .applicantId(1L)
                .applicantName("Shopify Integration")
                .build();

        rawEvent = ChannelRawEvent.builder()
                .id(10L)
                .channel("SHOPIFY")
                .eventType(ChannelRawEvent.TYPE_ORDER)
                .externalId("12345")
                .status(ChannelRawEvent.STATUS_RECEIVED)
                .build();
    }

    // ===== 测试工具 =====

    /**
     * 独立 mapper：不能用 @Spy 的 objectMapper——在 when(...) 参数里调用
     * spy 方法会触发 Mockito 的 UnfinishedStubbingException
     */
    private static final ObjectMapper TEST_MAPPER = new ObjectMapper();

    private String rawOrders(ShopifyOrderDto... orders) {
        try {
            ShopifyOrdersResponse resp = new ShopifyOrdersResponse();
            resp.setOrders(List.of(orders));
            return TEST_MAPPER.writeValueAsString(resp);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubActiveConfig() {
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));
    }

    /**
     * 事务模板直通：单元测试无真实事务，直接执行回调
     */
    @SuppressWarnings("unchecked")
    private void stubTransactionPassThrough() {
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private void stubNewRawEvent() {
        when(rawEventService.findLatest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(rawEventService.record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(rawEvent);
    }

    /**
     * SKU 解析漏斗桩：默认命中商品（P1-B2）
     */
    private void stubSkuResolved(int ratio) {
        when(skuResolver.resolve(anyString(), anyString(), anyString()))
                .thenReturn(resolutionOfProduct(ratio));
    }

    private void stubCustomerResolved() {
        when(consumerIdentityService.resolveForAutomaticIngestion(any(ShopifyOrderDto.class), any(IntegrationConfig.class)))
            .thenReturn(customer);
    }

    private void stubCustomerResolutionFailed() {
        when(consumerIdentityService.resolveForAutomaticIngestion(any(ShopifyOrderDto.class), any(IntegrationConfig.class)))
            .thenThrow(new BusinessException(ErrorKeys.VALIDATION_FAILED));
    }

    private ChannelSkuResolver.Resolution resolutionOfProduct(int ratio) {
        return ChannelSkuResolver.Resolution.productSku(product, ratio);
    }

    // ===== 用例 =====

    @Test
    void syncOrders_NoActiveConfigs() {
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.emptyList());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        verify(shopifyApiClient, never()).fetchOrdersRaw(any());
    }

    @Test
    void syncOrders_Success() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        stubSkuResolved(1);

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(salesOrderResponse);

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(integrationConfigRepository).save(config);
        verify(salesSubmissionService).createChannelOrderPendingApproval(
                any(), eq(1L), eq("Shopify Integration"), eq("SHOPIFY"), eq("12345"), eq("#1001"));
        // 报文留底状态回写
        verify(rawEventService).record(eq("SHOPIFY"), eq("test-store.myshopify.com"),
                eq(ChannelRawEvent.SOURCE_POLL), eq(ChannelRawEvent.TYPE_ORDER), eq("12345"), anyString());
        verify(rawEventService).markProcessed(10L);
    }

    @Test
    void syncOrders_OrderAlreadySynced_MarkedSkipped() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();

        // 业务表已有该订单（批次1 之前的历史同步）
        when(salesOrderRepository.findByExternalOrderId("12345")).thenReturn(Optional.of(salesOrder));

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(rawEventService).markSkipped(eq(10L), anyString());
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncOrders_TerminalRawEvent_SilentlySkipped() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));

        // 留底表已有 PROCESSED 终态 → 静默跳过，不再写任何新记录
        ChannelRawEvent processed = ChannelRawEvent.builder()
                .id(9L).channel("SHOPIFY").eventType(ChannelRawEvent.TYPE_ORDER)
                .externalId("12345").status(ChannelRawEvent.STATUS_PROCESSED).build();
        when(rawEventService.findLatest(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(processed));

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(rawEventService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(rawEventService, never()).markSkipped(anyLong(), anyString());
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncOrders_FailedRawEvent_RetriedAndProcessed() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubTransactionPassThrough();

        // 上一轮失败的留底 → 本轮复用同一事件重试，不重复落库
        ChannelRawEvent failed = ChannelRawEvent.builder()
                .id(8L).channel("SHOPIFY").eventType(ChannelRawEvent.TYPE_ORDER)
                .externalId("12345").status(ChannelRawEvent.STATUS_FAILED).build();
        when(rawEventService.findLatest(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(failed));

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        stubSkuResolved(1);

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(salesOrderResponse);

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(rawEventService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(rawEventService).markProcessed(8L);
    }

    @Test
    void syncOrders_UnknownSku_QueuedForMapping() {
        // P1-B2：未知 SKU 不再静默丢单——进待映射队列并阻断本单
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        when(skuResolver.resolve(anyString(), anyString(), anyString()))
                .thenReturn(ChannelSkuResolver.Resolution.miss());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(pendingSkuMappingService).recordMiss(eq("SHOPIFY"), anyString(), eq("TEST-SKU-001"),
                anyString(), any(), eq("#1001"));
        verify(rawEventService).markFailed(eq(10L), anyString());
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncOrders_UnmatchedCustomerBlockedWithoutCreatingMasterData() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolutionFailed();

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(skuResolver, never()).resolve(anyString(), anyString(), anyString());
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncOrders_NoOrders() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn("{\"orders\":[]}");

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncOrders_OrderWithoutEmail() {
        stubActiveConfig();
        shopifyOrder.setEmail(null);
        shopifyOrder.getCustomer().setEmail(null);
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolutionFailed();

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(rawEventService).markFailed(eq(10L), anyString());
    }

    @Test
    void syncOrders_OrderWithoutLineItems() {
        stubActiveConfig();
        shopifyOrder.setLineItems(Collections.emptyList());
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(skuResolver, never()).resolve(anyString(), anyString(), anyString());
    }

    @Test
    void syncOrders_LineItemWithoutSku_DefaultPendingPolicy() {
        // P1-B2：无 SKU 行默认策略 PENDING——以合成键 NOSKU::标题 进待映射队列并阻断
        stubActiveConfig();
        shopifyOrder.getLineItems().get(0).setSku(null);
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        when(skuResolver.resolve(anyString(), anyString(), startsWith(PendingSkuMapping.NO_SKU_PREFIX)))
                .thenReturn(ChannelSkuResolver.Resolution.miss());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(pendingSkuMappingService).recordMiss(eq("SHOPIFY"), anyString(),
                startsWith(PendingSkuMapping.NO_SKU_PREFIX), anyString(), any(), eq("#1001"));
    }

    @Test
    void syncOrders_LineItemWithoutSku_SkipPolicy() {
        // P1-B2：策略 SKIP——无 SKU 行直接丢行；本单只有这一行 → 无可履约行而失败，但不进队列
        stubActiveConfig();
        shopifyOrder.getLineItems().get(0).setSku(null);
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        when(systemConfigService.getConfigValue(ShopifyIntegrationService.NO_SKU_POLICY_KEY))
                .thenReturn(ShopifyIntegrationService.NO_SKU_POLICY_SKIP);
        when(skuResolver.resolve(anyString(), anyString(), startsWith(PendingSkuMapping.NO_SKU_PREFIX)))
                .thenReturn(ChannelSkuResolver.Resolution.miss());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(pendingSkuMappingService, never()).recordMiss(anyString(), anyString(), anyString(),
                anyString(), any(), anyString());
    }

    @Test
    void syncOrders_QuantityRatioConversion() {
        // P1-B2：数量换算——1 外部单位 = 20 内部单位（数量 ×20，单价 ÷20）
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        stubSkuResolved(20);

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(salesOrderResponse);

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(salesSubmissionService).createChannelOrderPendingApproval(argThat(req -> {
            var item = req.getItems().get(0);
            // 10 件 ×20 = 200 内部单位；99.99 ÷ 20 = 5.00（HALF_UP 到分）
            return item.getQuantity() == 200
                && item.getUnitPrice().compareTo(new BigDecimal("5.00")) == 0;
        }), anyLong(), anyString(), eq("SHOPIFY"), eq("12345"), eq("#1001"));
    }

    @Test
    void syncOrders_CopiesShippingAddressIntoOrderSnapshot() {
        ShopifyAddressDto address = new ShopifyAddressDto();
        address.setName("Jane Receiver");
        address.setPhone("+44 20 1234 5678");
        address.setAddress1("10 Market Street");
        address.setAddress2("Unit 2");
        address.setCity("London");
        address.setProvince("Greater London");
        address.setZip("SW1A 1AA");
        address.setCountryCode("gb");
        shopifyOrder.setShippingAddress(address);

        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();
        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        stubSkuResolved(1);

        SalesOrderResponse response = new SalesOrderResponse();
        response.setId(1L);
        when(salesSubmissionService.createChannelOrderPendingApproval(
            any(), anyLong(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn(response);

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(salesSubmissionService).createChannelOrderPendingApproval(argThat(request ->
            "Jane Receiver".equals(request.getConsigneeName())
                && "+44 20 1234 5678".equals(request.getConsigneePhone())
                && "10 Market Street".equals(request.getShipAddress1())
                && "Unit 2".equals(request.getShipAddress2())
                && "London".equals(request.getShipCity())
                && "Greater London".equals(request.getShipProvince())
                && "SW1A 1AA".equals(request.getShipZip())
                && "GB".equals(request.getShipCountryCode())
        ), anyLong(), anyString(), eq("SHOPIFY"), eq("12345"), eq("#1001"));
    }

    @Test
    void syncOrders_InvalidPriceFormat() {
        stubActiveConfig();
        shopifyOrder.getLineItems().get(0).setPrice("invalid-price");
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        stubSkuResolved(1);

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(salesOrderResponse);

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // 应使用默认价格 0 并成功创建订单
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void syncOrders_OrderCreationFailureMarksRawEventFailed() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        stubCustomerResolved();
        stubSkuResolved(1);

        when(salesSubmissionService.createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorKeys.VALIDATION_FAILED));

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(rawEventService).markFailed(eq(10L), anyString());
    }

    @Test
    void syncOrders_ApiClientThrowsException() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config))
                .thenThrow(new BusinessException(ErrorKeys.SHOPIFY_API_ERROR));

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void syncOrders_InactiveCustomerBlocked() {
        stubActiveConfig();
        shopifyOrder.getCustomer().setFirstName(null);
        shopifyOrder.getCustomer().setLastName(null);
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        customer.setIsActive(false);
        stubCustomerResolutionFailed();

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
                any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }
}
