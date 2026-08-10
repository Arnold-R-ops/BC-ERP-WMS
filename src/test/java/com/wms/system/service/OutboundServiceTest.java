package com.wms.system.service;

import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.BatchTrackingMode;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OutboundService Unit Test
 *
 * Test Coverage:
 * 1. Confirm picking success
 * 2. Confirm picking deduct inventory
 * 3. Confirm picking record transaction
 * 4. Confirm picking mark batch inactive
 * 5. Confirm picking check order completion
 * 6. Confirm picking invalid status
 * 7. Confirm picking exceed plan qty
 * 8. Batch confirm picking success
 *
 * @author WMS Team
 * @since 2026-01-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class OutboundServiceTest {

    @Mock
    private OutboundTaskRepository outboundTaskRepository;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private ProductSkuRepository productSkuRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private LocationOccupancyService locationOccupancyService;

    @InjectMocks
    private OutboundService outboundService;

    private OutboundTask testTask;
    private InventoryBatch testBatch;
    private SalesOrder testOrder;
    private ProductSku testProduct;
    private Location testLocation;

    @BeforeEach
    void setUp() {
        SalesOrderItem orderItem = SalesOrderItem.builder()
            .id(1L)
            .salesOrderId(1L)
            .productSkuId(1L)
            .quantity(50)
            .shippedQty(0)
            .build();
        lenient().when(salesOrderItemRepository.findById(1L)).thenReturn(Optional.of(orderItem));
        lenient().when(salesOrderItemRepository.save(any(SalesOrderItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Create test location
        testLocation = Location.builder()
            .id(1L)
            .locationCode("WH01-A-01-001")
            .build();

        // Create test product
        testProduct = ProductSku.builder()
            .skuCode("SKU00000001")
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

        // Create test sales order
        testOrder = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
            .build();

        // Create test outbound task
        testTask = OutboundTask.builder()
            .id(1L)
            .salesOrderId(1L)
            .salesOrderItemId(1L)
            .assignedBatchId(1L)
            .locationId(1L)
            .planQty(50)
            .actualQty(0)
            .status(OutboundTaskStatus.PENDING)
            .build();
    }

    // ========== Test 1: Confirm Picking Success ==========

    @Test
    @DisplayName("case-2")
    void testConfirmPicking_Success() {
        // Given
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());
        when(outboundTaskRepository.countTasksBySalesOrderId(1L)).thenReturn(1L);
        when(outboundTaskRepository.countCompletedTasksBySalesOrderId(1L)).thenReturn(1L);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        OutboundTaskResponse response = outboundService.confirmPicking(1L, 50, 1L, "Operator");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getActualQty()).isEqualTo(50);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getPickedBy()).isEqualTo(1L);
        assertThat(response.getProductSkuCode()).isEqualTo("SKU00000001");

        verify(outboundTaskRepository).save(any(OutboundTask.class));
        verify(inventoryBatchRepository).save(any(InventoryBatch.class));
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }

    // ========== Test 2: Deduct Inventory ==========

    @Test
    @DisplayName("case-3")
    void testConfirmPicking_DeductInventory() {
        // Given
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());
        when(outboundTaskRepository.countTasksBySalesOrderId(1L)).thenReturn(1L);
        when(outboundTaskRepository.countCompletedTasksBySalesOrderId(1L)).thenReturn(1L);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboundService.confirmPicking(1L, 50, 1L, "Operator");

        // Then
        ArgumentCaptor<InventoryBatch> batchCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(inventoryBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getQuantity()).isEqualTo(50); // 100 - 50 = 50
    }

    // ========== Test 3: Record Transaction ==========

    @Test
    @DisplayName("case-4")
    void testConfirmPicking_RecordTransaction() {
        // Given
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());
        when(outboundTaskRepository.countTasksBySalesOrderId(1L)).thenReturn(1L);
        when(outboundTaskRepository.countCompletedTasksBySalesOrderId(1L)).thenReturn(1L);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboundService.confirmPicking(1L, 50, 1L, "Operator");

        // Then
        ArgumentCaptor<StockTransaction> transactionCaptor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(stockTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getQuantity()).isEqualTo(50);
        assertThat(transactionCaptor.getValue().getOperatorName()).isEqualTo("Operator");
    }

    // ========== Test 4: Mark Batch Inactive ==========

    @Test
    @DisplayName("case-5")
    void testConfirmPicking_MarkBatchInactive() {
        // Given: Batch has exactly 50 quantity
        testBatch.setQuantity(50);

        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());
        when(outboundTaskRepository.countTasksBySalesOrderId(1L)).thenReturn(1L);
        when(outboundTaskRepository.countCompletedTasksBySalesOrderId(1L)).thenReturn(1L);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboundService.confirmPicking(1L, 50, 1L, "Operator");

        // Then
        ArgumentCaptor<InventoryBatch> batchCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(inventoryBatchRepository).save(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getQuantity()).isEqualTo(0);
        assertThat(batchCaptor.getValue().getActive()).isFalse();
    }

    // ========== Test 5: Check Order Completion ==========

    @Test
    @DisplayName("case-6")
    void testConfirmPicking_CheckOrderCompletion() {
        // Given: All tasks completed
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());
        when(outboundTaskRepository.countTasksBySalesOrderId(1L)).thenReturn(2L);
        when(outboundTaskRepository.countCompletedTasksBySalesOrderId(1L)).thenReturn(2L);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        outboundService.confirmPicking(1L, 50, 1L, "Operator");

        // Then
        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(SalesOrderStatus.SHIPPED);
    }

    // ========== Test 6: Invalid Status ==========

    @Test
    @DisplayName("case-7")
    void testConfirmPicking_InvalidStatus() {
        // Given: Task already completed
        testTask.setStatus(OutboundTaskStatus.COMPLETED);

        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        // When & Then
        assertThatThrownBy(() -> outboundService.confirmPicking(1L, 50, 1L, "Operator"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OUTBOUND_TASK_ALREADY_COMPLETED);

        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verify(stockTransactionRepository, never()).save(any(StockTransaction.class));
    }

    // ========== Test 7: Exceed Plan Qty ==========

    @Test
    @DisplayName("case-8")
    void testConfirmPicking_ExceedPlanQty() {
        // Given: Actual qty exceeds plan qty
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        // When & Then
        assertThatThrownBy(() -> outboundService.confirmPicking(1L, 100, 1L, "Operator"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OUTBOUND_ACTUAL_QTY_EXCEEDS_PLAN);

        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verify(stockTransactionRepository, never()).save(any(StockTransaction.class));
    }

    // ========== Test 8: Batch Confirm Picking ==========

    @Test
    @DisplayName("case-9")
    void testBatchConfirmPicking_Success() {
        // Given: 2 tasks
        OutboundTask task1 = OutboundTask.builder()
            .id(1L)
            .salesOrderId(1L)
            .salesOrderItemId(1L)
            .assignedBatchId(1L)
            .locationId(1L)
            .planQty(30)
            .actualQty(0)
            .status(OutboundTaskStatus.PENDING)
            .build();

        OutboundTask task2 = OutboundTask.builder()
            .id(2L)
            .salesOrderId(1L)
            .salesOrderItemId(1L)
            .assignedBatchId(1L)
            .locationId(1L)
            .planQty(20)
            .actualQty(0)
            .status(OutboundTaskStatus.PENDING)
            .build();

        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(task1));
        when(outboundTaskRepository.findById(2L)).thenReturn(Optional.of(task2));
        when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());
        when(outboundTaskRepository.countTasksBySalesOrderId(1L)).thenReturn(2L);
        when(outboundTaskRepository.countCompletedTasksBySalesOrderId(1L)).thenReturn(2L);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        List<OutboundTaskResponse> responses = outboundService.batchConfirmPicking(
            Arrays.asList(1L, 2L), 1L, "Operator"
        );

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getActualQty()).isEqualTo(30);
        assertThat(responses.get(1).getActualQty()).isEqualTo(20);

        verify(outboundTaskRepository, times(2)).save(any(OutboundTask.class));
        verify(inventoryBatchRepository, times(2)).save(any(InventoryBatch.class));
        verify(stockTransactionRepository, times(2)).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("LOCATION_VISUAL accepts the immutable internal skuCode")
    void confirmPicking_LocationVisual_AcceptsSkuCode() {
        testProduct.setBatchTrackingMode(BatchTrackingMode.LOCATION_VISUAL);
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(outboundTaskRepository.save(any(OutboundTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        when(locationOccupancyService.resolveSingleVisualBatch(1L, 1L)).thenReturn(testBatch);
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenReturn(new StockTransaction());
        when(outboundTaskRepository.countTasksBySalesOrderId(1L)).thenReturn(1L);
        when(outboundTaskRepository.countCompletedTasksBySalesOrderId(1L)).thenReturn(1L);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboundTaskResponse response = outboundService.confirmPicking(
            1L, 50, 1L, "SKU00000001", null, 1L, "Operator"
        );

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verify(locationOccupancyService).resolveSingleVisualBatch(1L, 1L);
    }

    @Test
    @DisplayName("LOCATION_VISUAL no longer treats barcode as skuCode")
    void confirmPicking_LocationVisual_RejectsBarcodeInSkuCodeField() {
        testProduct.setBatchTrackingMode(BatchTrackingMode.LOCATION_VISUAL);
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));

        assertThatThrownBy(() -> outboundService.confirmPicking(
            1L, 50, 1L, "TEST001", null, 1L, "Operator"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);

        verify(locationOccupancyService, never()).resolveSingleVisualBatch(anyLong(), anyLong());
    }
}
