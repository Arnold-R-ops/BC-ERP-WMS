package com.wms.system.service;

import com.wms.system.dto.v45.BackorderLineResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.BackorderStatus;
import com.wms.system.entity.enums.FulfillmentStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackorderService {

    private final BackorderLineRepository backorderLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final DomainOutboxService domainOutboxService;

    @Transactional(rollbackFor = Exception.class)
    public BackorderLine createBackorder(SalesOrderItem item, int shortageQty, int priority, LocalDate promisedDate) {
        if (shortageQty <= 0) {
            throw new BusinessException(ErrorKeys.STOCK_INVALID_QUANTITY, Map.of("quantity", shortageQty, "operationType", "BACKORDER"));
        }
        BackorderLine line = BackorderLine.builder()
            .salesOrderId(item.getSalesOrderId())
            .salesOrderItemId(item.getId())
            .productId(item.getProductId())
            .requestedQty(shortageQty)
            .remainingQty(shortageQty)
            .allocatedQty(0)
            .priority(priority)
            .promisedDate(promisedDate)
            .status(BackorderStatus.OPEN)
            .build();
        BackorderLine saved = backorderLineRepository.save(line);
        domainOutboxService.append("BACKORDER_CREATED", "BackorderLine", saved.getId(), Map.of(
            "backorderId", saved.getId(),
            "salesOrderId", saved.getSalesOrderId(),
            "productId", saved.getProductId(),
            "remainingQty", saved.getRemainingQty()
        ));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public int wakeProduct(Long productId) {
        List<BackorderLine> lines = backorderLineRepository.findOpenByProductForWakeup(
            productId,
            EnumSet.of(BackorderStatus.OPEN, BackorderStatus.PARTIAL)
        );

        int createdTasks = 0;
        for (BackorderLine line : lines) {
            if (!line.isOpenForAllocation() || line.getRemainingQty() <= 0) {
                continue;
            }
            SalesOrderItem item = salesOrderItemRepository.findById(line.getSalesOrderItemId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_ITEM_NOT_FOUND, Map.of("itemId", line.getSalesOrderItemId())));

            int allocated = reserveAvailable(item, line.getRemainingQty());
            if (allocated <= 0) {
                continue;
            }

            line.allocate(allocated);
            backorderLineRepository.save(line);

            item.setAllocatedQty((item.getAllocatedQty() == null ? 0 : item.getAllocatedQty()) + allocated);
            item.setBackorderQty(Math.max(0, (item.getBackorderQty() == null ? 0 : item.getBackorderQty()) - allocated));
            item.setFulfillmentStatus(item.getBackorderQty() > 0 ? FulfillmentStatus.BACKORDERED : FulfillmentStatus.RESERVED);
            salesOrderItemRepository.save(item);
            updateOrderFulfillment(item.getSalesOrderId());

            createdTasks++;
            domainOutboxService.append("BACKORDER_ALLOCATED", "BackorderLine", line.getId(), Map.of(
                "backorderId", line.getId(),
                "allocatedQty", allocated,
                "remainingQty", line.getRemainingQty()
            ));
        }
        return createdTasks;
    }

    @Transactional(readOnly = true)
    public List<BackorderLineResponse> listByOrder(Long salesOrderId) {
        return backorderLineRepository.findBySalesOrderId(salesOrderId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BackorderLineResponse> listByProduct(Long productId) {
        return backorderLineRepository.findOpenByProductForWakeup(
            productId,
            EnumSet.of(BackorderStatus.OPEN, BackorderStatus.PARTIAL, BackorderStatus.FULFILLED)
        ).stream().map(this::toResponse).toList();
    }

    private int reserveAvailable(SalesOrderItem item, int requestedQty) {
        int remaining = requestedQty;
        List<InventoryBatch> batches = inventoryBatchRepository.findByProductIdAndActiveOrderByExpiryDateAsc(item.getProductId(), true)
            .stream()
            .filter(batch -> batch.getAvailableQuantity() > 0)
            .toList();

        for (InventoryBatch batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int toReserve = Math.min(remaining, batch.getAvailableQuantity());
            batch.reserveQuantity(toReserve);
            InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

            InventoryReservation reservation = InventoryReservation.builder()
                .salesOrderId(item.getSalesOrderId())
                .salesOrderItemId(item.getId())
                .inventoryBatchId(savedBatch.getId())
                .productId(item.getProductId())
                .locationId(savedBatch.getLocation().getId())
                .reservedQty(toReserve)
                .sourceType("BACKORDER")
                .build();
            reservation = inventoryReservationRepository.save(reservation);

            OutboundTask task = OutboundTask.builder()
                .salesOrderId(item.getSalesOrderId())
                .salesOrderItemId(item.getId())
                .assignedBatchId(savedBatch.getId())
                .reservationId(reservation.getId())
                .locationId(savedBatch.getLocation().getId())
                .planQty(toReserve)
                .actualQty(0)
                .status(OutboundTaskStatus.PENDING)
                .remark(String.format("Backorder reserved from batch %s", savedBatch.getBatchCode()))
                .build();
            outboundTaskRepository.save(task);
            remaining -= toReserve;
        }
        return requestedQty - remaining;
    }

    private void updateOrderFulfillment(Long salesOrderId) {
        SalesOrder order = salesOrderRepository.findById(salesOrderId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND, Map.of("salesOrderId", salesOrderId)));
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrderId(salesOrderId);
        boolean anyBackorder = items.stream().anyMatch(item -> item.getBackorderQty() != null && item.getBackorderQty() > 0);
        boolean anyAllocated = items.stream().anyMatch(item -> item.getAllocatedQty() != null && item.getAllocatedQty() > 0);
        order.setFulfillmentStatus(anyBackorder
            ? (anyAllocated ? FulfillmentStatus.PARTIALLY_ALLOCATED : FulfillmentStatus.WAITING_INBOUND)
            : FulfillmentStatus.RESERVED);
        order.setFulfillmentVersion(order.getFulfillmentVersion() == null ? 1L : order.getFulfillmentVersion() + 1);
        salesOrderRepository.save(order);
    }

    private BackorderLineResponse toResponse(BackorderLine line) {
        return BackorderLineResponse.builder()
            .id(line.getId())
            .salesOrderId(line.getSalesOrderId())
            .salesOrderItemId(line.getSalesOrderItemId())
            .productId(line.getProductId())
            .requestedQty(line.getRequestedQty())
            .remainingQty(line.getRemainingQty())
            .allocatedQty(line.getAllocatedQty())
            .status(line.getStatus())
            .priority(line.getPriority())
            .promisedDate(line.getPromisedDate())
            .createdAt(line.getCreatedAt())
            .updatedAt(line.getUpdatedAt())
            .build();
    }
}
