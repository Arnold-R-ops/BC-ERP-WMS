package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.SalesOrderResponse;
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
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SalesSubmissionService salesSubmissionService;

    @Mock
    private AllocationService allocationService;

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
    private Product product;
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
        lineItem.setName("Test Product");
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

        product = Product.builder()
                .id(1L)
                .barcode("TEST-SKU-001")
                .name("Test Product")
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
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));
        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.of(product));

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
                .thenReturn(salesOrderResponse);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(salesOrder));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(salesOrder);
        when(allocationService.allocateInventory(anyLong())).thenReturn(Collections.emptyList());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(integrationConfigRepository).save(config);
        verify(allocationService).allocateInventory(1L);
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
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
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
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
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
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));
        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.of(product));

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
                .thenReturn(salesOrderResponse);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(salesOrder));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(salesOrder);
        when(allocationService.allocateInventory(anyLong())).thenReturn(Collections.emptyList());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(rawEventService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(rawEventService).markProcessed(8L);
    }

    @Test
    void syncOrders_SkuNotFound() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));
        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.empty());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(rawEventService).markFailed(eq(10L), anyString());
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    void syncOrders_CreateNewCustomer() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Customer newCustomer = Customer.builder()
                .id(2L)
                .code("SHOPIFY_100")
                .name("John Doe")
                .email("test@example.com")
                .isActive(true)
                .build();
        when(customerRepository.save(any(Customer.class))).thenReturn(newCustomer);

        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.of(product));

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
                .thenReturn(salesOrderResponse);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(salesOrder));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(salesOrder);
        when(allocationService.allocateInventory(anyLong())).thenReturn(Collections.emptyList());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(customerRepository).save(any(Customer.class));
        verify(allocationService).allocateInventory(1L);
    }

    @Test
    void syncOrders_NoOrders() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn("{\"orders\":[]}");

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    void syncOrders_OrderWithoutEmail() {
        stubActiveConfig();
        shopifyOrder.setEmail(null);
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(rawEventService).markFailed(eq(10L), anyString());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void syncOrders_OrderWithoutLineItems() {
        stubActiveConfig();
        shopifyOrder.setLineItems(Collections.emptyList());
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(productRepository, never()).findByBarcode(anyString());
    }

    @Test
    void syncOrders_LineItemWithoutSku() {
        // 已知缺陷（批次2 修复）：无 SKU 行被跳过导致下标错位，当前表现为整单失败
        stubActiveConfig();
        shopifyOrder.getLineItems().get(0).setSku(null);
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void syncOrders_InvalidPriceFormat() {
        stubActiveConfig();
        shopifyOrder.getLineItems().get(0).setPrice("invalid-price");
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));
        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.of(product));

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
                .thenReturn(salesOrderResponse);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(salesOrder));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(salesOrder);
        when(allocationService.allocateInventory(anyLong())).thenReturn(Collections.emptyList());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // 应使用默认价格 0 并成功创建订单
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void syncOrders_AllocationServiceThrowsException() {
        stubActiveConfig();
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));
        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.of(product));

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
                .thenReturn(salesOrderResponse);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(salesOrder));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(salesOrder);
        when(allocationService.allocateInventory(anyLong()))
                .thenThrow(new BusinessException(ErrorKeys.STOCK_INSUFFICIENT));

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
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    void syncOrders_CustomerWithoutName() {
        stubActiveConfig();
        shopifyOrder.getCustomer().setFirstName(null);
        shopifyOrder.getCustomer().setLastName(null);
        when(shopifyApiClient.fetchOrdersRaw(config)).thenReturn(rawOrders(shopifyOrder));
        stubNewRawEvent();
        stubTransactionPassThrough();

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Customer newCustomer = Customer.builder()
                .id(2L)
                .code("SHOPIFY_100")
                .name("test@example.com")
                .email("test@example.com")
                .isActive(true)
                .build();
        when(customerRepository.save(any(Customer.class))).thenReturn(newCustomer);

        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.of(product));

        SalesOrderResponse salesOrderResponse = new SalesOrderResponse();
        salesOrderResponse.setId(1L);
        when(salesSubmissionService.createSalesOrder(any(), anyLong(), anyString()))
                .thenReturn(salesOrderResponse);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(salesOrder));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(salesOrder);
        when(allocationService.allocateInventory(anyLong())).thenReturn(Collections.emptyList());

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(customerRepository).save(argThat(c -> c.getName().equals("test@example.com")));
    }
}
