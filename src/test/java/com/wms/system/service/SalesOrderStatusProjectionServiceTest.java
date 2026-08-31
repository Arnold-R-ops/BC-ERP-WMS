package com.wms.system.service;

import com.wms.system.entity.BackorderLine;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.OutboundTask;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesOrderItem;
import com.wms.system.entity.SalesOrderShipment;
import com.wms.system.entity.enums.BackorderStatus;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesAllocationStatus;
import com.wms.system.entity.enums.SalesLogisticsStatus;
import com.wms.system.entity.enums.SalesWarehouseStatus;
import com.wms.system.entity.enums.ShipmentStatus;
import com.wms.system.repository.BackorderLineRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.OutboundTaskRepository;
import com.wms.system.repository.SalesOrderItemRepository;
import com.wms.system.repository.SalesOrderRepository;
import com.wms.system.repository.SalesOrderShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderStatusProjectionServiceTest {

    @Mock private SalesOrderRepository orderRepository;
    @Mock private SalesOrderItemRepository itemRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private OutboundTaskRepository taskRepository;
    @Mock private BackorderLineRepository backorderRepository;
    @Mock private SalesOrderShipmentRepository shipmentRepository;

    @InjectMocks
    private SalesOrderStatusProjectionService service;

    @BeforeEach
    void setUp() {
        lenient().when(orderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(itemRepository.findBySalesOrderId(1L)).thenReturn(List.of());
        lenient().when(reservationRepository.findBySalesOrderId(1L)).thenReturn(List.of());
        lenient().when(taskRepository.findBySalesOrderId(1L)).thenReturn(List.of());
        lenient().when(backorderRepository.findBySalesOrderId(1L)).thenReturn(List.of());
        lenient().when(shipmentRepository.findBySalesOrderIdOrderByIdAsc(1L)).thenReturn(List.of());
    }

    @Test
    void approvedOrderDerivesFullAllocationAndPendingWarehouseWork() {
        SalesOrder order = order(CommercialStatus.APPROVED);
        when(itemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item(10, 0, 0)));
        when(reservationRepository.findBySalesOrderId(1L)).thenReturn(List.of(
            InventoryReservation.builder().reservedQty(10).consumedQty(0).releasedQty(0).build()
        ));
        when(taskRepository.findBySalesOrderId(1L)).thenReturn(List.of(
            OutboundTask.builder().status(OutboundTaskStatus.PENDING).build()
        ));

        service.refreshLocked(order);

        assertThat(order.getAllocationStatus()).isEqualTo(SalesAllocationStatus.FULLY_ALLOCATED);
        assertThat(order.getWarehouseStatus()).isEqualTo(SalesWarehouseStatus.PENDING_PICK);
        assertThat(order.getCommercialStatus()).isEqualTo(CommercialStatus.APPROVED);
    }

    @Test
    void promisedBackorderIsWaitingInboundWhileUnpromisedBackorderIsBackordered() {
        SalesOrder promised = order(CommercialStatus.APPROVED);
        when(itemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item(10, 0, 0)));
        when(backorderRepository.findBySalesOrderId(1L)).thenReturn(List.of(
            backorder(LocalDate.now().plusDays(2))
        ));
        service.refreshLocked(promised);
        assertThat(promised.getAllocationStatus()).isEqualTo(SalesAllocationStatus.WAITING_INBOUND);

        SalesOrder unpromised = order(CommercialStatus.APPROVED);
        when(backorderRepository.findBySalesOrderId(1L)).thenReturn(List.of(backorder(null)));
        service.refreshLocked(unpromised);
        assertThat(unpromised.getAllocationStatus()).isEqualTo(SalesAllocationStatus.BACKORDERED);
    }

    @Test
    void partialReservationTakesPrecedenceOverShortageDetail() {
        SalesOrder order = order(CommercialStatus.APPROVED);
        when(itemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item(10, 0, 0)));
        when(reservationRepository.findBySalesOrderId(1L)).thenReturn(List.of(
            InventoryReservation.builder().reservedQty(4).consumedQty(0).releasedQty(0).build()
        ));
        when(backorderRepository.findBySalesOrderId(1L)).thenReturn(List.of(backorder(null)));

        service.refreshLocked(order);

        assertThat(order.getAllocationStatus()).isEqualTo(SalesAllocationStatus.PARTIALLY_ALLOCATED);
    }

    @Test
    void fullEffectiveShipmentCompletesCommercialOrderWithoutWaitingForDelivery() {
        SalesOrder order = order(CommercialStatus.APPROVED);
        when(itemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item(10, 10, 0)));
        when(shipmentRepository.findBySalesOrderIdOrderByIdAsc(1L)).thenReturn(List.of(
            SalesOrderShipment.builder()
                .status(ShipmentStatus.ACTIVE)
                .logisticsStatus(SalesLogisticsStatus.IN_TRANSIT)
                .build()
        ));

        service.refreshLocked(order);

        assertThat(order.getCommercialStatus()).isEqualTo(CommercialStatus.COMPLETED);
        assertThat(order.getWarehouseStatus()).isEqualTo(SalesWarehouseStatus.SHIPPED);
        assertThat(order.getLogisticsStatus()).isEqualTo(SalesLogisticsStatus.IN_TRANSIT);
        assertThat(order.getShippedAt()).isNotNull();
    }

    @Test
    void closedRemainderPreservesPartialShipmentTruth() {
        SalesOrder order = order(CommercialStatus.CLOSED);
        when(itemRepository.findBySalesOrderId(1L)).thenReturn(List.of(item(10, 4, 6)));

        service.refreshLocked(order);

        assertThat(order.getCommercialStatus()).isEqualTo(CommercialStatus.CLOSED);
        assertThat(order.getAllocationStatus()).isEqualTo(SalesAllocationStatus.UNALLOCATED);
        assertThat(order.getWarehouseStatus()).isEqualTo(SalesWarehouseStatus.PARTIALLY_SHIPPED);
    }

    private SalesOrder order(CommercialStatus status) {
        return SalesOrder.builder().id(1L).commercialStatus(status).build();
    }

    private SalesOrderItem item(int requested, int shipped, int cancelled) {
        return SalesOrderItem.builder()
            .requestedQty(requested)
            .shippedQty(shipped)
            .cancelledQty(cancelled)
            .build();
    }

    private BackorderLine backorder(LocalDate promisedDate) {
        return BackorderLine.builder()
            .remainingQty(10)
            .status(BackorderStatus.OPEN)
            .promisedDate(promisedDate)
            .build();
    }
}
