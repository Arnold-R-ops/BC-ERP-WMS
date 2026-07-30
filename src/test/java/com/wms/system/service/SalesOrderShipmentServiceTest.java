package com.wms.system.service;

import com.wms.system.dto.sales.SalesOrderShipmentRequest;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesOrderShipment;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.ShipmentStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SalesOrderRepository;
import com.wms.system.repository.SalesOrderShipmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderShipmentServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderShipmentRepository shipmentRepository;

    @InjectMocks
    private SalesOrderShipmentService shipmentService;

    @Test
    void create_AllowsMultipleInternalShipmentsForOneOrder() {
        SalesOrder order = SalesOrder.builder()
            .id(10L)
            .status(SalesOrderStatus.SHIPPED)
            .build();
        when(salesOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findFirstBySalesOrderIdAndTrackingNo(10L, "TRACK-A"))
            .thenReturn(Optional.empty());
        when(shipmentRepository.findFirstBySalesOrderIdAndTrackingNo(10L, "TRACK-B"))
            .thenReturn(Optional.empty());

        AtomicLong ids = new AtomicLong(100L);
        when(shipmentRepository.save(any(SalesOrderShipment.class))).thenAnswer(invocation -> {
            SalesOrderShipment shipment = invocation.getArgument(0);
            shipment.setId(ids.getAndIncrement());
            return shipment;
        });

        SalesOrderShipmentRequest first = request("TRACK-A", "DHL");
        SalesOrderShipmentRequest second = request("TRACK-B", "Royal Mail");

        var firstResponse = shipmentService.create(10L, first, 2L, "operator");
        var secondResponse = shipmentService.create(10L, second, 2L, "operator");

        assertThat(firstResponse.getTrackingNo()).isEqualTo("TRACK-A");
        assertThat(secondResponse.getTrackingNo()).isEqualTo("TRACK-B");
        assertThat(firstResponse.getStatus()).isEqualTo(ShipmentStatus.ACTIVE.name());
        assertThat(secondResponse.getSalesOrderId()).isEqualTo(10L);
    }

    @Test
    void create_RejectsDuplicateTrackingNumberWithinOrder() {
        SalesOrder order = SalesOrder.builder()
            .id(10L)
            .status(SalesOrderStatus.SHIPPED)
            .build();
        SalesOrderShipment existing = SalesOrderShipment.builder()
            .id(100L)
            .salesOrderId(10L)
            .trackingNo("TRACK-A")
            .build();
        when(salesOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findFirstBySalesOrderIdAndTrackingNo(10L, "TRACK-A"))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> shipmentService.create(10L, request("TRACK-A", "DHL"), 2L, "operator"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.VALIDATION_FAILED);

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void create_RejectsClosedOrder() {
        SalesOrder order = SalesOrder.builder()
            .id(10L)
            .status(SalesOrderStatus.CANCELLED)
            .build();
        when(salesOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> shipmentService.create(10L, request("TRACK-A", "DHL"), 2L, "operator"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void voidShipment_RecordsActorAndTimestamp() {
        SalesOrder order = SalesOrder.builder()
            .id(10L)
            .status(SalesOrderStatus.SHIPPED)
            .build();
        SalesOrderShipment shipment = SalesOrderShipment.builder()
            .id(100L)
            .salesOrderId(10L)
            .trackingNo("TRACK-A")
            .status(ShipmentStatus.ACTIVE)
            .build();
        when(salesOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByIdAndSalesOrderId(100L, 10L)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(SalesOrderShipment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = shipmentService.voidShipment(10L, 100L, 2L, "admin");

        assertThat(response.getStatus()).isEqualTo(ShipmentStatus.VOIDED.name());
        assertThat(response.getVoidedBy()).isEqualTo(2L);
        assertThat(response.getVoidedByName()).isEqualTo("admin");
        assertThat(response.getVoidedAt()).isNotNull();
    }

    private SalesOrderShipmentRequest request(String trackingNo, String carrier) {
        SalesOrderShipmentRequest request = new SalesOrderShipmentRequest();
        request.setTrackingNo(trackingNo);
        request.setCarrier(carrier);
        return request;
    }
}
