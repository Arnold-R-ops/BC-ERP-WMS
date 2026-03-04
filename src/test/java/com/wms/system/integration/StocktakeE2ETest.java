package com.wms.system.integration;

import com.wms.system.config.TestSecurityConfig;
import com.wms.system.dto.stocktake.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.*;
import com.wms.system.service.StocktakeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stocktake End-to-End Integration Test
 *
 * V3.8 Architecture: Complete Stocktake Workflow Test
 *
 * Test Scenario:
 * 1. Prepare inventory data
 * 2. Create monthly stocktake task
 * 3. Start counting
 * 4. Get stocktake items (blind count - no snapshot qty)
 * 5. Submit count results (simulate loss/gain)
 * 6. Finish counting
 * 7. Get review items (with snapshot and difference)
 * 8. Manager reviews and approves
 * 9. Verify inventory adjusted
 * 10. Verify stock transactions recorded
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("case-1")
class StocktakeE2ETest {

    @Autowired
    private StocktakeService stocktakeService;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private StocktakeTaskRepository stocktakeTaskRepository;

    @Autowired
    private StocktakeItemRepository stocktakeItemRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    private Warehouse testWarehouse;
    private Location testLocation;
    private Product testProduct1;
    private Product testProduct2;
    private InventoryBatch testBatch1;
    private InventoryBatch testBatch2;

    @BeforeEach
    void setUp() {
        // Clean up data
        stockTransactionRepository.deleteAll();
        stocktakeItemRepository.deleteAll();
        stocktakeTaskRepository.deleteAll();
        inventoryBatchRepository.deleteAll();
        locationRepository.deleteAll();
        productRepository.deleteAll();
        productSpuRepository.deleteAll();
        warehouseRepository.deleteAll();

        // 1. Create warehouse
        testWarehouse = Warehouse.builder()
            .code("WH01")
            .name("Main Warehouse")
            .address("Warehouse Address")
            .isActive(true)
            .build();
        testWarehouse = warehouseRepository.save(testWarehouse);

        // 2. Create location
        testLocation = Location.builder()
            .warehouse(testWarehouse)
            .shelfNumber("A-01")
            .positionNumber("001")
            .zone(Zone.ZONE_A)
            .enabled(true)
            .build();
        testLocation = locationRepository.save(testLocation);

        // 3. Create product SPU
        ProductSpu productSpu = ProductSpu.builder()
            .spuCode("SPU001")
            .spuName("Test Product SPU")
            .category("Electronics")
            .brand("Test Brand")
            .build();
        productSpu = productSpuRepository.save(productSpu);

        // 4. Create products
        testProduct1 = Product.builder()
            .spu(productSpu)
            .skuName("SKU001")
            .barcode("1234567890")
            .name("Test Product 1")
            .specification("Standard")
            .unitPrice(new java.math.BigDecimal("20.00"))
            .nearExpiryDays(30)
            .enabled(true)
            .build();
        testProduct1 = productRepository.save(testProduct1);
        testProduct2 = Product.builder()
            .spu(productSpu)
            .skuName("SKU002")
            .barcode("0987654321")
            .name("Test Product 2")
            .specification("Standard")
            .unitPrice(new java.math.BigDecimal("30.00"))
            .nearExpiryDays(30)
            .enabled(true)
            .build();
        testProduct2 = productRepository.save(testProduct2);

        // 5. Create inventory batches
        testBatch1 = InventoryBatch.builder()
            .batchCode("BATCH001")
            .product(testProduct1)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(100)
            .initialQuantity(100)
            .productionDate(LocalDate.now().minusDays(10))
            .expiryDate(LocalDate.now().plusDays(355))
            .active(true)
            .build();
        testBatch1 = inventoryBatchRepository.save(testBatch1);
        testBatch2 = InventoryBatch.builder()
            .batchCode("BATCH002")
            .product(testProduct2)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(50)
            .initialQuantity(50)
            .productionDate(LocalDate.now().minusDays(5))
            .expiryDate(LocalDate.now().plusDays(360))
            .active(true)
            .build();
        testBatch2 = inventoryBatchRepository.save(testBatch2);
    }

    @Test
    @DisplayName("case-2")
    void testCompleteStocktakeWorkflowWithLoss() {
        // Step 1: Create monthly stocktake task
        CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
            .warehouseId(testWarehouse.getId())
            .cycleType("MONTHLY")
            .build();

        StocktakeTaskResponse taskResponse = stocktakeService.createCycleTask(
            request,
            1L,
            "manager"
        );

        // Verify: Task created with snapshot
        assertThat(taskResponse).isNotNull();
        assertThat(taskResponse.getStatus()).isEqualTo("CREATED");
        assertThat(taskResponse.getCycleType()).isEqualTo("MONTHLY");
        assertThat(taskResponse.getTotalItems()).isEqualTo(2);  // 2 batches
        assertThat(taskResponse.getCountedItems()).isEqualTo(0);
        assertThat(taskResponse.getProgress()).isEqualTo(0);
        assertThat(taskResponse.getSnapshotTime()).isNotNull();

        // Step 2: Start counting
        StocktakeTaskResponse countingTask = stocktakeService.startCounting(taskResponse.getId());

        // Verify: Status changed to COUNTING
        assertThat(countingTask.getStatus()).isEqualTo("COUNTING");

        // Step 3: Get stocktake items (blind count - no snapshot qty)
        List<StocktakeItemResponse> items = stocktakeService.getStocktakeItems(taskResponse.getId());

        // Verify: Items returned without snapshot qty (blind count)
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getCountedQty()).isNull();
        assertThat(items.get(0).getIsCounted()).isFalse();

        // Step 4: Submit count results (simulate inventory loss)
        StocktakeItemResponse item1 = items.stream()
            .filter(item -> item.getBatchCode().equals("BATCH001"))
            .findFirst()
            .orElseThrow();

        SubmitCountRequest countRequest1 = SubmitCountRequest.builder()
            .countedQty(95)  // Actual: 95, Snapshot: 100, Loss: -5
            .remark("Counted by warehouse staff")
            .build();

        StocktakeItemResponse countedItem1 = stocktakeService.submitCount(
            taskResponse.getId(),
            item1.getId(),
            countRequest1,
            2L,
            "warehouse_staff"
        );

        // Verify: Count submitted
        assertThat(countedItem1.getCountedQty()).isEqualTo(95);
        assertThat(countedItem1.getIsCounted()).isTrue();
        assertThat(countedItem1.getCountedByName()).isEqualTo("warehouse_staff");
        assertThat(countedItem1.getCountedAt()).isNotNull();

        // Submit count for second item (simulate inventory gain)
        StocktakeItemResponse item2 = items.stream()
            .filter(item -> item.getBatchCode().equals("BATCH002"))
            .findFirst()
            .orElseThrow();

        SubmitCountRequest countRequest2 = SubmitCountRequest.builder()
            .countedQty(55)  // Actual: 55, Snapshot: 50, Gain: +5
            .remark("Found extra items")
            .build();

        StocktakeItemResponse countedItem2 = stocktakeService.submitCount(
            taskResponse.getId(),
            item2.getId(),
            countRequest2,
            2L,
            "warehouse_staff"
        );

        assertThat(countedItem2.getCountedQty()).isEqualTo(55);
        assertThat(countedItem2.getIsCounted()).isTrue();

        // Step 5: Finish counting
        StocktakeTaskResponse finishedTask = stocktakeService.finishCounting(taskResponse.getId());

        // Verify: Status changed to REVIEWING
        assertThat(finishedTask.getStatus()).isEqualTo("REVIEWING");
        assertThat(finishedTask.getCountedItems()).isEqualTo(2);
        assertThat(finishedTask.getDifferenceItems()).isEqualTo(2);  // Both have differences
        assertThat(finishedTask.getProgress()).isEqualTo(100);

        // Step 6: Get review items (with snapshot and difference)
        List<StocktakeItemDetailResponse> reviewItems = stocktakeService
            .getStocktakeItemsForReview(taskResponse.getId());

        // Verify: Review items show snapshot and difference
        assertThat(reviewItems).hasSize(2);

        StocktakeItemDetailResponse reviewItem1 = reviewItems.stream()
            .filter(item -> item.getBatchCode().equals("BATCH001"))
            .findFirst()
            .orElseThrow();

        assertThat(reviewItem1.getSnapshotQty()).isEqualTo(100);  // Now visible
        assertThat(reviewItem1.getCountedQty()).isEqualTo(95);
        assertThat(reviewItem1.getDifferenceQty()).isEqualTo(-5);  // Loss

        StocktakeItemDetailResponse reviewItem2 = reviewItems.stream()
            .filter(item -> item.getBatchCode().equals("BATCH002"))
            .findFirst()
            .orElseThrow();

        assertThat(reviewItem2.getSnapshotQty()).isEqualTo(50);
        assertThat(reviewItem2.getCountedQty()).isEqualTo(55);
        assertThat(reviewItem2.getDifferenceQty()).isEqualTo(5);  // Gain

        // Step 7: Manager reviews and approves
        ReviewStocktakeRequest reviewRequest = ReviewStocktakeRequest.builder()
            .approved(true)
            .comment("Differences are acceptable and verified")
            .build();

        StocktakeTaskResponse completedTask = stocktakeService.reviewStocktake(
            taskResponse.getId(),
            reviewRequest,
            3L,
            "manager"
        );

        // Verify: Task completed
        assertThat(completedTask.getStatus()).isEqualTo("COMPLETED");
        assertThat(completedTask.getReviewedByName()).isEqualTo("manager");
        assertThat(completedTask.getReviewComment()).isEqualTo("Differences are acceptable and verified");
        assertThat(completedTask.getReviewedAt()).isNotNull();

        // Step 8: Verify inventory adjusted
        InventoryBatch adjustedBatch1 = inventoryBatchRepository.findById(testBatch1.getId()).orElseThrow();
        assertThat(adjustedBatch1.getQuantity()).isEqualTo(95);  // Adjusted from 100 to 95
        

        InventoryBatch adjustedBatch2 = inventoryBatchRepository.findById(testBatch2.getId()).orElseThrow();
        assertThat(adjustedBatch2.getQuantity()).isEqualTo(55);  // Adjusted from 50 to 55
        

        // Step 9: Verify stock transactions recorded
        List<StockTransaction> transactions = stockTransactionRepository
            .findBySourceOrderId(completedTask.getTaskNo());

        assertThat(transactions).hasSize(2);

        // Verify loss transaction
        StockTransaction lossTransaction = transactions.stream()
            .filter(t -> t.getProduct().getId().equals(testProduct1.getId()))
            .findFirst()
            .orElseThrow();

        assertThat(lossTransaction.getSourceType()).isEqualTo(SourceType.INVENTORY_LOSS);
        assertThat(lossTransaction.getTransactionType()).isEqualTo(TransactionType.OUT);
        assertThat(lossTransaction.getQuantity()).isEqualTo(5);
        assertThat(lossTransaction.getProduct().getId()).isEqualTo(testProduct1.getId());

        // Verify gain transaction
        StockTransaction gainTransaction = transactions.stream()
            .filter(t -> t.getProduct().getId().equals(testProduct2.getId()))
            .findFirst()
            .orElseThrow();

        assertThat(gainTransaction.getSourceType()).isEqualTo(SourceType.INVENTORY_GAIN);
        assertThat(gainTransaction.getTransactionType()).isEqualTo(TransactionType.IN);
        assertThat(gainTransaction.getQuantity()).isEqualTo(5);  // Gain
        assertThat(gainTransaction.getProduct().getId()).isEqualTo(testProduct2.getId());
    }

    @Test
    @DisplayName("case-3")
    void testStocktakeWorkflowRejected() {
        // Step 1: Create and start stocktake
        CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
            .warehouseId(testWarehouse.getId())
            .cycleType("MONTHLY")
            .build();

        StocktakeTaskResponse taskResponse = stocktakeService.createCycleTask(
            request,
            1L,
            "manager"
        );

        stocktakeService.startCounting(taskResponse.getId());

        // Step 2: Submit counts with large differences
        List<StocktakeItemResponse> items = stocktakeService.getStocktakeItems(taskResponse.getId());

        for (StocktakeItemResponse item : items) {
            SubmitCountRequest countRequest = SubmitCountRequest.builder()
                .countedQty(10)  // Very different from actual
                .remark("Counted")
                .build();

            stocktakeService.submitCount(
                taskResponse.getId(),
                item.getId(),
                countRequest,
                2L,
                "warehouse_staff"
            );
        }

        stocktakeService.finishCounting(taskResponse.getId());

        // Step 3: Manager rejects due to large differences
        ReviewStocktakeRequest reviewRequest = ReviewStocktakeRequest.builder()
            .approved(false)
            .comment("Too many differences, please recount")
            .build();

        StocktakeTaskResponse rejectedTask = stocktakeService.reviewStocktake(
            taskResponse.getId(),
            reviewRequest,
            3L,
            "manager"
        );

        // Verify: Task rejected
        assertThat(rejectedTask.getStatus()).isEqualTo("REJECTED");
        assertThat(rejectedTask.getReviewComment()).isEqualTo("Too many differences, please recount");

        // Step 4: Verify inventory NOT adjusted
        InventoryBatch batch1 = inventoryBatchRepository.findById(testBatch1.getId()).orElseThrow();
        assertThat(batch1.getQuantity()).isEqualTo(100);  // Unchanged

        InventoryBatch batch2 = inventoryBatchRepository.findById(testBatch2.getId()).orElseThrow();
        assertThat(batch2.getQuantity()).isEqualTo(50);  // Unchanged

        // Step 5: Verify no stock transactions created
        List<StockTransaction> transactions = stockTransactionRepository
            .findBySourceOrderId(rejectedTask.getTaskNo());
        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("case-4")
    void testStocktakeWorkflowNoDifferences() {
        // Step 1: Create and start stocktake
        CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
            .warehouseId(testWarehouse.getId())
            .cycleType("QUARTERLY")
            .build();

        StocktakeTaskResponse taskResponse = stocktakeService.createCycleTask(
            request,
            1L,
            "manager"
        );

        stocktakeService.startCounting(taskResponse.getId());

        // Step 2: Submit counts matching snapshot (no differences)
        List<StocktakeItemResponse> items = stocktakeService.getStocktakeItems(taskResponse.getId());

        StocktakeItemResponse item1 = items.stream()
            .filter(item -> item.getBatchCode().equals("BATCH001"))
            .findFirst()
            .orElseThrow();

        SubmitCountRequest countRequest1 = SubmitCountRequest.builder()
            .countedQty(100)  // Matches snapshot
            .remark("Count matches")
            .build();

        stocktakeService.submitCount(
            taskResponse.getId(),
            item1.getId(),
            countRequest1,
            2L,
            "warehouse_staff"
        );

        StocktakeItemResponse item2 = items.stream()
            .filter(item -> item.getBatchCode().equals("BATCH002"))
            .findFirst()
            .orElseThrow();

        SubmitCountRequest countRequest2 = SubmitCountRequest.builder()
            .countedQty(50)  // Matches snapshot
            .remark("Count matches")
            .build();

        stocktakeService.submitCount(
            taskResponse.getId(),
            item2.getId(),
            countRequest2,
            2L,
            "warehouse_staff"
        );

        StocktakeTaskResponse finishedTask = stocktakeService.finishCounting(taskResponse.getId());

        // Verify: No differences
        assertThat(finishedTask.getDifferenceItems()).isEqualTo(0);

        // Step 3: Manager approves
        ReviewStocktakeRequest reviewRequest = ReviewStocktakeRequest.builder()
            .approved(true)
            .comment("Perfect count, no adjustments needed")
            .build();

        StocktakeTaskResponse completedTask = stocktakeService.reviewStocktake(
            taskResponse.getId(),
            reviewRequest,
            3L,
            "manager"
        );

        assertThat(completedTask.getStatus()).isEqualTo("COMPLETED");

        // Step 4: Verify inventory unchanged
        InventoryBatch batch1 = inventoryBatchRepository.findById(testBatch1.getId()).orElseThrow();
        assertThat(batch1.getQuantity()).isEqualTo(100);

        InventoryBatch batch2 = inventoryBatchRepository.findById(testBatch2.getId()).orElseThrow();
        assertThat(batch2.getQuantity()).isEqualTo(50);

        // Step 5: Verify no stock transactions (no adjustments needed)
        List<StockTransaction> transactions = stockTransactionRepository
            .findBySourceOrderId(completedTask.getTaskNo());
        assertThat(transactions).isEmpty();
    }

    @Test
    @DisplayName("case-5")
    void testStocktakeWorkflowPartialCounting() {
        // Step 1: Create and start stocktake
        CreateStocktakeTaskRequest request = CreateStocktakeTaskRequest.builder()
            .warehouseId(testWarehouse.getId())
            .cycleType("ADHOC")
            .build();

        StocktakeTaskResponse taskResponse = stocktakeService.createCycleTask(
            request,
            1L,
            "manager"
        );

        stocktakeService.startCounting(taskResponse.getId());

        // Step 2: Submit count for only one item
        List<StocktakeItemResponse> items = stocktakeService.getStocktakeItems(taskResponse.getId());

        StocktakeItemResponse item1 = items.get(0);

        SubmitCountRequest countRequest = SubmitCountRequest.builder()
            .countedQty(95)
            .remark("First item counted")
            .build();

        stocktakeService.submitCount(
            taskResponse.getId(),
            item1.getId(),
            countRequest,
            2L,
            "warehouse_staff"
        );

        // Verify: Progress is 50% (1 of 2 items)
        StocktakeTaskResponse progressTask = stocktakeService.getStocktakeTask(taskResponse.getId());
        assertThat(progressTask.getCountedItems()).isEqualTo(1);
        assertThat(progressTask.getProgress()).isEqualTo(50);

        // Step 3: Submit count for second item
        StocktakeItemResponse item2 = items.get(1);

        SubmitCountRequest countRequest2 = SubmitCountRequest.builder()
            .countedQty(50)
            .remark("Second item counted")
            .build();

        stocktakeService.submitCount(
            taskResponse.getId(),
            item2.getId(),
            countRequest2,
            2L,
            "warehouse_staff"
        );

        // Verify: Progress is 100%
        StocktakeTaskResponse fullProgressTask = stocktakeService.getStocktakeTask(taskResponse.getId());
        assertThat(fullProgressTask.getCountedItems()).isEqualTo(2);
        assertThat(fullProgressTask.getProgress()).isEqualTo(100);
    }
}
