package com.wms.system.service;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Rebuilds order summaries from authoritative commercial, reservation, task and shipment facts. */
@Service
@RequiredArgsConstructor
public class SalesOrderStatusProjectionService {
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OutboundTaskRepository taskRepository;
    private final BackorderLineRepository backorderRepository;
    private final SalesOrderShipmentRepository shipmentRepository;

    @Transactional(rollbackFor = Exception.class)
    public SalesOrder refresh(Long salesOrderId) {
        SalesOrder order = orderRepository.findByIdForUpdate(salesOrderId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", salesOrderId)));
        return refreshLocked(order);
    }

    public SalesOrder refreshLocked(SalesOrder order) {
        Long orderId = order.getId();
        List<SalesOrderItem> items = itemRepository.findBySalesOrderId(orderId);
        List<InventoryReservation> reservations = reservationRepository.findBySalesOrderId(orderId);
        List<OutboundTask> tasks = taskRepository.findBySalesOrderId(orderId);
        List<BackorderLine> backorders = backorderRepository.findBySalesOrderId(orderId);
        List<SalesOrderShipment> shipments = shipmentRepository.findBySalesOrderIdOrderByIdAsc(orderId);

        int effectiveQuantity = items.stream().mapToInt(this::effectiveQuantity).sum();
        int shippedQuantity = items.stream().mapToInt(item -> value(item.getShippedQty())).sum();
        int outstandingQuantity = Math.max(0, effectiveQuantity - shippedQuantity);
        int openReservedQuantity = reservations.stream()
            .filter(InventoryReservation::hasOpenQuantity)
            .mapToInt(InventoryReservation::getOpenQty)
            .sum();
        int openBackorderQuantity = backorders.stream()
            .filter(BackorderLine::isOpenForAllocation)
            .mapToInt(line -> value(line.getRemainingQty()))
            .sum();
        boolean hasPromisedInbound = backorders.stream()
            .filter(BackorderLine::isOpenForAllocation)
            .anyMatch(line -> line.getPromisedDate() != null);

        order.setAllocationStatus(resolveAllocation(order, outstandingQuantity,
            openReservedQuantity, openBackorderQuantity, hasPromisedInbound));
        order.setWarehouseStatus(resolveWarehouse(order, tasks, shippedQuantity, effectiveQuantity));
        order.setLogisticsStatus(resolveLogistics(shipments));

        if (order.getCommercialStatus() == CommercialStatus.APPROVED
            && effectiveQuantity > 0 && shippedQuantity >= effectiveQuantity) {
            order.setCommercialStatus(CommercialStatus.COMPLETED);
            if (order.getShippedAt() == null) order.setShippedAt(LocalDateTime.now());
        }
        return orderRepository.save(order);
    }

    private SalesAllocationStatus resolveAllocation(
        SalesOrder order,
        int outstandingQuantity,
        int openReservedQuantity,
        int openBackorderQuantity,
        boolean hasPromisedInbound
    ) {
        CommercialStatus commercial = order.getCommercialStatus();
        if (commercial == CommercialStatus.DRAFT || commercial == CommercialStatus.PENDING_APPROVAL
            || commercial == CommercialStatus.REJECTED || commercial == CommercialStatus.CANCELLED
            || commercial == CommercialStatus.VOIDED || commercial == CommercialStatus.CLOSED) {
            return SalesAllocationStatus.UNALLOCATED;
        }
        if (outstandingQuantity == 0 || openReservedQuantity >= outstandingQuantity) {
            return SalesAllocationStatus.FULLY_ALLOCATED;
        }
        if (openReservedQuantity > 0) return SalesAllocationStatus.PARTIALLY_ALLOCATED;
        if (openBackorderQuantity > 0 && hasPromisedInbound) {
            return SalesAllocationStatus.WAITING_INBOUND;
        }
        if (openBackorderQuantity > 0) return SalesAllocationStatus.BACKORDERED;
        return SalesAllocationStatus.UNALLOCATED;
    }

    private SalesWarehouseStatus resolveWarehouse(
        SalesOrder order,
        List<OutboundTask> tasks,
        int shippedQuantity,
        int effectiveQuantity
    ) {
        if (order.getCommercialStatus() == CommercialStatus.REJECTED
            || order.getCommercialStatus() == CommercialStatus.CANCELLED
            || order.getCommercialStatus() == CommercialStatus.VOIDED) {
            return SalesWarehouseStatus.NOT_RELEASED;
        }
        // Closing the remainder must preserve the fact that only part of the
        // original customer demand shipped. cancelledQty reduces the effective
        // quantity, so this guard must run before the generic full-shipment rule.
        if (order.getCommercialStatus() == CommercialStatus.CLOSED && shippedQuantity > 0) {
            return SalesWarehouseStatus.PARTIALLY_SHIPPED;
        }
        if (tasks.stream().anyMatch(task -> task.getStatus() == OutboundTaskStatus.QUALITY_EXCEPTION)) {
            return SalesWarehouseStatus.EXCEPTION;
        }
        if (effectiveQuantity > 0 && shippedQuantity >= effectiveQuantity) return SalesWarehouseStatus.SHIPPED;
        if (shippedQuantity > 0 || tasks.stream().anyMatch(task -> task.getStatus() == OutboundTaskStatus.COMPLETED)) {
            return SalesWarehouseStatus.PARTIALLY_SHIPPED;
        }
        if (tasks.stream().anyMatch(task -> task.getStatus() == OutboundTaskStatus.PICKING)) {
            return SalesWarehouseStatus.PICKING;
        }
        if (tasks.stream().anyMatch(task -> task.getStatus() == OutboundTaskStatus.PENDING)) {
            return SalesWarehouseStatus.PENDING_PICK;
        }
        return SalesWarehouseStatus.NOT_RELEASED;
    }

    private SalesLogisticsStatus resolveLogistics(List<SalesOrderShipment> shipments) {
        return shipments.stream()
            .filter(shipment -> shipment.getStatus() == ShipmentStatus.ACTIVE)
            .map(SalesOrderShipment::getLogisticsStatus)
            .filter(status -> status != null)
            .max(java.util.Comparator.comparingInt(this::logisticsRank))
            .orElse(SalesLogisticsStatus.NOT_CREATED);
    }

    private int logisticsRank(SalesLogisticsStatus status) {
        return switch (status) {
            case NOT_CREATED -> 0;
            case LABEL_CREATED -> 1;
            case HANDED_TO_CARRIER -> 2;
            case IN_TRANSIT -> 3;
            case DELIVERED -> 4;
            case EXCEPTION -> 5;
            case RETURNED -> 6;
        };
    }

    private int effectiveQuantity(SalesOrderItem item) {
        return Math.max(0, value(item.getRequestedQty()) - value(item.getCancelledQty()));
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }
}
