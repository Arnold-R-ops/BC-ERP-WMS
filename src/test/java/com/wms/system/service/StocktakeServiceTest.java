package com.wms.system.service;

import com.wms.system.dto.stocktake.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.inventory.InventoryPostingCommand;
import com.wms.system.service.inventory.InventoryPostingResult;
import com.wms.system.service.inventory.InventoryPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * StocktakeService Unit Test
 *
 * Test Coverage:
 * 1. Create monthly cycle task
 * 2. Create quarterly cycle task
 * 3. Smart selection verification
 * 4. Start counting success
 * 5. Start counting invalid status
 * 6. Submit count success
 * 7. Submit count blind count verification
 * 8. Submit count calculate difference
 * 9. Last count remains editable until explicit finish
 * 10. Review stocktake approve
 * 11. Review stocktake reject
 * 12. Adjust inventory surplus
 * 13. Adjust inventory shortage
 *
 * @author WMS Team
 * @since 2026-01-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class StocktakeServiceTest {

    @Mock
    private StocktakeTaskRepository stocktakeTaskRepository;

    @Mock
    private StocktakeItemRepository stocktakeItemRepository;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private OutboundTaskRepository outboundTaskRepository;

    @Mock
    private ProductSkuRepository productSkuRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private LocationOccupancyService locationOccupancyService;

    @InjectMocks
    private StocktakeService stocktakeService;

    private Warehouse testWarehouse;
    private ProductSku testProduct;
    private Location testLocation;
    private InventoryBatch testBatch;
    private StocktakeTask testTask;

    @BeforeEach
    void setUp() {
        // Create test warehouse
        testWarehouse = Warehouse.builder()
            .id(1L)
            .code("WH01")
            .name("Test Warehouse")
            .isActive(true)
            .build();

        // Create test location
        testLocation = Location.builder()
            .id(1L)
            .locationCode("WH01-A-01-001")
            .warehouse(testWarehouse)
            .build();

        // Create test product
        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
            .id(1L)
            .name("Test ProductSku")
            .barcode("TEST001")
            .build();

        // Create test batch
        testBatch = InventoryBatch.builder()
            .id(1L)
            .batchCode("BATCH001")
            .productSku(testProduct)
            .location(testLocation)
            .quantity(100)
            .initialQuantity(100)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build();

        // Create test task
        testTask = StocktakeTask.builder()
            .id(1L)
            .taskNo("TK-202601-M01")
            .warehouseId(1L)
            .cycleType(StocktakeCycleType.MONTHLY)
            .status(StocktakeStatus.CREATED)
            .snapshotTime(LocalDateTime.now())
            .totalItems(10)
            .countedItems(0)
            .differenceItems(0)
            .createdBy(1L)
            .createdByName("Test User")
            .build();

        lenient().when(stocktakeTaskRepository.findByIdForUpdate(1L))
            .thenReturn(Optional.of(testTask));
        lenient().when(inventoryBatchRepository.findByIdForPosting(1L))
            .thenReturn(Optional.of(testBatch));
        lenient().when(inventoryPostingService.post(any(InventoryPostingCommand.class)))
            .thenAnswer(invocation -> {
                InventoryPostingCommand command = invocation.getArgument(0);
                return new InventoryPostingResult(
                    command.inventoryBatchId(), testBatch.getBatchCode(), command.productSkuId(),
                    command.locationId(), 1L, command.delta(), 100, 100 + command.delta(),
                    true, 99L, command.context().postingId(), command.context().idempotencyKey()
                );
            });
    }

    // ========== Test 1: Create Monthly Task ==========

    @Test
    @DisplayName("case-2")
    void testCreateCycleTask_Monthly() {
        // Given
        CreateStocktakeTaskRequest request = new CreateStocktakeTaskRequest();
        request.setWarehouseId(1L);
        request.setCycleType("MONTHLY");

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(inventoryBatchRepository.findMonthlyStocktakeCandidates(
            anyLong(), eq(1L), any(LocalDateTime.class), any()))
            .thenReturn(List.of());
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> {
                StocktakeTask task = invocation.getArgument(0);
                task.setId(1L);
                return task;
            });
        when(stocktakeItemRepository.saveAll(org.mockito.ArgumentMatchers.<StocktakeItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StocktakeTaskResponse response = stocktakeService.createCycleTask(request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCycleType()).isEqualTo("MONTHLY");
        assertThat(response.getStatus()).isEqualTo("CREATED");
        assertThat(response.getCreatedByName()).isEqualTo("Test User");

        verify(stocktakeTaskRepository).save(any(StocktakeTask.class));
        verify(stocktakeItemRepository).saveAll(org.mockito.ArgumentMatchers.<StocktakeItem>anyList());
    }

    // ========== Test 2: Create Quarterly Task ==========

    @Test
    @DisplayName("case-3")
    void testCreateCycleTask_Quarterly() {
        // Given
        CreateStocktakeTaskRequest request = new CreateStocktakeTaskRequest();
        request.setWarehouseId(1L);
        request.setCycleType("QUARTERLY");

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(inventoryBatchRepository.findAll()).thenReturn(Arrays.asList(testBatch, testBatch));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> {
                StocktakeTask task = invocation.getArgument(0);
                task.setId(1L);
                return task;
            });
        when(stocktakeItemRepository.saveAll(org.mockito.ArgumentMatchers.<StocktakeItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StocktakeTaskResponse response = stocktakeService.createCycleTask(request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCycleType()).isEqualTo("QUARTERLY");
        assertThat(response.getTotalItems()).isGreaterThan(0);

        verify(stocktakeTaskRepository).save(any(StocktakeTask.class));
    }

    // ========== Test 3: Smart Selection ==========

    @Test
    @DisplayName("case-4")
    void testCreateCycleTask_SmartSelection() {
        // Given: Monthly task should select recent batches
        CreateStocktakeTaskRequest request = new CreateStocktakeTaskRequest();
        request.setWarehouseId(1L);
        request.setCycleType("MONTHLY");

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(inventoryBatchRepository.findMonthlyStocktakeCandidates(
            anyLong(), eq(1L), any(LocalDateTime.class), any()))
            .thenReturn(List.of(testBatch));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> {
                StocktakeTask task = invocation.getArgument(0);
                task.setId(1L);
                return task;
            });
        when(stocktakeItemRepository.saveAll(org.mockito.ArgumentMatchers.<StocktakeItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StocktakeTaskResponse response = stocktakeService.createCycleTask(request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        verify(inventoryBatchRepository).findMonthlyStocktakeCandidates(
            anyLong(), eq(1L), any(LocalDateTime.class), any());
    }

    // ========== Test 4: Start Counting Success ==========

    @Test
    @DisplayName("case-5")
    void testStartCounting_Success() {
        // Given
        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StocktakeTaskResponse response = stocktakeService.startCounting(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("COUNTING");

        ArgumentCaptor<StocktakeTask> taskCaptor = ArgumentCaptor.forClass(StocktakeTask.class);
        verify(stocktakeTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(StocktakeStatus.COUNTING);
    }

    // ========== Test 5: Start Counting Invalid Status ==========

    @Test
    @DisplayName("case-6")
    void testStartCounting_InvalidStatus() {
        // Given: Task already in COUNTING status
        testTask.setStatus(StocktakeStatus.COUNTING);

        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));

        // When & Then
        assertThatThrownBy(() -> stocktakeService.startCounting(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.STOCKTAKE_TASK_CANNOT_START);

        verify(stocktakeTaskRepository, never()).save(any(StocktakeTask.class));
    }

    @Test
    @DisplayName("finish counting transitions a counting task to reviewing")
    void testFinishCounting_Success() {
        testTask.setStatus(StocktakeStatus.COUNTING);
        testTask.setCountedItems(testTask.getTotalItems());

        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        StocktakeTaskResponse response = stocktakeService.finishCounting(1L);

        assertThat(response.getStatus()).isEqualTo("REVIEWING");
        verify(stocktakeTaskRepository).save(testTask);
    }

    @Test
    @DisplayName("finish counting is idempotent after an explicit finish")
    void testFinishCounting_AlreadyReviewing() {
        testTask.setStatus(StocktakeStatus.REVIEWING);
        testTask.setCountedItems(testTask.getTotalItems());

        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        StocktakeTaskResponse response = stocktakeService.finishCounting(1L);

        assertThat(response.getStatus()).isEqualTo("REVIEWING");
        verify(stocktakeTaskRepository, never()).save(any(StocktakeTask.class));
    }

    @Test
    @DisplayName("finish counting rejects a task with uncounted items")
    void finishCountingRejectsUncountedItems() {
        testTask.setStatus(StocktakeStatus.COUNTING);
        testTask.setCountedItems(9);
        when(stocktakeItemRepository.countByTaskIdAndIsCounted(1L, false)).thenReturn(1L);

        assertThatThrownBy(() -> stocktakeService.finishCounting(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.STOCKTAKE_TASK_CANNOT_COUNT);

        verify(stocktakeTaskRepository, never()).save(any(StocktakeTask.class));
    }

    // ========== Test 6: Submit Count Success ==========

    @Test
    @DisplayName("case-7")
    void testSubmitCount_Success() {
        // Given
        testTask.setStatus(StocktakeStatus.COUNTING);

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(null)
            .differenceQty(0)
            .isCounted(false)
            .build();

        SubmitCountRequest request = new SubmitCountRequest();
        request.setCountedQty(95);
        request.setRemark("Test count");

        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(stocktakeItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stocktakeItemRepository.save(any(StocktakeItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(1L, 0)).thenReturn(1L);
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));

        // When
        StocktakeItemResponse response = stocktakeService.submitCount(1L, 1L, request, 1L, "Counter");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCountedQty()).isEqualTo(95);
        assertThat(response.getIsCounted()).isTrue();
        assertThat(response.getCountedByName()).isEqualTo("Counter");

        verify(stocktakeItemRepository).save(any(StocktakeItem.class));
        verify(stocktakeTaskRepository).save(any(StocktakeTask.class));
        var lockOrder = inOrder(stocktakeTaskRepository, stocktakeItemRepository);
        lockOrder.verify(stocktakeTaskRepository).findByIdForUpdate(1L);
        lockOrder.verify(stocktakeItemRepository).findById(1L);
    }

    // ========== Test 7: Blind Count ==========

    @Test
    @DisplayName("case-8")
    void testSubmitCount_BlindCount() {
        // Given
        testTask.setStatus(StocktakeStatus.COUNTING);

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(null)
            .differenceQty(0)
            .isCounted(false)
            .build();

        SubmitCountRequest request = new SubmitCountRequest();
        request.setCountedQty(95);

        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(stocktakeItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stocktakeItemRepository.save(any(StocktakeItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(1L, 0)).thenReturn(1L);
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));

        // When
        StocktakeItemResponse response = stocktakeService.submitCount(1L, 1L, request, 1L, "Counter");

        // Then: Blind-count response contains counted result
        assertThat(response.getCountedQty()).isEqualTo(95);
    }

    // ========== Test 8: Calculate Difference ==========

    @Test
    @DisplayName("case-9")
    void testSubmitCount_CalculateDifference() {
        // Given
        testTask.setStatus(StocktakeStatus.COUNTING);

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(null)
            .differenceQty(0)
            .isCounted(false)
            .build();

        SubmitCountRequest request = new SubmitCountRequest();
        request.setCountedQty(95);

        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(stocktakeItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stocktakeItemRepository.save(any(StocktakeItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(1L, 0)).thenReturn(1L);
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));

        // When
        stocktakeService.submitCount(1L, 1L, request, 1L, "Counter");

        // Then
        ArgumentCaptor<StocktakeItem> itemCaptor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(stocktakeItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getDifferenceQty()).isEqualTo(-5); // 95 - 100 = -5
    }

    @Test
    @DisplayName("recounting to zero difference clears task difference statistics")
    void recountToZeroDifferenceRefreshesTaskStatistics() {
        testTask.setStatus(StocktakeStatus.COUNTING);
        testTask.setDifferenceItems(1);

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(95)
            .differenceQty(-5)
            .isCounted(true)
            .build();
        SubmitCountRequest request = new SubmitCountRequest();
        request.setCountedQty(100);

        when(stocktakeItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stocktakeItemRepository.save(any(StocktakeItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(stocktakeItemRepository.countByTaskIdAndDifferenceQtyNot(1L, 0)).thenReturn(0L);
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));

        stocktakeService.submitCount(1L, 1L, request, 1L, "Counter");

        verify(stocktakeItemRepository).countByTaskIdAndDifferenceQtyNot(1L, 0);
        assertThat(testTask.getDifferenceItems()).isZero();
        assertThat(testTask.getCountedItems()).isZero();
    }

    // ========== Test 9: Last count waits for explicit finish ==========

    @Test
    @DisplayName("case-10")
    void testSubmitCount_LastItemRemainsCounting() {
        // Given: Last item to count
        testTask.setStatus(StocktakeStatus.COUNTING);
        testTask.setTotalItems(1);
        testTask.setCountedItems(0);

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(null)
            .differenceQty(0)
            .isCounted(false)
            .build();

        SubmitCountRequest request = new SubmitCountRequest();
        request.setCountedQty(100);

        when(stocktakeTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        when(stocktakeItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stocktakeItemRepository.save(any(StocktakeItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));

        // When
        stocktakeService.submitCount(1L, 1L, request, 1L, "Counter");

        // Then
        ArgumentCaptor<StocktakeTask> taskCaptor = ArgumentCaptor.forClass(StocktakeTask.class);
        verify(stocktakeTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(StocktakeStatus.COUNTING);
        assertThat(taskCaptor.getValue().getCountedItems()).isEqualTo(1);
        verifyNoInteractions(inventoryPostingService);
    }

    @Test
    void rejectedTaskAllowsMultipleCorrectionsThenExplicitFinish() {
        testTask.setStatus(StocktakeStatus.REVIEWING);
        testTask.setTotalItems(2);
        testTask.setCountedItems(2);
        when(stocktakeTaskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        ReviewStocktakeRequest reject = new ReviewStocktakeRequest();
        reject.setApproved(false);
        reject.setComment("Recheck both lines");
        stocktakeService.reviewStocktake(1L, reject, 7L, "Manager");

        when(stocktakeItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        for (long id : List.of(1L, 2L)) {
            StocktakeItem item = StocktakeItem.builder().id(id).taskId(1L).productSkuId(1L)
                .batchId(1L).locationId(1L).snapshotQty(100).countedQty(95)
                .differenceQty(-5).isCounted(true).build();
            when(stocktakeItemRepository.findById(id)).thenReturn(Optional.of(item));
            SubmitCountRequest count = new SubmitCountRequest();
            count.setCountedQty(100);
            stocktakeService.submitCount(1L, id, count, 8L, "Counter");
            assertThat(testTask.getStatus()).isEqualTo(StocktakeStatus.COUNTING);
            assertThat(testTask.getCountedItems()).isEqualTo(2);
            assertThat(item.getCountedQty()).isEqualTo(100);
        }
        assertThat(stocktakeService.finishCounting(1L).getStatus()).isEqualTo("REVIEWING");
        assertThat(stocktakeService.finishCounting(1L).getStatus()).isEqualTo("REVIEWING");
        SubmitCountRequest late = new SubmitCountRequest();
        late.setCountedQty(80);
        assertThatThrownBy(() -> stocktakeService.submitCount(1L, 1L, late, 8L, "Counter"))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventoryPostingService);
    }

    // ========== Test 10: Review Approve ==========

    @Test
    @DisplayName("case-11")
    void testReviewStocktake_Approve() {
        // Given
        testTask.setStatus(StocktakeStatus.REVIEWING);
        testTask.setCountedItems(testTask.getTotalItems());

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(95)
            .differenceQty(-5)
            .isCounted(true)
            .build();

        ReviewStocktakeRequest request = new ReviewStocktakeRequest();
        request.setApproved(true);
        request.setComment("Approved");

        when(stocktakeItemRepository.findByTaskId(1L)).thenReturn(List.of(item));
        when(inventoryBatchRepository.findAllByIdForPosting(List.of(1L)))
            .thenReturn(List.of(testBatch));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        // When
        StocktakeTaskResponse response = stocktakeService.reviewStocktake(1L, request, 1L, "Manager");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getReviewedByName()).isEqualTo("Manager");

        verify(inventoryPostingService).post(any(InventoryPostingCommand.class));
    }

    // ========== Test 11: Review Reject ==========

    @Test
    @DisplayName("case-12")
    void testReviewStocktake_Reject() {
        // Given
        testTask.setStatus(StocktakeStatus.REVIEWING);

        ReviewStocktakeRequest request = new ReviewStocktakeRequest();
        request.setApproved(false);
        request.setComment("Need recount");

        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        // When
        StocktakeTaskResponse response = stocktakeService.reviewStocktake(1L, request, 1L, "Manager");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("COUNTING");
        assertThat(response.getReviewComment()).isEqualTo("Need recount");

        verify(inventoryPostingService, never()).post(any(InventoryPostingCommand.class));
    }

    @Test
    @DisplayName("approved review rejects a task with uncounted items")
    void approvedReviewRejectsUncountedItems() {
        testTask.setStatus(StocktakeStatus.REVIEWING);
        testTask.setCountedItems(9);
        when(stocktakeItemRepository.countByTaskIdAndIsCounted(1L, false)).thenReturn(1L);

        ReviewStocktakeRequest request = new ReviewStocktakeRequest();
        request.setApproved(true);

        assertThatThrownBy(() -> stocktakeService.reviewStocktake(
            1L,
            request,
            1L,
            "Manager",
            "review-incomplete"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.STOCKTAKE_TASK_CANNOT_REVIEW);

        verify(inventoryPostingService, never()).post(any(InventoryPostingCommand.class));
        verify(stocktakeTaskRepository, never()).save(any(StocktakeTask.class));
    }

    // ========== Test 12: Adjust Inventory Surplus ==========

    @Test
    @DisplayName("case-13")
    void testAdjustInventory_Surplus() {
        // Given: Counted 105, snapshot 100 (surplus +5)
        testTask.setStatus(StocktakeStatus.REVIEWING);
        testTask.setCountedItems(testTask.getTotalItems());

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(105)
            .differenceQty(5)
            .isCounted(true)
            .build();

        ReviewStocktakeRequest request = new ReviewStocktakeRequest();
        request.setApproved(true);

        when(stocktakeItemRepository.findByTaskId(1L)).thenReturn(List.of(item));
        when(inventoryBatchRepository.findAllByIdForPosting(List.of(1L)))
            .thenReturn(List.of(testBatch));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        // When
        stocktakeService.reviewStocktake(1L, request, 1L, "Manager", "review-command-1");

        // Then
        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
            ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService).post(commandCaptor.capture());
        InventoryPostingCommand command = commandCaptor.getValue();
        assertThat(command.delta()).isEqualTo(5);
        assertThat(command.context().sourceType()).isEqualTo(SourceType.INVENTORY_GAIN);
        assertThat(command.context().postingId()).isEqualTo("STOCKTAKE:TK-202601-M01:1");
        assertThat(command.context().idempotencyKey()).startsWith("stocktake-").hasSize(74);
        verify(locationOccupancyService).refreshLocationStatus(1L);
    }

    // ========== Test 13: Adjust Inventory Shortage ==========

    @Test
    @DisplayName("case-14")
    void testAdjustInventory_Shortage() {
        // Given: Counted 90, snapshot 100 (shortage -10)
        testTask.setStatus(StocktakeStatus.REVIEWING);
        testTask.setCountedItems(testTask.getTotalItems());

        StocktakeItem item = StocktakeItem.builder()
            .id(1L)
            .taskId(1L)
            .productSkuId(1L)
            .batchId(1L)
            .locationId(1L)
            .snapshotQty(100)
            .countedQty(90)
            .differenceQty(-10)
            .isCounted(true)
            .build();

        ReviewStocktakeRequest request = new ReviewStocktakeRequest();
        request.setApproved(true);

        when(stocktakeItemRepository.findByTaskId(1L)).thenReturn(List.of(item));
        when(inventoryBatchRepository.findAllByIdForPosting(List.of(1L)))
            .thenReturn(List.of(testBatch));
        when(stocktakeTaskRepository.save(any(StocktakeTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));

        // When
        stocktakeService.reviewStocktake(1L, request, 1L, "Manager", "review-command-2");

        // Then
        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
            ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService).post(commandCaptor.capture());
        InventoryPostingCommand command = commandCaptor.getValue();
        assertThat(command.delta()).isEqualTo(-10);
        assertThat(command.context().sourceType()).isEqualTo(SourceType.INVENTORY_LOSS);
        assertThat(command.context().postingId()).isEqualTo("STOCKTAKE:TK-202601-M01:1");
        verify(locationOccupancyService).refreshLocationStatus(1L);
    }
}

