package com.wms.system.service;

import com.wms.system.entity.EmergencyStockCorrection;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.EmergencyStockCorrectionRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.service.inventory.InventoryPostingCommand;
import com.wms.system.service.inventory.InventoryPostingResult;
import com.wms.system.service.inventory.InventoryPostingService;
import com.wms.system.service.inventory.PostingContext;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes one correction posting atomically after the idempotency lease is committed. */
@Service
@RequiredArgsConstructor
public class EmergencyStockCorrectionApplicationExecutor {

    private final EmergencyStockCorrectionRepository correctionRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final ProductSkuRepository productSkuRepository;
    private final LocationRepository locationRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryCorrectionPolicyService policyService;
    private final InventoryCorrectionPreviewTokenService previewTokenService;
    private final InventoryPostingService inventoryPostingService;
    private final LocationOccupancyService locationOccupancyService;
    private final BackorderService backorderService;

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrection applyAtomic(
        Long correctionId,
        Long operatorId,
        String operatorName,
        String idempotencyKey,
        String previewToken
    ) {
        EmergencyStockCorrection correction = correctionRepository.findByIdForUpdate(correctionId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "EmergencyStockCorrection", "resourceId", correctionId)
            ));

        if (correction.getStatus() == EmergencyCorrectionStatus.APPLIED) {
            if (Objects.equals(correction.getIdempotencyKey(), idempotencyKey)) {
                return correction;
            }
            throw modeConflict(correction, "CORRECTION_ALREADY_APPLIED");
        }

        EmergencyCorrectionApprovalMode validatedApprovalMode =
            validateApprovalModeAndActor(correction, operatorId);

        ProductSku product = productSkuRepository.findById(correction.getProductSkuId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                Map.of("productSkuId", correction.getProductSkuId())
            ));
        Location location = locationRepository.findByIdForInventoryPosting(correction.getLocationId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.LOCATION_NOT_FOUND,
                Map.of("locationId", correction.getLocationId())
            ));
        validateWarehouse(location, correction.getWarehouseId());

        InventoryBatch batch = correction.getInventoryBatchId() == null
            ? null
            : inventoryBatchRepository.findByIdForPosting(correction.getInventoryBatchId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.BATCH_NOT_FOUND,
                    Map.of("batchId", correction.getInventoryBatchId())
                ));

        if (batch != null) {
            validateBatchIdentity(correction, batch);
        }

        int currentQuantity = batch == null ? 0 : batch.getQuantity();
        long projectedQuantity = (long) currentQuantity + correction.getAdjustmentQty();
        int reservedQuantity = batch == null || batch.getReservedQuantity() == null
            ? 0
            : batch.getReservedQuantity();
        previewTokenService.verify(
            previewToken,
            correction.getId(),
            correction.getVersion(),
            batch == null ? null : batch.getId(),
            batch == null ? null : batch.getVersion(),
            currentQuantity,
            reservedQuantity
        );
        captureDirectConfirmation(correction, operatorId, validatedApprovalMode);
        if (projectedQuantity < reservedQuantity) {
            throw reservedConflict(correction, currentQuantity, reservedQuantity, projectedQuantity);
        }
        if (batch == null && inventoryBatchRepository.existsByBatchCodeAndLocationId(
            correction.getBatchCode(), correction.getLocationId())) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_CORRECTION_BATCH_CONFLICT,
                Map.of(
                    "correctionId", correction.getId(),
                    "batchCode", correction.getBatchCode(),
                    "locationId", correction.getLocationId(),
                    "reason", "USE_EXISTING_BATCH"
                )
            );
        }

        String postingId = "ESC-" + correction.getId() + "-" + correction.getCorrectionNo();
        InventoryPostingResult postingResult;
        if (correction.getAdjustmentQty() == 0) {
            postingResult = zeroResult(correction, batch, currentQuantity, postingId, idempotencyKey);
        } else {
            if (batch == null) {
                if (correction.getAdjustmentQty() > 0) {
                    locationOccupancyService.validateCanStore(
                        location,
                        product,
                        correction.getBatchCode()
                    );
                }
                batch = createCorrectionBatch(correction, product, location);
                correction.setInventoryBatchId(batch.getId());
            } else if (correction.getAdjustmentQty() > 0) {
                locationOccupancyService.validateCanStore(location, product, batch.getBatchCode());
            }

            SourceType sourceType = correction.getAdjustmentQty() > 0
                ? SourceType.INVENTORY_GAIN
                : SourceType.INVENTORY_LOSS;
            postingResult = inventoryPostingService.post(new InventoryPostingCommand(
                batch.getId(),
                product.getId(),
                location.getId(),
                correction.getAdjustmentQty(),
                new PostingContext(
                    CompanyScope.currentCompanyId(),
                    correction.getWarehouseId(),
                    sourceType,
                    correction.getCorrectionNo(),
                    correction.getReasonCode(),
                    postingRemarks(correction.getReasonDetail()),
                    operatorId,
                    operatorName,
                    postingId,
                    idempotencyKey
                )
            ));
            locationOccupancyService.refreshLocationStatus(location.getId());
            if (correction.getAdjustmentQty() > 0) {
                backorderService.wakeProduct(product.getId());
            }
        }

        correction.setStatus(EmergencyCorrectionStatus.APPLIED);
        correction.setAppliedBy(operatorId);
        correction.setAppliedAt(LocalDateTime.now());
        correction.setAppliedQuantityBefore(postingResult.quantityBefore());
        correction.setAppliedQuantityAfter(postingResult.quantityAfter());
        correction.setPostingId(postingResult.postingId());
        correction.setIdempotencyKey(idempotencyKey);
        return correctionRepository.saveAndFlush(correction);
    }

    private EmergencyCorrectionApprovalMode validateApprovalModeAndActor(
        EmergencyStockCorrection correction,
        Long operatorId
    ) {
        if (correction.getStatus() == EmergencyCorrectionStatus.DRAFT) {
            EmergencyCorrectionApprovalMode capturedMode = correction.getApprovalModeSnapshot();
            if (capturedMode == null) {
                capturedMode = policyService.effectiveMode();
            }
            if (capturedMode != EmergencyCorrectionApprovalMode.NONE) {
                throw modeConflict(correction, "APPROVAL_REQUIRED");
            }
            if (!Objects.equals(correction.getCreatedBy(), operatorId)) {
                throw new BusinessException(
                    ErrorKeys.INVENTORY_CORRECTION_ACTOR_CONFLICT,
                    Map.of(
                        "correctionId", correction.getId(),
                        "operation", "APPLY",
                        "reason", "ONLY_MAKER_ALLOWED"
                    )
                );
            }
            return capturedMode;
        }

        if (correction.getStatus() != EmergencyCorrectionStatus.APPROVED) {
            throw modeConflict(correction, "CORRECTION_NOT_READY_TO_APPLY");
        }
        if (correction.getApprovalModeSnapshot() != EmergencyCorrectionApprovalMode.TWO_PERSON
            && correction.getApprovalModeSnapshot() != EmergencyCorrectionApprovalMode.THREE_PERSON) {
            throw modeConflict(correction, "APPROVAL_MODE_SNAPSHOT_INVALID");
        }
        return correction.getApprovalModeSnapshot();
    }

    private void captureDirectConfirmation(
        EmergencyStockCorrection correction,
        Long operatorId,
        EmergencyCorrectionApprovalMode validatedApprovalMode
    ) {
        if (correction.getStatus() != EmergencyCorrectionStatus.DRAFT) {
            return;
        }
        if (correction.getApprovalModeSnapshot() == null) {
            correction.setApprovalModeSnapshot(validatedApprovalMode);
        }
        correction.setSubmittedBy(operatorId);
        correction.setSubmittedAt(LocalDateTime.now());
    }

    private void validateBatchIdentity(
        EmergencyStockCorrection correction,
        InventoryBatch batch
    ) {
        Long actualProductId = batch.getProductSku() == null ? null : batch.getProductSku().getId();
        Long actualLocationId = batch.getLocation() == null ? null : batch.getLocation().getId();
        if (!Objects.equals(actualProductId, correction.getProductSkuId())
            || !Objects.equals(actualLocationId, correction.getLocationId())) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_POSTING_CONTEXT_MISMATCH,
                Map.of(
                    "batchId", batch.getId(),
                    "expectedProductSkuId", correction.getProductSkuId(),
                    "actualProductSkuId", Objects.toString(actualProductId, ""),
                    "expectedLocationId", correction.getLocationId(),
                    "actualLocationId", Objects.toString(actualLocationId, "")
                )
            );
        }
    }

    private void validateWarehouse(Location location, Long expectedWarehouseId) {
        Long actualWarehouseId = location.getWarehouse() == null
            ? null
            : location.getWarehouse().getId();
        if (!Objects.equals(actualWarehouseId, expectedWarehouseId)) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_POSTING_CONTEXT_MISMATCH,
                Map.of(
                    "locationId", location.getId(),
                    "expectedWarehouseId", expectedWarehouseId,
                    "actualWarehouseId", Objects.toString(actualWarehouseId, "")
                )
            );
        }
    }

    private InventoryBatch createCorrectionBatch(
        EmergencyStockCorrection correction,
        ProductSku product,
        Location location
    ) {
        InventoryBatch batch = InventoryBatch.builder()
            .batchCode(correction.getBatchCode())
            .productSku(product)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(0)
            .reservedQuantity(0)
            .initialQuantity(correction.getCountedQty())
            .productionDate(correction.getProductionDate())
            .expiryDate(correction.getExpiryDate())
            .entryDate(LocalDateTime.now())
            .active(true)
            .remark("Created by emergency stock correction " + correction.getCorrectionNo())
            .build();
        return inventoryBatchRepository.saveAndFlush(batch);
    }

    private InventoryPostingResult zeroResult(
        EmergencyStockCorrection correction,
        InventoryBatch batch,
        int currentQuantity,
        String postingId,
        String idempotencyKey
    ) {
        return new InventoryPostingResult(
            batch == null ? null : batch.getId(),
            batch == null ? correction.getBatchCode() : batch.getBatchCode(),
            correction.getProductSkuId(),
            correction.getLocationId(),
            correction.getWarehouseId(),
            0,
            currentQuantity,
            currentQuantity,
            false,
            null,
            postingId,
            idempotencyKey
        );
    }

    private BusinessException reservedConflict(
        EmergencyStockCorrection correction,
        int currentQuantity,
        int reservedQuantity,
        long projectedQuantity
    ) {
        List<Long> affectedOrderIds = correction.getInventoryBatchId() == null
            ? List.of()
            : reservationRepository.findByInventoryBatchId(correction.getInventoryBatchId())
                .stream()
                .filter(InventoryReservation::hasOpenQuantity)
                .map(InventoryReservation::getSalesOrderId)
                .distinct()
                .sorted()
                .toList();
        return new BusinessException(
            ErrorKeys.INVENTORY_RESERVED_QUANTITY_CONFLICT,
            Map.of(
                "correctionId", correction.getId(),
                "currentQuantity", currentQuantity,
                "adjustmentQuantity", correction.getAdjustmentQty(),
                "projectedQuantity", projectedQuantity,
                "reservedQuantity", reservedQuantity,
                "affectedSalesOrderIds", affectedOrderIds
            )
        );
    }

    private BusinessException modeConflict(
        EmergencyStockCorrection correction,
        String reason
    ) {
        return new BusinessException(
            ErrorKeys.INVENTORY_CORRECTION_MODE_CONFLICT,
            Map.of(
                "correctionId", correction.getId(),
                "status", correction.getStatus().name(),
                "reason", reason
            )
        );
    }

    private String postingRemarks(String reasonDetail) {
        if (reasonDetail == null || reasonDetail.isBlank()) {
            return null;
        }
        String normalized = reasonDetail.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
