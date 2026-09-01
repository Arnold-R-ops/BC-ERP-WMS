package com.wms.system.service.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inventorydisposition.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.*;
import com.wms.system.service.idempotency.*;
import com.wms.system.service.transfer.TransferAllocationCoordinator;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryDispositionService {
    static final String DAMAGE_OPERATION = "INVENTORY_DAMAGE_REPORT";
    static final String EXPIRY_OPERATION = "INVENTORY_EXPIRY_MARK";
    static final String CREATE_OPERATION = "INVENTORY_DISPOSITION_CREATE";
    static final String SUBMIT_OPERATION = "INVENTORY_DISPOSITION_SUBMIT";
    static final String REVIEW_OPERATION = "INVENTORY_DISPOSITION_REVIEW";
    static final String CANCEL_OPERATION = "INVENTORY_DISPOSITION_CANCEL";
    static final String EXECUTE_OPERATION = "INVENTORY_DISPOSITION_EXECUTE";
    static final long SYSTEM_ACTOR_ID = 0L;
    static final String SYSTEM_ACTOR_NAME = "SYSTEM_EXPIRY";

    private static final Set<String> DAMAGE_REASONS = Set.of(
        "PHYSICAL_DAMAGE", "STORAGE_DAMAGE", "HANDLING_DAMAGE",
        "PACKAGING_DAMAGE", "OTHER_DAMAGE"
    );
    private static final Set<String> SCRAP_REASONS = Set.of(
        "DAMAGED_SCRAP", "EXPIRED_SCRAP", "QUALITY_FAILED_SCRAP",
        "RECALL_DESTRUCTION", "OTHER_DESTRUCTION"
    );
    private static final Set<InventoryQualityStatus> DISPOSABLE_STATUSES = EnumSet.of(
        InventoryQualityStatus.DAMAGED,
        InventoryQualityStatus.EXPIRED,
        InventoryQualityStatus.QUARANTINED,
        InventoryQualityStatus.RECALL_HOLD
    );
    private static final Set<InventoryDispositionStatus> OUTSTANDING_STATUSES = EnumSet.of(
        InventoryDispositionStatus.DRAFT,
        InventoryDispositionStatus.PENDING_REVIEW,
        InventoryDispositionStatus.READY_TO_EXECUTE,
        InventoryDispositionStatus.PARTIALLY_EXECUTED
    );

    private final InventoryBatchRepository batchRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final InventoryDispositionOrderRepository orderRepository;
    private final InventoryDispositionLineRepository lineRepository;
    private final InventoryDispositionExecutionRepository executionRepository;
    private final InventoryDispositionExecutionLineRepository executionLineRepository;
    private final InventoryDispositionReservationImpactRepository impactRepository;
    private final InventoryQualityStateService qualityStateService;
    private final InventoryDispositionPolicyService policyService;
    private final InventoryPostingService postingService;
    private final BackorderService backorderService;
    private final LocationOccupancyService locationOccupancyService;
    private final IdempotencyService idempotencyService;
    private final DomainOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final TransferAllocationCoordinator transferAllocationCoordinator;

    @Transactional(readOnly = true)
    public List<InventoryDispositionCandidateResponse> candidates(
        String search,
        Set<Long> allowedWarehouseIds
    ) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(java.time.ZoneId.of(policyService.businessTimezone()));
        return batchRepository.findQualityCandidates(normalized).stream()
            .filter(batch -> warehouseAllowed(batch, allowedWarehouseIds))
            .limit(100)
            .map(batch -> toCandidate(batch, today))
            .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> warehouseIdsForBatches(List<InventoryDispositionQuantityRequest> lines) {
        Map<Long, Integer> requested = normalizeQuantities(lines);
        List<InventoryBatch> batches = batchRepository.findAllById(requested.keySet());
        requireAllBatches(requested, batches);
        return batches.stream().map(this::warehouseId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Set<Long> warehouseIdsForOrder(Long orderId) {
        return requireOrder(orderId).getLines().stream()
            .map(InventoryDispositionLine::getWarehouseId)
            .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public InventoryDamagePreviewResponse previewDamage(
        ReportInventoryDamageRequest request,
        Set<Long> allowedWarehouseIds
    ) {
        validateDamageRequest(request);
        return toDamagePreview(buildMarkPlan(
            request.lines(), InventoryQualityStatus.DAMAGED, allowedWarehouseIds, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionResponse reportDamage(
        ReportInventoryDamageRequest request,
        String idempotencyKey,
        Long actorId,
        String actorName,
        Set<Long> allowedWarehouseIds
    ) {
        validateDamageRequest(request);
        String body = serialize(request);
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, DAMAGE_OPERATION, body);
        InventoryDispositionResponse replay = replayOrder(claim, idempotencyKey, fingerprint, DAMAGE_OPERATION);
        if (replay != null) return replay;

        transferAllocationCoordinator.evacuate(
            request.lines().stream().map(InventoryDispositionQuantityRequest::batchId).toList(),
            "INVENTORY_DAMAGE_REPORT", actorId, actorName);
        MarkPlan plan = buildMarkPlan(
            request.lines(), InventoryQualityStatus.DAMAGED, allowedWarehouseIds, true);
        if (plan.salesImpactQuantity() > 0 && !request.confirmSalesImpact()) {
            throw new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_SALES_CONFIRMATION_REQUIRED,
                Map.of("salesImpactQuantity", plan.salesImpactQuantity(), "confirmationRequired", true));
        }

        InventoryDispositionOrder order = saveOrder(
            InventoryDispositionType.DAMAGE_REPORT,
            InventoryDispositionStatus.COMPLETED,
            null,
            standardReason(request.reasonCode(), DAMAGE_REASONS),
            request.reasonDetail(), request.externalReference(), request.recallNoticeNumber(),
            actorId, actorName, idempotencyKey, fingerprint
        );
        applyMarkPlan(order, plan, InventoryQualityStatus.DAMAGED, actorId, actorName);
        finishCreatedOrder(claim, order, "INVENTORY_DAMAGE_REPORTED");
        return toResponse(requireOrder(order.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markExpired(Long batchId, LocalDate businessDate) {
        InventoryBatch batch = batchRepository.findByIdForPosting(batchId).orElse(null);
        if (batch == null || !batch.isActive() || value(batch.getQuantity()) <= 0
            || batch.getQualityStatus() != InventoryQualityStatus.AVAILABLE
            || batch.getExpiryDate() == null || !batch.getExpiryDate().isBefore(businessDate)) {
            return false;
        }
        String key = "expiry-" + businessDate + "-" + batchId;
        List<InventoryDispositionQuantityRequest> lines = List.of(
            new InventoryDispositionQuantityRequest(batchId, batch.getQuantity()));
        String body = serialize(Map.of(
            "batchId", batchId, "businessDate", businessDate.toString(),
            "quantity", batch.getQuantity()));
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(key, EXPIRY_OPERATION, body);
        InventoryDispositionResponse replay = replayOrder(claim, key, fingerprint, EXPIRY_OPERATION);
        if (replay != null) return false;

        transferAllocationCoordinator.evacuate(List.of(batchId), "INVENTORY_EXPIRY_MARK",
            SYSTEM_ACTOR_ID, SYSTEM_ACTOR_NAME);
        MarkPlan plan = buildMarkPlan(lines, InventoryQualityStatus.EXPIRED, null, true);
        InventoryDispositionOrder order = saveOrder(
            InventoryDispositionType.EXPIRY_MARK,
            InventoryDispositionStatus.COMPLETED,
            null,
            "DATE_EXPIRED",
            "Automatically marked after the expiry date in tenant business timezone",
            businessDate.toString(), null,
            SYSTEM_ACTOR_ID, SYSTEM_ACTOR_NAME, key, fingerprint
        );
        applyMarkPlan(order, plan, InventoryQualityStatus.EXPIRED,
            SYSTEM_ACTOR_ID, SYSTEM_ACTOR_NAME);
        finishCreatedOrder(claim, order, "INVENTORY_BATCH_EXPIRED");
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> dueBatchIds(LocalDate businessDate) {
        return batchRepository.findDueAvailableIdsForExpiry(businessDate);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionResponse createScrapOrder(
        CreateInventoryScrapOrderRequest request,
        String idempotencyKey,
        Long actorId,
        String actorName,
        Set<Long> allowedWarehouseIds
    ) {
        String reason = standardReason(request.reasonCode(), SCRAP_REASONS);
        Map<Long, Integer> requested = normalizeQuantities(request.lines());
        String body = serialize(request);
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, CREATE_OPERATION, body);
        InventoryDispositionResponse replay = replayOrder(claim, idempotencyKey, fingerprint, CREATE_OPERATION);
        if (replay != null) return replay;

        List<Long> ids = requested.keySet().stream().sorted().toList();
        List<InventoryBatch> batches = batchRepository.findAllByIdForPosting(ids);
        requireAllBatches(requested, batches);
        for (InventoryBatch batch : batches) {
            requireWarehouse(batch, allowedWarehouseIds);
            int quantity = requested.get(batch.getId());
            if (!batch.isActive() || value(batch.getQuantity()) <= 0
                || !DISPOSABLE_STATUSES.contains(batch.getQualityStatus())
                || value(batch.getReservedQuantity()) != 0) {
                throw invalid("SOURCE_BATCH_NOT_DISPOSABLE", batch.getId());
            }
            long outstanding = Optional.ofNullable(lineRepository.sumOutstandingByBatch(
                batch.getId(), InventoryDispositionType.SCRAP_DESTROY, OUTSTANDING_STATUSES)).orElse(0L);
            if ((long) quantity + outstanding > batch.getQuantity()) {
                throw new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_QUANTITY_CONFLICT,
                    Map.of("batchId", batch.getId(), "onHandQuantity", batch.getQuantity(),
                        "existingOutstandingQuantity", outstanding, "requestedQuantity", quantity));
            }
        }

        InventoryDispositionReviewMode mode = policyService.reviewMode();
        InventoryDispositionStatus status = request.saveAsDraft()
            ? InventoryDispositionStatus.DRAFT
            : submittedStatus(mode);
        InventoryDispositionOrder order = saveOrder(
            InventoryDispositionType.SCRAP_DESTROY, status, mode, reason,
            request.reasonDetail(), request.externalReference(), request.recallNoticeNumber(),
            actorId, actorName, idempotencyKey, fingerprint
        );
        for (InventoryBatch batch : batches) {
            saveLine(order, batch, batch, requested.get(batch.getId()), 0, 0, 0,
                batch.getQualityStatus(), batch.getQualityStatus());
        }
        finishCreatedOrder(claim, order, "INVENTORY_DISPOSITION_CREATED");
        return toResponse(requireOrder(order.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionResponse submit(
        Long id, String idempotencyKey, Long actorId
    ) {
        String body = serialize(Map.of("orderId", id));
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, SUBMIT_OPERATION, body);
        InventoryDispositionResponse replay = replayMutation(claim, id, SUBMIT_OPERATION);
        if (replay != null) return replay;

        InventoryDispositionOrder order = requireOrderForUpdate(id);
        if (order.getDispositionType() != InventoryDispositionType.SCRAP_DESTROY
            || order.getStatus() != InventoryDispositionStatus.DRAFT
            || !Objects.equals(order.getRequestedBy(), actorId)) {
            throw invalid("DRAFT_NOT_SUBMITTABLE", id);
        }
        List<Long> batchIds = order.getLines().stream()
            .map(InventoryDispositionLine::getResultBatchId).sorted().toList();
        Map<Long, InventoryBatch> batches = batchRepository.findAllByIdForPosting(batchIds).stream()
            .collect(Collectors.toMap(InventoryBatch::getId, Function.identity()));
        for (InventoryDispositionLine line : order.getLines()) {
            InventoryBatch batch = batches.get(line.getResultBatchId());
            if (batch == null || !batch.isActive()
                || !DISPOSABLE_STATUSES.contains(batch.getQualityStatus())
                || value(batch.getReservedQuantity()) != 0
                || value(batch.getQuantity()) < line.outstandingQuantity()) {
                throw invalid("DRAFT_BATCH_STATE_CHANGED", line.getResultBatchId());
            }
        }
        order.setStatus(submittedStatus(order.getReviewModeSnapshot()));
        orderRepository.saveAndFlush(order);
        outboxService.append("INVENTORY_DISPOSITION_SUBMITTED", "InventoryDispositionOrder",
            order.getId(), Map.of("schemaVersion", 1,
                "dispositionNumber", order.getDispositionNumber(), "submittedBy", actorId,
                "status", order.getStatus().name()));
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionResponse approve(
        Long id, InventoryDispositionDecisionRequest request, String idempotencyKey,
        Long actorId, String actorName
    ) {
        return review(id, request, idempotencyKey, actorId, actorName, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionResponse reject(
        Long id, InventoryDispositionDecisionRequest request, String idempotencyKey,
        Long actorId, String actorName
    ) {
        return review(id, request, idempotencyKey, actorId, actorName, false);
    }

    private InventoryDispositionResponse review(
        Long id, InventoryDispositionDecisionRequest request, String idempotencyKey,
        Long actorId, String actorName, boolean approve
    ) {
        String body = serialize(Map.of("orderId", id, "approve", approve,
            "comment", request.comment().trim()));
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, REVIEW_OPERATION, body);
        InventoryDispositionResponse replay = replayMutation(claim, id, REVIEW_OPERATION);
        if (replay != null) return replay;

        InventoryDispositionOrder order = requireOrderForUpdate(id);
        if (order.getDispositionType() != InventoryDispositionType.SCRAP_DESTROY
            || order.getStatus() != InventoryDispositionStatus.PENDING_REVIEW
            || order.getReviewModeSnapshot() != InventoryDispositionReviewMode.APPROVAL_REQUIRED) {
            throw invalid("ORDER_NOT_REVIEWABLE", id);
        }
        if (Objects.equals(order.getRequestedBy(), actorId)) {
            throw invalid("REQUESTER_CANNOT_REVIEW", id);
        }
        order.setStatus(approve
            ? InventoryDispositionStatus.READY_TO_EXECUTE
            : InventoryDispositionStatus.REJECTED);
        order.setReviewedBy(actorId);
        order.setReviewedByName(normalizeActor(actorName));
        order.setReviewedAt(LocalDateTime.now());
        order.setReviewComment(requiredText(request.comment(), "comment", 500));
        orderRepository.saveAndFlush(order);
        outboxService.append(approve ? "INVENTORY_DISPOSITION_APPROVED" : "INVENTORY_DISPOSITION_REJECTED",
            "InventoryDispositionOrder", order.getId(), Map.of(
                "schemaVersion", 1, "dispositionNumber", order.getDispositionNumber(),
                "reviewedBy", actorId));
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionResponse cancel(
        Long id, InventoryDispositionDecisionRequest request, String idempotencyKey,
        Long actorId, String actorName
    ) {
        String body = serialize(Map.of("orderId", id, "comment", request.comment().trim()));
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, CANCEL_OPERATION, body);
        InventoryDispositionResponse replay = replayMutation(claim, id, CANCEL_OPERATION);
        if (replay != null) return replay;

        InventoryDispositionOrder order = requireOrderForUpdate(id);
        if (order.getDispositionType() != InventoryDispositionType.SCRAP_DESTROY
            || !EnumSet.of(InventoryDispositionStatus.DRAFT,
                InventoryDispositionStatus.PENDING_REVIEW,
                InventoryDispositionStatus.READY_TO_EXECUTE,
                InventoryDispositionStatus.PARTIALLY_EXECUTED).contains(order.getStatus())) {
            throw invalid("ORDER_NOT_CANCELLABLE", id);
        }
        order.setStatus(InventoryDispositionStatus.CANCELLED);
        order.setCancelledBy(actorId);
        order.setCancelledByName(normalizeActor(actorName));
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(requiredText(request.comment(), "comment", 500));
        orderRepository.saveAndFlush(order);
        outboxService.append("INVENTORY_DISPOSITION_CANCELLED", "InventoryDispositionOrder",
            order.getId(), Map.of("schemaVersion", 1,
                "dispositionNumber", order.getDispositionNumber(), "cancelledBy", actorId));
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryDispositionResponse execute(
        Long id, ExecuteInventoryDispositionRequest request, String idempotencyKey,
        Long actorId, String actorName
    ) {
        String body = serialize(Map.of("orderId", id, "request", request));
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, EXECUTE_OPERATION, body);
        if (claim.decision() != IdempotencyClaimDecision.ACQUIRED) {
            if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
                InventoryDispositionExecution execution = executionRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> idempotencyConflict(EXECUTE_OPERATION, "EXECUTION_NOT_FOUND"));
                if (!Objects.equals(execution.getRequestFingerprint(), fingerprint)
                    || !Objects.equals(claim.request().getResponseBody(), execution.getId().toString())) {
                    throw idempotencyConflict(EXECUTE_OPERATION, "EXECUTION_FINGERPRINT_MISMATCH");
                }
                return toResponse(requireOrder(execution.getOrder().getId()));
            }
            throw replayFailure(claim, EXECUTE_OPERATION);
        }

        InventoryDispositionOrder order = requireOrderForUpdate(id);
        if (order.getDispositionType() != InventoryDispositionType.SCRAP_DESTROY
            || !EnumSet.of(InventoryDispositionStatus.READY_TO_EXECUTE,
                InventoryDispositionStatus.PARTIALLY_EXECUTED).contains(order.getStatus())) {
            throw invalid("ORDER_NOT_EXECUTABLE", id);
        }
        Map<Long, Integer> requested = normalizeExecutionQuantities(request.lines());
        List<InventoryDispositionLine> lines = lineRepository.findAllByIdForUpdate(
            requested.keySet().stream().sorted().toList());
        if (lines.size() != requested.size()
            || lines.stream().anyMatch(line -> !Objects.equals(line.getOrder().getId(), id))) {
            throw invalid("EXECUTION_LINE_MISMATCH", id);
        }
        Map<Long, InventoryDispositionLine> byBatch = lines.stream().collect(Collectors.toMap(
            InventoryDispositionLine::getResultBatchId, Function.identity()));
        List<InventoryBatch> batches = batchRepository.findAllByIdForPosting(
            byBatch.keySet().stream().sorted().toList());
        if (batches.size() != byBatch.size()) throw invalid("EXECUTION_BATCH_MISSING", id);

        for (InventoryDispositionLine line : lines) {
            int quantity = requested.get(line.getId());
            if (quantity > line.outstandingQuantity()) {
                throw new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_QUANTITY_CONFLICT,
                    Map.of("lineId", line.getId(), "outstandingQuantity", line.outstandingQuantity(),
                        "requestedQuantity", quantity));
            }
        }
        for (InventoryBatch batch : batches) {
            InventoryDispositionLine line = byBatch.get(batch.getId());
            int quantity = requested.get(line.getId());
            if (!batch.isActive() || !DISPOSABLE_STATUSES.contains(batch.getQualityStatus())
                || value(batch.getQuantity()) < quantity) {
                throw invalid("EXECUTION_BATCH_STATE_CHANGED", batch.getId());
            }
            if (value(batch.getReservedQuantity()) != 0) {
                throw new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_RESERVED_CONFLICT,
                    Map.of("batchId", batch.getId(), "reservedQuantity", batch.getReservedQuantity()));
            }
        }

        InventoryDispositionExecution execution = executionRepository.saveAndFlush(
            InventoryDispositionExecution.builder()
                .order(order)
                .executionNumber(executionNumber(order))
                .executedBy(actorId)
                .executedByName(normalizeActor(actorName))
                .executedAt(LocalDateTime.now())
                .executionNote(optionalText(request.executionNote(), "executionNote", 500))
                .idempotencyKey(requiredText(idempotencyKey, "idempotencyKey", 120))
                .requestFingerprint(fingerprint)
                .build()
        );
        order.getExecutions().add(execution);

        for (InventoryBatch batch : batches) {
            InventoryDispositionLine line = byBatch.get(batch.getId());
            int quantity = requested.get(line.getId());
            String postingId = "DSP:" + execution.getExecutionNumber() + ":" + line.getId();
            InventoryPostingResult result = postingService.post(new InventoryPostingCommand(
                batch.getId(), line.getProductSkuId(), line.getLocationId(), -quantity,
                new PostingContext(
                    CompanyScope.currentCompanyId(), line.getWarehouseId(), SourceType.SCRAP_OUT,
                    order.getDispositionNumber(), order.getReasonCode(),
                    optionalText(request.executionNote(), "executionNote", 500),
                    actorId, normalizeActor(actorName), postingId,
                    shortKey(idempotencyKey, line.getId())
                )
            ));
            InventoryDispositionExecutionLine executionLine = executionLineRepository.save(
                InventoryDispositionExecutionLine.builder()
                    .execution(execution)
                    .dispositionLine(line)
                    .inventoryBatchId(batch.getId())
                    .quantity(quantity)
                    .quantityBefore(result.quantityBefore())
                    .quantityAfter(result.quantityAfter())
                    .stockTransactionId(result.stockTransactionId())
                    .postingId(result.postingId())
                    .build()
            );
            execution.getLines().add(executionLine);
            line.setExecutedQuantity(line.getExecutedQuantity() + quantity);
            lineRepository.save(line);
            locationOccupancyService.refreshLocationStatus(line.getLocationId());
        }
        boolean completed = order.getLines().stream().allMatch(line -> line.outstandingQuantity() == 0);
        order.setStatus(completed
            ? InventoryDispositionStatus.COMPLETED
            : InventoryDispositionStatus.PARTIALLY_EXECUTED);
        orderRepository.saveAndFlush(order);
        outboxService.append("INVENTORY_DISPOSITION_EXECUTED", "InventoryDispositionOrder",
            order.getId(), executionOutbox(order, execution));
        idempotencyService.markSucceeded(claim.request(), 200, execution.getId().toString());
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<InventoryDispositionResponse> list(
        InventoryDispositionType type,
        InventoryDispositionStatus status
    ) {
        List<InventoryDispositionOrder> orders = status != null
            ? orderRepository.findByStatusOrderByIdDesc(status)
            : type != null
                ? orderRepository.findByDispositionTypeOrderByIdDesc(type)
                : orderRepository.findAllByOrderByIdDesc();
        return orders.stream()
            .filter(order -> type == null || order.getDispositionType() == type)
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public InventoryDispositionResponse get(Long id) {
        return toResponse(requireOrder(id));
    }

    private MarkPlan buildMarkPlan(
        List<InventoryDispositionQuantityRequest> requestLines,
        InventoryQualityStatus targetStatus,
        Set<Long> allowedWarehouseIds,
        boolean lock
    ) {
        Map<Long, Integer> requested = normalizeQuantities(requestLines);
        List<Long> ids = requested.keySet().stream().sorted().toList();
        List<InventoryBatch> sources = lock
            ? batchRepository.findAllByIdForPosting(ids)
            : batchRepository.findAllById(ids);
        requireAllBatches(requested, sources);
        for (InventoryBatch source : sources) {
            requireWarehouse(source, allowedWarehouseIds);
            int quantity = requested.get(source.getId());
            boolean eligible = targetStatus == InventoryQualityStatus.EXPIRED
                ? source.getQualityStatus() == InventoryQualityStatus.AVAILABLE
                    && source.getExpiryDate() != null
                : EnumSet.of(InventoryQualityStatus.AVAILABLE,
                    InventoryQualityStatus.QUARANTINED,
                    InventoryQualityStatus.RECALL_HOLD).contains(source.getQualityStatus())
                    && !source.isExpired();
            if (!source.isActive() || value(source.getQuantity()) <= 0
                || quantity > source.getQuantity() || !eligible) {
                throw invalid("SOURCE_BATCH_NOT_MARKABLE", source.getId());
            }
        }

        List<InventoryReservation> reservations = lock
            ? reservationRepository.findOpenByBatchIdsForUpdate(ids)
            : ids.stream().flatMap(id -> reservationRepository.findByInventoryBatchId(id).stream()).toList();
        Map<Long, List<InventoryReservation>> byBatch = reservations.stream()
            .filter(InventoryReservation::hasOpenQuantity)
            .collect(Collectors.groupingBy(InventoryReservation::getInventoryBatchId));
        Map<Long, Integer> transferReserved = lock ? Map.of()
            : transferAllocationCoordinator.openReservedQuantities(ids);
        Map<Long, Integer> remainingCapacity = new HashMap<>();
        Set<Long> excluded = new LinkedHashSet<>(ids);
        List<MarkPlanLine> planned = new ArrayList<>();

        for (InventoryBatch source : sources.stream().sorted(Comparator.comparing(InventoryBatch::getId)).toList()) {
            int markQuantity = requested.get(source.getId());
            int authoritativeReserved = Math.max(0, value(source.getReservedQuantity())
                - transferReserved.getOrDefault(source.getId(), 0));
            int impactNeeded = source.getQualityStatus() == InventoryQualityStatus.AVAILABLE
                ? Math.max(0, markQuantity - (source.getQuantity() - authoritativeReserved))
                : 0;
            int remainingImpact = impactNeeded;
            List<ReservationImpactPlan> impacts = new ArrayList<>();
            List<InventoryBatch> candidates = lock
                ? batchRepository.findSaleableForQualityReallocationForUpdate(
                    source.getProductSku().getId(), excluded)
                : batchRepository.findSaleableByProductSkuIdOrderByExpiryDateAsc(
                    source.getProductSku().getId()).stream()
                    .filter(candidate -> !excluded.contains(candidate.getId())).toList();
            candidates = candidates.stream()
                .filter(candidate -> warehouseAllowed(candidate, allowedWarehouseIds))
                .toList();
            for (InventoryBatch candidate : candidates) {
                remainingCapacity.putIfAbsent(candidate.getId(), candidate.getAvailableQuantity());
            }

            for (InventoryReservation reservation : byBatch.getOrDefault(source.getId(), List.of())
                .stream().sorted(Comparator.comparing(InventoryReservation::getId)).toList()) {
                if (remainingImpact == 0) break;
                int affected = Math.min(remainingImpact, reservation.getOpenQty());
                OutboundTask task = lock
                    ? outboundTaskRepository.findByReservationIdForUpdate(reservation.getId()).orElse(null)
                    : outboundTaskRepository.findByReservationId(reservation.getId()).orElse(null);
                boolean movable = task != null
                    && task.getStatus() == OutboundTaskStatus.PENDING
                    && value(task.getActualQty()) == 0
                    && value(reservation.getConsumedQty()) == 0
                    && value(reservation.getReleasedQty()) == 0
                    && Objects.equals(task.getPlanQty(), reservation.getReservedQty());
                int moved = 0;
                List<DestinationAllocation> destinations = new ArrayList<>();
                if (movable) {
                    int toMove = affected;
                    for (InventoryBatch candidate : candidates) {
                        if (toMove == 0) break;
                        int capacity = remainingCapacity.getOrDefault(candidate.getId(), 0);
                        int chunk = Math.min(toMove, capacity);
                        if (chunk > 0) {
                            destinations.add(new DestinationAllocation(candidate, chunk));
                            remainingCapacity.put(candidate.getId(), capacity - chunk);
                            moved += chunk;
                            toMove -= chunk;
                        }
                    }
                }
                if (moved > 0) {
                    impacts.add(new ReservationImpactPlan(
                        reservation, task, moved,
                        InventoryDispositionReservationImpactType.REALLOCATED, destinations));
                }
                int released = affected - moved;
                if (released > 0) {
                    InventoryDispositionReservationImpactType impactType = task != null
                        && task.getStatus() == OutboundTaskStatus.PICKING
                        ? InventoryDispositionReservationImpactType.QUALITY_EXCEPTION
                        : InventoryDispositionReservationImpactType.BACKORDERED;
                    impacts.add(new ReservationImpactPlan(
                        reservation, task, released, impactType, List.of()));
                }
                remainingImpact -= affected;
            }
            if (remainingImpact != 0) {
                throw new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_QUANTITY_CONFLICT,
                    Map.of("batchId", source.getId(), "reason", "RESERVATION_LEDGER_MISMATCH",
                        "unplannedReservedQuantity", remainingImpact));
            }
            planned.add(new MarkPlanLine(source, markQuantity, impacts));
        }
        return new MarkPlan(planned);
    }

    private void applyMarkPlan(
        InventoryDispositionOrder order,
        MarkPlan plan,
        InventoryQualityStatus targetStatus,
        Long actorId,
        String actorName
    ) {
        int ordinal = 0;
        for (MarkPlanLine planned : plan.lines()) {
            InventoryDispositionLine line = saveLine(
                order, planned.source(), null, planned.quantity(),
                planned.reallocatedQuantity(), planned.backorderedQuantity(),
                planned.qualityExceptionQuantity(), planned.source().getQualityStatus(), targetStatus);
            applyReservationImpacts(planned, line, order);
            InventoryBatch result = applySplitAndTransition(
                planned.source(), planned.quantity(), targetStatus, order, line,
                ++ordinal, actorId, actorName);
            line.setResultBatchId(result.getId());
            line.setResultBatchCode(result.getBatchCode());
            lineRepository.save(line);
        }
        orderRepository.saveAndFlush(order);
    }

    private void applyReservationImpacts(
        MarkPlanLine planned,
        InventoryDispositionLine line,
        InventoryDispositionOrder order
    ) {
        for (ReservationImpactPlan impact : planned.impacts()) {
            if (impact.type() == InventoryDispositionReservationImpactType.REALLOCATED) {
                applyReallocation(planned.source(), line, impact, order);
            } else {
                applyBackorder(planned.source(), line, impact, order);
            }
        }
        batchRepository.save(planned.source());
    }

    private void applyReallocation(
        InventoryBatch source,
        InventoryDispositionLine line,
        ReservationImpactPlan impact,
        InventoryDispositionOrder order
    ) {
        InventoryReservation reservation = impact.reservation();
        OutboundTask task = impact.task();
        int remaining = impact.quantity();
        for (DestinationAllocation allocation : impact.destinations()) {
            int chunk = allocation.quantity();
            InventoryBatch target = allocation.batch();
            int openBefore = reservation.getOpenQty();
            source.releaseReservedQuantity(chunk);
            target.reserveQuantity(chunk);
            batchRepository.save(target);

            InventoryReservation movedReservation;
            OutboundTask movedTask;
            if (chunk == openBefore) {
                reservation.setInventoryBatchId(target.getId());
                reservation.setLocationId(target.getLocation().getId());
                movedReservation = reservationRepository.save(reservation);
                task.setAssignedBatchId(target.getId());
                task.setLocationId(target.getLocation().getId());
                task.setRemark("Reallocated by " + order.getDispositionNumber());
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
                    .idempotencyKey(shortKey(order.getIdempotencyKey(), line.getId(), target.getId()))
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
                    .remark("Split reallocation by " + order.getDispositionNumber())
                    .build());
            }
            saveImpact(line, movedReservation, movedTask,
                InventoryDispositionReservationImpactType.REALLOCATED,
                source.getId(), target.getId(), chunk);
            remaining -= chunk;
        }
        if (remaining != 0) throw new IllegalStateException("Disposition reallocation plan incomplete");
    }

    private void applyBackorder(
        InventoryBatch source,
        InventoryDispositionLine line,
        ReservationImpactPlan impact,
        InventoryDispositionOrder order
    ) {
        InventoryReservation reservation = impact.reservation();
        OutboundTask task = impact.task();
        int quantity = impact.quantity();
        source.releaseReservedQuantity(quantity);
        reservation.release(quantity);
        reservationRepository.save(reservation);

        OutboundTask affectedTask = null;
        if (task != null) {
            if (impact.type() == InventoryDispositionReservationImpactType.QUALITY_EXCEPTION) {
                task.setStatus(OutboundTaskStatus.QUALITY_EXCEPTION);
                task.setRemark("Blocked by " + order.getDispositionNumber());
                affectedTask = outboundTaskRepository.save(task);
            } else {
                int taskQuantity = value(task.getPlanQty());
                if (quantity >= taskQuantity) {
                    task.setStatus(OutboundTaskStatus.VOIDED);
                    task.setRemark("Reservation released to backorder by " + order.getDispositionNumber());
                } else {
                    task.setPlanQty(taskQuantity - quantity);
                    task.setRemark("Partially reallocated to backorder by " + order.getDispositionNumber());
                }
                affectedTask = outboundTaskRepository.save(task);
            }
        }
        backorderService.addQualityShortage(
            reservation.getSalesOrderItemId(), quantity,
            "Inventory quality exception: " + order.getDispositionNumber());
        saveImpact(line, reservation, affectedTask, impact.type(),
            source.getId(), null, quantity);
    }

    private InventoryDispositionStatus submittedStatus(InventoryDispositionReviewMode mode) {
        return mode == InventoryDispositionReviewMode.APPROVAL_REQUIRED
            ? InventoryDispositionStatus.PENDING_REVIEW
            : InventoryDispositionStatus.READY_TO_EXECUTE;
    }

    private InventoryBatch applySplitAndTransition(
        InventoryBatch source,
        int quantity,
        InventoryQualityStatus targetStatus,
        InventoryDispositionOrder order,
        InventoryDispositionLine line,
        int ordinal,
        Long actorId,
        String actorName
    ) {
        InventoryBatch result;
        if (quantity == source.getQuantity()) {
            result = source;
        } else {
            source.setQuantity(source.getQuantity() - quantity);
            batchRepository.saveAndFlush(source);
            String segmentNo = segmentNumber(order, ordinal);
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
                .remark("Disposition quality segment created by " + order.getDispositionNumber())
                .build());
        }
        qualityStateService.transition(new InventoryQualityTransitionCommand(
            result.getId(), targetStatus, "INVENTORY_DISPOSITION",
            order.getDispositionNumber(), order.getReasonCode(), order.getReasonDetail(),
            actorId, normalizeActor(actorName), "disp-state-" + order.getId() + "-" + line.getId()
        ));
        return result;
    }

    private InventoryDispositionOrder saveOrder(
        InventoryDispositionType type,
        InventoryDispositionStatus status,
        InventoryDispositionReviewMode reviewMode,
        String reasonCode,
        String reasonDetail,
        String externalReference,
        String recallNoticeNumber,
        Long actorId,
        String actorName,
        String idempotencyKey,
        String fingerprint
    ) {
        return orderRepository.saveAndFlush(InventoryDispositionOrder.builder()
            .dispositionNumber(dispositionNumber(type))
            .dispositionType(type)
            .status(status)
            .reviewModeSnapshot(reviewMode)
            .reasonCode(requiredText(reasonCode, "reasonCode", 80))
            .reasonDetail(optionalText(reasonDetail, "reasonDetail", 500))
            .externalReference(optionalText(externalReference, "externalReference", 120))
            .recallNoticeNumber(optionalText(recallNoticeNumber, "recallNoticeNumber", 120))
            .requestedBy(actorId)
            .requestedByName(normalizeActor(actorName))
            .requestedAt(LocalDateTime.now())
            .idempotencyKey(requiredText(idempotencyKey, "idempotencyKey", 120))
            .requestFingerprint(fingerprint)
            .build());
    }

    private InventoryDispositionLine saveLine(
        InventoryDispositionOrder order,
        InventoryBatch source,
        InventoryBatch result,
        int quantity,
        int reallocated,
        int backordered,
        int qualityException,
        InventoryQualityStatus from,
        InventoryQualityStatus to
    ) {
        InventoryDispositionLine line = lineRepository.saveAndFlush(
            InventoryDispositionLine.builder()
                .order(order)
                .sourceBatchId(source.getId())
                .resultBatchId(result == null ? null : result.getId())
                .productSkuId(source.getProductSku().getId())
                .productSkuCode(source.getProductSku().getSkuCode())
                .productName(source.getProductSku().getName())
                .sourceBatchCode(source.getBatchCode())
                .resultBatchCode(result == null ? null : result.getBatchCode())
                .warehouseId(warehouseId(source))
                .warehouseName(source.getLocation().getWarehouse().getName())
                .locationId(source.getLocation().getId())
                .locationCode(source.getLocationCode())
                .quantity(quantity)
                .executedQuantity(0)
                .reallocatedReservedQuantity(reallocated)
                .backorderedQuantity(backordered)
                .qualityExceptionQuantity(qualityException)
                .fromQualityStatus(from)
                .toQualityStatus(to)
                .expiryDate(source.getExpiryDate())
                .build());
        order.getLines().add(line);
        return line;
    }

    private void saveImpact(
        InventoryDispositionLine line,
        InventoryReservation reservation,
        OutboundTask task,
        InventoryDispositionReservationImpactType type,
        Long fromBatchId,
        Long toBatchId,
        int quantity
    ) {
        impactRepository.save(InventoryDispositionReservationImpact.builder()
            .dispositionLineId(line.getId())
            .reservationId(reservation.getId())
            .outboundTaskId(task == null ? null : task.getId())
            .salesOrderId(reservation.getSalesOrderId())
            .salesOrderItemId(reservation.getSalesOrderItemId())
            .impactType(type)
            .fromBatchId(fromBatchId)
            .toBatchId(toBatchId)
            .quantity(quantity)
            .build());
    }

    private void finishCreatedOrder(
        IdempotencyClaim claim,
        InventoryDispositionOrder order,
        String eventType
    ) {
        outboxService.append(eventType, "InventoryDispositionOrder", order.getId(), Map.of(
            "schemaVersion", 1,
            "dispositionNumber", order.getDispositionNumber(),
            "dispositionType", order.getDispositionType().name(),
            "status", order.getStatus().name(),
            "quantity", order.getLines().stream().mapToInt(InventoryDispositionLine::getQuantity).sum()
        ));
        idempotencyService.markSucceeded(claim.request(), 201, order.getId().toString());
    }

    private InventoryDispositionResponse replayOrder(
        IdempotencyClaim claim,
        String key,
        String fingerprint,
        String operation
    ) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return null;
        if (claim.decision() != IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            throw replayFailure(claim, operation);
        }
        InventoryDispositionOrder order = orderRepository.findByIdempotencyKey(key)
            .orElseThrow(() -> idempotencyConflict(operation, "ORDER_NOT_FOUND"));
        if (!Objects.equals(order.getRequestFingerprint(), fingerprint)
            || !Objects.equals(claim.request().getResponseBody(), order.getId().toString())) {
            throw idempotencyConflict(operation, "ORDER_FINGERPRINT_MISMATCH");
        }
        return toResponse(order);
    }

    private InventoryDispositionResponse replayMutation(
        IdempotencyClaim claim,
        Long orderId,
        String operation
    ) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return null;
        if (claim.decision() != IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            throw replayFailure(claim, operation);
        }
        if (!Objects.equals(claim.request().getResponseBody(), orderId.toString())) {
            throw idempotencyConflict(operation, "ORDER_RESPONSE_MISMATCH");
        }
        return toResponse(requireOrder(orderId));
    }

    private BusinessException replayFailure(IdempotencyClaim claim, String operation) {
        return claim.decision() == IdempotencyClaimDecision.IN_PROGRESS
            ? new BusinessException(ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", operation))
            : idempotencyConflict(operation, "PREVIOUS_ATTEMPT_FAILED");
    }

    private BusinessException idempotencyConflict(String operation, String reason) {
        return new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT,
            Map.of("operation", operation, "reason", reason));
    }

    private InventoryDispositionOrder requireOrder(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_NOT_FOUND,
                Map.of("dispositionId", id)));
    }

    private InventoryDispositionOrder requireOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_NOT_FOUND,
                Map.of("dispositionId", id)));
    }

    private InventoryDispositionResponse toResponse(InventoryDispositionOrder order) {
        List<InventoryDispositionLineResponse> lines = order.getLines().stream()
            .map(line -> new InventoryDispositionLineResponse(
                line.getId(), line.getSourceBatchId(), line.getResultBatchId(),
                line.getProductSkuId(), line.getProductSkuCode(), line.getProductName(),
                line.getSourceBatchCode(), line.getResultBatchCode(), line.getWarehouseId(),
                line.getWarehouseName(), line.getLocationId(), line.getLocationCode(),
                line.getQuantity(), line.getExecutedQuantity(), line.outstandingQuantity(),
                line.getReallocatedReservedQuantity(), line.getBackorderedQuantity(),
                line.getQualityExceptionQuantity(), line.getFromQualityStatus(),
                line.getToQualityStatus(), line.getExpiryDate()))
            .toList();
        List<InventoryDispositionExecutionResponse> executions = order.getExecutions().stream()
            .map(execution -> new InventoryDispositionExecutionResponse(
                execution.getId(), execution.getExecutionNumber(), execution.getExecutedBy(),
                execution.getExecutedByName(), execution.getExecutedAt(), execution.getExecutionNote(),
                execution.getLines().stream().map(line -> new InventoryDispositionExecutionLineResponse(
                    line.getId(), line.getDispositionLine().getId(), line.getInventoryBatchId(),
                    line.getQuantity(), line.getQuantityBefore(), line.getQuantityAfter(),
                    line.getStockTransactionId(), line.getPostingId())).toList()))
            .toList();
        return new InventoryDispositionResponse(
            order.getId(), order.getDispositionNumber(), order.getDispositionType(), order.getStatus(),
            order.getReviewModeSnapshot(), order.getReasonCode(), order.getReasonDetail(),
            order.getExternalReference(), order.getRecallNoticeNumber(), order.getRequestedBy(),
            order.getRequestedByName(), order.getRequestedAt(), order.getReviewedBy(),
            order.getReviewedByName(), order.getReviewedAt(), order.getReviewComment(),
            order.getCancelledBy(), order.getCancelledByName(), order.getCancelledAt(),
            order.getCancelReason(), lines, executions);
    }

    private InventoryDispositionCandidateResponse toCandidate(InventoryBatch batch, LocalDate today) {
        long outstanding = Optional.ofNullable(lineRepository.sumOutstandingByBatch(
            batch.getId(), InventoryDispositionType.SCRAP_DESTROY, OUTSTANDING_STATUSES)).orElse(0L);
        int disposable = DISPOSABLE_STATUSES.contains(batch.getQualityStatus())
            ? Math.max(0, batch.getQuantity() - Math.toIntExact(outstanding))
            : 0;
        return new InventoryDispositionCandidateResponse(
            batch.getId(), batch.getBatchCode(), batch.getExternalBatchCode(),
            batch.getQualitySegmentNo(), batch.getQualityParentBatchId(),
            batch.getProductSku().getId(), batch.getProductSku().getSkuCode(),
            batch.getProductSku().getName(), warehouseId(batch),
            batch.getLocation().getWarehouse().getName(), batch.getLocation().getId(),
            batch.getLocationCode(), batch.getQuantity(), value(batch.getReservedQuantity()),
            batch.getQualityStatus(), batch.getProductionDate(), batch.getExpiryDate(),
            batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today),
            batch.getExpiryDate() == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(today, batch.getExpiryDate()),
            Math.toIntExact(outstanding), disposable);
    }

    private InventoryDamagePreviewResponse toDamagePreview(MarkPlan plan) {
        List<InventoryDamagePreviewLineResponse> lines = plan.lines().stream().map(line -> {
            Map<String, InventoryDamageAffectedOrderResponse> affected = new LinkedHashMap<>();
            for (ReservationImpactPlan impact : line.impacts()) {
                String key = impact.reservation().getSalesOrderId() + ":"
                    + impact.reservation().getSalesOrderItemId() + ":" + impact.type();
                affected.merge(key, new InventoryDamageAffectedOrderResponse(
                    impact.reservation().getSalesOrderId(), impact.reservation().getSalesOrderItemId(),
                    impact.quantity(), impact.type().name()),
                    (left, right) -> new InventoryDamageAffectedOrderResponse(
                        left.salesOrderId(), left.salesOrderItemId(),
                        left.quantity() + right.quantity(), left.impact()));
            }
            InventoryBatch source = line.source();
            return new InventoryDamagePreviewLineResponse(
                source.getId(), source.getBatchCode(), source.getProductSku().getSkuCode(),
                source.getProductSku().getName(), warehouseId(source),
                source.getLocation().getWarehouse().getName(), source.getLocationCode(),
                source.getQuantity(), value(source.getReservedQuantity()), line.quantity(),
                line.reallocatedQuantity(), line.backorderedQuantity(),
                line.qualityExceptionQuantity(), new ArrayList<>(affected.values()));
        }).toList();
        return new InventoryDamagePreviewResponse(
            plan.salesImpactQuantity() > 0,
            plan.reallocatedQuantity(), plan.backorderedQuantity(),
            plan.qualityExceptionQuantity(), lines);
    }

    private Map<String, Object> executionOutbox(
        InventoryDispositionOrder order,
        InventoryDispositionExecution execution
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("dispositionId", order.getId());
        payload.put("dispositionNumber", order.getDispositionNumber());
        payload.put("executionId", execution.getId());
        payload.put("executionNumber", execution.getExecutionNumber());
        payload.put("reasonCode", order.getReasonCode());
        payload.put("quantity", execution.getLines().stream()
            .mapToInt(InventoryDispositionExecutionLine::getQuantity).sum());
        payload.put("financialPostingStatus", "PENDING_EXTERNAL_ACCOUNTING");
        return payload;
    }

    private void validateDamageRequest(ReportInventoryDamageRequest request) {
        standardReason(request.reasonCode(), DAMAGE_REASONS);
        normalizeQuantities(request.lines());
    }

    private Map<Long, Integer> normalizeQuantities(List<InventoryDispositionQuantityRequest> lines) {
        if (lines == null || lines.isEmpty()) throw validation("lines");
        Map<Long, Integer> normalized = new LinkedHashMap<>();
        for (InventoryDispositionQuantityRequest line : lines) {
            if (line == null || line.batchId() == null || line.batchId() <= 0
                || line.quantity() == null || line.quantity() <= 0) {
                throw validation("lines");
            }
            normalized.merge(line.batchId(), line.quantity(), Math::addExact);
        }
        return normalized;
    }

    private Map<Long, Integer> normalizeExecutionQuantities(
        List<InventoryDispositionExecutionQuantityRequest> lines
    ) {
        if (lines == null || lines.isEmpty()) throw validation("lines");
        Map<Long, Integer> normalized = new LinkedHashMap<>();
        for (InventoryDispositionExecutionQuantityRequest line : lines) {
            if (line == null || line.lineId() == null || line.lineId() <= 0
                || line.quantity() == null || line.quantity() <= 0) {
                throw validation("lines");
            }
            normalized.merge(line.lineId(), line.quantity(), Math::addExact);
        }
        return normalized;
    }

    private void requireAllBatches(Map<Long, Integer> requested, List<InventoryBatch> batches) {
        Set<Long> found = batches.stream().map(InventoryBatch::getId).collect(Collectors.toSet());
        requested.keySet().stream().filter(id -> !found.contains(id)).findFirst()
            .ifPresent(id -> { throw new BusinessException(ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", id)); });
    }

    private void requireWarehouse(InventoryBatch batch, Set<Long> allowedWarehouseIds) {
        if (!warehouseAllowed(batch, allowedWarehouseIds)) {
            throw new BusinessException(ErrorKeys.WAREHOUSE_SCOPE_DENIED,
                Map.of("warehouseId", warehouseId(batch), "batchId", batch.getId()));
        }
    }

    private boolean warehouseAllowed(InventoryBatch batch, Set<Long> allowedWarehouseIds) {
        return batch.getLocation() != null && batch.getLocation().getWarehouse() != null
            && (allowedWarehouseIds == null || allowedWarehouseIds.contains(warehouseId(batch)));
    }

    private Long warehouseId(InventoryBatch batch) {
        return batch.getLocation().getWarehouse().getId();
    }

    private String standardReason(String value, Set<String> supported) {
        String normalized = requiredText(value, "reasonCode", 80).toUpperCase(Locale.ROOT);
        if (!supported.contains(normalized)) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "reasonCode", "reason", "UNSUPPORTED_STANDARD_REASON"));
        }
        return normalized;
    }

    private String dispositionNumber(InventoryDispositionType type) {
        String prefix = switch (type) {
            case DAMAGE_REPORT -> "IDM";
            case EXPIRY_MARK -> "IEX";
            case SCRAP_DESTROY -> "IDS";
        };
        return prefix + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private String executionNumber(InventoryDispositionOrder order) {
        return "DX-" + order.getId() + "-"
            + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String segmentNumber(InventoryDispositionOrder order, int ordinal) {
        return "IDS-" + order.getId() + "-" + ordinal + "-"
            + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String shortKey(String key, Long discriminator) {
        return "disp-" + sha256(key + ":" + discriminator).substring(0, 48);
    }

    private String shortKey(String key, Long lineId, Long targetId) {
        return "dispmove-" + sha256(key + ":" + lineId + ":" + targetId).substring(0, 44);
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
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String normalizeActor(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String requiredText(String value, String field, int max) {
        String normalized = optionalText(value, field, max);
        if (normalized == null) throw validation(field);
        return normalized;
    }

    private String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw validation(field);
        return normalized;
    }

    private BusinessException validation(String field) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
    }

    private BusinessException invalid(String reason, Object resourceId) {
        return new BusinessException(ErrorKeys.INVENTORY_DISPOSITION_INVALID,
            Map.of("reason", reason, "resourceId", resourceId));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private record DestinationAllocation(InventoryBatch batch, int quantity) { }

    private record ReservationImpactPlan(
        InventoryReservation reservation,
        OutboundTask task,
        int quantity,
        InventoryDispositionReservationImpactType type,
        List<DestinationAllocation> destinations
    ) { }

    private record MarkPlanLine(
        InventoryBatch source,
        int quantity,
        List<ReservationImpactPlan> impacts
    ) {
        int reallocatedQuantity() {
            return impacts.stream()
                .filter(impact -> impact.type() == InventoryDispositionReservationImpactType.REALLOCATED)
                .mapToInt(ReservationImpactPlan::quantity).sum();
        }

        int backorderedQuantity() {
            return impacts.stream()
                .filter(impact -> impact.type() != InventoryDispositionReservationImpactType.REALLOCATED)
                .mapToInt(ReservationImpactPlan::quantity).sum();
        }

        int qualityExceptionQuantity() {
            return impacts.stream()
                .filter(impact -> impact.type() == InventoryDispositionReservationImpactType.QUALITY_EXCEPTION)
                .mapToInt(ReservationImpactPlan::quantity).sum();
        }

        int salesImpactQuantity() {
            return impacts.stream().mapToInt(ReservationImpactPlan::quantity).sum();
        }
    }

    private record MarkPlan(List<MarkPlanLine> lines) {
        int reallocatedQuantity() {
            return lines.stream().mapToInt(MarkPlanLine::reallocatedQuantity).sum();
        }

        int backorderedQuantity() {
            return lines.stream().mapToInt(MarkPlanLine::backorderedQuantity).sum();
        }

        int qualityExceptionQuantity() {
            return lines.stream().mapToInt(MarkPlanLine::qualityExceptionQuantity).sum();
        }

        int salesImpactQuantity() {
            return lines.stream().mapToInt(MarkPlanLine::salesImpactQuantity).sum();
        }
    }
}
