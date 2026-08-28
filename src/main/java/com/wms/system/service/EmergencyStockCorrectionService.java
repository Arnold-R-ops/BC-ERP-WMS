package com.wms.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.v45.EmergencyStockCorrectionImpactResponse;
import com.wms.system.dto.v45.EmergencyStockCorrectionRequest;
import com.wms.system.dto.v45.EmergencyStockCorrectionResponse;
import com.wms.system.entity.EmergencyStockCorrection;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryReservation;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.EmergencyStockCorrectionRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyStockCorrectionService {

    static final String APPLY_OPERATION = "EMERGENCY_STOCK_CORRECTION_APPLY";

    private final EmergencyStockCorrectionRepository correctionRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductSkuExpiryPolicy expiryPolicy;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationOccupancyService locationOccupancyService;
    private final InventoryCorrectionPolicyService policyService;
    private final InventoryCorrectionPreviewTokenService previewTokenService;
    private final IdempotencyService idempotencyService;
    private final EmergencyStockCorrectionApplicationExecutor applicationExecutor;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Long resolveWarehouseId(EmergencyStockCorrectionRequest request) {
        Location location = loadLocation(request.getLocationId());
        return warehouseId(location);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse create(
        EmergencyStockCorrectionRequest request,
        Long operatorId,
        Long authorizedWarehouseId
    ) {
        ProductSku product = loadProduct(request.getProductSkuId());
        Location location = loadLocationForCorrectionCreation(request.getLocationId());
        Long warehouseId = warehouseId(location);
        if (!Objects.equals(warehouseId, authorizedWarehouseId)) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_SCOPE_DENIED,
                Map.of(
                    "resourceType", "EmergencyStockCorrection",
                    "resourceId", "new",
                    "warehouseId", warehouseId,
                    "authorizedWarehouseId", authorizedWarehouseId
                )
            );
        }

        InventoryBatch batch = null;
        int systemQuantity = 0;
        String batchCode;
        if (request.getInventoryBatchId() != null) {
            batch = loadBatch(request.getInventoryBatchId());
            validateRequestedBatchIdentity(request, batch);
            systemQuantity = requireNonNegative(batch.getQuantity(), "batch.quantity");
            batchCode = batch.getBatchCode();
        } else {
            batchCode = requireText(request.getBatchCode(), "batchCode");
            requireNewBatchIdentityAvailable(batchCode, location.getId(), null);
            expiryPolicy.validateForInventoryReceipt(
                product,
                request.getProductionDate(),
                request.getExpiryDate()
            );
        }

        EmergencyStockCorrection correction = EmergencyStockCorrection.builder()
            .correctionNo(nextCorrectionNo())
            .productSkuId(product.getId())
            .warehouseId(warehouseId)
            .locationId(location.getId())
            .inventoryBatchId(batch == null ? null : batch.getId())
            .batchCode(batchCode)
            .productionDate(batch == null ? request.getProductionDate() : batch.getProductionDate())
            .expiryDate(batch == null ? request.getExpiryDate() : batch.getExpiryDate())
            .systemQty(systemQuantity)
            .countedQty(request.getCountedQty())
            .adjustmentQty(Math.subtractExact(request.getCountedQty(), systemQuantity))
            .reasonCode(request.getReasonCode().trim())
            .reasonDetail(request.getReasonDetail().trim())
            .evidenceUrl(normalize(request.getEvidenceUrl()))
            .relatedSalesOrderId(request.getRelatedSalesOrderId())
            .createdBy(operatorId)
            .status(EmergencyCorrectionStatus.DRAFT)
            .build();

        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse submit(Long id, Long operatorId, String comment) {
        EmergencyStockCorrection correction = loadForUpdate(id);
        requireStatus(correction, EmergencyCorrectionStatus.DRAFT);
        requireCreator(correction, operatorId, "SUBMIT");

        EmergencyCorrectionApprovalMode mode = capturedMode(correction);
        if (mode == EmergencyCorrectionApprovalMode.NONE) {
            throw modeConflict(correction, "DIRECT_CONFIRMATION_REQUIRED");
        }

        correction.setStatus(mode == EmergencyCorrectionApprovalMode.THREE_PERSON
            ? EmergencyCorrectionStatus.PENDING_REVIEW
            : EmergencyCorrectionStatus.PENDING_APPROVAL);
        correction.setSubmittedBy(operatorId);
        correction.setSubmittedAt(LocalDateTime.now());
        correction.setSubmissionComment(comment.trim());
        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse review(Long id, Long reviewerId, String comment) {
        EmergencyStockCorrection correction = loadForUpdate(id);
        requireStatus(correction, EmergencyCorrectionStatus.PENDING_REVIEW);
        requireMode(correction, EmergencyCorrectionApprovalMode.THREE_PERSON);
        requireDifferentActor(correction, reviewerId, correction.getCreatedBy(), "REVIEWER_IS_MAKER");

        correction.setStatus(EmergencyCorrectionStatus.PENDING_APPROVAL);
        correction.setReviewedBy(reviewerId);
        correction.setReviewedAt(LocalDateTime.now());
        correction.setReviewComment(comment.trim());
        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse approve(Long id, Long approverId, String comment) {
        EmergencyStockCorrection correction = loadForUpdate(id);
        requireStatus(correction, EmergencyCorrectionStatus.PENDING_APPROVAL);
        EmergencyCorrectionApprovalMode mode = capturedMode(correction);
        if (mode != EmergencyCorrectionApprovalMode.TWO_PERSON
            && mode != EmergencyCorrectionApprovalMode.THREE_PERSON) {
            throw modeConflict(correction, "APPROVAL_MODE_INVALID");
        }
        requireDifferentActor(correction, approverId, correction.getCreatedBy(), "APPROVER_IS_MAKER");
        if (mode == EmergencyCorrectionApprovalMode.THREE_PERSON) {
            requireDifferentActor(correction, approverId, correction.getReviewedBy(), "APPROVER_IS_REVIEWER");
        }

        correction.setStatus(EmergencyCorrectionStatus.APPROVED);
        correction.setApprovedBy(approverId);
        correction.setApprovedAt(LocalDateTime.now());
        correction.setApprovalComment(comment.trim());
        return toResponse(correctionRepository.save(correction));
    }

    @Transactional(rollbackFor = Exception.class)
    public EmergencyStockCorrectionResponse reject(Long id, Long operatorId, String comment) {
        EmergencyStockCorrection correction = loadForUpdate(id);
        if (correction.getStatus() != EmergencyCorrectionStatus.PENDING_REVIEW
            && correction.getStatus() != EmergencyCorrectionStatus.PENDING_APPROVAL) {
            throw invalidStatus(correction, "PENDING_REVIEW or PENDING_APPROVAL");
        }
        correction.setStatus(EmergencyCorrectionStatus.REJECTED);
        correction.setRejectedBy(operatorId);
        correction.setRejectedAt(LocalDateTime.now());
        correction.setRejectionComment(comment.trim());
        return toResponse(correctionRepository.save(correction));
    }

    /**
     * Orchestrates a durable idempotency claim around the atomic stock posting.
     * This method intentionally has no surrounding transaction: claim, posting,
     * and terminal replay state each commit independently.
     */
    public EmergencyStockCorrectionResponse apply(
        Long id,
        Long operatorId,
        String operatorName,
        String idempotencyKey,
        String previewToken
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String normalizedPreviewToken = previewToken == null ? "" : previewToken.trim();
        // The signed preview is a short-lived authorization proof, not the
        // business intent. Keeping it outside the request fingerprint allows a
        // transient claim to resume with a freshly issued proof for the same
        // correction while deterministic terminal results still replay.
        String commandIntent = writeJson(Map.of("correctionId", id));
        IdempotencyClaim claim = idempotencyService.claim(
            normalizedKey,
            APPLY_OPERATION,
            commandIntent
        );

        return switch (claim.decision()) {
            case ACQUIRED -> executeClaimed(
                claim,
                id,
                operatorId,
                normalizeOperatorName(operatorName, operatorId),
                normalizedKey,
                normalizedPreviewToken
            );
            case IN_PROGRESS -> throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("idempotencyKey", normalizedKey, "operation", APPLY_OPERATION)
            );
            case REPLAY_SUCCEEDED -> replaySucceeded(id, normalizedKey);
            case REPLAY_FAILED -> throw replayFailure(claim.request());
        };
    }

    @Transactional(readOnly = true)
    public List<EmergencyStockCorrectionResponse> list(EmergencyCorrectionStatus status) {
        List<EmergencyStockCorrection> corrections = status == null
            ? correctionRepository.findAll()
            : correctionRepository.findByStatusOrderByCreatedAtDesc(status);
        Map<Long, ProductSku> productsById = new LinkedHashMap<>();
        productSkuRepository.findAllById(corrections.stream()
                .map(EmergencyStockCorrection::getProductSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .forEach(product -> productsById.put(product.getId(), product));

        Map<Long, Location> locationsById = new LinkedHashMap<>();
        locationRepository.findAllById(corrections.stream()
                .map(EmergencyStockCorrection::getLocationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .forEach(location -> locationsById.put(location.getId(), location));

        Map<Long, Warehouse> warehousesById = new LinkedHashMap<>();
        warehouseRepository.findAllById(corrections.stream()
                .map(EmergencyStockCorrection::getWarehouseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .forEach(warehouse -> warehousesById.put(warehouse.getId(), warehouse));

        return corrections.stream()
            .map(correction -> toResponse(
                correction,
                productsById.get(correction.getProductSkuId()),
                locationsById.get(correction.getLocationId()),
                warehousesById.get(correction.getWarehouseId())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public EmergencyStockCorrectionResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public Long getWarehouseId(Long id) {
        return load(id).getWarehouseId();
    }

    @Transactional(readOnly = true)
    public EmergencyStockCorrectionImpactResponse impact(Long id) {
        EmergencyStockCorrection correction = load(id);
        InventoryBatch batch = correction.getInventoryBatchId() == null
            ? null
            : loadBatch(correction.getInventoryBatchId());
        if (batch != null) {
            validateCorrectionBatchIdentity(correction, batch);
        }

        int currentQuantity = batch == null
            ? 0
            : requireNonNegative(batch.getQuantity(), "batch.quantity");
        int reservedQuantity = batch == null || batch.getReservedQuantity() == null
            ? 0
            : requireNonNegative(batch.getReservedQuantity(), "batch.reservedQuantity");
        long projectedQuantity = (long) currentQuantity + correction.getAdjustmentQty();
        boolean quantityValid = projectedQuantity >= 0 && projectedQuantity <= Integer.MAX_VALUE;
        boolean reservedValid = projectedQuantity >= reservedQuantity;
        boolean workflowReady = workflowReady(correction);
        boolean batchStateValid = batch == null
            || Boolean.TRUE.equals(batch.getActive())
            || correction.getAdjustmentQty() == 0
            || (currentQuantity == 0 && correction.getAdjustmentQty() > 0);
        boolean newBatchIdentityAvailable = batch != null
            || !inventoryBatchRepository.existsByBatchCodeAndLocationId(
                correction.getBatchCode(), correction.getLocationId());
        boolean locationOccupancyValid = true;
        if (correction.getAdjustmentQty() > 0 && newBatchIdentityAvailable) {
            locationOccupancyValid = locationOccupancyService.canStore(
                loadLocation(correction.getLocationId()),
                loadProduct(correction.getProductSkuId()),
                correction.getBatchCode()
            );
        }

        List<Long> affectedOrderIds = !reservedValid && batch != null
            ? openReservationOrderIds(batch.getId())
            : List.of();
        InventoryCorrectionPreviewTokenService.IssuedPreview preview = previewTokenService.issue(
            correction.getId(),
            correction.getVersion(),
            batch == null ? null : batch.getId(),
            batch == null ? null : batch.getVersion(),
            currentQuantity,
            reservedQuantity
        );
        return EmergencyStockCorrectionImpactResponse.builder()
            .correctionId(correction.getId())
            .snapshotSystemQty(correction.getSystemQty())
            .countedQty(correction.getCountedQty())
            .adjustmentQty(correction.getAdjustmentQty())
            .currentQty(currentQuantity)
            .reservedQty(reservedQuantity)
            .projectedQty(projectedQuantity)
            .canApply(workflowReady
                && quantityValid
                && reservedValid
                && batchStateValid
                && newBatchIdentityAvailable
                && locationOccupancyValid)
            .affectedSalesOrderIds(affectedOrderIds)
            .previewToken(preview.token())
            .previewExpiresAt(preview.expiresAt())
            .build();
    }

    private EmergencyStockCorrectionResponse executeClaimed(
        IdempotencyClaim claim,
        Long correctionId,
        Long operatorId,
        String operatorName,
        String idempotencyKey,
        String previewToken
    ) {
        try {
            EmergencyStockCorrection applied = applicationExecutor.applyAtomic(
                correctionId,
                operatorId,
                operatorName,
                idempotencyKey,
                previewToken
            );
            idempotencyService.markSucceeded(
                claim.request(),
                200,
                writeJson(Map.of("correctionId", applied.getId()))
            );
            return toResponse(applied);
        } catch (BusinessException exception) {
            if (isDeterministic(exception)) {
                rememberFailure(claim.request(), exception);
            }
            throw exception;
        }
    }

    private EmergencyStockCorrectionResponse replaySucceeded(
        Long correctionId,
        String idempotencyKey
    ) {
        EmergencyStockCorrection correction = load(correctionId);
        if (correction.getStatus() != EmergencyCorrectionStatus.APPLIED
            || !Objects.equals(correction.getIdempotencyKey(), idempotencyKey)) {
            throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of(
                    "idempotencyKey", idempotencyKey,
                    "operation", APPLY_OPERATION,
                    "reason", "SUCCEEDED_RESULT_MISMATCH"
                )
            );
        }
        return toResponse(correction);
    }

    private void rememberFailure(IdempotencyRequest request, BusinessException exception) {
        try {
            idempotencyService.markFailed(
                request,
                409,
                writeJson(Map.of(
                    "errorKey", exception.getErrorKey(),
                    "params", exception.getParams()
                ))
            );
        } catch (RuntimeException completionFailure) {
            log.warn(
                "Unable to persist deterministic idempotency failure for key {}",
                request.getIdempotencyKey(),
                completionFailure
            );
        }
    }

    private BusinessException replayFailure(IdempotencyRequest request) {
        try {
            Map<String, Object> body = objectMapper.readValue(
                request.getResponseBody(),
                new TypeReference<Map<String, Object>>() { }
            );
            Object errorKey = body.get("errorKey");
            Map<String, Object> params = new LinkedHashMap<>();
            Object rawParams = body.get("params");
            if (rawParams instanceof Map<?, ?> map) {
                map.forEach((key, value) -> params.put(String.valueOf(key), value));
            }
            if (errorKey instanceof String key && !key.isBlank()) {
                return new BusinessException(key, params);
            }
        } catch (Exception parseFailure) {
            log.warn(
                "Unable to parse failed idempotency response for key {}",
                request.getIdempotencyKey(),
                parseFailure
            );
        }
        return new BusinessException(
            ErrorKeys.IDEMPOTENCY_CONFLICT,
            Map.of(
                "idempotencyKey", request.getIdempotencyKey(),
                "operation", APPLY_OPERATION,
                "reason", "FAILED_RESPONSE_INVALID"
            )
        );
    }

    private boolean isDeterministic(BusinessException exception) {
        return !ErrorKeys.STOCK_CONCURRENCY_CONFLICT.equals(exception.getErrorKey())
            && !ErrorKeys.INTERNAL_SERVER_ERROR.equals(exception.getErrorKey());
    }

    private boolean workflowReady(EmergencyStockCorrection correction) {
        if (correction.getStatus() == EmergencyCorrectionStatus.APPROVED) {
            EmergencyCorrectionApprovalMode mode = correction.getApprovalModeSnapshot();
            return mode == EmergencyCorrectionApprovalMode.TWO_PERSON
                || mode == EmergencyCorrectionApprovalMode.THREE_PERSON;
        }
        if (correction.getStatus() != EmergencyCorrectionStatus.DRAFT) {
            return false;
        }
        EmergencyCorrectionApprovalMode mode = correction.getApprovalModeSnapshot();
        return (mode == null ? policyService.effectiveMode() : mode)
            == EmergencyCorrectionApprovalMode.NONE;
    }

    private EmergencyCorrectionApprovalMode capturedMode(EmergencyStockCorrection correction) {
        if (correction.getApprovalModeSnapshot() == null) {
            correction.setApprovalModeSnapshot(policyService.effectiveMode());
        }
        return correction.getApprovalModeSnapshot();
    }

    private void requireMode(
        EmergencyStockCorrection correction,
        EmergencyCorrectionApprovalMode required
    ) {
        if (capturedMode(correction) != required) {
            throw modeConflict(correction, "REQUIRES_" + required.name());
        }
    }

    private void requireCreator(
        EmergencyStockCorrection correction,
        Long operatorId,
        String operation
    ) {
        if (!Objects.equals(correction.getCreatedBy(), operatorId)) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_CORRECTION_ACTOR_CONFLICT,
                Map.of(
                    "correctionId", correction.getId(),
                    "operation", operation,
                    "reason", "ONLY_MAKER_ALLOWED"
                )
            );
        }
    }

    private void requireDifferentActor(
        EmergencyStockCorrection correction,
        Long actorId,
        Long comparedActorId,
        String reason
    ) {
        if (Objects.equals(actorId, comparedActorId)) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_CORRECTION_ACTOR_CONFLICT,
                Map.of(
                    "correctionId", correction.getId(),
                    "actorId", actorId,
                    "reason", reason
                )
            );
        }
    }

    private void validateRequestedBatchIdentity(
        EmergencyStockCorrectionRequest request,
        InventoryBatch batch
    ) {
        Long actualProductId = batch.getProductSku() == null
            ? null
            : batch.getProductSku().getId();
        Long actualLocationId = batch.getLocation() == null
            ? null
            : batch.getLocation().getId();
        if (!Objects.equals(request.getProductSkuId(), actualProductId)
            || !Objects.equals(request.getLocationId(), actualLocationId)) {
            throw contextMismatch(
                batch.getId(),
                request.getProductSkuId(),
                actualProductId,
                request.getLocationId(),
                actualLocationId
            );
        }
    }

    private void validateCorrectionBatchIdentity(
        EmergencyStockCorrection correction,
        InventoryBatch batch
    ) {
        Long actualProductId = batch.getProductSku() == null
            ? null
            : batch.getProductSku().getId();
        Long actualLocationId = batch.getLocation() == null
            ? null
            : batch.getLocation().getId();
        if (!Objects.equals(correction.getProductSkuId(), actualProductId)
            || !Objects.equals(correction.getLocationId(), actualLocationId)) {
            throw contextMismatch(
                batch.getId(),
                correction.getProductSkuId(),
                actualProductId,
                correction.getLocationId(),
                actualLocationId
            );
        }
    }

    private BusinessException contextMismatch(
        Long batchId,
        Long expectedProductId,
        Long actualProductId,
        Long expectedLocationId,
        Long actualLocationId
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("batchId", batchId);
        params.put("expectedProductSkuId", expectedProductId);
        params.put("actualProductSkuId", actualProductId);
        params.put("expectedLocationId", expectedLocationId);
        params.put("actualLocationId", actualLocationId);
        return new BusinessException(ErrorKeys.INVENTORY_POSTING_CONTEXT_MISMATCH, params);
    }

    private Long warehouseId(Location location) {
        if (location.getWarehouse() == null || location.getWarehouse().getId() == null) {
            throw new BusinessException(
                ErrorKeys.WAREHOUSE_NOT_FOUND,
                Map.of("locationId", location.getId())
            );
        }
        return location.getWarehouse().getId();
    }

    private List<Long> openReservationOrderIds(Long batchId) {
        return reservationRepository.findByInventoryBatchId(batchId).stream()
            .filter(InventoryReservation::hasOpenQuantity)
            .map(InventoryReservation::getSalesOrderId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    }

    private void requireNewBatchIdentityAvailable(
        String batchCode,
        Long locationId,
        Long correctionId
    ) {
        if (!inventoryBatchRepository.existsByBatchCodeAndLocationId(batchCode, locationId)) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("batchCode", batchCode);
        params.put("locationId", locationId);
        params.put("reason", "USE_EXISTING_BATCH");
        if (correctionId != null) {
            params.put("correctionId", correctionId);
        }
        throw new BusinessException(ErrorKeys.INVENTORY_CORRECTION_BATCH_CONFLICT, params);
    }

    private ProductSku loadProduct(Long id) {
        return productSkuRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.PRODUCT_SKU_NOT_FOUND,
                Map.of("productSkuId", id)
            ));
    }

    private Location loadLocation(Long id) {
        return locationRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.LOCATION_NOT_FOUND,
                Map.of("locationId", id)
            ));
    }

    private Location loadLocationForCorrectionCreation(Long id) {
        return locationRepository.findByIdForInventoryPosting(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.LOCATION_NOT_FOUND,
                Map.of("locationId", id)
            ));
    }

    private InventoryBatch loadBatch(Long id) {
        return inventoryBatchRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", id)
            ));
    }

    private EmergencyStockCorrection load(Long id) {
        return correctionRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "EmergencyStockCorrection", "resourceId", id)
            ));
    }

    private EmergencyStockCorrection loadForUpdate(Long id) {
        return correctionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "EmergencyStockCorrection", "resourceId", id)
            ));
    }

    private void requireStatus(
        EmergencyStockCorrection correction,
        EmergencyCorrectionStatus required
    ) {
        if (correction.getStatus() != required) {
            throw invalidStatus(correction, required.name());
        }
    }

    private BusinessException invalidStatus(
        EmergencyStockCorrection correction,
        String required
    ) {
        return new BusinessException(
            ErrorKeys.OPERATION_NOT_ALLOWED,
            Map.of(
                "operation", "EmergencyStockCorrection",
                "currentStatus", correction.getStatus().name(),
                "requiredStatus", required
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

    private int requireNonNegative(Integer value, String field) {
        if (value == null || value < 0) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
        }
        return value;
    }

    private String requireText(String value, String parameter) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.PARAMETER_REQUIRED, Map.of("parameter", parameter));
        }
        return value.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_KEY_REQUIRED);
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "Idempotency-Key", "maxLength", 120)
            );
        }
        return normalized;
    }

    private String normalizeOperatorName(String operatorName, Long operatorId) {
        return operatorName == null || operatorName.isBlank()
            ? "User-" + operatorId
            : operatorName.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(
                ErrorKeys.INTERNAL_SERVER_ERROR,
                Map.of("message", "Unable to serialize idempotency result")
            );
        }
    }

    private String nextCorrectionNo() {
        String prefix = "ESC" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i <= 9999; i++) {
            String number = prefix + String.format("%04d", i);
            if (!correctionRepository.existsByCorrectionNo(number)) {
                return number;
            }
        }
        throw new BusinessException(
            ErrorKeys.INTERNAL_SERVER_ERROR,
            Map.of("message", "Unable to allocate correction number")
        );
    }

    private EmergencyStockCorrectionResponse toResponse(EmergencyStockCorrection correction) {
        ProductSku product = correction.getProductSkuId() == null
            ? null
            : productSkuRepository.findById(correction.getProductSkuId()).orElse(null);
        Location location = correction.getLocationId() == null
            ? null
            : locationRepository.findById(correction.getLocationId()).orElse(null);
        Warehouse warehouse = correction.getWarehouseId() == null
            ? null
            : warehouseRepository.findById(correction.getWarehouseId()).orElse(null);
        return toResponse(correction, product, location, warehouse);
    }

    private EmergencyStockCorrectionResponse toResponse(
        EmergencyStockCorrection correction,
        ProductSku product,
        Location location,
        Warehouse warehouse
    ) {
        return EmergencyStockCorrectionResponse.builder()
            .id(correction.getId())
            .correctionNo(correction.getCorrectionNo())
            .productSkuId(correction.getProductSkuId())
            .productSkuCode(product == null ? null : product.getSkuCode())
            .productSkuName(product == null ? null : product.getName())
            .warehouseId(correction.getWarehouseId())
            .warehouseCode(warehouse == null ? null : warehouse.getCode())
            .warehouseName(warehouse == null ? null : warehouse.getName())
            .locationId(correction.getLocationId())
            .locationCode(location == null ? null : location.getLocationCode())
            .inventoryBatchId(correction.getInventoryBatchId())
            .batchCode(correction.getBatchCode())
            .productionDate(correction.getProductionDate())
            .expiryDate(correction.getExpiryDate())
            .systemQty(correction.getSystemQty())
            .countedQty(correction.getCountedQty())
            .adjustmentQty(correction.getAdjustmentQty())
            .reasonCode(correction.getReasonCode())
            .reasonDetail(correction.getReasonDetail())
            .evidenceUrl(correction.getEvidenceUrl())
            .relatedSalesOrderId(correction.getRelatedSalesOrderId())
            .createdBy(correction.getCreatedBy())
            .approvalModeSnapshot(correction.getApprovalModeSnapshot())
            .status(correction.getStatus())
            .submittedBy(correction.getSubmittedBy())
            .submittedAt(correction.getSubmittedAt())
            .reviewedBy(correction.getReviewedBy())
            .reviewedAt(correction.getReviewedAt())
            .approvedBy(correction.getApprovedBy())
            .approvedAt(correction.getApprovedAt())
            .appliedBy(correction.getAppliedBy())
            .appliedAt(correction.getAppliedAt())
            .appliedQuantityBefore(correction.getAppliedQuantityBefore())
            .appliedQuantityAfter(correction.getAppliedQuantityAfter())
            .postingId(correction.getPostingId())
            .rejectedBy(correction.getRejectedBy())
            .rejectedAt(correction.getRejectedAt())
            .rejectionComment(correction.getRejectionComment())
            .submissionComment(correction.getSubmissionComment())
            .reviewComment(correction.getReviewComment())
            .approvalComment(correction.getApprovalComment())
            .createdAt(correction.getCreatedAt())
            .updatedAt(correction.getUpdatedAt())
            .build();
    }
}
