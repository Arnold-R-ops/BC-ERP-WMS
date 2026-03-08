package com.wms.system.service;

import com.wms.system.dto.sales.CreateSalesOrderRequest;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ShopifyIntegrationService 单元测试
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
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

    @InjectMocks
    private ShopifyIntegrationService shopifyIntegrationService;

    private IntegrationConfig config;
    private ShopifyOrderDto shopifyOrder;
    private Customer customer;
    private Product product;
    private SalesOrder salesOrder;

    @BeforeEach
    void setUp() {
        // Setup integration config
        config = IntegrationConfig.builder()
                .id(1L)
                .platform("SHOPIFY")
                .storeUrl("test-store.myshopify.com")
                .accessToken("test-token")
                .isActive(true)
                .build();

        // Setup Shopify order
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

        // Setup customer
        customer = Customer.builder()
                .id(1L)
                .code("CUST001")
                .name("John Doe")
                .email("test@example.com")
                .isActive(true)
                .build();

        // Setup product
        product = Product.builder()
                .id(1L)
                .barcode("TEST-SKU-001")
                .name("Test Product")
                .minSalesPrice(BigDecimal.valueOf(50.00))
                .build();

        // Setup sales order
        salesOrder = SalesOrder.builder()
                .id(1L)
                .orderNo("SO20260205001")
                .customerId(customer.getId())
                .status(SalesOrderStatus.DRAFT)
                .totalAmount(BigDecimal.valueOf(999.90))
                .applicantId(1L)
                .applicantName("Shopify Integration")
                .build();
    }

    @Test
    void syncOrders_NoActiveConfigs() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.emptyList());

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        verify(shopifyApiClient, never()).fetchOrders(any());
    }

    @Test
    void syncOrders_Success() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

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

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(integrationConfigRepository).save(config);
        verify(allocationService).allocateInventory(1L);
    }

    @Test
    void syncOrders_OrderAlreadySynced() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

        when(salesOrderRepository.findByExternalOrderId("12345")).thenReturn(Optional.of(salesOrder));

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    void syncOrders_SkuNotFound() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));
        when(productRepository.findByBarcode(anyString())).thenReturn(Optional.empty());

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    void syncOrders_CreateNewCustomer() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

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

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(customerRepository).save(any(Customer.class));
        verify(allocationService).allocateInventory(1L);
    }

    @Test
    void syncOrders_NoOrders() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.emptyList());
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    void syncOrders_OrderWithoutEmail() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        shopifyOrder.setEmail(null); // 没有邮箱
        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void syncOrders_OrderWithoutLineItems() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        shopifyOrder.setLineItems(Collections.emptyList()); // 没有明细
        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(productRepository, never()).findByBarcode(anyString());
    }

    @Test
    void syncOrders_LineItemWithoutSku() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        shopifyOrder.getLineItems().get(0).setSku(null); // SKU 为空
        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.of(customer));

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void syncOrders_InvalidPriceFormat() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        shopifyOrder.getLineItems().get(0).setPrice("invalid-price"); // 无效价格
        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

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

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then - 应该使用默认价格 0 并成功创建订单
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    void syncOrders_AllocationServiceThrowsException() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

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

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void syncOrders_ApiClientThrowsException() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        when(shopifyApiClient.fetchOrders(config))
                .thenThrow(new BusinessException(ErrorKeys.SHOPIFY_API_ERROR));

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(salesSubmissionService, never()).createSalesOrder(any(), anyLong(), anyString());
    }

    @Test
    void syncOrders_CustomerWithoutName() {
        // Given
        when(integrationConfigRepository.findByPlatformAndIsActiveTrue("SHOPIFY"))
                .thenReturn(Collections.singletonList(config));

        shopifyOrder.getCustomer().setFirstName(null);
        shopifyOrder.getCustomer().setLastName(null);
        ShopifyOrdersResponse response = new ShopifyOrdersResponse();
        response.setOrders(Collections.singletonList(shopifyOrder));
        when(shopifyApiClient.fetchOrders(config)).thenReturn(response);

        when(salesOrderRepository.findByExternalOrderId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        Customer newCustomer = Customer.builder()
                .id(2L)
                .code("SHOPIFY_100")
                .name("test@example.com") // 应该使用邮箱作为名字
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

        // When
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(customerRepository).save(argThat(c -> c.getName().equals("test@example.com")));
    }
}
