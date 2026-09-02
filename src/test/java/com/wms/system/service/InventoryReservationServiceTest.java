package com.wms.system.service;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.SalesOrder;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private SalesOrderRepository salesOrderRepository;
    @InjectMocks private InventoryReservationService service;

    @Test
    void internalOrderReleaseLocksOrderBatchAndReservationBeforeMutation() {
        SalesOrder order = SalesOrder.builder().id(41L).build();
        InventoryBatch batch = InventoryBatch.builder()
            .id(13L)
            .quantity(100)
            .reservedQuantity(30)
            .active(true)
            .build();
        InventoryReservation reservation = InventoryReservation.builder()
            .id(7L)
            .salesOrderId(41L)
            .inventoryBatchId(13L)
            .reservedQty(30)
            .build();

        when(salesOrderRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
        when(reservationRepository.findBySalesOrderIdAndStatusIn(eq(41L), anyCollection()))
            .thenReturn(List.of(reservation));
        when(inventoryBatchRepository.findAllByIdForPosting(List.of(13L)))
            .thenReturn(List.of(batch));
        when(reservationRepository.findBySalesOrderIdForUpdate(41L))
            .thenReturn(List.of(reservation));

        service.releaseOpenReservationsForOrder(41L);

        assertThat(batch.getReservedQuantity()).isZero();
        assertThat(reservation.getOpenQty()).isZero();
        InOrder orderOfLocks = inOrder(
            salesOrderRepository,
            inventoryBatchRepository,
            reservationRepository
        );
        orderOfLocks.verify(salesOrderRepository).findByIdForUpdate(41L);
        orderOfLocks.verify(inventoryBatchRepository).findAllByIdForPosting(List.of(13L));
        orderOfLocks.verify(reservationRepository).findBySalesOrderIdForUpdate(41L);
        verify(inventoryBatchRepository).save(batch);
        verify(reservationRepository).save(reservation);
    }
}
