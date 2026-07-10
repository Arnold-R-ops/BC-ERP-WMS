package com.wms.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.shopify.ShopifyCustomerDto;
import com.wms.system.dto.shopify.ShopifyLineItemDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.dto.shopify.ShopifyOrdersResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.Zone;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.repository.*;
import com.wms.system.service.ShopifyIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Shopify Integration End-to-End Test
 *
 * V3.9 Architecture: Complete Shopify Order Sync Workflow Test
 *
 * Test Scenario:
 * 1. Setup test data (product, inventory, integration config)
 * 2. Mock Shopify API response
 * 3. Trigger order sync
 * 4. Verify customer created/matched
 * 5. Verify sales order created with correct channel info
 * 6. Verify order status is APPROVED_AWAITING_SHIPMENT
 * 7. Verify outbound tasks generated
 * 8. Test deduplication (sync same order twice)
 * 9. Test SKU not found scenario
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class ShopifyIntegrationE2ETest {

    @Autowired
    private ShopifyIntegrationService shopifyIntegrationService;

    @MockBean
    private com.wms.system.integration.ShopifyApiClient shopifyApiClient;

    @Autowired
    private IntegrationConfigRepository integrationConfigRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    private OutboundTaskRepository outboundTaskRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private ChannelRawEventRepository channelRawEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private IntegrationConfig testConfig;
    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch testBatch;

    @BeforeEach
    void setUp() {
        // 0. Clean raw events: they are persisted via REQUIRES_NEW and survive
        //    the test transaction rollback, so leftover dedup state would bleed
        //    between test methods.
        channelRawEventRepository.deleteAll();

        // 1. Create test warehouse
        testWarehouse = Warehouse.builder()
                .code("WH-TEST")
                .name("Test Warehouse")
                .address("Test Address")
                .isActive(true)
                .build();
        testWarehouse = warehouseRepository.save(testWarehouse);

        // 2. Create test location
        testLocation = Location.builder()
                .warehouse(testWarehouse)
                .zone(Zone.ZONE_A)
                .shelfNumber("A-01")
                .positionNumber("01")
                .enabled(true)
                .build();
        testLocation = locationRepository.save(testLocation);

        // 3. Create test product SPU
        ProductSpu testSpu = ProductSpu.builder()
                .spuCode("SPU-TEST")
                .spuName("Test SPU")
                .build();
        testSpu = productSpuRepository.save(testSpu);

        // 4. Create test product
        testProduct = Product.builder()
                .spu(testSpu)
                .skuName("TEST-SKU-001")
                .barcode("TEST-SKU-001")
                .name("Test Product")
                .specification("Test Spec")
                .unitPrice(BigDecimal.valueOf(99.99))
                .minSalesPrice(BigDecimal.valueOf(50.00))
                .build();
        testProduct = productRepository.save(testProduct);

        // 5. Create test inventory batch
        testBatch = InventoryBatch.builder()
                .product(testProduct)
                .location(testLocation)
                .locationCode(testLocation.getLocationCode())
                .batchCode("BATCH-TEST-001")
                .quantity(1000)
                .initialQuantity(1000)
                .expiryDate(LocalDate.now().plusYears(1))
                .active(true)
                .build();
        testBatch = inventoryBatchRepository.save(testBatch);

        // 6. Create test integration config
        testConfig = IntegrationConfig.builder()
                .platform("SHOPIFY")
                .storeUrl("test-store.myshopify.com")
                .accessToken("test-access-token")
                .isActive(true)
                .build();
        testConfig = integrationConfigRepository.save(testConfig);

        // 7. Create risk-control system configs required by SalesSubmissionService
        systemConfigRepository.save(SystemConfig.builder()
                .configKey("sales.min_price_approval_enabled")
                .configValue("true")
                .configType(SystemConfig.ConfigType.BOOLEAN.name())
                .description("Enable low price approval")
                .build());
        systemConfigRepository.save(SystemConfig.builder()
                .configKey("sales.approval.amount_threshold")
                .configValue("50000.00")
                .configType(SystemConfig.ConfigType.DECIMAL.name())
                .description("High amount approval threshold")
                .build());
    }

    @Test
    @DisplayName("case-2")
    void testCompleteOrderSync_NewCustomer() {
        // Given: Mock Shopify API response
        ShopifyCustomerDto shopifyCustomer = new ShopifyCustomerDto();
        shopifyCustomer.setId(100L);
        shopifyCustomer.setEmail("newcustomer@example.com");
        shopifyCustomer.setFirstName("Jane");
        shopifyCustomer.setLastName("Smith");
        shopifyCustomer.setPhone("9876543210");

        ShopifyLineItemDto lineItem = new ShopifyLineItemDto();
        lineItem.setId(1L);
        lineItem.setSku("TEST-SKU-001");
        lineItem.setName("Test Product");
        lineItem.setQuantity(10);
        lineItem.setPrice("99.99");

        ShopifyOrderDto shopifyOrder = new ShopifyOrderDto();
        shopifyOrder.setId(12345L);
        shopifyOrder.setName("#1001");
        shopifyOrder.setEmail("newcustomer@example.com");
        shopifyOrder.setFinancialStatus("paid");
        shopifyOrder.setCustomer(shopifyCustomer);
        shopifyOrder.setLineItems(Collections.singletonList(lineItem));

        when(shopifyApiClient.fetchOrdersRaw(any())).thenReturn(ordersJson(shopifyOrder));

        // When: Trigger sync
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then: Verify sync result
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();

        // Verify raw event persisted and marked processed (P1-B1)
        Optional<ChannelRawEvent> rawEvent = channelRawEventRepository
                .findFirstByChannelAndEventTypeAndExternalIdOrderByIdDesc("SHOPIFY", ChannelRawEvent.TYPE_ORDER, "12345");
        assertThat(rawEvent).isPresent();
        assertThat(rawEvent.get().getStatus()).isEqualTo(ChannelRawEvent.STATUS_PROCESSED);
        assertThat(rawEvent.get().getPayload()).contains("#1001");

        // Verify customer created
        Optional<Customer> customer = customerRepository.findByEmail("newcustomer@example.com");
        assertThat(customer).isPresent();
        assertThat(customer.get().getCode()).isEqualTo("SHOPIFY_100");
        assertThat(customer.get().getName()).isEqualTo("Jane Smith");
        assertThat(customer.get().getPhone()).isEqualTo("9876543210");

        // Verify sales order created
        Optional<SalesOrder> salesOrder = salesOrderRepository.findByExternalOrderId("12345");
        assertThat(salesOrder).isPresent();
        assertThat(salesOrder.get().getChannel()).isEqualTo("SHOPIFY");
        assertThat(salesOrder.get().getExternalOrderNo()).isEqualTo("#1001");
        assertThat(salesOrder.get().getStatus()).isEqualTo(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT);
        assertThat(salesOrder.get().getCustomerId()).isEqualTo(customer.get().getId());

        // Verify sales order items
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(salesOrder.get().getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductId()).isEqualTo(testProduct.getId());
        assertThat(items.get(0).getQuantity()).isEqualTo(10);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(99.99));

        // Verify outbound tasks generated
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(salesOrder.get().getId());
        assertThat(tasks).isNotEmpty();
    }

    @Test
    @DisplayName("case-3")
    void testOrderSync_ExistingCustomer() {
        // Given: Create existing customer
        Customer existingCustomer = Customer.builder()
                .code("CUST-001")
                .name("Existing Customer")
                .email("existing@example.com")
                .isActive(true)
                .creditLimit(BigDecimal.ZERO)
                .build();
        existingCustomer = customerRepository.save(existingCustomer);

        // Mock Shopify API response
        ShopifyCustomerDto shopifyCustomer = new ShopifyCustomerDto();
        shopifyCustomer.setId(200L);
        shopifyCustomer.setEmail("existing@example.com");
        shopifyCustomer.setFirstName("Existing");
        shopifyCustomer.setLastName("Customer");

        ShopifyLineItemDto lineItem = new ShopifyLineItemDto();
        lineItem.setId(2L);
        lineItem.setSku("TEST-SKU-001");
        lineItem.setQuantity(5);
        lineItem.setPrice("75.00");

        ShopifyOrderDto shopifyOrder = new ShopifyOrderDto();
        shopifyOrder.setId(67890L);
        shopifyOrder.setName("#1002");
        shopifyOrder.setEmail("existing@example.com");
        shopifyOrder.setFinancialStatus("paid");
        shopifyOrder.setCustomer(shopifyCustomer);
        shopifyOrder.setLineItems(Collections.singletonList(lineItem));

        when(shopifyApiClient.fetchOrdersRaw(any())).thenReturn(ordersJson(shopifyOrder));

        // When: Trigger sync
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then: Verify sync result
        assertThat(result.getSuccessCount()).isEqualTo(1);

        // Verify customer not duplicated
        Optional<Customer> customer = customerRepository.findByEmail("existing@example.com");
        assertThat(customer).isPresent();
        assertThat(customer.get().getId()).isEqualTo(existingCustomer.getId());

        // Verify sales order created with existing customer
        Optional<SalesOrder> salesOrder = salesOrderRepository.findByExternalOrderId("67890");
        assertThat(salesOrder).isPresent();
        assertThat(salesOrder.get().getCustomerId()).isEqualTo(existingCustomer.getId());
    }

    @Test
    @DisplayName("case-4")
    void testDeduplication() {
        // Given: Mock Shopify API response
        ShopifyCustomerDto shopifyCustomer = new ShopifyCustomerDto();
        shopifyCustomer.setId(300L);
        shopifyCustomer.setEmail("dedup@example.com");
        shopifyCustomer.setFirstName("Dedup");
        shopifyCustomer.setLastName("Test");

        ShopifyLineItemDto lineItem = new ShopifyLineItemDto();
        lineItem.setId(3L);
        lineItem.setSku("TEST-SKU-001");
        lineItem.setQuantity(3);
        lineItem.setPrice("60.00");

        ShopifyOrderDto shopifyOrder = new ShopifyOrderDto();
        shopifyOrder.setId(99999L);
        shopifyOrder.setName("#1003");
        shopifyOrder.setEmail("dedup@example.com");
        shopifyOrder.setFinancialStatus("paid");
        shopifyOrder.setCustomer(shopifyCustomer);
        shopifyOrder.setLineItems(Collections.singletonList(lineItem));

        when(shopifyApiClient.fetchOrdersRaw(any())).thenReturn(ordersJson(shopifyOrder));

        // When: Sync first time
        ShopifyIntegrationService.SyncResult result1 = shopifyIntegrationService.syncOrders();

        // Then: First sync should succeed
        assertThat(result1.getSuccessCount()).isEqualTo(1);

        // When: Sync second time (same order)
        ShopifyIntegrationService.SyncResult result2 = shopifyIntegrationService.syncOrders();

        // Then: Second sync silently skips via raw-event terminal state (P1-B1:
        // previously this was counted as a failure; dedup is now a clean skip)
        assertThat(result2.getSuccessCount()).isZero();
        assertThat(result2.getFailedCount()).isZero();
        assertThat(result2.getSkippedCount()).isEqualTo(1);

        // Verify only one sales order exists
        List<SalesOrder> orders = salesOrderRepository.findAll();
        long shopifyOrders = orders.stream()
                .filter(o -> "99999".equals(o.getExternalOrderId()))
                .count();
        assertThat(shopifyOrders).isEqualTo(1);
    }

    @Test
    @DisplayName("case-5")
    void testSkuNotFound() {
        // Given: Mock Shopify API response with non-existent SKU
        ShopifyCustomerDto shopifyCustomer = new ShopifyCustomerDto();
        shopifyCustomer.setId(400L);
        shopifyCustomer.setEmail("sku-test@example.com");
        shopifyCustomer.setFirstName("SKU");
        shopifyCustomer.setLastName("Test");

        ShopifyLineItemDto lineItem = new ShopifyLineItemDto();
        lineItem.setId(4L);
        lineItem.setSku("NON-EXISTENT-SKU");
        lineItem.setQuantity(1);
        lineItem.setPrice("100.00");

        ShopifyOrderDto shopifyOrder = new ShopifyOrderDto();
        shopifyOrder.setId(88888L);
        shopifyOrder.setName("#1004");
        shopifyOrder.setEmail("sku-test@example.com");
        shopifyOrder.setFinancialStatus("paid");
        shopifyOrder.setCustomer(shopifyCustomer);
        shopifyOrder.setLineItems(Collections.singletonList(lineItem));

        when(shopifyApiClient.fetchOrdersRaw(any())).thenReturn(ordersJson(shopifyOrder));

        // When: Trigger sync
        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        // Then: Sync should fail (SKU not found)
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);

        // Verify no sales order created
        Optional<SalesOrder> salesOrder = salesOrderRepository.findByExternalOrderId("88888");
        assertThat(salesOrder).isEmpty();

        // Verify raw event kept with FAILED status and error message (diagnosable/replayable)
        Optional<ChannelRawEvent> rawEvent = channelRawEventRepository
                .findFirstByChannelAndEventTypeAndExternalIdOrderByIdDesc("SHOPIFY", ChannelRawEvent.TYPE_ORDER, "88888");
        assertThat(rawEvent).isPresent();
        assertThat(rawEvent.get().getStatus()).isEqualTo(ChannelRawEvent.STATUS_FAILED);
        assertThat(rawEvent.get().getErrorMessage()).isNotBlank();
    }

    /**
     * 构造 Shopify orders.json 原始报文（P1-B1：服务改为消费原始 JSON）
     */
    private String ordersJson(ShopifyOrderDto... orders) {
        try {
            ShopifyOrdersResponse resp = new ShopifyOrdersResponse();
            resp.setOrders(java.util.List.of(orders));
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
