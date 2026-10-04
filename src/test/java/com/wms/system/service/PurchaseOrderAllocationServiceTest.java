package com.wms.system.service;

import com.wms.system.dto.receiving.SavePurchaseOrderAllocationsRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderAllocationServiceTest {
    @Mock PurchaseOrderReceivingRepository purchaseOrderRepository;
    @Mock PurchaseOrderAllocationRepository allocationRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock ReceivingResponseMapper mapper;
    @InjectMocks PurchaseOrderAllocationService service;
    PurchaseOrder order;
    PurchaseOrderItem item;
    Warehouse warehouse;

    @BeforeEach void setup() {
        item = PurchaseOrderItem.builder().id(11L).orderedQuantity(100).build();
        order = PurchaseOrder.builder().id(1L).version(2L).status(PurchaseOrderStatus.DRAFT).items(new ArrayList<>(List.of(item))).build();
        warehouse = Warehouse.builder().id(3L).isActive(true).build();
    }
    @Test void draftAcceptsPartialAllocationButSubmissionRequiresCompletePlan() {
        when(purchaseOrderRepository.findByIdForReceiving(1L)).thenReturn(Optional.of(order));
        when(warehouseRepository.findAllById(any())).thenReturn(List.of(warehouse));
        var partial = PurchaseOrderAllocation.builder().purchaseOrderItem(item).warehouse(warehouse).plannedQuantity(60).build();
        when(allocationRepository.findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(1L)).thenReturn(List.of(), List.of(partial), List.of(partial));
        service.replace(1L, new SavePurchaseOrderAllocationsRequest(2L, List.of(new SavePurchaseOrderAllocationsRequest.Row(11L, 3L, 60))));
        verify(allocationRepository).save(any(PurchaseOrderAllocation.class));
        assertThatThrownBy(() -> service.requireCompletePlan(order)).isInstanceOf(BusinessException.class);
    }
    @Test void emptyAllocationPlanCanBeSavedButCannotBeSubmitted() {
        when(purchaseOrderRepository.findByIdForReceiving(1L)).thenReturn(Optional.of(order));
        service.replace(1L, new SavePurchaseOrderAllocationsRequest(2L, List.of()));
        assertThatThrownBy(() -> service.requireCompletePlan(order)).isInstanceOf(BusinessException.class);
    }
    @Test void overAllocationIsRejectedEvenForDrafts() {
        when(purchaseOrderRepository.findByIdForReceiving(1L)).thenReturn(Optional.of(order));
        when(warehouseRepository.findAllById(any())).thenReturn(List.of(warehouse));
        assertThatThrownBy(() -> service.replace(1L, new SavePurchaseOrderAllocationsRequest(2L,
            List.of(new SavePurchaseOrderAllocationsRequest.Row(11L, 3L, 101))))).isInstanceOf(BusinessException.class);
        verify(allocationRepository, never()).save(any());
    }
    @Test void completePlanCanAdvance() {
        when(allocationRepository.findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(1L)).thenReturn(List.of(
            PurchaseOrderAllocation.builder().purchaseOrderItem(item).warehouse(warehouse).plannedQuantity(100).build()));
        assertThat(service.requireCompletePlan(order)).hasSize(1);
    }
}
