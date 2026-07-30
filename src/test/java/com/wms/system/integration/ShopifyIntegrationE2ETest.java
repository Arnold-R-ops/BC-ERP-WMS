package com.wms.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.shopify.ShopifyAddressDto;
import com.wms.system.dto.shopify.ShopifyCustomerDto;
import com.wms.system.dto.shopify.ShopifyLineItemDto;
import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.dto.shopify.ShopifyOrdersResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.Zone;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.CustomerSource;
import com.wms.system.entity.enums.CustomerType;
import com.wms.system.repository.*;
import com.wms.system.service.ShopifyIntegrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
 * 4. Verify only an existing active customer can be matched
 * 5. Verify sales order created with correct channel info
 * 6. Verify order status is PENDING_APPROVAL
 * 7. Verify no reservation or outbound task exists before approval
 * 8. Test deduplication (sync same order twice)
 * 9. Test SKU not found scenario
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@SpringBootTest
@ActiveProfiles("test")
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
    private ProductSkuRepository productSkuRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
    private PendingSkuMappingRepository pendingSkuMappingRepository;

    @Autowired
    private ChannelSkuMappingRepository channelSkuMappingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private IntegrationConfig testConfig;
    private ProductSku testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch testBatch;

    @BeforeEach
    void setUp() {
        // 0. Clean raw events / pending SKUs: they are persisted via REQUIRES_NEW
        //    and survive the test transaction rollback, so leftover state would
        //    bleed between test methods.
        channelRawEventRepository.deleteAll();
        pendingSkuMappingRepository.deleteAll();
        channelSkuMappingRepository.deleteAll();

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
        Product testSpu = Product.builder()
                .category(com.wms.system.support.TestCatalogFactory.saveLeafCategory(categoryRepository))
                .productCode("SPU-TEST")
                .productName("Test SPU")
                .build();
        testSpu = productRepository.save(testSpu);

        // 4. Create test product
        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .product(testSpu)
                .skuName("TEST-SKU-001")
                .barcode("TEST-SKU-001")
                .name("Test ProductSku")
                .specification("Test Spec")
                .unitPrice(BigDecimal.valueOf(99.99))
                .minSalesPrice(BigDecimal.valueOf(50.00))
                .build();
        testProduct = productSkuRepository.save(testProduct);

        // 5. Create test inventory batch
        testBatch = InventoryBatch.builder()
                .productSku(testProduct)
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
    void testOrderSync_UnknownCustomerIsBlocked() {
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
        lineItem.setName("Test ProductSku");
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

        // Unknown channel customers must be reviewed and created through the
        // customer-master workflow, never by the integration account.
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(1);

        // Verify raw event persisted and marked processed (P1-B1)
        Optional<ChannelRawEvent> rawEvent = channelRawEventRepository
                .findFirstByChannelAndEventTypeAndExternalIdOrderByIdDesc("SHOPIFY", ChannelRawEvent.TYPE_ORDER, "12345");
        assertThat(rawEvent).isPresent();
        assertThat(rawEvent.get().getStatus()).isEqualTo(ChannelRawEvent.STATUS_FAILED);
        assertThat(rawEvent.get().getPayload()).contains("#1001");

        // No customer, order, reservation or outbound task may be created.
        Optional<Customer> customer = customerRepository.findByEmail("newcustomer@example.com");
        assertThat(customer).isEmpty();
        Optional<SalesOrder> salesOrder = salesOrderRepository.findByExternalOrderId("12345");
        assertThat(salesOrder).isEmpty();
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
        assertThat(salesOrder.get().getStatus()).isEqualTo(SalesOrderStatus.PENDING_APPROVAL);
        assertThat(outboundTaskRepository.findBySalesOrderId(salesOrder.get().getId())).isEmpty();
        assertThat(inventoryBatchRepository.findById(testBatch.getId()))
                .get()
                .extracting(InventoryBatch::getReservedQuantity)
                .isEqualTo(0);
    }

    @AfterEach
    void cleanUpCommittedFixtures() {
        /*
         * Consumer identity, raw events and pending mappings deliberately use
         * independent transactions. Remove their committed test fixtures before
         * the context is closed so the next integration-test class starts clean.
         */
        jdbcTemplate.execute("""
            TRUNCATE TABLE
                channel_raw_events,
                pending_sku_mapping,
                channel_sku_mapping,
                outbound_tasks,
                inventory_reservations,
                sales_order_items,
                sales_orders,
                inventory_batch,
                locations,
                product_skus,
                products,
                categories,
                customers,
                integration_configs,
                system_config,
                warehouses
            RESTART IDENTITY CASCADE
            """);
    }

    @Test
    @DisplayName("retail mode creates a lightweight consumer and shipping snapshot without inventory side effects")
    void testOrderSync_RetailModeCreatesConsumerAndPendingOrder() {
        testConfig.setRetailMode(true);
        integrationConfigRepository.save(testConfig);

        ShopifyCustomerDto shopifyCustomer = new ShopifyCustomerDto();
        shopifyCustomer.setId(250L);
        shopifyCustomer.setEmail("retail@example.com");
        shopifyCustomer.setFirstName("Retail");
        shopifyCustomer.setLastName("Buyer");

        ShopifyAddressDto address = new ShopifyAddressDto();
        address.setName("Retail Receiver");
        address.setPhone("+44 20 5555 0101");
        address.setAddress1("25 Retail Road");
        address.setCity("London");
        address.setProvince("Greater London");
        address.setZip("E1 1AA");
        address.setCountryCode("GB");

        ShopifyLineItemDto lineItem = new ShopifyLineItemDto();
        lineItem.setId(25L);
        lineItem.setSku("TEST-SKU-001");
        lineItem.setName("Test ProductSku");
        lineItem.setQuantity(2);
        lineItem.setPrice("99.99");

        ShopifyOrderDto order = new ShopifyOrderDto();
        order.setId(25000L);
        order.setName("#1025");
        order.setEmail("retail@example.com");
        order.setFinancialStatus("paid");
        order.setCustomer(shopifyCustomer);
        order.setShippingAddress(address);
        order.setLineItems(List.of(lineItem));
        when(shopifyApiClient.fetchOrdersRaw(any())).thenReturn(ordersJson(order));

        ShopifyIntegrationService.SyncResult result = shopifyIntegrationService.syncOrders();

        assertThat(result.getSuccessCount()).isEqualTo(1);
        Customer consumer = customerRepository.findByEmail("retail@example.com").orElseThrow();
        assertThat(consumer.getCustomerType()).isEqualTo(CustomerType.CONSUMER);
        assertThat(consumer.getSource()).isEqualTo(CustomerSource.CHANNEL);
        assertThat(consumer.getExternalCustomerId()).isEqualTo("250");
        assertThat(consumer.getCreditLimit()).isEqualByComparingTo(BigDecimal.ZERO);

        SalesOrder salesOrder = salesOrderRepository.findByExternalOrderId("25000").orElseThrow();
        assertThat(salesOrder.getStatus()).isEqualTo(SalesOrderStatus.PENDING_APPROVAL);
        assertThat(salesOrder.getConsigneeName()).isEqualTo("Retail Receiver");
        assertThat(salesOrder.getShipAddress1()).isEqualTo("25 Retail Road");
        assertThat(salesOrder.getShipCountryCode()).isEqualTo("GB");
        assertThat(outboundTaskRepository.findBySalesOrderId(salesOrder.getId())).isEmpty();
        assertThat(inventoryBatchRepository.findById(testBatch.getId()))
            .get()
            .extracting(InventoryBatch::getReservedQuantity)
            .isEqualTo(0);
    }

    @Test
    @DisplayName("case-4")
    void testDeduplication() {
        // Given: customer already exists; integration has no customer-create permission
        customerRepository.save(Customer.builder()
                .code("CUST-DEDUP")
                .name("Dedup Test")
                .email("dedup@example.com")
                .isActive(true)
                .creditLimit(BigDecimal.ZERO)
                .build());

        // Mock Shopify API response
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
        // Given: customer exists, but SKU mapping does not
        customerRepository.save(Customer.builder()
                .code("CUST-SKU-MISS")
                .name("SKU Test")
                .email("sku-test@example.com")
                .isActive(true)
                .creditLimit(BigDecimal.ZERO)
                .build());

        // Mock Shopify API response with non-existent SKU
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

        // P1-B2: unknown SKU lands in the pending mapping queue instead of being lost
        Optional<PendingSkuMapping> pendingSku = pendingSkuMappingRepository
                .findByChannelAndExternalSku("SHOPIFY", "NON-EXISTENT-SKU");
        assertThat(pendingSku).isPresent();
        assertThat(pendingSku.get().getStatus()).isEqualTo(PendingSkuMapping.STATUS_PENDING);
        assertThat(pendingSku.get().getSampleExternalOrderNo()).isEqualTo("#1004");
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
