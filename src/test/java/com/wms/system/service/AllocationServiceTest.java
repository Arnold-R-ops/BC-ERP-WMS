package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AllocationService Unit Test
 *
 * Test Coverage:
 * 1. Normal allocation with FEFO sorting
 * 2. User-specified batches priority
 * 3. Strict box strategy (full box only)
 * 4. Near-expiry filtering
 * 5. Insufficient stock exception
 * 6. Loose item first strategy
 * 7. Break box when needed
 * 8. Multiple order items allocation
 * 9. Product not found exception
 * 10. Specified batch not found exception
 * 11. Specified batch inactive exception
 * 12. Mixed scenario (full box + loose items)
 * 13. Near-expiry with fresh batches
 * 14. Sales order not found exception
 * 15. FEFO sorting verification
 *
 * @author WMS Team
 * @since 2026-01-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class AllocationServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OutboundTaskRepository outboundTaskRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AllocationService allocationService;

    private SalesOrder testOrder;
    private Product testProduct;
    private Location testLocation;
    private List<InventoryBatch> testBatches;

    @BeforeEach
    void setUp() {
        // Create test location
        testLocation = Location.builder()
            .id(1L)
            .locationCode("WH01-A-01-001")
            .build();

        // Create test product (per_pack_qty = 12)
        testProduct = Product.builder()
            .id(1L)
            .name("Test Product")
            .barcode("TEST001")
            .perPackQty(12)
            .nearExpiryDays(30)
            .minSalesPrice(new BigDecimal("10.00"))
            .build();

        // Create test sales order
        testOrder = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
            .totalAmount(new BigDecimal("100.00"))
            .build();

        // Create test batches
        testBatches = new ArrayList<>();
    }

    // ========== Test 1: Normal Allocation ==========

    @Test
    @DisplayName("case-2")
    void testAllocateInventory_Success() {
        // Given
        SalesOrderItem item = createOrderItem(1L, 1L, 10);

        InventoryBatch batch1 = createBatch(1L, "BATCH001", 15, LocalDate.now().plusMonths(2));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(List.of(batch1));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(10);
        assertThat(tasks.get(0).getStatus()).isEqualTo(OutboundTaskStatus.PENDING);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(1L);

        verify(outboundTaskRepository).save(any(OutboundTask.class));
    }

    // ========== Test 2: User-Specified Batches ==========

    @Test
    @DisplayName("case-3")
    void testAllocateInventory_UserSpecifiedBatches() throws Exception {
        // Given
        SalesOrderItem item = createOrderItem(1L, 1L, 10);
        item.setSpecifiedBatchIds("[2, 3]");

        InventoryBatch batch1 = createBatch(1L, "BATCH001", 20, LocalDate.now().plusMonths(1));
        InventoryBatch batch2 = createBatch(2L, "BATCH002", 8, LocalDate.now().plusMonths(3));
        InventoryBatch batch3 = createBatch(3L, "BATCH003", 5, LocalDate.now().plusMonths(2));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(objectMapper.readValue(
            eq("[2, 3]"),
            org.mockito.ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<List<Long>>>any()
        ))
            .thenReturn(Arrays.asList(2L, 3L));
        when(inventoryBatchRepository.findById(2L)).thenReturn(Optional.of(batch2));
        when(inventoryBatchRepository.findById(3L)).thenReturn(Optional.of(batch3));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(3L); // FEFO: batch3 expires first
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(5);
        assertThat(tasks.get(1).getAssignedBatchId()).isEqualTo(2L);
        assertThat(tasks.get(1).getPlanQty()).isEqualTo(5);

        verify(inventoryBatchRepository, never()).findByProductIdAndActiveOrderByExpiryDateAsc(anyLong(), anyBoolean());
    }

    // ========== Test 3: Strict Box Strategy ==========

    @Test
    @DisplayName("case-4")
    void testAllocateInventory_StrictBoxStrategy() {
        // Given: Order needs 24 items (2 full boxes), product per_pack_qty = 12
        SalesOrderItem item = createOrderItem(1L, 1L, 24);

        InventoryBatch looseBatch = createBatch(1L, "BATCH001", 8, LocalDate.now().plusMonths(1));
        InventoryBatch fullBoxBatch1 = createBatch(2L, "BATCH002", 12, LocalDate.now().plusMonths(2));
        InventoryBatch fullBoxBatch2 = createBatch(3L, "BATCH003", 24, LocalDate.now().plusMonths(3));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(Arrays.asList(looseBatch, fullBoxBatch1, fullBoxBatch2));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should only use full box batches
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(2L); // First full box
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(12);
        assertThat(tasks.get(1).getAssignedBatchId()).isEqualTo(3L); // Second full box
        assertThat(tasks.get(1).getPlanQty()).isEqualTo(12);

        // Verify loose batch was not used
        assertThat(tasks).noneMatch(task -> task.getAssignedBatchId().equals(1L));
    }

    // ========== Test 4: Near-Expiry Filtering ==========

    @Test
    @DisplayName("case-5")
    void testAllocateInventory_NearExpiryFiltering() {
        // Given: reject_near_expiry = true, near_expiry_days = 30
        SalesOrderItem item = createOrderItem(1L, 1L, 10);
        item.setRejectNearExpiry(true);

        InventoryBatch nearExpiryBatch = createBatch(1L, "BATCH001", 20, LocalDate.now().plusDays(20)); // Near expiry
        InventoryBatch freshBatch = createBatch(2L, "BATCH002", 15, LocalDate.now().plusMonths(3)); // Fresh

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(Arrays.asList(nearExpiryBatch, freshBatch));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should only use fresh batch
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(2L);
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(10);
    }

    // ========== Test 5: Insufficient Stock ==========

    @Test
    @DisplayName("case-6")
    void testAllocateInventory_InsufficientStock() {
        // Given: Request 100, but only 30 available
        SalesOrderItem item = createOrderItem(1L, 1L, 100);

        InventoryBatch batch1 = createBatch(1L, "BATCH001", 20, LocalDate.now().plusMonths(1));
        InventoryBatch batch2 = createBatch(2L, "BATCH002", 10, LocalDate.now().plusMonths(2));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(Arrays.asList(batch1, batch2));

        // When & Then
        assertThatThrownBy(() -> allocationService.allocateInventory(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.BATCH_STOCK_INSUFFICIENT);

        verify(outboundTaskRepository, never()).save(any(OutboundTask.class));
    }

    // ========== Test 6: FEFO Sorting ==========

    @Test
    @DisplayName("case-7")
    void testAllocateInventory_FEFOSorting() {
        // Given: Multiple batches with different expiry dates
        SalesOrderItem item = createOrderItem(1L, 1L, 30);

        InventoryBatch batch1 = createBatch(1L, "BATCH001", 10, LocalDate.now().plusMonths(3));
        InventoryBatch batch2 = createBatch(2L, "BATCH002", 10, LocalDate.now().plusMonths(1)); // Expires first
        InventoryBatch batch3 = createBatch(3L, "BATCH003", 10, LocalDate.now().plusMonths(2));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(Arrays.asList(batch2, batch3, batch1)); // Already sorted by expiry date
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should allocate in FEFO order
        assertThat(tasks).hasSize(3);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(2L); // Expires first
        assertThat(tasks.get(1).getAssignedBatchId()).isEqualTo(3L); // Expires second
        assertThat(tasks.get(2).getAssignedBatchId()).isEqualTo(1L); // Expires last
    }

    // ========== Test 7: Loose Item First ==========

    @Test
    @DisplayName("case-8")
    void testAllocateInventory_LooseItemFirst() {
        // Given: Request 15, have loose batch (8) and full box batch (24)
        SalesOrderItem item = createOrderItem(1L, 1L, 15);

        InventoryBatch looseBatch = createBatch(1L, "BATCH001", 8, LocalDate.now().plusMonths(1));
        InventoryBatch fullBoxBatch = createBatch(2L, "BATCH002", 24, LocalDate.now().plusMonths(2));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(Arrays.asList(looseBatch, fullBoxBatch));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should use loose batch first, then break box
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(1L); // Loose batch first
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(8);
        assertThat(tasks.get(1).getAssignedBatchId()).isEqualTo(2L); // Break box
        assertThat(tasks.get(1).getPlanQty()).isEqualTo(7);
    }

    // ========== Test 8: Break Box When Needed ==========

    @Test
    @DisplayName("case-9")
    void testAllocateInventory_BreakBoxWhenNeeded() {
        // Given: Request 5, only full box available
        SalesOrderItem item = createOrderItem(1L, 1L, 5);

        InventoryBatch fullBoxBatch = createBatch(1L, "BATCH001", 24, LocalDate.now().plusMonths(1));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(List.of(fullBoxBatch));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should break box
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(5);
    }

    // ========== Test 9: Multiple Items ==========

    @Test
    @DisplayName("case-10")
    void testAllocateInventory_MultipleItems() {
        // Given: 2 order items
        SalesOrderItem item1 = createOrderItem(1L, 1L, 10);
        SalesOrderItem item2 = createOrderItem(2L, 1L, 15);

        InventoryBatch batch1 = createBatch(1L, "BATCH001", 30, LocalDate.now().plusMonths(1));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(Arrays.asList(item1, item2));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(List.of(batch1));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should create 2 tasks
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getSalesOrderItemId()).isEqualTo(1L);
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(10);
        assertThat(tasks.get(1).getSalesOrderItemId()).isEqualTo(2L);
        assertThat(tasks.get(1).getPlanQty()).isEqualTo(15);
    }

    // ========== Test 10: Sales Order Not Found ==========

    @Test
    @DisplayName("case-11")
    void testAllocateInventory_SalesOrderNotFound() {
        // Given
        when(salesOrderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> allocationService.allocateInventory(999L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SALES_ORDER_NOT_FOUND);
    }

    // ========== Test 11: Product Not Found ==========

    @Test
    @DisplayName("case-12")
    void testAllocateInventory_ProductNotFound() {
        // Given
        SalesOrderItem item = createOrderItem(1L, 999L, 10);

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> allocationService.allocateInventory(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PRODUCT_NOT_FOUND);
    }

    // ========== Test 12: Specified Batch Not Found ==========

    @Test
    @DisplayName("case-13")
    void testAllocateInventory_SpecifiedBatchNotFound() throws Exception {
        // Given
        SalesOrderItem item = createOrderItem(1L, 1L, 10);
        item.setSpecifiedBatchIds("[999]");

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(objectMapper.readValue(
            eq("[999]"),
            org.mockito.ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<List<Long>>>any()
        ))
            .thenReturn(List.of(999L));
        when(inventoryBatchRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> allocationService.allocateInventory(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.BATCH_NOT_FOUND);
    }

    // ========== Test 13: Specified Batch Inactive ==========

    @Test
    @DisplayName("case-14")
    void testAllocateInventory_SpecifiedBatchInactive() throws Exception {
        // Given
        SalesOrderItem item = createOrderItem(1L, 1L, 10);
        item.setSpecifiedBatchIds("[1]");

        InventoryBatch inactiveBatch = createBatch(1L, "BATCH001", 20, LocalDate.now().plusMonths(1));
        inactiveBatch.setActive(false);

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(objectMapper.readValue(
            eq("[1]"),
            org.mockito.ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<List<Long>>>any()
        ))
            .thenReturn(List.of(1L));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(inactiveBatch));

        // When & Then
        assertThatThrownBy(() -> allocationService.allocateInventory(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.BATCH_INACTIVE);
    }

    // ========== Test 14: Mixed Scenario ==========

    @Test
    @DisplayName("case-15")
    void testAllocateInventory_MixedScenario() {
        // Given: Request 30, have loose (8) + full box (12) + full box (24)
        SalesOrderItem item = createOrderItem(1L, 1L, 30);

        InventoryBatch looseBatch = createBatch(1L, "BATCH001", 8, LocalDate.now().plusMonths(1));
        InventoryBatch fullBoxBatch1 = createBatch(2L, "BATCH002", 12, LocalDate.now().plusMonths(2));
        InventoryBatch fullBoxBatch2 = createBatch(3L, "BATCH003", 24, LocalDate.now().plusMonths(3));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(Arrays.asList(looseBatch, fullBoxBatch1, fullBoxBatch2));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should use loose first, then full boxes
        assertThat(tasks).hasSize(3);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(1L); // Loose first
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(8);
        assertThat(tasks.get(1).getAssignedBatchId()).isEqualTo(2L); // Full box
        assertThat(tasks.get(1).getPlanQty()).isEqualTo(12);
        assertThat(tasks.get(2).getAssignedBatchId()).isEqualTo(3L); // Full box
        assertThat(tasks.get(2).getPlanQty()).isEqualTo(10);
    }

    @Test
    @DisplayName("pack-aware allocation merges loose and full portions by batch")
    void testAllocateInventory_PackAwareBatchAllocation() {
        SalesOrderItem item = createOrderItem(1L, 1L, 65);

        InventoryBatch oldBatch = createBatch(1L, "BATCH-OLD", 50, LocalDate.now().plusMonths(1));
        InventoryBatch freshBatch = createBatch(2L, "BATCH-FRESH", 100, LocalDate.now().plusMonths(6));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(List.of(oldBatch, freshBatch));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(1L);
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(50);
        assertThat(tasks.get(1).getAssignedBatchId()).isEqualTo(2L);
        assertThat(tasks.get(1).getPlanQty()).isEqualTo(15);
    }

    @Test
    @DisplayName("full-pack orders can use the full-pack portion of a mixed batch")
    void testAllocateInventory_FullPackPortionFromMixedBatch() {
        SalesOrderItem item = createOrderItem(1L, 1L, 48);
        InventoryBatch mixedBatch = createBatch(1L, "BATCH-MIXED", 50, LocalDate.now().plusMonths(1));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(List.of(mixedBatch));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        assertThat(tasks).singleElement()
            .satisfies(task -> {
                assertThat(task.getAssignedBatchId()).isEqualTo(1L);
                assertThat(task.getPlanQty()).isEqualTo(48);
            });
    }

    // ========== Test 15: Near-Expiry with Fresh Batches ==========

    @Test
    @DisplayName("case-16")
    void testAllocateInventory_NearExpiryWithFreshBatches() {
        // Given: reject_near_expiry = true, multiple batches
        SalesOrderItem item = createOrderItem(1L, 1L, 20);
        item.setRejectNearExpiry(true);

        InventoryBatch nearExpiryBatch1 = createBatch(1L, "BATCH001", 15, LocalDate.now().plusDays(15));
        InventoryBatch nearExpiryBatch2 = createBatch(2L, "BATCH002", 10, LocalDate.now().plusDays(25));
        InventoryBatch freshBatch1 = createBatch(3L, "BATCH003", 12, LocalDate.now().plusMonths(2));
        InventoryBatch freshBatch2 = createBatch(4L, "BATCH004", 12, LocalDate.now().plusMonths(3));

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(1L, true))
            .thenReturn(Arrays.asList(nearExpiryBatch1, nearExpiryBatch2, freshBatch1, freshBatch2));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTask> tasks = allocationService.allocateInventory(1L);

        // Then: Should only use fresh batches
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getAssignedBatchId()).isEqualTo(3L);
        assertThat(tasks.get(0).getPlanQty()).isEqualTo(12);
        assertThat(tasks.get(1).getAssignedBatchId()).isEqualTo(4L);
        assertThat(tasks.get(1).getPlanQty()).isEqualTo(8);
    }

    // ========== Helper Methods ==========

    private SalesOrderItem createOrderItem(Long id, Long productId, Integer quantity) {
        return SalesOrderItem.builder()
            .id(id)
            .salesOrderId(1L)
            .productId(productId)
            .quantity(quantity)
            .unitPrice(new BigDecimal("10.00"))
            .subtotal(new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)))
            .rejectNearExpiry(false)
            .build();
    }

    private InventoryBatch createBatch(Long id, String batchCode, Integer quantity, LocalDate expiryDate) {
        return InventoryBatch.builder()
            .id(id)
            .batchCode(batchCode)
            .product(testProduct)
            .location(testLocation)
            .quantity(quantity)
            .initialQuantity(quantity)
            .expiryDate(expiryDate)
            .active(true)
            .build();
    }
}
