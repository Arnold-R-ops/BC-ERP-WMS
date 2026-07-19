package com.wms.system.service;

import com.wms.system.dto.sales.SalesOrderShipmentRequest;
import com.wms.system.dto.sales.SalesOrderShipmentResponse;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesOrderShipment;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.ShipmentStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SalesOrderRepository;
import com.wms.system.repository.SalesOrderShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalesOrderShipmentService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderShipmentRepository shipmentRepository;

    @Transactional(readOnly = true)
    public List<SalesOrderShipmentResponse> list(Long salesOrderId) {
        requireOrder(salesOrderId);
        return shipmentRepository.findBySalesOrderIdOrderByIdAsc(salesOrderId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderShipmentResponse create(
        Long salesOrderId,
        SalesOrderShipmentRequest request,
        Long operatorId,
        String operatorName
    ) {
        SalesOrder order = requireOrder(salesOrderId);
        validateOrderAllowsShipment(order);

        String trackingNo = normalizeRequired(request.getTrackingNo());
        ensureTrackingNumberAvailable(salesOrderId, trackingNo, null);

        SalesOrderShipment shipment = SalesOrderShipment.builder()
            .salesOrderId(salesOrderId)
            .trackingNo(trackingNo)
            .carrier(normalizeOptional(request.getCarrier()))
            .trackingUrl(normalizeOptional(request.getTrackingUrl()))
            .shippedAt(request.getShippedAt())
            .createdBy(operatorId)
            .createdByName(operatorName)
            .remark(normalizeOptional(request.getRemark()))
            .status(ShipmentStatus.ACTIVE)
            .build();
        return toResponse(shipmentRepository.save(shipment));
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderShipmentResponse update(
        Long salesOrderId,
        Long shipmentId,
        SalesOrderShipmentRequest request
    ) {
        requireOrder(salesOrderId);
        SalesOrderShipment shipment = requireShipment(salesOrderId, shipmentId);
        if (shipment.getStatus() == ShipmentStatus.VOIDED) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Voided shipment records cannot be edited", "shipmentId", shipmentId)
            );
        }

        String trackingNo = normalizeRequired(request.getTrackingNo());
        ensureTrackingNumberAvailable(salesOrderId, trackingNo, shipmentId);
        shipment.setTrackingNo(trackingNo);
        shipment.setCarrier(normalizeOptional(request.getCarrier()));
        shipment.setTrackingUrl(normalizeOptional(request.getTrackingUrl()));
        shipment.setShippedAt(request.getShippedAt());
        shipment.setRemark(normalizeOptional(request.getRemark()));
        return toResponse(shipmentRepository.save(shipment));
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderShipmentResponse voidShipment(Long salesOrderId, Long shipmentId) {
        requireOrder(salesOrderId);
        SalesOrderShipment shipment = requireShipment(salesOrderId, shipmentId);
        shipment.setStatus(ShipmentStatus.VOIDED);
        return toResponse(shipmentRepository.save(shipment));
    }

    private SalesOrder requireOrder(Long salesOrderId) {
        return salesOrderRepository.findById(salesOrderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", salesOrderId)
            ));
    }

    private SalesOrderShipment requireShipment(Long salesOrderId, Long shipmentId) {
        return shipmentRepository.findByIdAndSalesOrderId(shipmentId, salesOrderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "SalesOrderShipment", "resourceId", shipmentId)
            ));
    }

    private void validateOrderAllowsShipment(SalesOrder order) {
        if (order.getStatus() == SalesOrderStatus.REJECTED
            || order.getStatus() == SalesOrderStatus.CANCELLED
            || order.getStatus() == SalesOrderStatus.VOIDED) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Shipment tracking cannot be added to a closed order", "salesOrderId", order.getId())
            );
        }
    }

    private void ensureTrackingNumberAvailable(Long salesOrderId, String trackingNo, Long currentShipmentId) {
        shipmentRepository.findFirstBySalesOrderIdAndTrackingNo(salesOrderId, trackingNo)
            .filter(existing -> !existing.getId().equals(currentShipmentId))
            .ifPresent(existing -> {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("message", "Tracking number already exists for this order", "trackingNo", trackingNo)
                );
            });
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SalesOrderShipmentResponse toResponse(SalesOrderShipment shipment) {
        return SalesOrderShipmentResponse.builder()
            .id(shipment.getId())
            .salesOrderId(shipment.getSalesOrderId())
            .trackingNo(shipment.getTrackingNo())
            .carrier(shipment.getCarrier())
            .trackingUrl(shipment.getTrackingUrl())
            .status(shipment.getStatus().name())
            .shippedAt(shipment.getShippedAt())
            .createdBy(shipment.getCreatedBy())
            .createdByName(shipment.getCreatedByName())
            .remark(shipment.getRemark())
            .version(shipment.getVersion())
            .createdAt(shipment.getCreatedAt())
            .updatedAt(shipment.getUpdatedAt())
            .build();
    }
}
