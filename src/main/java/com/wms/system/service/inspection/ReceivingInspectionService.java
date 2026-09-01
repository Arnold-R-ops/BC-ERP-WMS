package com.wms.system.service.inspection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inventoryquality.*;
import com.wms.system.dto.receivinginspection.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.BackorderService;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.*;
import com.wms.system.service.inventory.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceivingInspectionService {
    static final String SUBMIT_OPERATION = "RECEIVING_INSPECTION_SUBMIT";
    static final String REINSPECTION_OPERATION = "RECEIVING_INSPECTION_CORRECTION";
    static final String REVIEW_OPERATION = "RECEIVING_INSPECTION_REVIEW";
    private static final Set<String> REASON_CODES = Set.of(
        "INSPECTION_PASSED", "INSPECTION_PARTIAL", "QUALITY_FAILED",
        "DAMAGE_FOUND", "EXPIRY_FAILED", "REINSPECTION_DECISION", "OTHER"
    );

    private final ReceivingInspectionTaskRepository taskRepository;
    private final ReceivingInspectionTaskLineRepository taskLineRepository;
    private final ReceivingInspectionResultRepository resultRepository;
    private final ReceivingInspectionResultLineRepository resultLineRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryQualityStateService qualityStateService;
    private final InventoryQualityActionService qualityActionService;
    private final ReceivingInspectionPolicyService policyService;
    private final IdempotencyService idempotencyService;
    private final BackorderService backorderService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ReceivingInspectionTaskResponse> list(
        ReceivingInspectionTaskStatus status,
        Set<Long> allowedWarehouseIds
    ) {
        List<ReceivingInspectionTask> tasks = status == null
            ? taskRepository.findAllByOrderByIdDesc()
            : taskRepository.findByStatusOrderByIdDesc(status);
        return tasks.stream()
            .filter(task -> allowedWarehouseIds == null
                || allowedWarehouseIds.contains(task.getWarehouse().getId()))
            .map(this::toTaskResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ReceivingInspectionTaskResponse get(Long id) {
        return toTaskResponse(requireTask(id));
    }

    @Transactional(readOnly = true)
    public Long warehouseIdForTask(Long id) {
        return taskRepository.findWarehouseId(id)
            .orElseThrow(() -> notFound("inspectionTaskId", id));
    }

    @Transactional(readOnly = true)
    public Long warehouseIdForResult(Long id) {
        ReceivingInspectionResult result = resultRepository.findById(id)
            .orElseThrow(() -> notFound("inspectionResultId", id));
        return result.getInspectionTask().getWarehouse().getId();
    }

    @Transactional(readOnly = true)
    public Long warehouseIdForBatch(Long id) {
        InventoryBatch batch = batchRepository.findById(id)
            .orElseThrow(() -> notFound("inventoryBatchId", id));
        return batch.getLocation().getWarehouse().getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceivingInspectionResultResponse submit(
        Long taskId,
        SubmitReceivingInspectionRequest request,
        String idempotencyKey,
        Long actorId,
        String actorName
    ) {
        ValidatedSubmission validated = validateSubmission(taskId, request);
        String body = serialize(validated);
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(
            requiredText(idempotencyKey, "idempotencyKey", 120), SUBMIT_OPERATION, body);
        ReceivingInspectionResultResponse replay = replayResult(
            claim, idempotencyKey, fingerprint, SUBMIT_OPERATION);
        if (replay != null) return replay;

        ReceivingInspectionTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> notFound("inspectionTaskId", taskId));
        if (task.getStatus() == ReceivingInspectionTaskStatus.COMPLETED) {
            throw invalid("TASK_ALREADY_COMPLETED", taskId);
        }
        Map<Long, Integer[]> requested = normalizeLines(request.lines());
        List<ReceivingInspectionTaskLine> lines =
            taskLineRepository.findAllByIdForUpdate(requested.keySet());
        if (lines.size() != requested.size()
            || lines.stream().anyMatch(line -> !line.getInspectionTask().getId().equals(taskId))) {
            throw invalid("TASK_LINE_MISMATCH", taskId);
        }
        int decisionQuantity = requested.values().stream()
            .mapToInt(values -> values[0] + values[1]).sum();
        validateSample(request, decisionQuantity);

        boolean requiresReview = task.getReviewModeSnapshot()
            == ReceivingInspectionReviewMode.REVIEW_REQUIRED
            && requested.values().stream().anyMatch(values -> values[0] > 0);
        ReceivingInspectionResult result = resultRepository.saveAndFlush(
            ReceivingInspectionResult.builder()
                .resultNumber(ReceivingInspectionTaskFactory.number("IQR"))
                .inspectionTask(task)
                .status(requiresReview
                    ? ReceivingInspectionResultStatus.PENDING_REVIEW
                    : ReceivingInspectionResultStatus.COMPLETED)
                .inspectionMethod(request.inspectionMethod())
                .sampleQuantity(request.inspectionMethod() == ReceivingInspectionMethod.SAMPLING
                    ? request.sampleQuantity() : null)
                .defectQuantity(request.defectQuantity())
                .reasonCode(requiredReason(request.reasonCode()))
                .reasonDetail(optionalText(request.reasonDetail(), "reasonDetail", 500))
                .submittedBy(actorId)
                .submittedByName(normalizeActor(actorName))
                .submittedAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey.trim())
                .requestFingerprint(fingerprint)
                .build()
        );

        int ordinal = 0;
        for (ReceivingInspectionTaskLine line : lines.stream()
            .sorted(Comparator.comparing(ReceivingInspectionTaskLine::getId)).toList()) {
            Integer[] quantities = requested.get(line.getId());
            int passed = quantities[0];
            int quarantined = quantities[1];
            int total = passed + quarantined;
            if (total <= 0 || total > line.getPendingQuantity()) {
                throw invalid("INSPECTION_QUANTITY_EXCEEDS_PENDING", line.getId());
            }
            InventoryBatch source = batchRepository.findByIdForPosting(
                line.getSourceBatch().getId()).orElseThrow(
                    () -> notFound("inventoryBatchId", line.getSourceBatch().getId()));
            requirePendingSource(source, total);
            ReceivingInspectionResultLine resultLine = ReceivingInspectionResultLine.builder()
                .inspectionTaskLine(line)
                .sourceBatch(source)
                .passedQuantity(passed)
                .quarantinedQuantity(quarantined)
                .build();
            result.addLine(resultLine);
            if (quarantined > 0) {
                InventoryBatch quarantinedBatch = transitionQuantity(
                    source, quarantined, InventoryQualityStatus.QUARANTINED,
                    result.getResultNumber(), "INSPECTION_FAILED",
                    request.reasonDetail(), actorId, actorName, ++ordinal);
                resultLine.setQuarantinedBatch(quarantinedBatch);
                line.setQuarantinedQuantity(line.getQuarantinedQuantity() + quarantined);
            }
            if (passed > 0) {
                if (requiresReview) {
                    line.setPendingReviewQuantity(line.getPendingReviewQuantity() + passed);
                } else {
                    InventoryBatch passedBatch = transitionQuantity(
                        source, passed, InventoryQualityStatus.AVAILABLE,
                        result.getResultNumber(), "INSPECTION_PASSED",
                        request.reasonDetail(), actorId, actorName, ++ordinal);
                    resultLine.setPassedBatch(passedBatch);
                    line.setPassedQuantity(line.getPassedQuantity() + passed);
                    wakeBackorder(line);
                }
            }
            line.setPendingQuantity(line.getPendingQuantity() - total);
            taskLineRepository.save(line);
        }
        resultRepository.saveAndFlush(result);
        refreshTaskStatus(task);
        taskRepository.saveAndFlush(task);
        idempotencyService.markSucceeded(claim.request(), 201, result.getId().toString());
        return toResultResponse(resultRepository.findById(result.getId()).orElseThrow());
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceivingInspectionResultResponse approve(
        Long resultId,
        String comment,
        String idempotencyKey,
        Long actorId,
        String actorName
    ) {
        return review(resultId, true, comment, idempotencyKey, actorId, actorName);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceivingInspectionResultResponse reject(
        Long resultId,
        String comment,
        String idempotencyKey,
        Long actorId,
        String actorName
    ) {
        return review(resultId, false, comment, idempotencyKey, actorId, actorName);
    }

    private ReceivingInspectionResultResponse review(
        Long resultId,
        boolean approve,
        String comment,
        String idempotencyKey,
        Long actorId,
        String actorName
    ) {
        String operation = REVIEW_OPERATION + ":" + resultId + ":" + (approve ? "APPROVE" : "REJECT");
        String body = serialize(Map.of("resultId", resultId, "approve", approve,
            "comment", requiredText(comment, "comment", 500)));
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, operation, body);
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return toResultResponse(resultRepository.findById(resultId)
                .orElseThrow(() -> notFound("inspectionResultId", resultId)));
        }
        requireAcquired(claim, operation);

        ReceivingInspectionResult result = resultRepository.findByIdForUpdate(resultId)
            .orElseThrow(() -> notFound("inspectionResultId", resultId));
        if (result.getStatus() != ReceivingInspectionResultStatus.PENDING_REVIEW) {
            throw invalid("RESULT_NOT_PENDING_REVIEW", resultId);
        }
        if (Objects.equals(result.getSubmittedBy(), actorId)) {
            throw invalid("SUBMITTER_CANNOT_REVIEW", resultId);
        }
        ReceivingInspectionTask task = taskRepository.findByIdForUpdate(
            result.getInspectionTask().getId()).orElseThrow();
        int ordinal = 0;
        for (ReceivingInspectionResultLine resultLine : result.getLines()) {
            int passed = resultLine.getPassedQuantity();
            if (passed <= 0) continue;
            ReceivingInspectionTaskLine taskLine = resultLine.getInspectionTaskLine();
            if (taskLine.getPendingReviewQuantity() < passed) {
                throw invalid("PENDING_REVIEW_QUANTITY_MISMATCH", taskLine.getId());
            }
            taskLine.setPendingReviewQuantity(taskLine.getPendingReviewQuantity() - passed);
            if (approve) {
                InventoryBatch source = batchRepository.findByIdForPosting(
                    resultLine.getSourceBatch().getId()).orElseThrow();
                requirePendingSource(source, passed);
                InventoryBatch passedBatch = transitionQuantity(
                    source, passed, InventoryQualityStatus.AVAILABLE,
                    result.getResultNumber(), "INSPECTION_REVIEW_APPROVED",
                    comment, actorId, actorName, ++ordinal);
                resultLine.setPassedBatch(passedBatch);
                taskLine.setPassedQuantity(taskLine.getPassedQuantity() + passed);
                wakeBackorder(taskLine);
            } else {
                taskLine.setPendingQuantity(taskLine.getPendingQuantity() + passed);
            }
            taskLineRepository.save(taskLine);
            resultLineRepository.save(resultLine);
        }
        result.setStatus(approve
            ? ReceivingInspectionResultStatus.APPROVED
            : ReceivingInspectionResultStatus.REJECTED);
        result.setReviewedBy(actorId);
        result.setReviewedByName(normalizeActor(actorName));
        result.setReviewedAt(LocalDateTime.now());
        result.setReviewComment(comment.trim());
        resultRepository.saveAndFlush(result);
        refreshTaskStatus(task);
        taskRepository.saveAndFlush(task);
        idempotencyService.markSucceeded(claim.request(), 200, result.getId().toString());
        return toResultResponse(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceivingInspectionTaskResponse createReinspection(
        CreateReceivingReinspectionRequest request,
        String idempotencyKey,
        Long actorId,
        String actorName,
        Set<Long> allowedWarehouseIds
    ) {
        String body = serialize(request);
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(
            idempotencyKey, REINSPECTION_OPERATION, body);
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            Long taskId = Long.valueOf(claim.request().getResponseBody());
            return get(taskId);
        }
        requireAcquired(claim, REINSPECTION_OPERATION);

        ReceivingInspectionResult original = resultRepository.findById(request.originalResultId())
            .orElseThrow(() -> notFound("originalResultId", request.originalResultId()));
        if (original.getStatus() != ReceivingInspectionResultStatus.COMPLETED
            && original.getStatus() != ReceivingInspectionResultStatus.APPROVED) {
            throw invalid("ORIGINAL_RESULT_NOT_EFFECTIVE", original.getId());
        }
        InventoryBatch source = batchRepository.findByIdForPosting(request.inventoryBatchId())
            .orElseThrow(() -> notFound("inventoryBatchId", request.inventoryBatchId()));
        boolean linked = original.getLines().stream().anyMatch(line ->
            line.getPassedBatch() != null && line.getPassedBatch().getId().equals(source.getId())
                || line.getQuarantinedBatch() != null
                    && line.getQuarantinedBatch().getId().equals(source.getId()));
        if (!linked || request.quantity() > source.getQuantity()
            || !source.isActive() || source.getQuantity() <= 0) {
            throw invalid("CORRECTION_BATCH_NOT_LINKED", source.getId());
        }
        Long warehouseId = source.getLocation().getWarehouse().getId();
        if (allowedWarehouseIds != null && !allowedWarehouseIds.contains(warehouseId)) {
            throw new BusinessException(ErrorKeys.WAREHOUSE_SCOPE_DENIED,
                Map.of("warehouseId", warehouseId, "batchId", source.getId()));
        }

        InventoryBatch pendingBatch;
        if (source.getQualityStatus() == InventoryQualityStatus.AVAILABLE) {
            InventoryQualityActionResponse hold = qualityActionService.hold(
                new InventoryQualityHoldRequest(
                    InventoryQualityHoldType.QUARANTINE,
                    List.of(new InventoryQualityQuantityRequest(source.getId(), request.quantity())),
                    "QUALITY_RISK", request.reason(), null,
                    original.getResultNumber(), request.confirmReservationReallocation()),
                childKey("reinsp-hold", idempotencyKey), actorId, actorName,
                allowedWarehouseIds
            );
            Long heldBatchId = hold.lines().get(0).resultBatchId();
            pendingBatch = batchRepository.findByIdForPosting(heldBatchId).orElseThrow();
            qualityStateService.transition(new InventoryQualityTransitionCommand(
                pendingBatch.getId(), InventoryQualityStatus.PENDING_INSPECTION,
                "RECEIVING_REINSPECTION", original.getResultNumber(),
                "REINSPECTION_REQUESTED", request.reason(), actorId, normalizeActor(actorName),
                childKey("reinsp-state", idempotencyKey)
            ));
        } else if (source.getQualityStatus() == InventoryQualityStatus.QUARANTINED) {
            pendingBatch = transitionQuantity(
                source, request.quantity(), InventoryQualityStatus.PENDING_INSPECTION,
                original.getResultNumber(), "REINSPECTION_REQUESTED",
                request.reason(), actorId, actorName, 1);
        } else {
            throw invalid("CORRECTION_BATCH_STATUS_INVALID", source.getId());
        }

        ReceivingInspectionTask task = ReceivingInspectionTask.builder()
            .taskNumber(ReceivingInspectionTaskFactory.number("IQT"))
            .sourceType(ReceivingInspectionSourceType.REINSPECTION)
            .correctionOfResult(original)
            .warehouse(pendingBatch.getLocation().getWarehouse())
            .sourceReference(original.getResultNumber())
            .status(ReceivingInspectionTaskStatus.PENDING)
            .reviewModeSnapshot(policyService.reviewMode())
            .createdBy(actorId)
            .createdByName(normalizeActor(actorName))
            .build();
        task.addLine(ReceivingInspectionTaskLine.builder()
            .productSku(pendingBatch.getProductSku())
            .sourceBatch(pendingBatch)
            .location(pendingBatch.getLocation())
            .skuCode(pendingBatch.getProductSku().getSkuCode())
            .productName(pendingBatch.getProductSku().getName())
            .batchCode(pendingBatch.getBatchCode())
            .locationCode(pendingBatch.getLocationCode())
            .plannedQuantity(request.quantity())
            .pendingQuantity(request.quantity())
            .build());
        task = taskRepository.saveAndFlush(task);
        idempotencyService.markSucceeded(claim.request(), 201, task.getId().toString());
        return toTaskResponse(task);
    }

    private InventoryBatch transitionQuantity(
        InventoryBatch source,
        int quantity,
        InventoryQualityStatus targetStatus,
        String sourceReference,
        String reasonCode,
        String reasonDetail,
        Long actorId,
        String actorName,
        int ordinal
    ) {
        if (quantity <= 0 || quantity > source.getQuantity()) {
            throw invalid("QUALITY_TRANSITION_QUANTITY_INVALID", source.getId());
        }
        InventoryBatch result;
        if (quantity == source.getQuantity()) {
            result = source;
        } else {
            source.setQuantity(source.getQuantity() - quantity);
            batchRepository.saveAndFlush(source);
            String segment = ReceivingInspectionTaskFactory.number("IQS");
            result = batchRepository.saveAndFlush(InventoryBatch.builder()
                .batchCode(segment)
                .locationCode(source.getLocationCode())
                .purchaseOrderItem(source.getPurchaseOrderItem())
                .productSku(source.getProductSku())
                .location(source.getLocation())
                .quantity(quantity)
                .reservedQuantity(0)
                .qualityStatus(source.getQualityStatus())
                .qualityParentBatchId(source.getId())
                .qualitySegmentNo(segment)
                .initialQuantity(quantity)
                .expiryDate(source.getExpiryDate())
                .productionDate(source.getProductionDate())
                .externalBatchCode(source.getExternalBatchCode())
                .entryDate(source.getEntryDate())
                .active(true)
                .remark("Inspection segment created by " + sourceReference)
                .build());
        }
        qualityStateService.transition(new InventoryQualityTransitionCommand(
            result.getId(), targetStatus, "RECEIVING_INSPECTION", sourceReference,
            reasonCode, optionalText(reasonDetail, "reasonDetail", 500),
            actorId, normalizeActor(actorName),
            childKey("inspection-" + ordinal, sourceReference + "-" + result.getId())
        ));
        return result;
    }

    private void requirePendingSource(InventoryBatch source, int quantity) {
        if (!source.isActive() || source.getQualityStatus() != InventoryQualityStatus.PENDING_INSPECTION
            || source.getQuantity() < quantity || source.getReservedQuantity() != 0) {
            throw invalid("SOURCE_BATCH_NOT_PENDING_INSPECTION", source.getId());
        }
    }

    private void refreshTaskStatus(ReceivingInspectionTask task) {
        int pending = task.getLines().stream().mapToInt(ReceivingInspectionTaskLine::getPendingQuantity).sum();
        int pendingReview = task.getLines().stream()
            .mapToInt(ReceivingInspectionTaskLine::getPendingReviewQuantity).sum();
        int completed = task.getLines().stream()
            .mapToInt(line -> line.getPassedQuantity() + line.getQuarantinedQuantity()).sum();
        if (pending == 0 && pendingReview == 0) {
            task.setStatus(ReceivingInspectionTaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
        } else if (pendingReview > 0) {
            task.setStatus(ReceivingInspectionTaskStatus.PENDING_REVIEW);
            task.setCompletedAt(null);
        } else if (completed > 0) {
            task.setStatus(ReceivingInspectionTaskStatus.IN_PROGRESS);
            task.setCompletedAt(null);
        } else {
            task.setStatus(ReceivingInspectionTaskStatus.PENDING);
            task.setCompletedAt(null);
        }
    }

    private void wakeBackorder(ReceivingInspectionTaskLine line) {
        TransferReceiptLine transferLine = line.getTransferReceiptLine();
        if (transferLine != null
            && transferLine.getTransferLine().getBackorderLineId() != null) {
            backorderService.wakeBackorderAtWarehouse(
                transferLine.getTransferLine().getBackorderLineId(),
                transferLine.getReceipt().getTransferOrder().getDestinationWarehouse().getId());
            return;
        }
        backorderService.wakeProduct(line.getProductSku().getId());
    }

    private ReceivingInspectionResultResponse replayResult(
        IdempotencyClaim claim,
        String idempotencyKey,
        String fingerprint,
        String operation
    ) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return null;
        if (claim.decision() != IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            requireAcquired(claim, operation);
        }
        ReceivingInspectionResult result = resultRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT));
        if (!fingerprint.equals(result.getRequestFingerprint())
            || !result.getId().toString().equals(claim.request().getResponseBody())) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT);
        }
        return toResultResponse(result);
    }

    private void requireAcquired(IdempotencyClaim claim, String operation) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return;
        if (claim.decision() == IdempotencyClaimDecision.IN_PROGRESS) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", operation));
        }
        throw new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT,
            Map.of("operation", operation));
    }

    private Map<Long, Integer[]> normalizeLines(List<SubmitReceivingInspectionRequest.Line> lines) {
        Map<Long, Integer[]> result = new LinkedHashMap<>();
        for (SubmitReceivingInspectionRequest.Line line : lines) {
            Integer[] previous = result.putIfAbsent(line.inspectionTaskLineId(),
                new Integer[]{line.passedQuantity(), line.quarantinedQuantity()});
            if (previous != null) {
                throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                    Map.of("field", "lines.inspectionTaskLineId", "duplicate", line.inspectionTaskLineId()));
            }
        }
        return result;
    }

    private ValidatedSubmission validateSubmission(
        Long taskId, SubmitReceivingInspectionRequest request) {
        if (!REASON_CODES.contains(request.reasonCode())) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reasonCode"));
        }
        return new ValidatedSubmission(taskId, request);
    }

    private void validateSample(SubmitReceivingInspectionRequest request, int decisionQuantity) {
        if (decisionQuantity <= 0 || request.defectQuantity() < 0
            || request.inspectionMethod() == ReceivingInspectionMethod.FULL
                && request.sampleQuantity() != null
            || request.inspectionMethod() == ReceivingInspectionMethod.SAMPLING
                && (request.sampleQuantity() == null || request.sampleQuantity() <= 0
                    || request.sampleQuantity() > decisionQuantity
                    || request.defectQuantity() > request.sampleQuantity())) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "inspectionMethod/sampleQuantity/defectQuantity"));
        }
    }

    private String requiredReason(String value) {
        if (!REASON_CODES.contains(value)) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "reasonCode"));
        }
        return value;
    }

    private ReceivingInspectionTask requireTask(Long id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> notFound("inspectionTaskId", id));
    }

    private ReceivingInspectionTaskResponse toTaskResponse(ReceivingInspectionTask task) {
        List<ReceivingInspectionResultResponse> results = resultRepository
            .findByInspectionTaskIdOrderByIdDesc(task.getId()).stream()
            .map(this::toResultResponse).toList();
        return new ReceivingInspectionTaskResponse(
            task.getId(), task.getTaskNumber(), task.getSourceType(),
            task.getSourceReference(), task.getWarehouse().getId(),
            task.getWarehouse().getCode(), task.getWarehouse().getName(),
            task.getStatus(), task.getReviewModeSnapshot(), task.getCreatedAt(),
            task.getCompletedAt(),
            task.getCorrectionOfResult() == null ? null : task.getCorrectionOfResult().getId(),
            task.getLines().stream().map(line -> new ReceivingInspectionTaskResponse.Line(
                line.getId(), line.getProductSku().getId(), line.getSkuCode(),
                line.getProductName(), line.getSourceBatch().getId(), line.getBatchCode(),
                line.getLocation().getId(), line.getLocationCode(), line.getPlannedQuantity(),
                line.getPendingQuantity(), line.getPendingReviewQuantity(),
                line.getPassedQuantity(), line.getQuarantinedQuantity()
            )).toList(),
            results
        );
    }

    private ReceivingInspectionResultResponse toResultResponse(ReceivingInspectionResult result) {
        return new ReceivingInspectionResultResponse(
            result.getId(), result.getResultNumber(), result.getInspectionTask().getId(),
            result.getStatus(), result.getInspectionMethod(), result.getSampleQuantity(),
            result.getDefectQuantity(), result.getReasonCode(), result.getReasonDetail(),
            result.getSubmittedBy(), result.getSubmittedByName(), result.getSubmittedAt(),
            result.getReviewedBy(), result.getReviewedByName(), result.getReviewedAt(),
            result.getReviewComment(), result.getLines().stream().map(line ->
                new ReceivingInspectionResultResponse.Line(
                    line.getId(), line.getInspectionTaskLine().getId(),
                    line.getSourceBatch().getId(), line.getPassedQuantity(),
                    line.getQuarantinedQuantity(),
                    line.getPassedBatch() == null ? null : line.getPassedBatch().getId(),
                    line.getQuarantinedBatch() == null
                        ? null : line.getQuarantinedBatch().getId()
                )).toList()
        );
    }

    private BusinessException invalid(String reason, Object id) {
        return new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
            Map.of("operation", "RECEIVING_INSPECTION", "reason", reason, "resourceId", id));
    }

    private BusinessException notFound(String field, Object id) {
        return new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
            Map.of("operation", "RECEIVING_INSPECTION", field, id, "reason", "NOT_FOUND"));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String requiredText(String value, String field, int max) {
        String normalized = optionalText(value, field, max);
        if (normalized == null) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
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

    private String normalizeActor(String value) {
        return requiredText(value, "actorName", 100);
    }

    private String childKey(String prefix, String value) {
        String candidate = prefix + "-" + value;
        return candidate.length() <= 120 ? candidate : candidate.substring(0, 120);
    }

    private record ValidatedSubmission(Long taskId, SubmitReceivingInspectionRequest request) {
    }
}
