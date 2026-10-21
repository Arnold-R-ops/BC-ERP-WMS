package com.wms.system.service.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inventoryquality.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import com.wms.system.service.transfer.TransferAllocationCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryQualityActionService {
    static final String HOLD_OPERATION = "INVENTORY_QUALITY_HOLD";
    static final String RELEASE_OPERATION = "INVENTORY_QUALITY_RELEASE";
    private static final Set<String> HOLD_REASON_CODES = Set.of(
        "QUALITY_RISK",
        "RECALL_NOTICE",
        "TRACEABILITY_EXCEPTION",
        "STORAGE_CONDITION_EXCEPTION",
        "MANUAL_CONTROL"
    );
    private static final Set<String> RELEASE_REASON_CODES = Set.of(
        "RISK_CLEARED",
        "RECALL_CLOSED",
        "VERIFICATION_PASSED",
        "FALSE_ALARM_CORRECTION"
    );

    private final InventoryBatchRepository batchRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final InventoryQualityActionRepository actionRepository;
    private final InventoryQualityActionLineRepository lineRepository;
    private final InventoryQualityReservationMoveRepository reservationMoveRepository;
    private final InventoryQualityStateService qualityStateService;
    private final InventoryQualityPolicyService policyService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final TransferAllocationCoordinator transferAllocationCoordinator;

    @Transactional(readOnly = true)
    public List<InventoryQualityCandidateResponse> candidates(
        String search,
        Set<Long> allowedWarehouseIds
    ) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return batchRepository.findQualityCandidates(normalized).stream()
            .filter(batch -> warehouseAllowed(batch, allowedWarehouseIds))
            .limit(100)
            .map(this::toCandidate)
            .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> warehouseIdsForBatches(List<InventoryQualityQuantityRequest> lines) {
        Map<Long, Integer> requested = normalizeQuantities(lines);
        List<InventoryBatch> batches = batchRepository.findAllById(requested.keySet());
        requireAllBatches(requested, batches);
        return batches.stream().map(this::warehouseId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<Long> warehouseIdsForAction(Long actionId) {
        return requireAction(actionId).getLines().stream()
            .map(InventoryQualityActionLine::getWarehouseId)
            .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public InventoryQualityHoldPreviewResponse previewHold(
        InventoryQualityHoldRequest request,
        Set<Long> allowedWarehouseIds
    ) {
        HoldPlan plan = buildHoldPlan(request, allowedWarehouseIds, false);
        return toPreview(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryQualityActionResponse hold(
        InventoryQualityHoldRequest request,
        String idempotencyKey,
        Long actorId,
        String actorName,
        Set<Long> allowedWarehouseIds
    ) {
        ValidatedHold validated = validateHold(request);
        String body = serialize(validated);
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, HOLD_OPERATION, body);
        InventoryQualityActionResponse replay = replay(claim, idempotencyKey, fingerprint, HOLD_OPERATION);
        if (replay != null) return replay;

        transferAllocationCoordinator.evacuate(
            request.lines().stream().map(InventoryQualityQuantityRequest::batchId).toList(),
            "INVENTORY_QUALITY_HOLD", actorId, actorName);
        HoldPlan plan = buildHoldPlan(request, allowedWarehouseIds, true);
        if (plan.totalShortage() > 0) {
            throw new BusinessException(ErrorKeys.INVENTORY_QUALITY_REALLOCATION_SHORTAGE,
                Map.of("shortageQuantity", plan.totalShortage()));
        }
        if (plan.totalReallocation() > 0 && !request.confirmReservationReallocation()) {
            throw new BusinessException(ErrorKeys.INVENTORY_QUALITY_RESERVED_CONFLICT,
                Map.of("reservationQuantityToReallocate", plan.totalReallocation(),
                    "confirmationRequired", true));
        }

        InventoryQualityAction action = saveAction(
            InventoryQualityActionType.HOLD,
            request.holdType(),
            InventoryQualityActionStatus.COMPLETED,
            null,
            request.reasonCode(), request.reasonDetail(),
            request.recallNoticeNumber(), request.externalReference(),
            actorId, actorName, idempotencyKey, fingerprint
        );

        int ordinal = 0;
        for (HoldPlanLine planned : plan.lines()) {
            InventoryQualityActionLine line = saveLine(
                action, planned.source(), planned.holdQuantity(),
                planned.reallocationQuantity(), InventoryQualityStatus.AVAILABLE,
                request.holdType().targetStatus()
            );
            reallocate(planned, line, idempotencyKey);
            InventoryBatch result = applySplitAndTransition(
                planned.source(), planned.holdQuantity(), request.holdType().targetStatus(),
                action, line, ++ordinal, actorId, actorName
            );
            line.setResultBatchId(result.getId());
            line.setResultBatchCode(result.getBatchCode());
            lineRepository.save(line);
        }
        actionRepository.saveAndFlush(action);
        idempotencyService.markSucceeded(claim.request(), 201, action.getId().toString());
        return toResponse(actionRepository.findById(action.getId()).orElseThrow());
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryQualityActionResponse release(
        InventoryQualityReleaseRequest request,
        String idempotencyKey,
        Long actorId,
        String actorName
    ) {
        InventoryQualityReleaseMode mode = policyService.releaseMode();
        ValidatedRelease validated = validateRelease(request, mode);
        String body = serialize(validated);
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, RELEASE_OPERATION, body);
        InventoryQualityActionResponse replay = replay(claim, idempotencyKey, fingerprint, RELEASE_OPERATION);
        if (replay != null) return replay;

        List<ReleasePlanLine> plan = buildReleasePlan(request.lines(), true, true);
        InventoryQualityActionStatus status = mode == InventoryQualityReleaseMode.APPROVAL_REQUIRED
            ? InventoryQualityActionStatus.PENDING_REVIEW
            : InventoryQualityActionStatus.COMPLETED;
        InventoryQualityAction action = saveAction(
            InventoryQualityActionType.RELEASE, null, status, mode,
            request.reasonCode(), request.reasonDetail(), null, request.externalReference(),
            actorId, actorName, idempotencyKey, fingerprint
        );

        int ordinal = 0;
        for (ReleasePlanLine planned : plan) {
            InventoryQualityActionLine line = saveLine(
                action, planned.source(), planned.quantity(), 0,
                planned.source().getQualityStatus(), InventoryQualityStatus.AVAILABLE
            );
            if (status == InventoryQualityActionStatus.COMPLETED) {
                InventoryBatch result = applySplitAndTransition(
                    planned.source(), planned.quantity(), InventoryQualityStatus.AVAILABLE,
                    action, line, ++ordinal, actorId, actorName
                );
                line.setResultBatchId(result.getId());
                line.setResultBatchCode(result.getBatchCode());
                lineRepository.save(line);
            }
        }
        actionRepository.saveAndFlush(action);
        idempotencyService.markSucceeded(claim.request(), 201, action.getId().toString());
        return toResponse(actionRepository.findById(action.getId()).orElseThrow());
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryQualityActionResponse approve(
        Long id, String comment, Long actorId, String actorName
    ) {
        InventoryQualityAction action = requirePendingForUpdate(id);
        requireReviewerSeparation(action, actorId);
        if (action.getReleaseModeSnapshot() != InventoryQualityReleaseMode.APPROVAL_REQUIRED) {
            throw invalid("RELEASE_APPROVAL_NOT_REQUIRED", id);
        }
        List<InventoryQualityQuantityRequest> requested = action.getLines().stream()
            .map(line -> new InventoryQualityQuantityRequest(line.getSourceBatchId(), line.getQuantity()))
            .toList();
        Map<Long, InventoryQualityActionLine> persistedLines = action.getLines().stream()
            .collect(Collectors.toMap(InventoryQualityActionLine::getSourceBatchId, Function.identity()));
        List<ReleasePlanLine> plan = buildReleasePlan(requested, true, false);
        int ordinal = 0;
        for (ReleasePlanLine planned : plan) {
            InventoryQualityActionLine line = persistedLines.get(planned.source().getId());
            InventoryBatch result = applySplitAndTransition(
                planned.source(), planned.quantity(), InventoryQualityStatus.AVAILABLE,
                action, line, ++ordinal, actorId, actorName
            );
            line.setResultBatchId(result.getId());
            line.setResultBatchCode(result.getBatchCode());
            lineRepository.save(line);
        }
        completeReview(action, InventoryQualityActionStatus.APPROVED, comment, actorId, actorName);
        return toResponse(actionRepository.saveAndFlush(action));
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryQualityActionResponse reject(
        Long id, String comment, Long actorId, String actorName
    ) {
        InventoryQualityAction action = requirePendingForUpdate(id);
        requireReviewerSeparation(action, actorId);
        completeReview(action, InventoryQualityActionStatus.REJECTED, comment, actorId, actorName);
        return toResponse(actionRepository.saveAndFlush(action));
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryQualityActionResponse withdraw(
        Long id, String comment, Long actorId, String actorName
    ) {
        InventoryQualityAction action = requirePendingForUpdate(id);
        if (!Objects.equals(action.getRequestedBy(), actorId)) {
            throw invalid("ONLY_REQUESTER_CAN_WITHDRAW", id);
        }
        completeReview(action, InventoryQualityActionStatus.WITHDRAWN, comment, actorId, actorName);
        return toResponse(actionRepository.saveAndFlush(action));
    }

    @Transactional(readOnly = true)
    public List<InventoryQualityActionResponse> list(
        InventoryQualityActionType type,
        InventoryQualityActionStatus status
    ) {
        List<InventoryQualityAction> actions = status != null
            ? actionRepository.findByStatusOrderByIdDesc(status)
            : type != null
                ? actionRepository.findByActionTypeOrderByIdDesc(type)
                : actionRepository.findAllByOrderByIdDesc();
        return actions.stream()
            .filter(action -> type == null || action.getActionType() == type)
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public InventoryQualityActionResponse get(Long id) {
        return toResponse(requireAction(id));
    }

    private HoldPlan buildHoldPlan(
        InventoryQualityHoldRequest request,
        Set<Long> allowedWarehouseIds,
        boolean lock
    ) {
        validateHold(request);
        Map<Long, Integer> requested = normalizeQuantities(request.lines());
        List<Long> sourceIds = requested.keySet().stream().sorted().toList();
        List<InventoryBatch> sources = lock
            ? batchRepository.findAllByIdForPosting(sourceIds)
            : batchRepository.findAllById(sourceIds);
        requireAllBatches(requested, sources);
        sources.forEach(source -> {
            if (!warehouseAllowed(source, allowedWarehouseIds)) {
                throw new BusinessException(ErrorKeys.WAREHOUSE_SCOPE_DENIED,
                    Map.of("warehouseId", warehouseId(source), "batchId", source.getId()));
            }
            int quantity = requested.get(source.getId());
            if (!source.isSaleable() || quantity > source.getQuantity()) {
                throw invalid("SOURCE_BATCH_NOT_HOLDABLE", source.getId());
            }
        });

        List<InventoryReservation> reservations = lock
            ? reservationRepository.findOpenByBatchIdsForUpdate(sourceIds)
            : sourceIds.stream().flatMap(id -> reservationRepository.findByInventoryBatchId(id).stream()).toList();
        Map<Long, List<InventoryReservation>> byBatch = reservations.stream()
            .filter(InventoryReservation::hasOpenQuantity)
            .collect(Collectors.groupingBy(InventoryReservation::getInventoryBatchId));
        Map<Long, Integer> transferReserved = lock ? Map.of()
            : transferAllocationCoordinator.openReservedQuantities(sourceIds);
        Set<Long> excluded = new LinkedHashSet<>(sourceIds);
        Map<Long, Integer> remainingCandidateCapacity = new HashMap<>();
        List<HoldPlanLine> planned = new ArrayList<>();

        for (InventoryBatch source : sources.stream().sorted(Comparator.comparing(InventoryBatch::getId)).toList()) {
            int holdQuantity = requested.get(source.getId());
            int authoritativeReserved = Math.max(0, value(source.getReservedQuantity())
                - transferReserved.getOrDefault(source.getId(), 0));
            int reallocationNeeded = Math.max(0, holdQuantity - (source.getQuantity() - authoritativeReserved));
            List<MovableReservation> movable = new ArrayList<>();
            int movableQty = 0;
            for (InventoryReservation reservation : byBatch.getOrDefault(source.getId(), List.of())) {
                OutboundTask task = lock
                    ? outboundTaskRepository.findByReservationIdForUpdate(reservation.getId()).orElse(null)
                    : outboundTaskRepository.findByReservationId(reservation.getId()).orElse(null);
                boolean purePending = task != null
                    && task.getStatus() == OutboundTaskStatus.PENDING
                    && value(task.getActualQty()) == 0
                    && value(reservation.getConsumedQty()) == 0
                    && value(reservation.getReleasedQty()) == 0
                    && Objects.equals(task.getPlanQty(), reservation.getReservedQty());
                if (purePending) {
                    movable.add(new MovableReservation(reservation, task));
                    movableQty += reservation.getOpenQty();
                }
            }

            List<InventoryBatch> candidates = lock
                ? batchRepository.findSaleableForQualityReallocationForUpdate(source.getProductSku().getId(), excluded)
                : batchRepository.findSaleableByProductSkuIdOrderByExpiryDateAsc(source.getProductSku().getId()).stream()
                    .filter(candidate -> !excluded.contains(candidate.getId())).toList();
            candidates = candidates.stream()
                .filter(candidate -> warehouseAllowed(candidate, allowedWarehouseIds))
                .toList();
            int alternateTotal = candidates.stream()
                .mapToInt(candidate -> remainingCandidateCapacity.computeIfAbsent(
                    candidate.getId(), ignored -> candidate.getAvailableQuantity()))
                .sum();

            int moveTarget = Math.min(reallocationNeeded, movableQty);
            int remaining = moveTarget;
            List<DestinationAllocation> destinations = new ArrayList<>();
            for (InventoryBatch candidate : candidates) {
                if (remaining == 0) break;
                int capacity = remainingCandidateCapacity.getOrDefault(candidate.getId(), 0);
                int allocated = Math.min(remaining, capacity);
                if (allocated > 0) {
                    destinations.add(new DestinationAllocation(candidate, allocated));
                    remainingCandidateCapacity.put(candidate.getId(), capacity - allocated);
                    remaining -= allocated;
                }
            }
            int executableMove = moveTarget - remaining;
            int shortage = reallocationNeeded - executableMove;
            List<InventoryQualityAffectedOrderResponse> affected = affectedOrders(movable, reallocationNeeded);
            planned.add(new HoldPlanLine(
                source, holdQuantity, reallocationNeeded, alternateTotal, shortage,
                movable, destinations, affected
            ));
        }
        return new HoldPlan(planned);
    }

    private List<ReleasePlanLine> buildReleasePlan(
        List<InventoryQualityQuantityRequest> lines,
        boolean lock,
        boolean rejectExistingPending
    ) {
        Map<Long, Integer> requested = normalizeQuantities(lines);
        List<Long> ids = requested.keySet().stream().sorted().toList();
        List<InventoryBatch> batches = lock
            ? batchRepository.findAllByIdForPosting(ids)
            : batchRepository.findAllById(ids);
        requireAllBatches(requested, batches);
        List<ReleasePlanLine> result = new ArrayList<>();
        for (InventoryBatch batch : batches) {
            int quantity = requested.get(batch.getId());
            if (!batch.isActive() || batch.getQuantity() == null || batch.getQuantity() <= 0
                || quantity > batch.getQuantity()
                || (batch.getQualityStatus() != InventoryQualityStatus.QUARANTINED
                    && batch.getQualityStatus() != InventoryQualityStatus.RECALL_HOLD)
                || batch.isExpired()
                || value(batch.getReservedQuantity()) != 0) {
                throw invalid("SOURCE_BATCH_NOT_RELEASABLE", batch.getId());
            }
            if (rejectExistingPending && actionRepository.existsPendingReleaseForBatch(batch.getId())) {
                throw new BusinessException(ErrorKeys.INVENTORY_QUALITY_RELEASE_PENDING,
                    Map.of("batchId", batch.getId()));
            }
            result.add(new ReleasePlanLine(batch, quantity));
        }
        return result;
    }

    private void reallocate(
        HoldPlanLine planned,
        InventoryQualityActionLine actionLine,
        String actionIdempotencyKey
    ) {
        if (planned.reallocationQuantity() == 0) return;
        Deque<DestinationAllocationState> destinations = planned.destinations().stream()
            .map(item -> new DestinationAllocationState(item.batch(), item.quantity()))
            .collect(Collectors.toCollection(ArrayDeque::new));
        int remaining = planned.reallocationQuantity();

        for (MovableReservation movable : planned.movableReservations()) {
            if (remaining == 0) break;
            InventoryReservation reservation = movable.reservation();
            OutboundTask task = movable.task();
            while (remaining > 0 && reservation.getReservedQty() > 0) {
                DestinationAllocationState destination = destinations.peekFirst();
                if (destination == null) {
                    throw new IllegalStateException("Quality reallocation plan exhausted unexpectedly");
                }
                int chunk = Math.min(Math.min(remaining, reservation.getReservedQty()), destination.remaining);
                InventoryBatch target = destination.batch;
                planned.source().releaseReservedQuantity(chunk);
                target.reserveQuantity(chunk);
                batchRepository.save(target);

                InventoryReservation movedReservation;
                OutboundTask movedTask;
                if (chunk == reservation.getReservedQty()) {
                    reservation.setInventoryBatchId(target.getId());
                    reservation.setLocationId(target.getLocation().getId());
                    movedReservation = reservationRepository.save(reservation);
                    task.setAssignedBatchId(target.getId());
                    task.setLocationId(target.getLocation().getId());
                    task.setRemark("Reallocated by inventory quality hold " + actionLine.getAction().getActionNumber());
                    movedTask = outboundTaskRepository.save(task);
                } else {
                    reservation.setReservedQty(reservation.getReservedQty() - chunk);
                    task.setPlanQty(task.getPlanQty() - chunk);
                    reservationRepository.save(reservation);
                    outboundTaskRepository.save(task);
                    movedReservation = reservationRepository.save(InventoryReservation.builder()
                        .salesOrderId(reservation.getSalesOrderId())
                        .salesOrderItemId(reservation.getSalesOrderItemId())
                        .inventoryBatchId(target.getId())
                        .productSkuId(reservation.getProductSkuId())
                        .locationId(target.getLocation().getId())
                        .reservedQty(chunk)
                        .sourceType(reservation.getSourceType())
                        .idempotencyKey(shortKey(actionIdempotencyKey, actionLine.getId(), target.getId(), remaining))
                        .createdBy(reservation.getCreatedBy())
                        .build());
                    movedTask = outboundTaskRepository.save(OutboundTask.builder()
                        .salesOrderId(task.getSalesOrderId())
                        .salesOrderItemId(task.getSalesOrderItemId())
                        .assignedBatchId(target.getId())
                        .reservationId(movedReservation.getId())
                        .locationId(target.getLocation().getId())
                        .planQty(chunk)
                        .actualQty(0)
                        .status(OutboundTaskStatus.PENDING)
                        .remark("Split reallocation by inventory quality hold " + actionLine.getAction().getActionNumber())
                        .build());
                }
                reservationMoveRepository.save(InventoryQualityReservationMove.builder()
                    .actionLineId(actionLine.getId())
                    .reservationId(movedReservation.getId())
                    .outboundTaskId(movedTask.getId())
                    .salesOrderId(reservation.getSalesOrderId())
                    .salesOrderItemId(reservation.getSalesOrderItemId())
                    .fromBatchId(planned.source().getId())
                    .toBatchId(target.getId())
                    .fromLocationId(planned.source().getLocation().getId())
                    .toLocationId(target.getLocation().getId())
                    .quantity(chunk)
                    .build());
                remaining -= chunk;
                destination.remaining -= chunk;
                if (destination.remaining == 0) destinations.removeFirst();
            }
        }
        if (remaining != 0) {
            throw new IllegalStateException("Quality reservation reallocation was not fully applied");
        }
        batchRepository.save(planned.source());
    }

    private InventoryBatch applySplitAndTransition(
        InventoryBatch source,
        int quantity,
        InventoryQualityStatus targetStatus,
        InventoryQualityAction action,
        InventoryQualityActionLine line,
        int ordinal,
        Long actorId,
        String actorName
    ) {
        InventoryBatch result;
        if (quantity == source.getQuantity()) {
            result = source;
        } else {
            if (source.getStocktakeTaskId() != null) throw new BusinessException("PACKAGING_STOCKTAKE_LOCKED");
            // A quality segment specifies units, not which physical opened box supplied them.
            source.setPackaging(null);
            source.setQuantity(source.getQuantity() - quantity);
            batchRepository.saveAndFlush(source);
            String segmentNo = segmentNumber(action, ordinal);
            result = batchRepository.saveAndFlush(InventoryBatch.builder()
                .batchCode(segmentNo)
                .locationCode(source.getLocationCode())
                .purchaseOrderItem(source.getPurchaseOrderItem())
                .productSku(source.getProductSku())
                .location(source.getLocation())
                .quantity(quantity)
                .reservedQuantity(0)
                .qualityStatus(source.getQualityStatus())
                .qualityParentBatchId(source.getId())
                .qualitySegmentNo(segmentNo)
                .initialQuantity(quantity)
                .expiryDate(source.getExpiryDate())
                .productionDate(source.getProductionDate())
                .externalBatchCode(source.getExternalBatchCode())
                .entryDate(source.getEntryDate())
                .active(true)
                .remark("Quality segment created by " + action.getActionNumber())
                .build());
        }
        qualityStateService.transition(new InventoryQualityTransitionCommand(
            result.getId(), targetStatus, "INVENTORY_QUALITY_ACTION",
            action.getActionNumber(), action.getReasonCode(), action.getReasonDetail(),
            actorId, normalizeActor(actorName), "qstate-" + action.getId() + "-" + line.getId()
        ));
        return result;
    }

    private InventoryQualityAction saveAction(
        InventoryQualityActionType type,
        InventoryQualityHoldType holdType,
        InventoryQualityActionStatus status,
        InventoryQualityReleaseMode releaseMode,
        String reasonCode,
        String reasonDetail,
        String recallNoticeNumber,
        String externalReference,
        Long actorId,
        String actorName,
        String idempotencyKey,
        String fingerprint
    ) {
        return actionRepository.saveAndFlush(InventoryQualityAction.builder()
            .actionNumber(actionNumber(type))
            .actionType(type)
            .holdType(holdType)
            .status(status)
            .releaseModeSnapshot(releaseMode)
            .reasonCode(requiredText(reasonCode, "reasonCode", 80))
            .reasonDetail(optionalText(reasonDetail, "reasonDetail", 500))
            .recallNoticeNumber(optionalText(recallNoticeNumber, "recallNoticeNumber", 120))
            .externalReference(optionalText(externalReference, "externalReference", 120))
            .requestedBy(actorId)
            .requestedByName(normalizeActor(actorName))
            .requestedAt(LocalDateTime.now())
            .idempotencyKey(requiredText(idempotencyKey, "idempotencyKey", 120))
            .requestFingerprint(fingerprint)
            .build());
    }

    private InventoryQualityActionLine saveLine(
        InventoryQualityAction action,
        InventoryBatch source,
        int quantity,
        int reallocated,
        InventoryQualityStatus from,
        InventoryQualityStatus to
    ) {
        InventoryQualityActionLine line = lineRepository.saveAndFlush(
            InventoryQualityActionLine.builder()
                .action(action)
                .sourceBatchId(source.getId())
                .productSkuId(source.getProductSku().getId())
                .productSkuCode(source.getProductSku().getSkuCode())
                .productName(source.getProductSku().getName())
                .sourceBatchCode(source.getBatchCode())
                .warehouseId(warehouseId(source))
                .warehouseName(source.getLocation().getWarehouse().getName())
                .locationId(source.getLocation().getId())
                .locationCode(source.getLocationCode())
                .quantity(quantity)
                .reallocatedReservedQuantity(reallocated)
                .fromStatus(from)
                .toStatus(to)
                .build()
        );
        action.getLines().add(line);
        return line;
    }

    private InventoryQualityActionResponse replay(
        IdempotencyClaim claim,
        String key,
        String fingerprint,
        String operation
    ) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return null;
        if (claim.decision() == IdempotencyClaimDecision.IN_PROGRESS) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", operation));
        }
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_FAILED) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", operation, "reason", "PREVIOUS_ATTEMPT_FAILED"));
        }
        InventoryQualityAction action = actionRepository.findByIdempotencyKey(key)
            .orElseThrow(() -> new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", operation, "reason", "ACTION_NOT_FOUND")));
        if (!Objects.equals(action.getRequestFingerprint(), fingerprint)
            || !Objects.equals(claim.request().getResponseBody(), action.getId().toString())) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", operation, "reason", "ACTION_FINGERPRINT_MISMATCH"));
        }
        return toResponse(action);
    }

    private List<InventoryQualityAffectedOrderResponse> affectedOrders(
        List<MovableReservation> reservations,
        int requested
    ) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        Map<String, InventoryReservation> examples = new LinkedHashMap<>();
        int remaining = requested;
        for (MovableReservation movable : reservations) {
            if (remaining == 0) break;
            InventoryReservation reservation = movable.reservation();
            int quantity = Math.min(remaining, reservation.getOpenQty());
            String key = reservation.getSalesOrderId() + ":" + reservation.getSalesOrderItemId();
            quantities.merge(key, quantity, Integer::sum);
            examples.putIfAbsent(key, reservation);
            remaining -= quantity;
        }
        return quantities.entrySet().stream().map(entry -> {
            InventoryReservation sample = examples.get(entry.getKey());
            return new InventoryQualityAffectedOrderResponse(
                sample.getSalesOrderId(), sample.getSalesOrderItemId(), entry.getValue());
        }).toList();
    }

    private InventoryQualityHoldPreviewResponse toPreview(HoldPlan plan) {
        List<InventoryQualityHoldPreviewLineResponse> lines = plan.lines().stream()
            .map(line -> new InventoryQualityHoldPreviewLineResponse(
                line.source().getId(), line.source().getBatchCode(),
                line.source().getProductSku().getSkuCode(), line.source().getProductSku().getName(),
                warehouseId(line.source()), line.source().getLocation().getWarehouse().getName(),
                line.source().getLocationCode(), line.source().getQuantity(),
                value(line.source().getReservedQuantity()), line.holdQuantity(),
                line.reallocationQuantity(), line.alternateSaleableQuantity(), line.shortageQuantity(),
                line.affectedOrders()
            )).toList();
        return new InventoryQualityHoldPreviewResponse(
            plan.totalShortage() == 0,
            plan.totalReallocation() > 0,
            plan.totalReallocation(),
            plan.totalShortage(),
            lines
        );
    }

    private InventoryQualityCandidateResponse toCandidate(InventoryBatch batch) {
        return new InventoryQualityCandidateResponse(
            batch.getId(), batch.getBatchCode(), batch.getExternalBatchCode(),
            batch.getQualitySegmentNo(), batch.getQualityParentBatchId(),
            batch.getProductSku().getId(), batch.getProductSku().getSkuCode(),
            batch.getProductSku().getName(), warehouseId(batch),
            batch.getLocation().getWarehouse().getName(), batch.getLocation().getId(),
            batch.getLocationCode(), batch.getQuantity(), value(batch.getReservedQuantity()),
            batch.getAvailableQuantity(), batch.getQualityStatus(),
            batch.getProductionDate(), batch.getExpiryDate()
        );
    }

    private InventoryQualityActionResponse toResponse(InventoryQualityAction action) {
        List<InventoryQualityActionLineResponse> lines = action.getLines().stream()
            .map(line -> new InventoryQualityActionLineResponse(
                line.getId(), line.getSourceBatchId(), line.getResultBatchId(),
                line.getSourceBatchCode(), line.getResultBatchCode(), line.getProductSkuId(),
                line.getProductSkuCode(), line.getProductName(), line.getWarehouseId(),
                line.getWarehouseName(), line.getLocationId(), line.getLocationCode(),
                line.getQuantity(), line.getReallocatedReservedQuantity(),
                line.getFromStatus(), line.getToStatus()
            )).toList();
        return new InventoryQualityActionResponse(
            action.getId(), action.getVersion(), action.getActionNumber(), action.getActionType(),
            action.getHoldType(), action.getStatus(), action.getReleaseModeSnapshot(),
            action.getReasonCode(), action.getReasonDetail(), action.getRecallNoticeNumber(),
            action.getExternalReference(), action.getRequestedBy(), action.getRequestedByName(),
            action.getRequestedAt(), action.getReviewedBy(), action.getReviewedByName(),
            action.getReviewedAt(), action.getReviewComment(), lines
        );
    }

    private InventoryQualityAction requireAction(Long id) {
        return actionRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.INVENTORY_QUALITY_ACTION_NOT_FOUND,
                Map.of("actionId", id)));
    }

    private InventoryQualityAction requirePendingForUpdate(Long id) {
        InventoryQualityAction action = actionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.INVENTORY_QUALITY_ACTION_NOT_FOUND,
                Map.of("actionId", id)));
        if (action.getActionType() != InventoryQualityActionType.RELEASE
            || action.getStatus() != InventoryQualityActionStatus.PENDING_REVIEW) {
            throw invalid("ACTION_NOT_PENDING_RELEASE", id);
        }
        return action;
    }

    private void completeReview(
        InventoryQualityAction action,
        InventoryQualityActionStatus status,
        String comment,
        Long actorId,
        String actorName
    ) {
        action.setStatus(status);
        action.setReviewedBy(actorId);
        action.setReviewedByName(normalizeActor(actorName));
        action.setReviewedAt(LocalDateTime.now());
        action.setReviewComment(requiredText(comment, "comment", 500));
    }

    private void requireReviewerSeparation(InventoryQualityAction action, Long actorId) {
        if (Objects.equals(action.getRequestedBy(), actorId)) {
            throw invalid("REVIEWER_IS_REQUESTER", action.getId());
        }
    }

    private ValidatedHold validateHold(InventoryQualityHoldRequest request) {
        if (request == null || request.holdType() == null) throw validation("holdType");
        Map<Long, Integer> lines = normalizeQuantities(request.lines());
        return new ValidatedHold(
            request.holdType(), lines, standardReasonCode(request.reasonCode(), HOLD_REASON_CODES),
            optionalText(request.reasonDetail(), "reasonDetail", 500),
            optionalText(request.recallNoticeNumber(), "recallNoticeNumber", 120),
            optionalText(request.externalReference(), "externalReference", 120),
            request.confirmReservationReallocation()
        );
    }

    private ValidatedRelease validateRelease(
        InventoryQualityReleaseRequest request,
        InventoryQualityReleaseMode mode
    ) {
        if (request == null) throw validation("request");
        return new ValidatedRelease(
            normalizeQuantities(request.lines()),
            standardReasonCode(request.reasonCode(), RELEASE_REASON_CODES),
            optionalText(request.reasonDetail(), "reasonDetail", 500),
            optionalText(request.externalReference(), "externalReference", 120),
            mode
        );
    }

    private Map<Long, Integer> normalizeQuantities(List<InventoryQualityQuantityRequest> lines) {
        if (lines == null || lines.isEmpty()) throw validation("lines");
        LinkedHashMap<Long, Integer> normalized = new LinkedHashMap<>();
        for (InventoryQualityQuantityRequest line : lines) {
            if (line == null || line.batchId() == null || line.batchId() <= 0
                || line.quantity() == null || line.quantity() <= 0) {
                throw validation("lines");
            }
            normalized.merge(line.batchId(), line.quantity(), Math::addExact);
        }
        return normalized;
    }

    private void requireAllBatches(Map<Long, Integer> requested, List<InventoryBatch> batches) {
        if (batches.size() != requested.size()) {
            Set<Long> found = batches.stream().map(InventoryBatch::getId).collect(Collectors.toSet());
            Long missing = requested.keySet().stream().filter(id -> !found.contains(id)).findFirst().orElse(0L);
            throw new BusinessException(ErrorKeys.BATCH_NOT_FOUND, Map.of("batchId", missing));
        }
    }

    private boolean warehouseAllowed(InventoryBatch batch, Set<Long> allowedWarehouseIds) {
        return allowedWarehouseIds == null || allowedWarehouseIds.contains(warehouseId(batch));
    }

    private Long warehouseId(InventoryBatch batch) {
        if (batch.getLocation() == null || batch.getLocation().getWarehouse() == null) {
            throw invalid("BATCH_LOCATION_OR_WAREHOUSE_MISSING", batch.getId());
        }
        return batch.getLocation().getWarehouse().getId();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String actionNumber(InventoryQualityActionType type) {
        return (type == InventoryQualityActionType.HOLD ? "IQH" : "IQR")
            + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private String segmentNumber(InventoryQualityAction action, int ordinal) {
        return "IQS-" + action.getId() + "-" + ordinal + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String shortKey(String key, Long lineId, Long batchId, int sequence) {
        return "iqmove-" + sha256(key + ":" + lineId + ":" + batchId + ":" + sequence).substring(0, 40);
    }

    private String normalizeActor(String actorName) {
        return actorName == null || actorName.isBlank() ? "unknown" : actorName.trim();
    }

    private String requiredText(String value, String field, int max) {
        String normalized = optionalText(value, field, max);
        if (normalized == null) throw validation(field);
        return normalized;
    }

    private String standardReasonCode(String value, Set<String> supported) {
        String normalized = requiredText(value, "reasonCode", 80).toUpperCase(Locale.ROOT);
        if (!supported.contains(normalized)) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reasonCode", "reason", "UNSUPPORTED_STANDARD_REASON"));
        }
        return normalized;
    }

    private String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", field, "maxLength", max));
        }
        return normalized;
    }

    private BusinessException validation(String field) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
    }

    private BusinessException invalid(String reason, Object resourceId) {
        return new BusinessException(ErrorKeys.INVENTORY_QUALITY_ACTION_INVALID,
            Map.of("reason", reason, "resourceId", resourceId));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw validation("request");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ValidatedHold(
        InventoryQualityHoldType holdType,
        Map<Long, Integer> lines,
        String reasonCode,
        String reasonDetail,
        String recallNoticeNumber,
        String externalReference,
        boolean confirmReservationReallocation
    ) { }

    private record ValidatedRelease(
        Map<Long, Integer> lines,
        String reasonCode,
        String reasonDetail,
        String externalReference,
        InventoryQualityReleaseMode releaseMode
    ) { }

    private record MovableReservation(InventoryReservation reservation, OutboundTask task) { }
    private record DestinationAllocation(InventoryBatch batch, int quantity) { }
    private static final class DestinationAllocationState {
        private final InventoryBatch batch;
        private int remaining;
        private DestinationAllocationState(InventoryBatch batch, int remaining) {
            this.batch = batch;
            this.remaining = remaining;
        }
    }
    private record HoldPlanLine(
        InventoryBatch source,
        int holdQuantity,
        int reallocationQuantity,
        int alternateSaleableQuantity,
        int shortageQuantity,
        List<MovableReservation> movableReservations,
        List<DestinationAllocation> destinations,
        List<InventoryQualityAffectedOrderResponse> affectedOrders
    ) { }
    private record HoldPlan(List<HoldPlanLine> lines) {
        int totalReallocation() {
            return lines.stream().mapToInt(HoldPlanLine::reallocationQuantity).sum();
        }
        int totalShortage() {
            return lines.stream().mapToInt(HoldPlanLine::shortageQuantity).sum();
        }
    }
    private record ReleasePlanLine(InventoryBatch source, int quantity) { }
}
