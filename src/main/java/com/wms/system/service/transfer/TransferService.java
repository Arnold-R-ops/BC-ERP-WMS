package com.wms.system.service.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.transfer.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.*;
import com.wms.system.service.idempotency.*;
import com.wms.system.service.inspection.ReceivingInspectionTaskFactory;
import com.wms.system.service.inventory.*;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferService {
    private static final Set<TransferBusinessStatus> ACTIVE_BACKORDER_LINK_STATUSES =
        EnumSet.of(TransferBusinessStatus.PENDING_APPROVAL, TransferBusinessStatus.APPROVED);
    private static final String CREATE = "TRANSFER_CREATE";
    private static final String UPDATE = "TRANSFER_UPDATE";
    private static final String SUBMIT = "TRANSFER_SUBMIT";
    private static final String APPROVE = "TRANSFER_APPROVE";
    private static final String REJECT = "TRANSFER_REJECT";
    private static final String REALLOCATE = "TRANSFER_REALLOCATE";
    private static final String CANCEL = "TRANSFER_CANCEL";
    private static final String CLOSE_REMAINDER = "TRANSFER_CLOSE_REMAINDER";
    private static final String CLOSE_SHORTAGE = "TRANSFER_CLOSE_SHORTAGE";
    private static final String VOID = "TRANSFER_VOID";
    private static final String TASK_UPDATE = "TRANSFER_TASK_UPDATE";
    private static final String DISPATCH = "TRANSFER_DISPATCH";
    private static final String RECEIVE = "TRANSFER_RECEIVE";

    private final TransferOrderRepository orderRepository;
    private final TransferOrderLineRepository lineRepository;
    private final TransferAllocationRepository allocationRepository;
    private final TransferWarehouseTaskRepository taskRepository;
    private final TransferDispatchRepository dispatchRepository;
    private final TransferDispatchLineRepository dispatchLineRepository;
    private final TransferReceiptRepository receiptRepository;
    private final TransferReceiptLineRepository receiptLineRepository;
    private final TransferEventRepository eventRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductSkuRepository productSkuRepository;
    private final InventoryBatchRepository batchRepository;
    private final LocationRepository locationRepository;
    private final BackorderLineRepository backorderRepository;
    private final LocationOccupancyService locationOccupancyService;
    private final TransferPolicyService policyService;
    private final InventoryPostingService postingService;
    private final IdempotencyService idempotencyService;
    private final DomainOutboxService outboxService;
    private final BackorderService backorderService;
    private final ReceivingInspectionTaskFactory inspectionTaskFactory;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TransferResponse> list() {
        return orderRepository.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Long id) {
        return toResponse(requireOrder(id));
    }

    @Transactional(readOnly = true)
    public TransferReferencesResponse references() {
        List<TransferReferencesResponse.WarehouseRef> warehouses = warehouseRepository.findAllActive().stream()
            .map(item -> new TransferReferencesResponse.WarehouseRef(item.getId(), item.getCode(), item.getName()))
            .toList();
        List<TransferReferencesResponse.SkuRef> skus = productSkuRepository.findAll().stream()
            .sorted(Comparator.comparing(ProductSku::getSkuCode))
            .map(item -> new TransferReferencesResponse.SkuRef(
                item.getId(), item.getSkuCode(), item.getName(), item.getBarcode()))
            .toList();
        List<TransferReferencesResponse.BackorderRef> backorders = backorderRepository.findAll().stream()
            .filter(BackorderLine::isOpenForAllocation)
            .filter(item -> value(item.getRemainingQty()) > 0)
            .sorted(Comparator.comparing(BackorderLine::getPriority)
                .thenComparing(BackorderLine::getCreatedAt).thenComparing(BackorderLine::getId))
            .map(item -> new TransferReferencesResponse.BackorderRef(
                item.getId(), item.getSalesOrderId(), item.getProductSkuId(),
                value(item.getRemainingQty()), item.getPromisedDate()))
            .toList();
        return new TransferReferencesResponse(warehouses, skus, backorders);
    }

    @Transactional(readOnly = true)
    public List<TransferBatchCandidateResponse> batches(Long warehouseId, Long productSkuId) {
        requireWarehouse(warehouseId);
        requireSku(productSkuId);
        return batchRepository.findTransferCandidates(warehouseId, productSkuId).stream()
            .map(batch -> new TransferBatchCandidateResponse(
                batch.getId(), batch.getBatchCode(), batch.getExternalBatchCode(),
                batch.getLocation().getId(), batch.getLocationCode(), batch.getAvailableQuantity(),
                batch.getProductionDate(), batch.getExpiryDate()))
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse create(CreateTransferRequest request, String key,
                                   Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, CREATE, request);
        TransferResponse replay = replayOrder(claim, CREATE);
        if (replay != null) return replay;
        Warehouse source = requireWarehouse(request.sourceWarehouseId());
        Warehouse destination = requireWarehouse(request.destinationWarehouseId());
        requireDistinctWarehouses(source, destination);
        TransferPolicyResponse policy = policyService.get();
        TransferOrder order = TransferOrder.builder()
            .transferNumber(number("TR"))
            .sourceWarehouse(source)
            .destinationWarehouse(destination)
            .businessStatus(TransferBusinessStatus.DRAFT)
            .outboundStatus(TransferOutboundStatus.NOT_RELEASED)
            .inboundStatus(TransferInboundStatus.NOT_IN_TRANSIT)
            .approvalModeSnapshot(policy.approvalMode())
            .receivingInspectionSnapshot(policy.receivingInspectionEnabled())
            .reason(required(request.reason(), "reason", 500))
            .estimatedArrivalAt(request.estimatedArrivalAt())
            .requestedBy(actorId)
            .requestedByName(actor(actorName))
            .build();
        addLines(order, request.lines(), source, destination);
        order = orderRepository.saveAndFlush(order);
        appendEvent(order, "TRANSFER_DRAFT_CREATED", Map.of(), actorId, actorName);
        outboxService.append("TRANSFER_CREATED", "TransferOrder", order.getId(),
            Map.of("transferId", order.getId(), "transferNumber", order.getTransferNumber()));
        idempotencyService.markSucceeded(claim.request(), 201, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse update(Long id, UpdateTransferRequest request, String key,
                                   Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, UPDATE + ":" + id, request);
        TransferResponse replay = replayOrder(claim, UPDATE);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        if (order.getBusinessStatus() != TransferBusinessStatus.DRAFT
            && order.getBusinessStatus() != TransferBusinessStatus.REJECTED) {
            throw invalid("TRANSFER_NOT_EDITABLE", id);
        }
        if (!allocationRepository.findByTransferLineTransferOrderIdOrderById(id).isEmpty()
            || !dispatchRepository.findByTransferOrderIdOrderById(id).isEmpty()) {
            throw invalid("TRANSFER_HAS_EXECUTION_RECORDS", id);
        }
        Warehouse source = requireWarehouse(request.sourceWarehouseId());
        Warehouse destination = requireWarehouse(request.destinationWarehouseId());
        requireDistinctWarehouses(source, destination);
        order.setSourceWarehouse(source);
        order.setDestinationWarehouse(destination);
        order.setReason(required(request.reason(), "reason", 500));
        order.setEstimatedArrivalAt(request.estimatedArrivalAt());
        order.getLines().clear();
        addLines(order, request.lines(), source, destination);
        order.setBusinessStatus(TransferBusinessStatus.DRAFT);
        clearReview(order);
        order = orderRepository.saveAndFlush(order);
        appendEvent(order, "TRANSFER_DRAFT_UPDATED", Map.of(), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse submit(Long id, TransferDecisionRequest request, String key,
                                   Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, SUBMIT + ":" + id, request);
        TransferResponse replay = replayOrder(claim, SUBMIT);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        if (order.getBusinessStatus() != TransferBusinessStatus.DRAFT
            && order.getBusinessStatus() != TransferBusinessStatus.REJECTED) {
            throw invalid("TRANSFER_NOT_SUBMITTABLE", id);
        }
        validateBackorderLinks(order);
        clearReview(order);
        TransferPolicyResponse policy = policyService.get();
        order.setApprovalModeSnapshot(policy.approvalMode());
        order.setReceivingInspectionSnapshot(policy.receivingInspectionEnabled());
        order.setSubmittedAt(LocalDateTime.now());
        if (policy.approvalMode() == TransferApprovalMode.ALWAYS) {
            order.setBusinessStatus(TransferBusinessStatus.PENDING_APPROVAL);
            orderRepository.saveAndFlush(order);
            appendEvent(order, "TRANSFER_APPROVAL_REQUESTED", Map.of(), actorId, actorName);
        } else {
            authorize(order, actorId, actorName, request.comment(), false);
        }
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse approve(Long id, TransferDecisionRequest request, String key,
                                    Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, APPROVE + ":" + id, request);
        TransferResponse replay = replayOrder(claim, APPROVE);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        requireStatus(order, TransferBusinessStatus.PENDING_APPROVAL);
        if (Objects.equals(order.getRequestedBy(), actorId)) {
            throw invalid("REQUESTER_CANNOT_APPROVE", id);
        }
        authorize(order, actorId, actorName, request.comment(), true);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse reject(Long id, TransferDecisionRequest request, String key,
                                   Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, REJECT + ":" + id, request);
        TransferResponse replay = replayOrder(claim, REJECT);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        requireStatus(order, TransferBusinessStatus.PENDING_APPROVAL);
        if (Objects.equals(order.getRequestedBy(), actorId)) {
            throw invalid("REQUESTER_CANNOT_REVIEW", id);
        }
        order.setBusinessStatus(TransferBusinessStatus.REJECTED);
        review(order, actorId, actorName, request.comment());
        orderRepository.saveAndFlush(order);
        appendEvent(order, "TRANSFER_REJECTED", Map.of("comment", request.comment()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse reallocate(Long id, TransferDecisionRequest request, String key,
                                       Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, REALLOCATE + ":" + id, request);
        TransferResponse replay = replayOrder(claim, REALLOCATE);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        requireStatus(order, TransferBusinessStatus.APPROVED);
        int before = totalAllocated(order);
        allocate(order);
        refresh(order);
        appendEvent(order, "TRANSFER_REALLOCATED",
            Map.of("allocatedQuantity", totalAllocated(order) - before,
                "comment", request.comment()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse cancel(Long id, TransferReasonRequest request, String key,
                                   Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, CANCEL + ":" + id, request);
        TransferResponse replay = replayOrder(claim, CANCEL);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        if (totalDispatched(order) > 0
            || order.getBusinessStatus() != TransferBusinessStatus.DRAFT
                && order.getBusinessStatus() != TransferBusinessStatus.REJECTED
                && order.getBusinessStatus() != TransferBusinessStatus.PENDING_APPROVAL
                && order.getBusinessStatus() != TransferBusinessStatus.APPROVED) {
            throw invalid("TRANSFER_NOT_CANCELLABLE", id);
        }
        releaseAllAllocations(order);
        terminate(order, TransferBusinessStatus.CANCELLED, request.reason(), actorId, actorName);
        closeTasks(order);
        appendEvent(order, "TRANSFER_CANCELLED", Map.of("reason", request.reason()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse closeRemainder(Long id, TransferReasonRequest request, String key,
                                           Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, CLOSE_REMAINDER + ":" + id, request);
        TransferResponse replay = replayOrder(claim, CLOSE_REMAINDER);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        requireStatus(order, TransferBusinessStatus.APPROVED);
        int dispatched = totalDispatched(order);
        int requested = totalRequested(order);
        if (dispatched <= 0 || dispatched >= requested) {
            throw invalid("NO_PARTIAL_UNSHIPPED_REMAINDER", id);
        }
        if (order.getOutboundStatus() == TransferOutboundStatus.CLOSED
            || order.getLines().stream().anyMatch(line -> line.getClosedUnshippedQuantity() > 0)) {
            throw invalid("TRANSFER_UNSHIPPED_REMAINDER_ALREADY_CLOSED", id);
        }
        releaseAllAllocations(order);
        for (TransferOrderLine line : order.getLines()) {
            line.setClosedUnshippedQuantity(line.getRequestedQuantity() - line.getDispatchedQuantity());
        }
        order.setTerminationReason(required(request.reason(), "reason", 500));
        order.setTerminatedBy(actorId);
        order.setTerminatedByName(actor(actorName));
        order.setTerminatedAt(LocalDateTime.now());
        order.setOutboundStatus(TransferOutboundStatus.CLOSED);
        task(order, TransferTaskDirection.OUTBOUND).ifPresent(item -> item.setStatus(TransferTaskStatus.CLOSED));
        refresh(order);
        appendEvent(order, "TRANSFER_UNSHIPPED_REMAINDER_CLOSED",
            Map.of("quantity", requested - dispatched, "reason", request.reason()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse closeShortage(Long id, CloseTransferShortageRequest request, String key,
                                          Long actorId, String actorName) {
        if (!request.confirmed()) {
            throw invalid("TRANSFER_SHORTAGE_CLOSE_NOT_CONFIRMED", id);
        }
        IdempotencyClaim claim = claim(key, CLOSE_SHORTAGE + ":" + id, request);
        TransferResponse replay = replayOrder(claim, CLOSE_SHORTAGE);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        requireStatus(order, TransferBusinessStatus.APPROVED);
        int closedUnshipped = order.getLines().stream()
            .mapToInt(TransferOrderLine::getClosedUnshippedQuantity).sum();
        if (totalDispatched(order) + closedUnshipped != totalRequested(order)) {
            throw invalid("TRANSFER_OUTBOUND_NOT_FINAL", id);
        }
        List<TransferDispatchLine> dispatchLines = dispatchLineRepository
            .findByDispatchTransferOrderIdOrderById(id);
        int shortage = dispatchLines.stream().mapToInt(TransferDispatchLine::openQuantity).sum();
        if (shortage <= 0) throw invalid("NO_IN_TRANSIT_SHORTAGE", id);
        for (TransferDispatchLine dispatchLine : dispatchLines) {
            int open = dispatchLine.openQuantity();
            if (open <= 0) continue;
            dispatchLine.setShortageClosedQuantity(dispatchLine.getShortageClosedQuantity() + open);
            if (dispatchLine.getPackaging() != null)
                dispatchLine.setPackaging(com.wms.system.entity.StockPackaging.empty(dispatchLine.getPackaging().packSize()));
            TransferOrderLine line = dispatchLine.getTransferLine();
            line.setShortageClosedQuantity(line.getShortageClosedQuantity() + open);
        }
        dispatchLineRepository.saveAll(dispatchLines);
        order.setTerminationReason(required(request.reason(), "reason", 500));
        order.setTerminatedBy(actorId);
        order.setTerminatedByName(actor(actorName));
        order.setTerminatedAt(LocalDateTime.now());
        refresh(order);
        appendEvent(order, "TRANSFER_IN_TRANSIT_SHORTAGE_CLOSED",
            Map.of("quantity", shortage, "reason", request.reason()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferResponse voidOrder(Long id, TransferReasonRequest request, String key,
                                      Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, VOID + ":" + id, request);
        TransferResponse replay = replayOrder(claim, VOID);
        if (replay != null) return replay;
        TransferOrder order = requireOrderForUpdate(id);
        requireVersion(order.getVersion(), request.version(), id);
        if (order.getBusinessStatus() != TransferBusinessStatus.DRAFT
            && order.getBusinessStatus() != TransferBusinessStatus.REJECTED
            || !allocationRepository.findByTransferLineTransferOrderIdOrderById(id).isEmpty()
            || taskRepository.existsByTransferOrderId(id)
            || !dispatchRepository.findByTransferOrderIdOrderById(id).isEmpty()) {
            throw invalid("TRANSFER_NOT_VOIDABLE", id);
        }
        terminate(order, TransferBusinessStatus.VOIDED, request.reason(), actorId, actorName);
        appendEvent(order, "TRANSFER_VOIDED", Map.of("reason", request.reason()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, order.getId().toString());
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<TransferWarehouseTaskResponse> listTasks(TransferTaskDirection direction,
                                                         Set<Long> allowedWarehouseIds) {
        List<TransferWarehouseTask> tasks = direction == null
            ? taskRepository.findAllByOrderByPriorityAscIdDesc()
            : taskRepository.findByDirectionOrderByPriorityAscIdDesc(direction);
        return tasks.stream()
            .filter(task -> allowedWarehouseIds == null || allowedWarehouseIds.contains(task.getWarehouse().getId()))
            .map(this::toTaskResponse).toList();
    }

    @Transactional(readOnly = true)
    public TransferWarehouseTaskResponse getTask(Long taskId) {
        return toTaskResponse(requireTask(taskId));
    }

    @Transactional(readOnly = true)
    public Long warehouseIdForTask(Long taskId) {
        return requireTask(taskId).getWarehouse().getId();
    }

    @Transactional(readOnly = true)
    public TransferTaskDirection directionForTask(Long taskId) {
        return requireTask(taskId).getDirection();
    }

    @Transactional(readOnly = true)
    public List<TransferLocationResponse> locations(Long taskId) {
        TransferWarehouseTask task = requireTask(taskId);
        if (task.getDirection() != TransferTaskDirection.INBOUND) {
            throw invalid("TRANSFER_TASK_NOT_RECEIVABLE", taskId);
        }
        Long warehouseId = task.getWarehouse().getId();
        return locationRepository.findByWarehouseIdAndEnabledTrueOrderByLocationCodeAsc(warehouseId).stream()
            .map(location -> new TransferLocationResponse(
                location.getId(), location.getLocationCode(), location.getLocationCode()))
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferWarehouseTaskResponse updateTask(Long taskId, UpdateTransferTaskRequest request,
                                                     String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, TASK_UPDATE + ":" + taskId, request);
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return getTask(Long.valueOf(claim.request().getResponseBody()));
        }
        requireAcquired(claim, TASK_UPDATE);
        LockedTransfer locked = lockOrderThenTask(taskId);
        TransferOrder order = locked.order();
        TransferWarehouseTask task = locked.task();
        requireVersion(task.getVersion(), request.version(), taskId);
        if (EnumSet.of(TransferTaskStatus.COMPLETED, TransferTaskStatus.CLOSED)
            .contains(task.getStatus())) throw invalid("TRANSFER_TASK_TERMINAL", taskId);
        if (request.expectedFrom() != null && request.expectedTo() != null
            && request.expectedTo().isBefore(request.expectedFrom())) {
            throw invalid("EXPECTED_WINDOW_INVALID", taskId);
        }
        if (request.priority() != null) task.setPriority(request.priority());
        task.setExpectedFrom(request.expectedFrom());
        task.setExpectedTo(request.expectedTo());
        task.setAssignee(optional(request.assignee(), "assignee", 100));
        task.setTeamName(optional(request.teamName(), "teamName", 100));
        task.setOperationArea(optional(request.operationArea(), "operationArea", 100));
        task.setWarehouseRemark(optional(request.warehouseRemark(), "warehouseRemark", 500));
        task = taskRepository.saveAndFlush(task);
        appendEvent(task.getTransferOrder(), "TRANSFER_TASK_UPDATED", Map.of("taskId", taskId), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, task.getId().toString());
        return toTaskResponse(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferWarehouseTaskResponse dispatch(Long taskId, DispatchTransferRequest request,
                                                   String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, DISPATCH + ":" + taskId, request);
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return getTask(Long.valueOf(claim.request().getResponseBody()));
        }
        requireAcquired(claim, DISPATCH);
        LockedTransfer locked = lockOrderThenTask(taskId);
        TransferOrder order = locked.order();
        TransferWarehouseTask task = locked.task();
        requireVersion(task.getVersion(), request.version(), taskId);
        if (task.getDirection() != TransferTaskDirection.OUTBOUND
            || EnumSet.of(TransferTaskStatus.COMPLETED, TransferTaskStatus.CLOSED).contains(task.getStatus())) {
            throw invalid("TRANSFER_TASK_NOT_DISPATCHABLE", taskId);
        }
        requireStatus(order, TransferBusinessStatus.APPROVED);
        LinkedHashMap<Long, Integer> requested = normalizeDispatchLines(request.lines());
        List<TransferAllocation> allocations = allocationRepository.findAllByIdForUpdate(requested.keySet());
        if (allocations.size() != requested.size()
            || allocations.stream().anyMatch(a -> !a.getTransferLine().getTransferOrder().getId().equals(order.getId()))) {
            throw invalid("TRANSFER_ALLOCATION_MISMATCH", taskId);
        }
        TransferDispatch dispatch = TransferDispatch.builder()
            .transferOrder(order).task(task).dispatchNumber(number("TRD"))
            .idempotencyKey(key.trim())
            .requestFingerprint(claim.request().getRequestHash())
            .handoverReference(optional(request.handoverReference(), "handoverReference", 120))
            .estimatedArrivalAt(request.estimatedArrivalAt())
            .warehouseRemark(optional(request.warehouseRemark(), "warehouseRemark", 500))
            .dispatchedBy(actorId).dispatchedByName(actor(actorName))
            .dispatchedAt(LocalDateTime.now()).build();
        dispatch = dispatchRepository.saveAndFlush(dispatch);
        int sequence = 0;
        for (TransferAllocation allocation : allocations) {
            int quantity = requested.get(allocation.getId());
            if (quantity <= 0 || quantity > allocation.remainingQuantity()) {
                throw invalid("TRANSFER_DISPATCH_EXCEEDS_ALLOCATION", allocation.getId());
            }
            InventoryBatch batch = batchRepository.findByIdForPosting(allocation.getSourceBatch().getId())
                .orElseThrow(() -> notFound("inventoryBatchId", allocation.getSourceBatch().getId()));
            requireTransferBatch(batch, order, allocation.getTransferLine().getProductSku().getId());
            if (batch.getStocktakeTaskId() != null) throw new BusinessException("PACKAGING_STOCKTAKE_LOCKED");
            var selected = request.lines().stream().filter(l -> l.allocationId().equals(allocation.getId())).findFirst().orElseThrow().packagingSelection();
            var packagingTake = takePackaging(batch.getPackaging(), selected, batch.getVersion(), quantity);
            batch.releaseReservedQuantity(quantity);
            batchRepository.saveAndFlush(batch);
            allocation.setConsumedQuantity(allocation.getConsumedQuantity() + quantity);
            allocation.setStatus(allocation.remainingQuantity() == 0
                ? TransferAllocationStatus.CONSUMED : TransferAllocationStatus.PARTIALLY_CONSUMED);
            TransferOrderLine line = allocation.getTransferLine();
            line.setAllocatedQuantity(line.getAllocatedQuantity() - quantity);
            line.setDispatchedQuantity(line.getDispatchedQuantity() + quantity);
            String postingId = dispatch.getDispatchNumber() + ":" + (++sequence);
            InventoryPostingResult posting = postingService.post(new InventoryPostingCommand(
                batch.getId(), line.getProductSku().getId(), batch.getLocation().getId(), -quantity,
                context(order, order.getSourceWarehouse().getId(), SourceType.TRANSFER_OUT,
                    "TRANSFER_OUT", request.warehouseRemark(), actorId, actorName,
                    postingId, key), packagingTake == null ? null : packagingTake.remaining()));
            locationOccupancyService.refreshLocationStatus(batch.getLocation().getId());
            dispatch.addLine(TransferDispatchLine.builder()
                .transferLine(line).allocation(allocation).sourceBatch(batch)
                .sourceBatchCode(batch.getBatchCode()).externalBatchCode(batch.getExternalBatchCode())
                .productionDate(batch.getProductionDate()).expiryDate(batch.getExpiryDate())
                .quantity(quantity).postingId(postingId)
                .packaging(packagingTake == null ? null : packagingTake.taken())
                .stockTransactionId(posting.stockTransactionId()).build());
        }
        allocationRepository.saveAll(allocations);
        dispatch = dispatchRepository.saveAndFlush(dispatch);
        refresh(order);
        appendEvent(order, "TRANSFER_DISPATCHED",
            Map.of("dispatchId", dispatch.getId(), "dispatchNumber", dispatch.getDispatchNumber(),
                "quantity", requested.values().stream().mapToInt(Integer::intValue).sum()), actorId, actorName);
        outboxService.append("TRANSFER_DISPATCHED", "TransferOrder", order.getId(),
            Map.of("transferId", order.getId(), "dispatchId", dispatch.getId()));
        idempotencyService.markSucceeded(claim.request(), 201, task.getId().toString());
        return toTaskResponse(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferWarehouseTaskResponse receive(Long taskId, ReceiveTransferRequest request,
                                                  String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, RECEIVE + ":" + taskId, request);
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return getTask(Long.valueOf(claim.request().getResponseBody()));
        }
        requireAcquired(claim, RECEIVE);
        LockedTransfer locked = lockOrderThenTask(taskId);
        TransferOrder order = locked.order();
        TransferWarehouseTask task = locked.task();
        requireVersion(task.getVersion(), request.version(), taskId);
        if (task.getDirection() != TransferTaskDirection.INBOUND
            || EnumSet.of(TransferTaskStatus.COMPLETED, TransferTaskStatus.CLOSED).contains(task.getStatus())) {
            throw invalid("TRANSFER_TASK_NOT_RECEIVABLE", taskId);
        }
        requireStatus(order, TransferBusinessStatus.APPROVED);
        Set<Long> dispatchLineIds = request.lines().stream()
            .map(ReceiveTransferRequest.Line::dispatchLineId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<TransferDispatchLine> dispatchLines = dispatchLineRepository.findAllByIdForUpdate(dispatchLineIds);
        if (dispatchLines.size() != dispatchLineIds.size()
            || dispatchLines.stream().anyMatch(l -> !l.getDispatch().getTransferOrder().getId().equals(order.getId()))) {
            throw invalid("TRANSFER_DISPATCH_LINE_MISMATCH", taskId);
        }
        Map<Long, TransferDispatchLine> dispatchById = dispatchLines.stream()
            .collect(Collectors.toMap(TransferDispatchLine::getId, Function.identity()));
        var receiptPackaging = planReceiptPackaging(request.lines(), dispatchById);
        Map<Long, Integer> totals = request.lines().stream().collect(Collectors.groupingBy(
            ReceiveTransferRequest.Line::dispatchLineId, LinkedHashMap::new,
            Collectors.summingInt(ReceiveTransferRequest.Line::quantity)));
        totals.forEach((id, quantity) -> {
            if (quantity <= 0 || quantity > dispatchById.get(id).openQuantity()) {
                throw invalid("TRANSFER_RECEIPT_EXCEEDS_DISPATCH", id);
            }
        });
        TransferReceipt receipt = TransferReceipt.builder()
            .transferOrder(order).task(task).receiptNumber(number("TRR"))
            .idempotencyKey(key.trim()).requestFingerprint(claim.request().getRequestHash())
            .receivedBy(actorId).receivedByName(actor(actorName))
            .receivedAt(LocalDateTime.now()).remark(optional(request.remark(), "remark", 500)).build();
        receipt = receiptRepository.saveAndFlush(receipt);
        int sequence = 0;
        Set<Long> directWakeBackorders = new LinkedHashSet<>();
        for (ReceiveTransferRequest.Line requestedLine : request.lines()) {
            TransferDispatchLine dispatchLine = dispatchById.get(requestedLine.dispatchLineId());
            Location location = locationRepository.findByIdForInventoryPosting(requestedLine.locationId())
                .orElseThrow(() -> notFound("locationId", requestedLine.locationId()));
            if (!location.getWarehouse().getId().equals(order.getDestinationWarehouse().getId())
                || !Boolean.TRUE.equals(location.getEnabled())) {
                throw invalid("TRANSFER_DESTINATION_LOCATION_INVALID", location.getId());
            }
            InventoryQualityStatus qualityStatus = qualityStatus(requestedLine.disposition(),
                Boolean.TRUE.equals(order.getReceivingInspectionSnapshot()));
            InventoryBatch sourceBatch = dispatchLine.getSourceBatch();
            String destinationBatchCode = number("TRB");
            locationOccupancyService.validateCanStore(location,
                dispatchLine.getTransferLine().getProductSku(), destinationBatchCode);
            InventoryBatch destinationBatch = batchRepository.saveAndFlush(InventoryBatch.builder()
                .batchCode(destinationBatchCode)
                .purchaseOrderItem(sourceBatch.getPurchaseOrderItem())
                .productSku(dispatchLine.getTransferLine().getProductSku())
                .location(location).locationCode(location.getLocationCode())
                .quantity(0).reservedQuantity(0).qualityStatus(qualityStatus)
                .initialQuantity(requestedLine.quantity())
                .productionDate(dispatchLine.getProductionDate()).expiryDate(dispatchLine.getExpiryDate())
                .externalBatchCode(dispatchLine.getExternalBatchCode())
                .entryDate(LocalDateTime.now()).active(true)
                .remark(optional(request.remark(), "remark", 500)).build());
            String postingId = receipt.getReceiptNumber() + ":" + (++sequence);
            InventoryPostingResult posting = postingService.post(new InventoryPostingCommand(
                destinationBatch.getId(), dispatchLine.getTransferLine().getProductSku().getId(),
                location.getId(), requestedLine.quantity(),
                context(order, order.getDestinationWarehouse().getId(), SourceType.TRANSFER_IN,
                    "TRANSFER_IN", request.remark(), actorId, actorName,
                    postingId, key), receiptPackaging.taken().get(requestedLine)));
            locationOccupancyService.refreshLocationStatus(location.getId());
            TransferReceiptLine receiptLine = TransferReceiptLine.builder()
                .dispatchLine(dispatchLine).transferLine(dispatchLine.getTransferLine())
                .location(location).sourceBatch(sourceBatch).destinationBatch(destinationBatch)
                .disposition(requestedLine.disposition()).quantity(requestedLine.quantity())
                .postingId(postingId).stockTransactionId(posting.stockTransactionId()).build();
            receipt.addLine(receiptLine);
            dispatchLine.setReceivedQuantity(dispatchLine.getReceivedQuantity() + requestedLine.quantity());
            TransferOrderLine line = dispatchLine.getTransferLine();
            line.setReceivedQuantity(line.getReceivedQuantity() + requestedLine.quantity());
            if (requestedLine.disposition() == TransferReceiptDisposition.DAMAGED) {
                dispatchLine.setDamagedQuantity(dispatchLine.getDamagedQuantity() + requestedLine.quantity());
                line.setDamagedQuantity(line.getDamagedQuantity() + requestedLine.quantity());
            } else if (requestedLine.disposition() == TransferReceiptDisposition.QUARANTINED) {
                dispatchLine.setQuarantinedQuantity(dispatchLine.getQuarantinedQuantity() + requestedLine.quantity());
                line.setQuarantinedQuantity(line.getQuarantinedQuantity() + requestedLine.quantity());
            } else if (!Boolean.TRUE.equals(order.getReceivingInspectionSnapshot())
                && line.getBackorderLineId() != null) {
                directWakeBackorders.add(line.getBackorderLineId());
            }
        }
        receiptPackaging.remaining().forEach((id, packaging) -> dispatchById.get(id).setPackaging(packaging));
        dispatchLineRepository.saveAll(dispatchLines);
        receipt = receiptRepository.saveAndFlush(receipt);
        if (Boolean.TRUE.equals(order.getReceivingInspectionSnapshot())) {
            inspectionTaskFactory.createForTransferReceipt(receipt);
        }
        directWakeBackorders.forEach(backorderId ->
            backorderService.wakeBackorderAtWarehouse(backorderId, order.getDestinationWarehouse().getId()));
        refresh(order);
        appendEvent(order, "TRANSFER_RECEIVED",
            Map.of("receiptId", receipt.getId(), "receiptNumber", receipt.getReceiptNumber(),
                "quantity", request.lines().stream().mapToInt(ReceiveTransferRequest.Line::quantity).sum()),
            actorId, actorName);
        outboxService.append("TRANSFER_RECEIVED", "TransferOrder", order.getId(),
            Map.of("transferId", order.getId(), "receiptId", receipt.getId()));
        idempotencyService.markSucceeded(claim.request(), 201, task.getId().toString());
        return toTaskResponse(task);
    }

    private void authorize(TransferOrder order, Long actorId, String actorName,
                           String comment, boolean reviewed) {
        order.setBusinessStatus(TransferBusinessStatus.APPROVED);
        order.setOutboundStatus(TransferOutboundStatus.PENDING);
        if (reviewed) review(order, actorId, actorName, comment);
        allocate(order);
        createTasks(order);
        promiseBackorders(order);
        orderRepository.saveAndFlush(order);
        appendEvent(order, reviewed ? "TRANSFER_APPROVED" : "TRANSFER_DIRECTLY_AUTHORIZED",
            Map.of("allocatedQuantity", totalAllocated(order)), actorId, actorName);
        outboxService.append("TRANSFER_APPROVED", "TransferOrder", order.getId(),
            Map.of("transferId", order.getId(), "allocatedQuantity", totalAllocated(order)));
    }

    private void allocate(TransferOrder order) {
        for (TransferOrderLine line : order.getLines()) {
            int remaining = line.openToAllocateQuantity();
            if (remaining <= 0) continue;
            if (line.getPreferredBatch() != null) {
                InventoryBatch preferred = batchRepository.findByIdForPosting(line.getPreferredBatch().getId())
                    .orElseThrow(() -> notFound("preferredBatchId", line.getPreferredBatch().getId()));
                requireTransferBatch(preferred, order, line.getProductSku().getId());
                remaining -= allocate(line, preferred, remaining);
            }
            if (remaining <= 0) continue;
            for (InventoryBatch batch : batchRepository.findTransferCandidatesForUpdate(
                order.getSourceWarehouse().getId(), line.getProductSku().getId())) {
                if (remaining <= 0) break;
                remaining -= allocate(line, batch, remaining);
            }
        }
    }

    private int allocate(TransferOrderLine line, InventoryBatch batch, int requested) {
        int quantity = Math.min(requested, batch.getAvailableQuantity());
        if (quantity <= 0) return 0;
        batch.reserveQuantity(quantity);
        batchRepository.saveAndFlush(batch);
        allocationRepository.save(TransferAllocation.builder()
            .transferLine(line).sourceBatch(batch).allocatedQuantity(quantity)
            .status(TransferAllocationStatus.ACTIVE).build());
        line.setAllocatedQuantity(line.getAllocatedQuantity() + quantity);
        lineRepository.save(line);
        return quantity;
    }

    private void releaseAllAllocations(TransferOrder order) {
        Set<Long> lineIds = order.getLines().stream().map(TransferOrderLine::getId).collect(Collectors.toSet());
        if (lineIds.isEmpty()) return;
        for (TransferAllocation allocation : allocationRepository.findByTransferLineIdsForUpdate(lineIds)) {
            int remaining = allocation.remainingQuantity();
            if (remaining <= 0) continue;
            InventoryBatch batch = batchRepository.findByIdForPosting(allocation.getSourceBatch().getId())
                .orElseThrow(() -> notFound("inventoryBatchId", allocation.getSourceBatch().getId()));
            batch.releaseReservedQuantity(remaining);
            batchRepository.saveAndFlush(batch);
            allocation.setReleasedQuantity(allocation.getReleasedQuantity() + remaining);
            allocation.setStatus(allocation.getConsumedQuantity() > 0
                ? TransferAllocationStatus.PARTIALLY_RELEASED : TransferAllocationStatus.RELEASED);
            TransferOrderLine line = allocation.getTransferLine();
            line.setAllocatedQuantity(line.getAllocatedQuantity() - remaining);
            allocationRepository.save(allocation);
        }
    }

    private void createTasks(TransferOrder order) {
        if (!order.getTasks().isEmpty()) return;
        order.addTask(TransferWarehouseTask.builder()
            .warehouse(order.getSourceWarehouse()).taskNumber(number("TRO"))
            .direction(TransferTaskDirection.OUTBOUND).status(TransferTaskStatus.PENDING)
            .expectedTo(order.getEstimatedArrivalAt()).build());
        order.addTask(TransferWarehouseTask.builder()
            .warehouse(order.getDestinationWarehouse()).taskNumber(number("TRI"))
            .direction(TransferTaskDirection.INBOUND).status(TransferTaskStatus.EXPECTED)
            .expectedTo(order.getEstimatedArrivalAt()).build());
        orderRepository.saveAndFlush(order);
    }

    private void promiseBackorders(TransferOrder order) {
        for (TransferOrderLine line : order.getLines()) {
            if (line.getBackorderLineId() == null) continue;
            backorderService.markWaitingInbound(
                line.getBackorderLineId(), order.getEstimatedArrivalAt().toLocalDate());
        }
    }

    private void validateBackorderLinks(TransferOrder order) {
        List<TransferOrderLine> linkedLines = order.getLines().stream()
            .filter(line -> line.getBackorderLineId() != null)
            .sorted(Comparator.comparing(TransferOrderLine::getBackorderLineId))
            .toList();
        for (TransferOrderLine line : linkedLines) {
            if (order.getEstimatedArrivalAt() == null) {
                throw invalid("BACKORDER_LINK_REQUIRES_ETA", line.getId());
            }
            BackorderLine backorder = backorderRepository.findByIdForUpdate(line.getBackorderLineId())
                .orElseThrow(() -> notFound("backorderLineId", line.getBackorderLineId()));
            if (!backorder.isOpenForAllocation() || value(backorder.getRemainingQty()) <= 0
                || !backorder.getProductSkuId().equals(line.getProductSku().getId())) {
                throw invalid("BACKORDER_LINK_INVALID", line.getBackorderLineId());
            }
            if (lineRepository.existsByBackorderLineIdAndTransferOrderIdNotAndTransferOrderBusinessStatusIn(
                line.getBackorderLineId(), order.getId(), ACTIVE_BACKORDER_LINK_STATUSES)) {
                throw invalid("BACKORDER_ALREADY_LINKED_TO_ACTIVE_TRANSFER", line.getBackorderLineId());
            }
        }
    }

    private void refresh(TransferOrder order) {
        int requested = totalRequested(order);
        int dispatched = totalDispatched(order);
        int received = totalReceived(order);
        int inTransit = order.getLines().stream().mapToInt(TransferOrderLine::inTransitQuantity).sum();
        int closedUnshipped = order.getLines().stream().mapToInt(TransferOrderLine::getClosedUnshippedQuantity).sum();
        int shortageClosed = order.getLines().stream().mapToInt(TransferOrderLine::getShortageClosedQuantity).sum();
        if (closedUnshipped > 0) order.setOutboundStatus(TransferOutboundStatus.CLOSED);
        else if (dispatched == 0) order.setOutboundStatus(TransferOutboundStatus.PENDING);
        else if (dispatched < requested) order.setOutboundStatus(TransferOutboundStatus.PARTIAL);
        else order.setOutboundStatus(TransferOutboundStatus.DISPATCHED);

        if (shortageClosed > 0 && inTransit == 0) order.setInboundStatus(TransferInboundStatus.CLOSED);
        else if (dispatched == 0) order.setInboundStatus(TransferInboundStatus.NOT_IN_TRANSIT);
        else if (inTransit == 0) order.setInboundStatus(TransferInboundStatus.RECEIVED);
        else if (received == 0) order.setInboundStatus(TransferInboundStatus.IN_TRANSIT);
        else order.setInboundStatus(TransferInboundStatus.PARTIAL_RECEIPT);

        task(order, TransferTaskDirection.OUTBOUND).ifPresent(task -> task.setStatus(
            order.getOutboundStatus() == TransferOutboundStatus.CLOSED ? TransferTaskStatus.CLOSED
                : order.getOutboundStatus() == TransferOutboundStatus.DISPATCHED ? TransferTaskStatus.COMPLETED
                : order.getOutboundStatus() == TransferOutboundStatus.PARTIAL ? TransferTaskStatus.PARTIAL
                : TransferTaskStatus.PENDING));
        task(order, TransferTaskDirection.INBOUND).ifPresent(task -> task.setStatus(
            order.getInboundStatus() == TransferInboundStatus.CLOSED ? TransferTaskStatus.CLOSED
                : order.getInboundStatus() == TransferInboundStatus.RECEIVED ? TransferTaskStatus.COMPLETED
                : order.getInboundStatus() == TransferInboundStatus.PARTIAL_RECEIPT ? TransferTaskStatus.PARTIAL
                : order.getInboundStatus() == TransferInboundStatus.IN_TRANSIT ? TransferTaskStatus.PENDING
                : TransferTaskStatus.EXPECTED));
        if (dispatched + closedUnshipped == requested && inTransit == 0) {
            order.setBusinessStatus(closedUnshipped > 0 || shortageClosed > 0
                ? TransferBusinessStatus.CLOSED : TransferBusinessStatus.COMPLETED);
            order.setTerminatedAt(order.getBusinessStatus() == TransferBusinessStatus.CLOSED
                ? Optional.ofNullable(order.getTerminatedAt()).orElse(LocalDateTime.now()) : order.getTerminatedAt());
        }
        orderRepository.saveAndFlush(order);
    }

    private InventoryQualityStatus qualityStatus(TransferReceiptDisposition disposition,
                                                  boolean inspectionEnabled) {
        return switch (disposition) {
            case DAMAGED -> InventoryQualityStatus.DAMAGED;
            case QUARANTINED -> InventoryQualityStatus.QUARANTINED;
            case AVAILABLE -> inspectionEnabled
                ? InventoryQualityStatus.PENDING_INSPECTION : InventoryQualityStatus.AVAILABLE;
        };
    }

    private PostingContext context(TransferOrder order, Long warehouseId, SourceType sourceType,
                                   String reasonCode, String remarks, Long actorId, String actorName,
                                   String postingId, String key) {
        return new PostingContext(CompanyScope.currentCompanyId(), warehouseId, sourceType,
            order.getTransferNumber(), reasonCode, optional(remarks, "remarks", 500),
            actorId, actor(actorName), postingId, key.trim());
    }

    private void addLines(TransferOrder order, List<CreateTransferRequest.Line> requested,
                          Warehouse source, Warehouse destination) {
        Set<Long> backorders = new HashSet<>();
        for (CreateTransferRequest.Line item : requested) {
            ProductSku sku = requireSku(item.productSkuId());
            InventoryBatch preferred = item.preferredBatchId() == null ? null
                : batchRepository.findById(item.preferredBatchId())
                    .orElseThrow(() -> notFound("preferredBatchId", item.preferredBatchId()));
            if (preferred != null) requireTransferBatch(preferred, source, sku.getId());
            if (item.backorderLineId() != null && !backorders.add(item.backorderLineId())) {
                throw invalid("DUPLICATE_BACKORDER_LINK", item.backorderLineId());
            }
            if (item.backorderLineId() != null) {
                BackorderLine backorder = backorderRepository.findById(item.backorderLineId())
                    .orElseThrow(() -> notFound("backorderLineId", item.backorderLineId()));
                if (!backorder.isOpenForAllocation() || !backorder.getProductSkuId().equals(sku.getId())) {
                    throw invalid("BACKORDER_LINK_INVALID", item.backorderLineId());
                }
                if (order.getEstimatedArrivalAt() == null) {
                    throw invalid("BACKORDER_LINK_REQUIRES_ETA", item.backorderLineId());
                }
            }
            order.addLine(TransferOrderLine.builder().productSku(sku).preferredBatch(preferred)
                .backorderLineId(item.backorderLineId()).requestedQuantity(item.quantity()).build());
        }
    }

    private void requireTransferBatch(InventoryBatch batch, TransferOrder order, Long skuId) {
        requireTransferBatch(batch, order.getSourceWarehouse(), skuId);
    }

    private void requireTransferBatch(InventoryBatch batch, Warehouse source, Long skuId) {
        if (!batch.isSaleable() || batch.getLocation() == null
            || !batch.getLocation().getWarehouse().getId().equals(source.getId())
            || !batch.getProductSku().getId().equals(skuId)) {
            throw invalid("TRANSFER_SOURCE_BATCH_INVALID", batch.getId());
        }
    }

    private void requireDistinctWarehouses(Warehouse source, Warehouse destination) {
        if (source.getId().equals(destination.getId())) {
            throw invalid("TRANSFER_WAREHOUSES_MUST_DIFFER", source.getId());
        }
    }

    private Warehouse requireWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
            .orElseThrow(() -> notFound("warehouseId", id));
        if (!Boolean.TRUE.equals(warehouse.getIsActive())) throw invalid("WAREHOUSE_INACTIVE", id);
        return warehouse;
    }

    private ProductSku requireSku(Long id) {
        return productSkuRepository.findById(id).orElseThrow(() -> notFound("productSkuId", id));
    }

    private TransferOrder requireOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> notFound("transferId", id));
    }

    private TransferOrder requireOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("transferId", id));
    }

    private TransferWarehouseTask requireTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> notFound("transferTaskId", id));
    }

    private void requireStatus(TransferOrder order, TransferBusinessStatus status) {
        if (order.getBusinessStatus() != status) throw invalid("TRANSFER_STATUS_INVALID", order.getId());
    }

    private void requireVersion(Long actual, Long expected, Object id) {
        if (!Objects.equals(actual, expected)) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("reason", "OPTIMISTIC_VERSION_MISMATCH", "resourceId", id,
                    "expectedVersion", expected, "actualVersion", actual));
        }
    }

    private void review(TransferOrder order, Long actorId, String actorName, String comment) {
        order.setReviewedBy(actorId);
        order.setReviewedByName(actor(actorName));
        order.setReviewedAt(LocalDateTime.now());
        order.setReviewComment(required(comment, "comment", 500));
    }

    private void terminate(TransferOrder order, TransferBusinessStatus status, String reason,
                           Long actorId, String actorName) {
        order.setBusinessStatus(status);
        order.setOutboundStatus(TransferOutboundStatus.CLOSED);
        order.setInboundStatus(TransferInboundStatus.CLOSED);
        order.setTerminatedBy(actorId);
        order.setTerminatedByName(actor(actorName));
        order.setTerminatedAt(LocalDateTime.now());
        order.setTerminationReason(required(reason, "reason", 500));
        orderRepository.saveAndFlush(order);
    }

    private void closeTasks(TransferOrder order) {
        order.getTasks().forEach(task -> task.setStatus(TransferTaskStatus.CLOSED));
        taskRepository.saveAll(order.getTasks());
    }

    private LockedTransfer lockOrderThenTask(Long taskId) {
        Long orderId = taskRepository.findTransferOrderIdById(taskId)
            .orElseThrow(() -> notFound("transferTaskId", taskId));
        TransferOrder order = requireOrderForUpdate(orderId);
        TransferWarehouseTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> notFound("transferTaskId", taskId));
        if (!task.getTransferOrder().getId().equals(order.getId())) {
            throw invalid("TRANSFER_TASK_ORDER_MISMATCH", taskId);
        }
        return new LockedTransfer(order, task);
    }

    private void clearReview(TransferOrder order) {
        order.setReviewComment(null);
        order.setReviewedAt(null);
        order.setReviewedBy(null);
        order.setReviewedByName(null);
    }

    private record LockedTransfer(TransferOrder order, TransferWarehouseTask task) { }

    private Optional<TransferWarehouseTask> task(TransferOrder order, TransferTaskDirection direction) {
        return order.getTasks().stream().filter(item -> item.getDirection() == direction).findFirst();
    }

    private com.wms.system.entity.StockPackaging.Taken takePackaging(com.wms.system.entity.StockPackaging packaging,
        com.wms.system.entity.StockPackaging.Selection selection, int version, int quantity) {
        if (packaging == null) {
            if (selection != null) throw new BusinessException("PACKAGING_VERIFY_REQUIRED");
            return null;
        }
        if (selection == null && quantity == packaging.total())
            return new com.wms.system.entity.StockPackaging.Taken(com.wms.system.entity.StockPackaging.empty(packaging.packSize()), packaging);
        return packaging.take(selection, version, quantity);
    }

    private record ReceiptPackagingPlan(
        Map<ReceiveTransferRequest.Line, com.wms.system.entity.StockPackaging> taken,
        Map<Long, com.wms.system.entity.StockPackaging> remaining) {}

    private ReceiptPackagingPlan planReceiptPackaging(List<ReceiveTransferRequest.Line> lines,
        Map<Long, TransferDispatchLine> dispatches) {
        var taken = new IdentityHashMap<ReceiveTransferRequest.Line, com.wms.system.entity.StockPackaging>();
        var remaining = new HashMap<Long, com.wms.system.entity.StockPackaging>();
        for (var group : lines.stream().collect(Collectors.groupingBy(ReceiveTransferRequest.Line::dispatchLineId)).entrySet()) {
            var dispatch = dispatches.get(group.getKey());
            var original = dispatch.getPackaging();
            int version = Math.toIntExact(dispatch.getVersion());
            int sealed = 0, total = 0;
            var opened = original == null ? new ArrayList<Integer>()
                : new ArrayList<Integer>(Collections.nCopies(original.openedQuantities().size(), 0));
            var newlyOpened = new ArrayList<Integer>();
            for (var line : group.getValue()) {
                var result = takePackaging(original, line.packagingSelection(), version, line.quantity());
                if (result == null) continue;
                taken.put(line, result.taken());
                var selection = line.packagingSelection();
                if (selection == null) selection = new com.wms.system.entity.StockPackaging.Selection(
                    version, original.sealedBoxes(), original.openedQuantities(), List.of());
                sealed = Math.addExact(sealed, selection.sealedBoxes());
                total = Math.addExact(total, line.quantity());
                for (int i = 0; i < opened.size(); i++) opened.set(i, Math.addExact(opened.get(i), selection.openedQuantities().get(i)));
                if (selection.takeFromNewlyOpened() != null) newlyOpened.addAll(selection.takeFromNewlyOpened());
            }
            if (original != null) remaining.put(dispatch.getId(), original.take(
                new com.wms.system.entity.StockPackaging.Selection(version, sealed, opened, newlyOpened), version, total).remaining());
        }
        return new ReceiptPackagingPlan(taken, remaining);
    }

    private LinkedHashMap<Long, Integer> normalizeDispatchLines(List<DispatchTransferRequest.Line> lines) {
        LinkedHashMap<Long, Integer> result = new LinkedHashMap<>();
        for (DispatchTransferRequest.Line line : lines) {
            if (result.putIfAbsent(line.allocationId(), line.quantity()) != null) {
                throw invalid("DUPLICATE_TRANSFER_ALLOCATION", line.allocationId());
            }
        }
        return result;
    }

    private int totalRequested(TransferOrder order) {
        return order.getLines().stream().mapToInt(TransferOrderLine::getRequestedQuantity).sum();
    }

    private int totalAllocated(TransferOrder order) {
        return order.getLines().stream().mapToInt(TransferOrderLine::getAllocatedQuantity).sum();
    }

    private int totalDispatched(TransferOrder order) {
        return order.getLines().stream().mapToInt(TransferOrderLine::getDispatchedQuantity).sum();
    }

    private int totalReceived(TransferOrder order) {
        return order.getLines().stream().mapToInt(TransferOrderLine::getReceivedQuantity).sum();
    }

    private void appendEvent(TransferOrder order, String eventType, Map<String, ?> payload,
                             Long actorId, String actorName) {
        eventRepository.save(TransferEvent.builder().transferOrder(order).eventType(eventType)
            .payloadJson(serialize(payload)).actorId(actorId).actorName(actor(actorName))
            .occurredAt(LocalDateTime.now()).build());
    }

    private IdempotencyClaim claim(String key, String operation, Object request) {
        return idempotencyService.claim(key, operation, serialize(request));
    }

    private TransferResponse replayOrder(IdempotencyClaim claim, String operation) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return null;
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return get(Long.valueOf(claim.request().getResponseBody()));
        }
        requireAcquired(claim, operation);
        return null;
    }

    private void requireAcquired(IdempotencyClaim claim, String operation) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return;
        if (claim.decision() == IdempotencyClaimDecision.IN_PROGRESS) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", operation));
        }
        throw new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT, Map.of("operation", operation));
    }

    private TransferResponse toResponse(TransferOrder order) {
        List<TransferAllocation> allocations = allocationRepository
            .findByTransferLineTransferOrderIdOrderById(order.getId());
        Map<Long, List<TransferAllocation>> allocationsByLine = allocations.stream()
            .collect(Collectors.groupingBy(item -> item.getTransferLine().getId()));
        List<TransferResponse.Line> lines = order.getLines().stream().map(line ->
            new TransferResponse.Line(line.getId(), line.getVersion(), line.getProductSku().getId(),
                line.getProductSku().getSkuCode(), line.getProductSku().getName(),
                line.getPreferredBatch() == null ? null : line.getPreferredBatch().getId(),
                line.getBackorderLineId(), line.getRequestedQuantity(), line.getAllocatedQuantity(),
                line.getDispatchedQuantity(), line.inTransitQuantity(), line.getReceivedQuantity(),
                line.getDamagedQuantity(), line.getQuarantinedQuantity(),
                line.getClosedUnshippedQuantity(), line.getShortageClosedQuantity(),
                allocationsByLine.getOrDefault(line.getId(), List.of()).stream().map(this::toAllocation).toList()))
            .toList();
        List<TransferResponse.Dispatch> dispatches = dispatchRepository
            .findByTransferOrderIdOrderById(order.getId()).stream().map(this::toDispatch).toList();
        List<TransferResponse.Receipt> receipts = receiptRepository
            .findByTransferOrderIdOrderById(order.getId()).stream().map(this::toReceipt).toList();
        List<TransferResponse.Event> events = eventRepository
            .findByTransferOrderIdOrderById(order.getId()).stream().map(event ->
                new TransferResponse.Event(event.getId(), event.getEventType(), event.getPayloadJson(),
                    event.getActorId(), event.getActorName(), event.getOccurredAt())).toList();
        int requested = totalRequested(order), allocated = totalAllocated(order), dispatched = totalDispatched(order);
        int received = totalReceived(order);
        int damaged = order.getLines().stream().mapToInt(TransferOrderLine::getDamagedQuantity).sum();
        int quarantined = order.getLines().stream().mapToInt(TransferOrderLine::getQuarantinedQuantity).sum();
        int closed = order.getLines().stream().mapToInt(line ->
            line.getClosedUnshippedQuantity() + line.getShortageClosedQuantity()).sum();
        return new TransferResponse(order.getId(), order.getVersion(), order.getTransferNumber(),
            order.getSourceWarehouse().getId(), order.getSourceWarehouse().getCode(), order.getSourceWarehouse().getName(),
            order.getDestinationWarehouse().getId(), order.getDestinationWarehouse().getCode(), order.getDestinationWarehouse().getName(),
            order.getBusinessStatus(), order.getOutboundStatus(), order.getInboundStatus(),
            order.getApprovalModeSnapshot(), Boolean.TRUE.equals(order.getReceivingInspectionSnapshot()),
            order.getReason(), order.getEstimatedArrivalAt(), requested, allocated, dispatched,
            dispatched - received - order.getLines().stream().mapToInt(TransferOrderLine::getShortageClosedQuantity).sum(),
            received, damaged, quarantined, closed, order.getRequestedBy(), order.getRequestedByName(),
            order.getSubmittedAt(), order.getReviewedBy(), order.getReviewedByName(), order.getReviewedAt(),
            order.getReviewComment(), order.getTerminatedAt(), order.getTerminationReason(),
            order.getCreatedAt(), order.getUpdatedAt(), lines, dispatches, receipts, events);
    }

    private TransferResponse.Allocation toAllocation(TransferAllocation allocation) {
        InventoryBatch batch = allocation.getSourceBatch();
        return new TransferResponse.Allocation(allocation.getId(), batch.getId(), batch.getBatchCode(),
            batch.getExternalBatchCode(), batch.getLocation().getId(), batch.getLocationCode(),
            allocation.getAllocatedQuantity(), allocation.getConsumedQuantity(), allocation.getReleasedQuantity(),
            allocation.remainingQuantity(), allocation.getStatus(), batch.getProductionDate(), batch.getExpiryDate());
    }

    private TransferResponse.Dispatch toDispatch(TransferDispatch dispatch) {
        return new TransferResponse.Dispatch(dispatch.getId(), dispatch.getDispatchNumber(),
            dispatch.getHandoverReference(), dispatch.getEstimatedArrivalAt(), dispatch.getWarehouseRemark(),
            dispatch.getDispatchedBy(), dispatch.getDispatchedByName(), dispatch.getDispatchedAt(),
            dispatch.getLines().stream().map(line -> new TransferResponse.DispatchLine(
                line.getId(), line.getTransferLine().getId(), line.getAllocation().getId(),
                line.getSourceBatch().getId(), line.getSourceBatchCode(), line.getExternalBatchCode(),
                line.getProductionDate(), line.getExpiryDate(), line.getQuantity(), line.getReceivedQuantity(),
                line.getDamagedQuantity(), line.getQuarantinedQuantity(), line.getShortageClosedQuantity(),
                line.openQuantity())).toList());
    }

    private TransferResponse.Receipt toReceipt(TransferReceipt receipt) {
        return new TransferResponse.Receipt(receipt.getId(), receipt.getReceiptNumber(),
            receipt.getReceivedBy(), receipt.getReceivedByName(), receipt.getReceivedAt(), receipt.getRemark(),
            receipt.getLines().stream().map(line -> new TransferResponse.ReceiptLine(
                line.getId(), line.getDispatchLine().getId(), line.getTransferLine().getId(),
                line.getLocation().getId(), line.getLocation().getLocationCode(),
                line.getDestinationBatch().getId(), line.getDestinationBatch().getBatchCode(),
                line.getDisposition(), line.getQuantity())).toList());
    }

    private TransferWarehouseTaskResponse toTaskResponse(TransferWarehouseTask task) {
        TransferOrder order = task.getTransferOrder();
        List<TransferAllocation> allocations = allocationRepository
            .findByTransferLineTransferOrderIdOrderById(order.getId());
        Map<Long, List<TransferAllocation>> byLine = allocations.stream()
            .collect(Collectors.groupingBy(item -> item.getTransferLine().getId()));
        List<TransferWarehouseTaskResponse.Line> lines = order.getLines().stream().map(line ->
            new TransferWarehouseTaskResponse.Line(line.getId(), line.getProductSku().getId(),
                line.getProductSku().getSkuCode(), line.getProductSku().getName(), line.getRequestedQuantity(),
                line.getAllocatedQuantity(), line.getDispatchedQuantity(), line.getReceivedQuantity(),
                line.inTransitQuantity(), byLine.getOrDefault(line.getId(), List.of()).stream()
                    .filter(item -> item.remainingQuantity() > 0)
                    .map(item -> new TransferWarehouseTaskResponse.Allocation(item.getId(),
                        item.getSourceBatch().getId(), item.getSourceBatch().getBatchCode(),
                        item.getSourceBatch().getExternalBatchCode(), item.getSourceBatch().getLocation().getId(),
                        item.getSourceBatch().getLocationCode(), item.getSourceBatch().getProductionDate(),
                        item.getSourceBatch().getExpiryDate(), item.remainingQuantity())).toList()))
            .toList();
        List<TransferWarehouseTaskResponse.DispatchLine> dispatchLines = dispatchLineRepository
            .findByDispatchTransferOrderIdOrderById(order.getId()).stream().map(line ->
                new TransferWarehouseTaskResponse.DispatchLine(line.getId(), line.getDispatch().getDispatchNumber(),
                    line.getTransferLine().getId(), line.getTransferLine().getProductSku().getId(),
                    line.getTransferLine().getProductSku().getSkuCode(), line.getTransferLine().getProductSku().getName(),
                    line.getSourceBatch().getId(), line.getSourceBatchCode(), line.getExternalBatchCode(),
                    line.getProductionDate(), line.getExpiryDate(), line.getQuantity(), line.getReceivedQuantity(),
                    line.openQuantity(), line.getVersion(), line.getPackaging(), line.getTransferLine().getProductSku().getDisplayBaseUnit())).toList();
        return new TransferWarehouseTaskResponse(task.getId(), task.getVersion(), task.getTaskNumber(),
            task.getDirection(), task.getStatus(), task.getPriority(), task.getExpectedFrom(), task.getExpectedTo(),
            task.getAssignee(), task.getTeamName(), task.getOperationArea(), task.getWarehouseRemark(),
            order.getId(), order.getTransferNumber(), order.getSourceWarehouse().getId(), order.getSourceWarehouse().getName(),
            order.getDestinationWarehouse().getId(), order.getDestinationWarehouse().getName(),
            order.getBusinessStatus(), order.getOutboundStatus(), order.getInboundStatus(),
            order.getEstimatedArrivalAt(), lines, dispatchLines);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", "request"));
        }
    }

    private String number(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private String actor(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String required(String value, String field, int max) {
        String normalized = optional(value, field, max);
        if (normalized == null) throw validation(field);
        return normalized;
    }

    private String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw validation(field);
        return normalized;
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private BusinessException validation(String field) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
    }

    private BusinessException invalid(String reason, Object id) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED,
            Map.of("reason", reason, "resourceId", id));
    }

    private BusinessException notFound(String field, Object id) {
        return new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND,
            Map.of("resourceType", field, "resourceId", id));
    }
}
