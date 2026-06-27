package com.wms.system.service;

import com.wms.system.dto.inventory.InventoryReservationResponse;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.enums.ReservationStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public InventoryReservationResponse releaseReservation(Long reservationId) {
        InventoryReservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.INVENTORY_RESERVATION_NOT_FOUND,
                Map.of("reservationId", reservationId)
            ));

        release(reservation);
        return toResponse(reservationRepository.save(reservation));
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseOpenReservationsForOrder(Long salesOrderId) {
        List<InventoryReservation> reservations = reservationRepository
            .findBySalesOrderIdAndStatusIn(salesOrderId, OPEN_STATUSES);

        for (InventoryReservation reservation : reservations) {
            release(reservation);
            reservationRepository.save(reservation);
        }
    }

    private void release(InventoryReservation reservation) {
        if (!reservation.hasOpenQuantity()) {
            return;
        }

        InventoryBatch batch = inventoryBatchRepository.findById(reservation.getInventoryBatchId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", reservation.getInventoryBatchId())
            ));

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
            .productId(reservation.getProductId())
            .productName(batch != null && batch.getProduct() != null ? batch.getProduct().getName() : null)
            .locationId(reservation.getLocationId())
            .locationCode(batch != null ? batch.getLocationCode() : null)
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
