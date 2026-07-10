package com.wms.system.integration;

import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.sales.SalesOrderResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.*;
import com.wms.system.service.OutboundService;
import com.wms.system.service.SalesSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sales Outbound End-to-End Integration Test
 *
 * V3.7 Architecture: Complete Sales Outbound Workflow Test
 *
 * Test Scenario:
 * 1. Create customer
 * 2. Create product and inventory
 * 3. Create sales order (low price triggers approval)
 * 4. Manager approves order
 * 5. Verify outbound tasks generated
 * 6. Warehouse staff confirms picking
 * 7. Verify order status updated to SHIPPED
 * 8. Verify inventory deducted
 * 9. Verify stock transactions recorded
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.7 (Smart Sales and Outbound System)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class SalesOutboundE2ETest {

    @Autowired
    private SalesSubmissionService salesSubmissionService;

    @Autowired
    private OutboundService outboundService;

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
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    private Customer testCustomer;
    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch testBatch;

    @BeforeEach
    void setUp() {
        // Clean up data
        stockTransactionRepository.deleteAll();
        outboundTaskRepository.deleteAll();
        salesOrderItemRepository.deleteAll();
        salesOrderRepository.deleteAll();
        inventoryBatchRepository.deleteAll();
        locationRepository.deleteAll();
        productRepository.deleteAll();
        productSpuRepository.deleteAll();
        customerRepository.deleteAll();
        warehouseRepository.deleteAll();
        systemConfigRepository.deleteAll();

        // Create system config for approval threshold
        SystemConfig minPriceConfig = SystemConfig.builder()
            .configKey("sales.min_price_approval_enabled")
            .configValue("true")
            .configType(SystemConfig.ConfigType.BOOLEAN.name())
            .description("Enable low price approval")
            .build();
        systemConfigRepository.save(minPriceConfig);

        SystemConfig thresholdConfig = SystemConfig.builder()
            .configKey("sales.approval.amount_threshold")
            .configValue("50000.00")
            .configType(SystemConfig.ConfigType.DECIMAL.name())
            .description("High amount approval threshold")
            .build();
        systemConfigRepository.save(thresholdConfig);

        // 1. Create customer
        testCustomer = Customer.builder()
            .code("CUST001")
            .name("Test Customer")
            .contact("John Doe")
            .phone("13800138000")
            .email("john@example.com")
            .address("123 Test Street")
            .creditLimit(new BigDecimal("100000.00"))
            .isActive(true)
            .build();
        testCustomer = customerRepository.save(testCustomer);

        // 2. Create warehouse
        testWarehouse = Warehouse.builder()
            .code("WH01")
            .name("Main Warehouse")
            .address("Warehouse Address")
            .isActive(true)
            .build();
        testWarehouse = warehouseRepository.save(testWarehouse);

        // 3. Create location
        testLocation = Location.builder()
            .warehouse(testWarehouse)
            .shelfNumber("A-01")
            .positionNumber("001")
            .zone(Zone.ZONE_A)
            .enabled(true)
            .build();
        testLocation = locationRepository.save(testLocation);

        // 4. Create product SPU
        ProductSpu productSpu = ProductSpu.builder()
            .spuCode("SPU001")
            .spuName("Test Product SPU")
            .category("Electronics")
            .brand("Test Brand")
            .build();
        productSpu = productSpuRepository.save(productSpu);

        // 5. Create product
        testProduct = Product.builder()
            .spu(productSpu)
            .skuName("SKU001")
            .barcode("1234567890")
            .name("Test Product")
            .specification("Standard")
            .unitPrice(new BigDecimal("15.00"))
            .minSalesPrice(new BigDecimal("10.00"))
            .nearExpiryDays(30)
            .enabled(true)
            .build();
        testProduct = productRepository.save(testProduct);

        // 6. Create inventory batch with sufficient stock
        testBatch = InventoryBatch.builder()
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(200)
            .initialQuantity(200)
            .productionDate(LocalDate.now().minusDays(10))
            .expiryDate(LocalDate.now().plusDays(355))
            .active(true)
            .build();
        testBatch = inventoryBatchRepository.save(testBatch);
    }

    @Test
    @DisplayName("case-2")
    void testCompleteSalesOutboundWorkflow() {
        // Step 1: Create sales order with low price (triggers approval)
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(testCustomer.getId())
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(testProduct.getId())
                    .quantity(100)
                    .unitPrice(new BigDecimal("8.00"))  // Below min price (10.00)
                    .rejectNearExpiry(false)
                    .remark("Test order item")
                    .build()
            ))
            .build();

        SalesOrderResponse orderResponse = salesSubmissionService.createSalesOrder(
            request,
            1L,
            "sales_staff"
        );

        // Verify: Order created with PENDING_APPROVAL status
        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(orderResponse.getTotalAmount()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(orderResponse.getReviewReason()).contains(testProduct.getName());

        // Step 2: Manager approves the order
        SalesOrderResponse approvedOrder = salesSubmissionService.approveSalesOrder(
            orderResponse.getId(),
            2L,
            "manager",
            "Price approved for this customer",
            null,
            null,
            null
        );

        // Verify: Order approved and status changed
        assertThat(approvedOrder.getStatus()).isEqualTo("APPROVED_AWAITING_SHIPMENT");
        assertThat(approvedOrder.getReviewedByName()).containsIgnoringCase("manager");
        assertThat(approvedOrder.getReviewComment()).isEqualTo("Price approved for this customer");

        // Step 3: Verify outbound tasks generated
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(approvedOrder.getId());
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).hasSize(1);

        OutboundTask task = tasks.get(0);
        assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.PENDING);
        assertThat(task.getPlanQty()).isEqualTo(100);
        assertThat(task.getAssignedBatchId()).isEqualTo(testBatch.getId());

        // Step 4: Verify inventory allocated
        InventoryBatch allocatedBatch = inventoryBatchRepository.findById(testBatch.getId()).orElseThrow();
        assertThat(allocatedBatch.getQuantity()).isEqualTo(200);

        // Step 5: Warehouse staff confirms picking
        outboundService.confirmPicking(
            task.getId(),
            100,
            3L,
            "warehouse_staff"
        );

        // Step 6: Verify task completed
        OutboundTask completedTask = outboundTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(completedTask.getStatus()).isEqualTo(OutboundTaskStatus.COMPLETED);
        assertThat(completedTask.getActualQty()).isEqualTo(100);
        assertThat(completedTask.getPickedBy()).isEqualTo(3L);
        assertThat(completedTask.getPickedAt()).isNotNull();

        // Step 7: Verify order status updated to SHIPPED
        SalesOrder shippedOrder = salesOrderRepository.findById(approvedOrder.getId()).orElseThrow();
        assertThat(shippedOrder.getStatus()).isEqualTo(SalesOrderStatus.SHIPPED);

        // Step 8: Verify inventory deducted
        InventoryBatch finalBatch = inventoryBatchRepository.findById(testBatch.getId()).orElseThrow();
        assertThat(finalBatch.getQuantity()).isEqualTo(100);  // 200 - 100
        

        // Step 9: Verify stock transactions recorded
        List<StockTransaction> transactions = stockTransactionRepository
            .findBySourceOrderId(approvedOrder.getOrderNo());
        assertThat(transactions).isNotEmpty();
        assertThat(transactions).hasSize(1);

        StockTransaction transaction = transactions.get(0);
        assertThat(transaction.getSourceType()).isEqualTo(SourceType.SALE_OUT);
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.OUT);
        assertThat(transaction.getQuantity()).isEqualTo(100);
        assertThat(transaction.getProduct().getId()).isEqualTo(testProduct.getId());
        assertThat(transaction.getLocation().getId()).isEqualTo(testLocation.getId());
    }

    @Test
    @DisplayName("case-3")
    void testSalesOutboundWorkflowAutoApproved() {
        // Step 1: Create sales order with normal price (auto approved)
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(testCustomer.getId())
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(testProduct.getId())
                    .quantity(50)
                    .unitPrice(new BigDecimal("15.00"))  // Standard price
                    .rejectNearExpiry(false)
                    .build()
            ))
            .build();

        SalesOrderResponse orderResponse = salesSubmissionService.createSalesOrder(
            request,
            1L,
            "sales_staff"
        );

        // Verify: Order auto-approved
        assertThat(orderResponse.getStatus()).isEqualTo("APPROVED_AWAITING_SHIPMENT");
        assertThat(orderResponse.getTotalAmount()).isEqualByComparingTo(new BigDecimal("750.00"));

        // Step 2: Verify outbound tasks generated immediately
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(orderResponse.getId());
        assertThat(tasks).hasSize(1);

        // Step 3: Confirm picking
        OutboundTask task = tasks.get(0);
        outboundService.confirmPicking(task.getId(), 50, 3L, "warehouse_staff");

        // Step 4: Verify order shipped
        SalesOrder shippedOrder = salesOrderRepository.findById(orderResponse.getId()).orElseThrow();
        assertThat(shippedOrder.getStatus()).isEqualTo(SalesOrderStatus.SHIPPED);

        // Step 5: Verify inventory deducted
        InventoryBatch finalBatch = inventoryBatchRepository.findById(testBatch.getId()).orElseThrow();
        assertThat(finalBatch.getQuantity()).isEqualTo(150);  // 200 - 50
    }

    @Test
    @DisplayName("case-4")
    void testSalesOutboundWorkflowRejected() {
        // Step 1: Create sales order with low price
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(testCustomer.getId())
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(testProduct.getId())
                    .quantity(100)
                    .unitPrice(new BigDecimal("5.00"))  // Very low price
                    .rejectNearExpiry(false)
                    .build()
            ))
            .build();

        SalesOrderResponse orderResponse = salesSubmissionService.createSalesOrder(
            request,
            1L,
            "sales_staff"
        );

        assertThat(orderResponse.getStatus()).isEqualTo("PENDING_APPROVAL");

        // Step 2: Manager rejects the order
        SalesOrderResponse rejectedOrder = salesSubmissionService.rejectSalesOrder(
            orderResponse.getId(),
            2L,
            "manager",
            "Price too low, not acceptable"
        );

        // Verify: Order rejected
        assertThat(rejectedOrder.getStatus()).isEqualTo("REJECTED");
        assertThat(rejectedOrder.getReviewReason()).contains(testProduct.getName());
        assertThat(rejectedOrder.getReviewComment()).isEqualTo("Price too low, not acceptable");

        // Step 3: Verify no outbound tasks generated
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(rejectedOrder.getId());
        assertThat(tasks).isEmpty();

        // Step 4: Verify inventory not allocated
        InventoryBatch batch = inventoryBatchRepository.findById(testBatch.getId()).orElseThrow();
        assertThat(batch.getQuantity()).isEqualTo(200);  // Unchanged
        
    }

    @Test
    @DisplayName("case-5")
    void testSalesOutboundWorkflowPartialPicking() {
        // Step 1: Create and approve order
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(testCustomer.getId())
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(testProduct.getId())
                    .quantity(100)
                    .unitPrice(new BigDecimal("15.00"))
                    .rejectNearExpiry(false)
                    .build()
            ))
            .build();

        SalesOrderResponse orderResponse = salesSubmissionService.createSalesOrder(
            request,
            1L,
            "sales_staff"
        );

        // Step 2: Confirm picking with partial quantity
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(orderResponse.getId());
        OutboundTask task = tasks.get(0);

        outboundService.confirmPicking(
            task.getId(),
            80,  // Only 80 picked instead of 100
            3L,
            "warehouse_staff"
        );

        // Step 3: Verify task completed with partial quantity
        OutboundTask completedTask = outboundTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(completedTask.getStatus()).isEqualTo(OutboundTaskStatus.COMPLETED);
        assertThat(completedTask.getPlanQty()).isEqualTo(100);
        assertThat(completedTask.getActualQty()).isEqualTo(80);

        // Step 4: Verify inventory deducted by actual quantity
        InventoryBatch finalBatch = inventoryBatchRepository.findById(testBatch.getId()).orElseThrow();
        assertThat(finalBatch.getQuantity()).isEqualTo(120);  // 200 - 80

        // Step 5: Verify stock transaction recorded with actual quantity
        List<StockTransaction> transactions = stockTransactionRepository
            .findBySourceOrderId(orderResponse.getOrderNo());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getQuantity()).isEqualTo(80);
    }

    @Test
    @DisplayName("case-6")
    void testSalesOutboundWorkflowMultipleItems() {
        // Create second product and batch
        Product product2 = Product.builder()
            .spu(testProduct.getSpu())
            .skuName("SKU002")
            .barcode("0987654321")
            .name("Test Product 2")
            .specification("Standard")
            .unitPrice(new BigDecimal("25.00"))
            .minSalesPrice(new BigDecimal("20.00"))
            .nearExpiryDays(30)
            .enabled(true)
            .build();
        product2 = productRepository.save(product2);
        InventoryBatch batch2 = InventoryBatch.builder()
            .batchCode("BATCH002")
            .product(product2)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(100)
            .initialQuantity(100)
            .productionDate(LocalDate.now().minusDays(5))
            .expiryDate(LocalDate.now().plusDays(360))
            .active(true)
            .build();
        batch2 = inventoryBatchRepository.save(batch2);

        // Step 1: Create order with multiple items
        CreateSalesOrderRequest request = CreateSalesOrderRequest.builder()
            .customerId(testCustomer.getId())
            .items(Arrays.asList(
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(testProduct.getId())
                    .quantity(50)
                    .unitPrice(new BigDecimal("15.00"))
                    .build(),
                CreateSalesOrderRequest.SalesOrderItemData.builder()
                    .productId(product2.getId())
                    .quantity(30)
                    .unitPrice(new BigDecimal("25.00"))
                    .build()
            ))
            .build();

        SalesOrderResponse orderResponse = salesSubmissionService.createSalesOrder(
            request,
            1L,
            "sales_staff"
        );

        // Step 2: Verify multiple outbound tasks generated
        List<OutboundTask> tasks = outboundTaskRepository.findBySalesOrderId(orderResponse.getId());
        assertThat(tasks).hasSize(2);

        // Step 3: Confirm picking for all tasks
        for (OutboundTask task : tasks) {
            outboundService.confirmPicking(
                task.getId(),
                task.getPlanQty(),
                3L,
                "warehouse_staff"
            );
        }

        // Step 4: Verify order shipped
        SalesOrder shippedOrder = salesOrderRepository.findById(orderResponse.getId()).orElseThrow();
        assertThat(shippedOrder.getStatus()).isEqualTo(SalesOrderStatus.SHIPPED);

        // Step 5: Verify both inventories deducted
        InventoryBatch finalBatch1 = inventoryBatchRepository.findById(testBatch.getId()).orElseThrow();
        assertThat(finalBatch1.getQuantity()).isEqualTo(150);  // 200 - 50

        InventoryBatch finalBatch2 = inventoryBatchRepository.findById(batch2.getId()).orElseThrow();
        assertThat(finalBatch2.getQuantity()).isEqualTo(70);  // 100 - 30

        // Step 6: Verify stock transactions for both products
        List<StockTransaction> transactions = stockTransactionRepository
            .findBySourceOrderId(orderResponse.getOrderNo());
        assertThat(transactions).hasSize(2);
    }
}
