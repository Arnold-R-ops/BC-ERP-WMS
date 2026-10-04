package com.wms.system.service;

import com.wms.system.dto.sales.SalesOrderShipmentRequest;
import com.wms.system.dto.sales.SalesOrderShipmentResponse;
import com.wms.system.dto.sales.SalesLogisticsEventResponse;
import com.wms.system.dto.sales.UpdateSalesLogisticsStatusRequest;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesOrderShipment;
import com.wms.system.entity.SalesShipmentLogisticsEvent;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.SalesLogisticsStatus;
import com.wms.system.entity.enums.ShipmentStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SalesOrderRepository;
import com.wms.system.repository.SalesOrderShipmentRepository;
import com.wms.system.repository.SalesShipmentLogisticsEventRepository;
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
    private final SalesShipmentLogisticsEventRepository logisticsEventRepository;
    private final SalesOrderPolicyService policyService;
    private final SalesOrderStatusProjectionService statusProjectionService;

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

        String trackingNo = validatedTrackingNo(request);
        ensureTrackingNumberAvailable(salesOrderId, trackingNo, null);

        SalesLogisticsStatus logisticsStatus = deriveBasicStatus(request);
        SalesOrderShipment shipment = SalesOrderShipment.builder()
            .salesOrderId(salesOrderId)
            .trackingNo(trackingNo)
            .deliveryMethod(request.getDeliveryMethod())
            .carrier(normalizeOptional(request.getCarrier()))
            .trackingUrl(normalizeOptional(request.getTrackingUrl()))
            .shippedAt(request.getShippedAt())
            .createdBy(operatorId)
            .createdByName(operatorName)
            .remark(normalizeOptional(request.getRemark()))
            .status(ShipmentStatus.ACTIVE)
            .logisticsStatus(logisticsStatus)
            .statusReason(null)
            .statusChangedBy(operatorId)
            .statusChangedByName(operatorName)
            .statusChangedAt(java.time.LocalDateTime.now())
            .build();
        SalesOrderShipment saved = shipmentRepository.save(shipment);
        appendLogisticsEvent(saved, SalesLogisticsStatus.NOT_CREATED, logisticsStatus,
            "BASIC", "运单已登记", operatorId, operatorName);
        statusProjectionService.refresh(salesOrderId);
        return toResponse(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderShipmentResponse update(
        Long salesOrderId,
        Long shipmentId,
        SalesOrderShipmentRequest request,
        Long operatorId,
        String operatorName
    ) {
        requireOrder(salesOrderId);
        SalesOrderShipment shipment = requireShipment(salesOrderId, shipmentId);
        if (shipment.getStatus() == ShipmentStatus.VOIDED) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Voided shipment records cannot be edited", "shipmentId", shipmentId)
            );
        }

        String trackingNo = validatedTrackingNo(request);
        ensureTrackingNumberAvailable(salesOrderId, trackingNo, shipmentId);
        shipment.setTrackingNo(trackingNo);
        shipment.setDeliveryMethod(request.getDeliveryMethod());
        shipment.setCarrier(normalizeOptional(request.getCarrier()));
        shipment.setTrackingUrl(normalizeOptional(request.getTrackingUrl()));
        shipment.setShippedAt(request.getShippedAt());
        shipment.setRemark(normalizeOptional(request.getRemark()));
        SalesLogisticsStatus previousStatus = shipment.getLogisticsStatus();
        SalesLogisticsStatus nextStatus = previousStatus == SalesLogisticsStatus.LABEL_CREATED
            || previousStatus == SalesLogisticsStatus.HANDED_TO_CARRIER
            ? deriveBasicStatus(request)
            : previousStatus;
        shipment.setLogisticsStatus(nextStatus);
        if (nextStatus != previousStatus) {
            shipment.setStatusChangedBy(operatorId);
            shipment.setStatusChangedByName(operatorName);
            shipment.setStatusChangedAt(java.time.LocalDateTime.now());
        }
        SalesOrderShipment saved = shipmentRepository.save(shipment);
        if (nextStatus != previousStatus) {
            appendLogisticsEvent(saved, previousStatus, nextStatus,
                "BASIC", "发货时间已更新", operatorId, operatorName);
        }
        statusProjectionService.refresh(salesOrderId);
        return toResponse(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderShipmentResponse voidShipment(
        Long salesOrderId,
        Long shipmentId,
        Long operatorId,
        String operatorName
    ) {
        requireOrder(salesOrderId);
        SalesOrderShipment shipment = requireShipment(salesOrderId, shipmentId);
        shipment.setStatus(ShipmentStatus.VOIDED);
        shipment.setVoidedBy(operatorId);
        shipment.setVoidedByName(operatorName);
        shipment.setVoidedAt(java.time.LocalDateTime.now());
        SalesOrderShipment saved = shipmentRepository.save(shipment);
        statusProjectionService.refresh(salesOrderId);
        return toResponse(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderShipmentResponse updateLogisticsStatus(
        Long salesOrderId,
        Long shipmentId,
        UpdateSalesLogisticsStatusRequest request,
        Long operatorId,
        String operatorName
    ) {
        requireOrder(salesOrderId);
        if (!policyService.detailedLogisticsEnabled()) {
            throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Detailed logistics status is disabled"));
        }
        if (request.status() == SalesLogisticsStatus.NOT_CREATED) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "status", "message", "An existing shipment cannot return to NOT_CREATED"));
        }
        SalesOrderShipment shipment = requireShipment(salesOrderId, shipmentId);
        if (shipment.getStatus() == ShipmentStatus.VOIDED) {
            throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Voided shipment records cannot be updated", "shipmentId", shipmentId));
        }
        requireTransitionReason(request.reason());
        SalesLogisticsStatus previousStatus = shipment.getLogisticsStatus();
        shipment.setLogisticsStatus(request.status());
        shipment.setStatusReason(request.reason().trim());
        shipment.setStatusChangedBy(operatorId);
        shipment.setStatusChangedByName(operatorName);
        shipment.setStatusChangedAt(java.time.LocalDateTime.now());
        SalesOrderShipment saved = shipmentRepository.save(shipment);
        appendLogisticsEvent(saved, previousStatus, request.status(),
            "MANUAL", request.reason().trim(), operatorId, operatorName);
        statusProjectionService.refresh(salesOrderId);
        return toResponse(saved);
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
        if (order.getCommercialStatus() != CommercialStatus.APPROVED
            && order.getCommercialStatus() != CommercialStatus.COMPLETED
            && order.getCommercialStatus() != CommercialStatus.CLOSED) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("message", "Shipment tracking requires an approved or shipped order", "salesOrderId", order.getId())
            );
        }
    }

    private void ensureTrackingNumberAvailable(Long salesOrderId, String trackingNo, Long currentShipmentId) {
        if (trackingNo == null) return;
        shipmentRepository.findFirstBySalesOrderIdAndTrackingNo(salesOrderId, trackingNo)
            .filter(existing -> !existing.getId().equals(currentShipmentId))
            .ifPresent(existing -> {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of("message", "Tracking number already exists for this order", "trackingNo", trackingNo)
                );
            });
    }

    private String validatedTrackingNo(SalesOrderShipmentRequest request) {
        var method = request.getDeliveryMethod();
        if (method == null) method = com.wms.system.entity.enums.DeliveryMethod.COURIER;
        request.setDeliveryMethod(method);
        String tracking = normalizeOptional(request.getTrackingNo());
        if (method == com.wms.system.entity.enums.DeliveryMethod.COURIER && tracking == null) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("message", "Courier delivery requires a tracking number"));
        }
        return tracking;
    }

    private void appendLogisticsEvent(
        SalesOrderShipment shipment,
        SalesLogisticsStatus fromStatus,
        SalesLogisticsStatus toStatus,
        String source,
        String reason,
        Long operatorId,
        String operatorName
    ) {
        logisticsEventRepository.save(SalesShipmentLogisticsEvent.builder()
            .salesOrderId(shipment.getSalesOrderId())
            .shipmentId(shipment.getId())
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .source(source)
            .reason(reason)
            .changedBy(operatorId)
            .changedByName(operatorName)
            .changedAt(java.time.LocalDateTime.now())
            .build());
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SalesOrderShipmentResponse toResponse(SalesOrderShipment shipment) {
        return SalesOrderShipmentResponse.builder()
            .id(shipment.getId())
            .salesOrderId(shipment.getSalesOrderId())
            .trackingNo(shipment.getTrackingNo())
            .deliveryMethod(shipment.getDeliveryMethod())
            .carrier(shipment.getCarrier())
            .trackingUrl(shipment.getTrackingUrl())
            .status(shipment.getStatus().name())
            .logisticsStatus(shipment.getLogisticsStatus().name())
            .logisticsStatusDescription(shipment.getLogisticsStatus().getDescription())
            .statusReason(shipment.getStatusReason())
            .statusChangedBy(shipment.getStatusChangedBy())
            .statusChangedByName(shipment.getStatusChangedByName())
            .statusChangedAt(shipment.getStatusChangedAt())
            .logisticsEvents(policyService.detailedLogisticsEnabled()
                ? logisticsEventRepository.findByShipmentIdOrderByIdAsc(shipment.getId()).stream()
                    .map(event -> SalesLogisticsEventResponse.builder()
                        .id(event.getId())
                        .fromStatus(event.getFromStatus() == null ? null : event.getFromStatus().name())
                        .toStatus(event.getToStatus().name())
                        .toStatusDescription(event.getToStatus().getDescription())
                        .source(event.getSource())
                        .reason(event.getReason())
                        .changedBy(event.getChangedBy())
                        .changedByName(event.getChangedByName())
                        .changedAt(event.getChangedAt())
                        .build())
                    .toList()
                : List.of())
            .shippedAt(shipment.getShippedAt())
            .createdBy(shipment.getCreatedBy())
            .createdByName(shipment.getCreatedByName())
            .voidedBy(shipment.getVoidedBy())
            .voidedByName(shipment.getVoidedByName())
            .voidedAt(shipment.getVoidedAt())
            .remark(shipment.getRemark())
            .version(shipment.getVersion())
            .createdAt(shipment.getCreatedAt())
            .updatedAt(shipment.getUpdatedAt())
            .build();
    }

    private SalesLogisticsStatus deriveBasicStatus(SalesOrderShipmentRequest request) {
        return request.getShippedAt() == null
            ? SalesLogisticsStatus.LABEL_CREATED
            : SalesLogisticsStatus.HANDED_TO_CARRIER;
    }

    private void requireTransitionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "statusReason", "message", "Manual logistics transitions require a reason"));
        }
    }
}
