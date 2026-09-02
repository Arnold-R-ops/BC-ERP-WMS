package com.wms.system.service;

import com.wms.system.dto.outbound.OutboundTaskResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.BatchTrackingMode;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.InventoryQualityStatus;
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
import org.mockito.InOrder;
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
 * 8. Batch confirm dispatch success
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

    @Mock
    private SalesOrderStatusProjectionService statusProjectionService;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @InjectMocks
    private OutboundService outboundService;

    private OutboundTask testTask;
    private InventoryBatch testBatch;
    private SalesOrder testOrder;
    private ProductSku testProduct;
    private Location testLocation;

    @BeforeEach
    void setUp() {
        Warehouse warehouse = Warehouse.builder()
            .id(1L)
            .code("WH01")
            .name("Test Warehouse")
            .isActive(true)
            .build();
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
            .warehouse(warehouse)
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
            .commercialStatus(CommercialStatus.APPROVED)
            .build();
        lenient().when(statusProjectionService.refresh(1L)).thenReturn(testOrder);

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

        lenient().when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        lenient().when(outboundTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));
        lenient().when(outboundTaskRepository.save(any(OutboundTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(inventoryBatchRepository.findById(1L)).thenReturn(Optional.of(testBatch));
        lenient().when(inventoryBatchRepository.findByIdForPosting(1L)).thenReturn(Optional.of(testBatch));
        lenient().when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        lenient().when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
        lenient().when(inventoryPostingService.post(any(InventoryPostingCommand.class)))
            .thenAnswer(invocation -> {
                InventoryPostingCommand command = invocation.getArgument(0);
                int before = testBatch.getQuantity();
                int after = before + command.delta();
                testBatch.setQuantity(after);
                testBatch.setActive(after > 0);
                return new InventoryPostingResult(
                    testBatch.getId(), testBatch.getBatchCode(), testProduct.getId(),
                    testLocation.getId(), warehouse.getId(), command.delta(), before, after,
                    command.delta() != 0, 99L, command.context().postingId(),
                    command.context().idempotencyKey()
                );
            });
    }

    // ========== Test 1: Confirm Dispatch Success ==========

    @Test
    @DisplayName("case-2")
    void testConfirmDispatch_Success() {
        // When
        OutboundTaskResponse response = outboundService.confirmDispatch(1L, 50, 1L, "Operator");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getActualQty()).isEqualTo(50);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getPickedBy()).isEqualTo(1L);
        assertThat(response.getProductSkuCode()).isEqualTo("SKU00000001");

        verify(outboundTaskRepository).save(any(OutboundTask.class));
        verify(inventoryPostingService).post(any(InventoryPostingCommand.class));
    }

    // ========== Test 2: Deduct Inventory ==========

    @Test
    @DisplayName("case-3")
    void testConfirmDispatch_DeductInventory() {
        // When
        outboundService.confirmDispatch(1L, 50, 1L, "Operator");

        // Then
        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
            ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService).post(commandCaptor.capture());
        assertThat(commandCaptor.getValue().delta()).isEqualTo(-50);
        assertThat(testBatch.getQuantity()).isEqualTo(50);
    }

    // ========== Test 3: Record Transaction ==========

    @Test
    @DisplayName("case-4")
    void testConfirmDispatch_RecordTransaction() {
        // When
        outboundService.confirmDispatch(1L, 50, 1L, "Operator");

        // Then
        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
            ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService).post(commandCaptor.capture());
        InventoryPostingCommand command = commandCaptor.getValue();
        assertThat(command.context().sourceType()).isEqualTo(com.wms.system.entity.enums.SourceType.SALE_OUT);
        assertThat(command.context().sourceOrderId()).isEqualTo("SO20260129001");
        assertThat(command.context().operatorName()).isEqualTo("Operator");
        assertThat(command.context().postingId()).isEqualTo("SALE-OUT:1");
        assertThat(command.context().idempotencyKey()).isEqualTo("sale-out-task:1");
    }

    // ========== Test 4: Mark Batch Inactive ==========

    @Test
    @DisplayName("case-5")
    void testConfirmDispatch_MarkBatchInactive() {
        // Given: Batch has exactly 50 quantity
        testBatch.setQuantity(50);

        // When
        outboundService.confirmDispatch(1L, 50, 1L, "Operator");

        // Then
        assertThat(testBatch.getQuantity()).isEqualTo(0);
        assertThat(testBatch.getActive()).isFalse();
    }

    // ========== Test 5: Check Order Completion ==========

    @Test
    @DisplayName("case-6")
    void testConfirmDispatch_CheckOrderCompletion() {
        // When
        outboundService.confirmDispatch(1L, 50, 1L, "Operator");

        // Then
        verify(statusProjectionService).refresh(1L);
    }

    // ========== Test 6: Invalid Status ==========

    @Test
    @DisplayName("case-7")
    void testConfirmDispatch_InvalidStatus() {
        // Given: Task already completed
        testTask.setStatus(OutboundTaskStatus.COMPLETED);

        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        // When & Then
        assertThatThrownBy(() -> outboundService.confirmDispatch(1L, 50, 1L, "Operator"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OUTBOUND_TASK_ALREADY_COMPLETED);

        verify(inventoryPostingService, never()).post(any());
    }

    // ========== Test 7: Exceed Plan Qty ==========

    @Test
    @DisplayName("case-8")
    void testConfirmDispatch_ExceedPlanQty() {
        // Given: Actual qty exceeds plan qty
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));

        // When & Then
        assertThatThrownBy(() -> outboundService.confirmDispatch(1L, 100, 1L, "Operator"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OUTBOUND_ACTUAL_QTY_EXCEEDS_PLAN);

        verify(inventoryPostingService, never()).post(any());
    }

    @Test
    @DisplayName("quality-held inventory cannot be shipped")
    void confirmDispatchRejectsNonSaleableBatch() {
        testBatch.setQualityStatus(InventoryQualityStatus.QUARANTINED);
        when(outboundTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
        when(outboundTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testTask));

        assertThatThrownBy(() -> outboundService.confirmDispatch(1L, 10, 1L, "Operator"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.INVENTORY_BATCH_NOT_SALEABLE);

        verify(inventoryPostingService, never()).post(any());
    }

    @Test
    @DisplayName("reserved dispatch releases reserved quantity and posts physical quantity once")
    void confirmDispatchWithReservationDoesNotDoubleDeduct() {
        testTask.setReservationId(7L);
        testBatch.setReservedQuantity(50);
        InventoryReservation reservation = InventoryReservation.builder()
            .id(7L)
            .salesOrderId(1L)
            .salesOrderItemId(1L)
            .inventoryBatchId(1L)
            .productSkuId(1L)
            .locationId(1L)
            .reservedQty(50)
            .build();
        when(inventoryReservationRepository.findByIdForUpdate(7L))
            .thenReturn(Optional.of(reservation));
        when(inventoryReservationRepository.save(any(InventoryReservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            InventoryPostingCommand command = invocation.getArgument(0);
            assertThat(testBatch.getQuantity()).isEqualTo(100);
            assertThat(testBatch.getReservedQuantity()).isEqualTo(20);
            int after = testBatch.getQuantity() + command.delta();
            testBatch.setQuantity(after);
            return new InventoryPostingResult(
                1L, "BATCH001", 1L, 1L, 1L, command.delta(), 100, after,
                true, 99L, command.context().postingId(), command.context().idempotencyKey()
            );
        }).when(inventoryPostingService).post(any(InventoryPostingCommand.class));

        outboundService.confirmDispatch(1L, 30, 1L, "Operator");

        assertThat(testBatch.getQuantity()).isEqualTo(70);
        assertThat(testBatch.getReservedQuantity()).isEqualTo(20);
        assertThat(reservation.getConsumedQty()).isEqualTo(30);
        assertThat(reservation.getOpenQty()).isEqualTo(20);
        InOrder lockOrder = inOrder(
            salesOrderRepository,
            inventoryBatchRepository,
            inventoryReservationRepository,
            outboundTaskRepository
        );
        lockOrder.verify(salesOrderRepository).findByIdForUpdate(1L);
        lockOrder.verify(inventoryBatchRepository).findByIdForPosting(1L);
        lockOrder.verify(inventoryReservationRepository).findByIdForUpdate(7L);
        lockOrder.verify(outboundTaskRepository).findByIdForUpdate(1L);
        verify(inventoryBatchRepository).saveAndFlush(testBatch);
        verify(inventoryPostingService).post(any(InventoryPostingCommand.class));
        verify(inventoryReservationRepository).save(reservation);
    }

    // ========== Test 8: Batch Confirm Dispatch ==========

    @Test
    @DisplayName("case-9")
    void testBatchConfirmDispatch_Success() {
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
        when(outboundTaskRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(task1));
        when(outboundTaskRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(task2));
        // When
        List<OutboundTaskResponse> responses = outboundService.batchConfirmDispatch(
            Arrays.asList(1L, 2L), 1L, "Operator"
        );

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getActualQty()).isEqualTo(30);
        assertThat(responses.get(1).getActualQty()).isEqualTo(20);

        verify(outboundTaskRepository, times(2)).save(any(OutboundTask.class));
        verify(inventoryPostingService, times(2)).post(any(InventoryPostingCommand.class));
    }

    @Test
    @DisplayName("LOCATION_VISUAL accepts the immutable internal skuCode")
    void confirmDispatch_LocationVisual_AcceptsSkuCode() {
        testProduct.setBatchTrackingMode(BatchTrackingMode.LOCATION_VISUAL);
        when(locationOccupancyService.resolveSingleVisualBatch(1L, 1L)).thenReturn(testBatch);
        OutboundTaskResponse response = outboundService.confirmDispatch(
            1L, 50, 1L, "SKU00000001", null, 1L, "Operator"
        );

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verify(locationOccupancyService).resolveSingleVisualBatch(1L, 1L);
    }

    @Test
    @DisplayName("LOCATION_VISUAL no longer treats barcode as skuCode")
    void confirmDispatch_LocationVisual_RejectsBarcodeInSkuCodeField() {
        testProduct.setBatchTrackingMode(BatchTrackingMode.LOCATION_VISUAL);
        assertThatThrownBy(() -> outboundService.confirmDispatch(
            1L, 50, 1L, "TEST001", null, 1L, "Operator"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);

        verify(locationOccupancyService, never()).resolveSingleVisualBatch(anyLong(), anyLong());
    }
}
