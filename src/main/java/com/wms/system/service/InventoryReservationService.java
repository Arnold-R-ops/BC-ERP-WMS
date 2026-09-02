package com.wms.system.service;

import com.wms.system.dto.inventory.InventoryReservationResponse;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.enums.ReservationStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private static final Set<ReservationStatus> OPEN_STATUSES = Set.of(
        ReservationStatus.ACTIVE,
        ReservationStatus.PARTIALLY_CONSUMED
    );

    private final InventoryReservationRepository reservationRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final SalesOrderRepository salesOrderRepository;

    @Transactional(readOnly = true)
    public List<InventoryReservationResponse> listBySalesOrder(Long salesOrderId) {
        return reservationRepository.findBySalesOrderId(salesOrderId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryReservationResponse> listByBatch(Long batchId) {
        return reservationRepository.findByInventoryBatchId(batchId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseOpenReservationsForOrder(Long salesOrderId) {
        // Use the same leading lock order as dispatch: sales order, batches,
        // reservations. This serializes cancellation/closure against a picker
        // consuming the same reservation and prevents reserved-stock drift.
        salesOrderRepository.findByIdForUpdate(salesOrderId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.SALES_ORDER_NOT_FOUND,
                Map.of("salesOrderId", salesOrderId)
            ));

        List<InventoryReservation> snapshot = reservationRepository
            .findBySalesOrderIdAndStatusIn(salesOrderId, OPEN_STATUSES);
        List<Long> batchIds = snapshot.stream()
            .map(InventoryReservation::getInventoryBatchId)
            .distinct()
            .sorted()
            .toList();
        Map<Long, InventoryBatch> lockedBatches = new LinkedHashMap<>();
        List<InventoryBatch> batches = batchIds.isEmpty()
            ? List.of()
            : inventoryBatchRepository.findAllByIdForPosting(batchIds);
        batches.stream()
            .sorted(Comparator.comparing(InventoryBatch::getId))
            .forEach(batch -> lockedBatches.put(batch.getId(), batch));

        List<InventoryReservation> reservations = reservationRepository
            .findBySalesOrderIdForUpdate(salesOrderId).stream()
            .filter(InventoryReservation::hasOpenQuantity)
            .toList();

        for (InventoryReservation reservation : reservations) {
            InventoryBatch batch = lockedBatches.get(reservation.getInventoryBatchId());
            if (batch == null) {
                throw new BusinessException(
                    ErrorKeys.STOCK_CONCURRENCY_CONFLICT,
                    Map.of(
                        "salesOrderId", salesOrderId,
                        "reservationId", reservation.getId(),
                        "reason", "RESERVATION_SET_CHANGED"
                    )
                );
            }
            release(reservation, batch);
            reservationRepository.save(reservation);
        }
    }

    private void release(InventoryReservation reservation, InventoryBatch batch) {
        if (!reservation.hasOpenQuantity()) {
            return;
        }

        int releasedQty = reservation.releaseRemaining();
        batch.releaseReservedQuantity(releasedQty);
        inventoryBatchRepository.save(batch);

        log.info("Released inventory reservation: reservationId={}, batchId={}, releasedQty={}",
            reservation.getId(), reservation.getInventoryBatchId(), releasedQty);
    }

    private InventoryReservationResponse toResponse(InventoryReservation reservation) {
        InventoryBatch batch = inventoryBatchRepository.findById(reservation.getInventoryBatchId()).orElse(null);

        return InventoryReservationResponse.builder()
            .id(reservation.getId())
            .salesOrderId(reservation.getSalesOrderId())
            .salesOrderItemId(reservation.getSalesOrderItemId())
            .inventoryBatchId(reservation.getInventoryBatchId())
            .batchCode(batch != null ? batch.getBatchCode() : null)
            .productSkuId(reservation.getProductSkuId())
            .productName(batch != null && batch.getProductSku() != null ? batch.getProductSku().getName() : null)
            .locationId(reservation.getLocationId())
            .locationCode(batch != null ? batch.getLocationCode() : null)
            .warehouseId(batch != null
                && batch.getLocation() != null
                && batch.getLocation().getWarehouse() != null
                    ? batch.getLocation().getWarehouse().getId()
                    : null)
            .reservedQty(reservation.getReservedQty())
            .consumedQty(reservation.getConsumedQty())
            .releasedQty(reservation.getReleasedQty())
            .openQty(reservation.getOpenQty())
            .status(reservation.getStatus().name())
            .statusDescription(reservation.getStatus().getDescription())
            .expiresAt(reservation.getExpiresAt())
            .sourceType(reservation.getSourceType())
            .createdBy(reservation.getCreatedBy())
            .createdAt(reservation.getCreatedAt())
            .updatedAt(reservation.getUpdatedAt())
            .build();
    }
}
