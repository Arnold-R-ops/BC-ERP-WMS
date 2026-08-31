package com.wms.system.service;

import com.wms.system.dto.v45.BackorderLineResponse;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.BackorderStatus;
import com.wms.system.entity.enums.CommercialStatus;
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
    private final SalesOrderStatusProjectionService statusProjectionService;

    @Transactional(rollbackFor = Exception.class)
    public BackorderLine createBackorder(SalesOrderItem item, int shortageQty, int priority, LocalDate promisedDate) {
        if (shortageQty <= 0) {
            throw new BusinessException(ErrorKeys.STOCK_INVALID_QUANTITY, Map.of("quantity", shortageQty, "operationType", "BACKORDER"));
        }
        BackorderLine line = BackorderLine.builder()
            .salesOrderId(item.getSalesOrderId())
            .salesOrderItemId(item.getId())
            .productSkuId(item.getProductSkuId())
            .requestedQty(shortageQty)
            .remainingQty(shortageQty)
            .allocatedQty(0)
            .priority(priority)
            .promisedDate(promisedDate)
            .status(BackorderStatus.OPEN)
            .build();
        BackorderLine saved = backorderLineRepository.save(line);
        domainOutboxService.append("BACKORDER_CREATED", "BackorderLine", saved.getId(), Map.of(
            "schemaVersion", 2,
            "backorderId", saved.getId(),
            "salesOrderId", saved.getSalesOrderId(),
            "productSkuId", saved.getProductSkuId(),
            "remainingQty", saved.getRemainingQty()
        ));
        return saved;
    }

    /** Converts a quality-driven reservation release into an auditable backorder. */
    @Transactional(rollbackFor = Exception.class)
    public BackorderLine addQualityShortage(Long salesOrderItemId, int quantity, String reason) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorKeys.STOCK_INVALID_QUANTITY,
                Map.of("quantity", quantity, "operationType", "QUALITY_BACKORDER"));
        }
        SalesOrderItem item = salesOrderItemRepository.findByIdForUpdate(salesOrderItemId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_ITEM_NOT_FOUND,
                Map.of("itemId", salesOrderItemId)));
        int allocated = item.getAllocatedQty() == null ? 0 : item.getAllocatedQty();
        if (quantity > allocated) {
            throw new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_INVALID,
                Map.of("reason", "QUALITY_SHORTAGE_EXCEEDS_ALLOCATED", "itemId", salesOrderItemId));
        }
        item.setAllocatedQty(allocated - quantity);
        item.setBackorderQty((item.getBackorderQty() == null ? 0 : item.getBackorderQty()) + quantity);
        salesOrderItemRepository.save(item);

        SalesOrder order = salesOrderRepository.findByIdForUpdate(item.getSalesOrderId())
            .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", item.getSalesOrderId())));
        BackorderLine line = createBackorder(item, quantity, 50, order.getPromisedShipDate());
        order.setShortageReason(reason);
        salesOrderRepository.save(order);
        statusProjectionService.refreshLocked(order);
        return line;
    }

    @Transactional(rollbackFor = Exception.class)
    public int wakeProduct(Long productSkuId) {
        List<BackorderLine> lines = backorderLineRepository.findOpenByProductSkuForWakeup(
            productSkuId,
            EnumSet.of(BackorderStatus.OPEN, BackorderStatus.PARTIAL)
        );

        int createdTasks = 0;
        for (BackorderLine line : lines) {
            if (!line.isOpenForAllocation() || line.getRemainingQty() <= 0) {
                continue;
            }
            SalesOrderItem item = salesOrderItemRepository.findById(line.getSalesOrderItemId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_ITEM_NOT_FOUND, Map.of("itemId", line.getSalesOrderItemId())));
            SalesOrder order = salesOrderRepository.findById(item.getSalesOrderId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND,
                    Map.of("salesOrderId", item.getSalesOrderId())));
            if (order.getCommercialStatus() != CommercialStatus.APPROVED) {
                line.setStatus(BackorderStatus.CANCELLED);
                backorderLineRepository.save(line);
                continue;
            }

            int allocated = reserveAvailable(item, line.getRemainingQty());
            if (allocated <= 0) {
                continue;
            }

            line.allocate(allocated);
            backorderLineRepository.save(line);

            item.setAllocatedQty((item.getAllocatedQty() == null ? 0 : item.getAllocatedQty()) + allocated);
            item.setBackorderQty(Math.max(0, (item.getBackorderQty() == null ? 0 : item.getBackorderQty()) - allocated));
            salesOrderItemRepository.save(item);
            updateOrderProjection(item.getSalesOrderId());

            createdTasks++;
            domainOutboxService.append("BACKORDER_ALLOCATED", "BackorderLine", line.getId(), Map.of(
                "schemaVersion", 2,
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
    public List<BackorderLineResponse> listByProductSku(Long productSkuId) {
        return backorderLineRepository.findOpenByProductSkuForWakeup(
            productSkuId,
            EnumSet.of(BackorderStatus.OPEN, BackorderStatus.PARTIAL, BackorderStatus.FULFILLED)
        ).stream().map(this::toResponse).toList();
    }

    private int reserveAvailable(SalesOrderItem item, int requestedQty) {
        int remaining = requestedQty;
        List<InventoryBatch> batches = inventoryBatchRepository.findSaleableByProductSkuIdOrderByExpiryDateAsc(item.getProductSkuId())
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
                .productSkuId(item.getProductSkuId())
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

    private void updateOrderProjection(Long salesOrderId) {
        SalesOrder order = salesOrderRepository.findByIdForUpdate(salesOrderId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.SALES_ORDER_NOT_FOUND, Map.of("salesOrderId", salesOrderId)));
        statusProjectionService.refreshLocked(order);
    }

    private BackorderLineResponse toResponse(BackorderLine line) {
        return BackorderLineResponse.builder()
            .id(line.getId())
            .salesOrderId(line.getSalesOrderId())
            .salesOrderItemId(line.getSalesOrderItemId())
            .productSkuId(line.getProductSkuId())
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
