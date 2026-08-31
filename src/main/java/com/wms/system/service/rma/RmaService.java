package com.wms.system.service.rma;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.rma.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.BackorderService;
import com.wms.system.service.DomainOutboxService;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import com.wms.system.service.inventory.InventoryQualityStateService;
import com.wms.system.service.inventory.InventoryQualityTransitionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RmaService {
    private static final String CREATE = "RMA_CREATE";
    private static final String APPROVE = "RMA_APPROVE";
    private static final String REJECT = "RMA_REJECT";
    private static final String CANCEL = "RMA_CANCEL";
    private static final String CLOSE = "RMA_CLOSE";
    private static final String RECEIVE = "RMA_RECEIVE";
    private static final String INSPECT = "RMA_INSPECT";
    private static final String ELIGIBILITY = "RMA_REFUND_ELIGIBILITY";
    private static final String RESOLVE = "RMA_RESOLVE";
    private static final String REFUND_CONFIRM = "RMA_REFUND_CONFIRM";
    private static final String TASK_UPDATE = "RMA_TASK_UPDATE";

    private final SalesReturnRmaRepository rmaRepository;
    private final SalesReturnRmaLineRepository rmaLineRepository;
    private final SalesReturnTaskRepository taskRepository;
    private final SalesReturnReceiptRepository receiptRepository;
    private final SalesReturnReceiptLineRepository receiptLineRepository;
    private final SalesReturnInspectionRepository inspectionRepository;
    private final SalesReturnInspectionLineRepository inspectionLineRepository;
    private final SalesReturnEventRepository eventRepository;
    private final SalesReturnChannelCaseRepository channelCaseRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SalesOrderShipmentRepository shipmentRepository;
    private final OutboundTaskRepository outboundTaskRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final InventoryBatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;
    private final InventoryQualityStateService qualityStateService;
    private final DomainOutboxService outboxService;
    private final BackorderService backorderService;
    private final RmaPolicyService policyService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<RmaResponse> list(Long salesOrderId) {
        List<SalesReturnRma> rmas = salesOrderId == null
            ? rmaRepository.findAllByOrderByIdDesc()
            : rmaRepository.findBySalesOrderIdOrderByIdDesc(salesOrderId);
        return rmas.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RmaResponse get(Long id) {
        return toResponse(requireRma(id));
    }

    @Transactional(readOnly = true)
    public List<RmaWarehouseTaskResponse> listTasks(RmaTaskStatus status, Set<Long> allowedWarehouseIds) {
        List<SalesReturnTask> tasks = status == null
            ? taskRepository.findAllByOrderByPriorityAscIdDesc()
            : taskRepository.findByStatusOrderByPriorityAscIdDesc(status);
        return tasks.stream()
            .filter(task -> allowedWarehouseIds == null || allowedWarehouseIds.contains(task.getWarehouse().getId()))
            .map(this::toWarehouseTask)
            .toList();
    }

    @Transactional(readOnly = true)
    public RmaWarehouseTaskResponse getTask(Long id) {
        return toWarehouseTask(requireTask(id));
    }

    @Transactional(readOnly = true)
    public Long warehouseIdForTask(Long taskId) {
        return requireTask(taskId).getWarehouse().getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse create(CreateRmaRequest request, String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, CREATE, request);
        RmaResponse replay = replay(claim, CREATE);
        if (replay != null) return replay;

        SalesOrder order = salesOrderRepository.findByIdForUpdate(request.salesOrderId())
            .orElseThrow(() -> notFound("salesOrderId", request.salesOrderId()));
        SalesReturnChannelCase channelCase = null;
        if (request.channelCaseId() != null) {
            channelCase = channelCaseRepository.findByIdForUpdate(request.channelCaseId())
                .orElseThrow(() -> notFound("channelCaseId", request.channelCaseId()));
            if (channelCase.getStatus() != RmaChannelCaseStatus.MATCHED_PENDING
                || channelCase.getSalesOrder() == null
                || !Objects.equals(channelCase.getSalesOrder().getId(), order.getId())) {
                throw invalid("CHANNEL_CASE_NOT_MATCHED_TO_ORDER", request.channelCaseId());
            }
        }
        if (request.rmaType() == RmaType.REFUND_ONLY && !policyService.get().refundOnlyEnabled()) {
            throw invalid("REFUND_ONLY_DISABLED", request.salesOrderId());
        }
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
            .orElseThrow(() -> notFound("warehouseId", request.warehouseId()));
        SalesOrderShipment shipment = request.shipmentId() == null ? null
            : shipmentRepository.findByIdAndSalesOrderId(request.shipmentId(), order.getId())
                .orElseThrow(() -> invalid("SHIPMENT_NOT_LINKED_TO_ORDER", request.shipmentId()));

        LinkedHashMap<Long, Integer> requested = normalizeCreateLines(request.lines());
        List<LineEvidence> evidence = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            OutboundTask task = outboundTaskRepository.findByIdForUpdate(entry.getKey())
                .orElseThrow(() -> notFound("outboundTaskId", entry.getKey()));
            if (!Objects.equals(task.getSalesOrderId(), order.getId())
                || task.getStatus() != OutboundTaskStatus.COMPLETED
                || value(task.getActualQty()) <= 0) {
                throw invalid("COMPLETED_DISPATCH_EVIDENCE_REQUIRED", task.getId());
            }
            SalesOrderItem item = salesOrderItemRepository.findByIdForUpdate(task.getSalesOrderItemId())
                .orElseThrow(() -> notFound("salesOrderItemId", task.getSalesOrderItemId()));
            InventoryBatch originalBatch = batchRepository.findById(task.getAssignedBatchId())
                .orElseThrow(() -> notFound("originalBatchId", task.getAssignedBatchId()));
            int alreadyClaimed = value(rmaLineRepository.sumActiveRequestedByOutboundTaskId(task.getId()));
            if (entry.getValue() > value(task.getActualQty()) - alreadyClaimed) {
                throw invalid("RETURN_QUANTITY_EXCEEDS_DISPATCH_REMAINDER", task.getId());
            }
            evidence.add(new LineEvidence(task, item, originalBatch, entry.getValue()));
        }

        RmaPolicyResponse policy = policyService.get();
        boolean outsideWindow = outsideStandardWindow(order, shipment, evidence, policy.standardReturnDays());
        boolean risk = request.rmaType() == RmaType.REFUND_ONLY
            || (outsideWindow && request.statutoryBasis() == RmaStatutoryBasis.VOLUNTARY_POLICY);
        RmaBusinessStatus status = policy.approvalMode() == RmaApprovalMode.ALWAYS
            || policy.approvalMode() == RmaApprovalMode.RISK_BASED && risk
            ? RmaBusinessStatus.PENDING_APPROVAL : RmaBusinessStatus.AUTHORIZED;

        SalesReturnRma rma = SalesReturnRma.builder()
            .rmaNumber(number("RMA"))
            .salesOrder(order)
            .shipment(shipment)
            .warehouse(warehouse)
            .rmaType(request.rmaType())
            .businessStatus(status)
            .receiptStatus(request.rmaType() == RmaType.REFUND_ONLY
                ? RmaReceiptStatus.NOT_EXPECTED : RmaReceiptStatus.EXPECTED)
            .inspectionStatus(request.rmaType() == RmaType.REFUND_ONLY
                ? RmaInspectionStatus.NOT_REQUIRED : RmaInspectionStatus.PENDING)
            .resolutionStatus(RmaResolutionStatus.PENDING)
            .approvalModeSnapshot(policy.approvalMode())
            .customerTypeSnapshot(order.getCustomer().getCustomerType())
            .statutoryBasis(request.statutoryBasis())
            .standardReturnDaysSnapshot(policy.standardReturnDays())
            .outsideStandardWindow(outsideWindow)
            .reasonCode(required(request.reasonCode(), "reasonCode", 80))
            .reasonDetail(optional(request.reasonDetail(), "reasonDetail", 1000))
            .customerShippedAt(request.customerShippedAt())
            .returnCarrier(optional(request.returnCarrier(), "returnCarrier", 80))
            .returnTrackingNo(optional(request.returnTrackingNo(), "returnTrackingNo", 120))
            .channel(channelCase == null ? null : channelCase.getChannel())
            .externalCaseId(channelCase == null ? null : channelCase.getExternalCaseId())
            .requestedBy(actorId)
            .requestedByName(actor(actorName))
            .requestedAt(LocalDateTime.now())
            .build();
        for (LineEvidence line : evidence) {
            rma.addLine(SalesReturnRmaLine.builder()
                .salesOrderItem(line.item())
                .outboundTask(line.task())
                .originalBatch(line.originalBatch())
                .productSku(line.originalBatch().getProductSku())
                .requestedQuantity(line.quantity())
                .authorizedQuantity(status == RmaBusinessStatus.AUTHORIZED ? line.quantity() : 0)
                .unitPriceSnapshot(line.item().getUnitPrice())
                .build());
        }
        rma = rmaRepository.saveAndFlush(rma);
        if (channelCase != null) {
            channelCase.setStatus(RmaChannelCaseStatus.RMA_CREATED);
            channelCase.setRmaId(rma.getId());
            channelCaseRepository.save(channelCase);
        }
        if (status == RmaBusinessStatus.AUTHORIZED && request.rmaType() != RmaType.REFUND_ONLY) {
            createExpectedTask(rma);
        }
        appendEvent(rma, status == RmaBusinessStatus.AUTHORIZED ? "RMA_AUTHORIZED" : "RMA_APPROVAL_REQUESTED",
            Map.of("approvalMode", policy.approvalMode().name(), "outsideStandardWindow", outsideWindow), actorId, actorName);
        outboxService.append("RMA_CREATED", "SalesReturnRma", rma.getId(),
            Map.of("rmaId", rma.getId(), "rmaNumber", rma.getRmaNumber(), "businessStatus", status.name()));
        idempotencyService.markSucceeded(claim.request(), 201, rma.getId().toString());
        return toResponse(rma);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse approve(Long id, RmaDecisionRequest request, String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, APPROVE + ":" + id, request);
        RmaResponse replay = replay(claim, APPROVE + ":" + id);
        if (replay != null) return replay;
        SalesReturnRma rma = requireRmaForUpdate(id);
        requireStatus(rma, RmaBusinessStatus.PENDING_APPROVAL);
        if (Objects.equals(rma.getRequestedBy(), actorId)) throw invalid("REQUESTER_CANNOT_APPROVE", id);
        rma.setBusinessStatus(RmaBusinessStatus.AUTHORIZED);
        rma.getLines().forEach(line -> line.setAuthorizedQuantity(line.getRequestedQuantity()));
        rma.setReviewedBy(actorId);
        rma.setReviewedByName(actor(actorName));
        rma.setReviewedAt(LocalDateTime.now());
        rma.setReviewComment(required(request.comment(), "comment", 500));
        rmaRepository.saveAndFlush(rma);
        if (rma.getRmaType() != RmaType.REFUND_ONLY) createExpectedTask(rma);
        appendEvent(rma, "RMA_APPROVED", Map.of("comment", request.comment()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, rma.getId().toString());
        return toResponse(rma);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse reject(Long id, RmaDecisionRequest request, String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, REJECT + ":" + id, request);
        RmaResponse replay = replay(claim, REJECT + ":" + id);
        if (replay != null) return replay;
        SalesReturnRma rma = requireRmaForUpdate(id);
        requireStatus(rma, RmaBusinessStatus.PENDING_APPROVAL);
        if (Objects.equals(rma.getRequestedBy(), actorId)) throw invalid("REQUESTER_CANNOT_REVIEW", id);
        rma.setBusinessStatus(RmaBusinessStatus.REJECTED);
        rma.setReviewedBy(actorId);
        rma.setReviewedByName(actor(actorName));
        rma.setReviewedAt(LocalDateTime.now());
        rma.setReviewComment(required(request.comment(), "comment", 500));
        rmaRepository.saveAndFlush(rma);
        appendEvent(rma, "RMA_REJECTED", Map.of("comment", request.comment()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, rma.getId().toString());
        return toResponse(rma);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse cancel(Long id, RmaReasonRequest request, String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, CANCEL + ":" + id, request);
        RmaResponse replay = replay(claim, CANCEL + ":" + id);
        if (replay != null) return replay;
        SalesReturnRma rma = requireRmaForUpdate(id);
        if (rma.getBusinessStatus() != RmaBusinessStatus.AUTHORIZED
            && rma.getBusinessStatus() != RmaBusinessStatus.PENDING_APPROVAL) {
            throw invalid("RMA_NOT_CANCELLABLE", id);
        }
        if (rma.getLines().stream().mapToInt(line -> value(line.getReceivedQuantity())).sum() > 0
            || rma.getResolutionStatus() != RmaResolutionStatus.PENDING) {
            throw invalid("RMA_HAS_DOWNSTREAM_RECORDS", id);
        }
        rma.setBusinessStatus(RmaBusinessStatus.CANCELLED);
        rma.setReceiptStatus(rma.getRmaType() == RmaType.REFUND_ONLY
            ? RmaReceiptStatus.NOT_EXPECTED : RmaReceiptStatus.CLOSED);
        terminate(rma, request.reason(), actorId, actorName);
        taskRepository.findByRmaId(id).ifPresent(task -> {
            task.setStatus(RmaTaskStatus.CLOSED);
            taskRepository.save(task);
        });
        appendEvent(rma, "RMA_CANCELLED", Map.of("reason", request.reason()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, rma.getId().toString());
        return toResponse(rmaRepository.saveAndFlush(rma));
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse closeRemainder(Long id, RmaReasonRequest request, String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, CLOSE + ":" + id, request);
        RmaResponse replay = replay(claim, CLOSE + ":" + id);
        if (replay != null) return replay;
        SalesReturnRma rma = requireRmaForUpdate(id);
        requireStatus(rma, RmaBusinessStatus.AUTHORIZED);
        int authorized = rma.getLines().stream().mapToInt(line -> value(line.getAuthorizedQuantity())).sum();
        int received = rma.getLines().stream().mapToInt(line -> value(line.getReceivedQuantity())).sum();
        if (received <= 0 || received >= authorized) throw invalid("NO_PARTIAL_REMAINDER_TO_CLOSE", id);
        rma.setBusinessStatus(RmaBusinessStatus.CLOSED);
        rma.setReceiptStatus(RmaReceiptStatus.CLOSED);
        terminate(rma, request.reason(), actorId, actorName);
        taskRepository.findByRmaId(id).ifPresent(task -> {
            task.setStatus(RmaTaskStatus.CLOSED);
            taskRepository.save(task);
        });
        appendEvent(rma, "RMA_REMAINDER_CLOSED", Map.of("closedQuantity", authorized - received,
            "reason", request.reason()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, rma.getId().toString());
        return toResponse(rmaRepository.saveAndFlush(rma));
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaWarehouseTaskResponse updateTask(Long taskId, UpdateRmaTaskRequest request,
                                                String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, TASK_UPDATE + ":" + taskId, request);
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return getTask(Long.valueOf(claim.request().getResponseBody()));
        }
        requireAcquired(claim, TASK_UPDATE);
        SalesReturnTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> notFound("returnTaskId", taskId));
        if (task.getStatus() == RmaTaskStatus.CLOSED || task.getStatus() == RmaTaskStatus.RECEIVED) {
            throw invalid("RETURN_TASK_TERMINAL", taskId);
        }
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
        appendEvent(task.getRma(), "RMA_TASK_UPDATED", Map.of("taskId", taskId), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, task.getId().toString());
        return toWarehouseTask(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse receive(Long taskId, ReceiveRmaRequest request, String key,
                               Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, RECEIVE + ":" + taskId, request);
        RmaResponse replay = replay(claim, RECEIVE + ":" + taskId);
        if (replay != null) return replay;
        SalesReturnTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> notFound("returnTaskId", taskId));
        SalesReturnRma rma = requireRmaForUpdate(task.getRma().getId());
        if (rma.getBusinessStatus() != RmaBusinessStatus.AUTHORIZED
            || task.getStatus() == RmaTaskStatus.CLOSED || task.getStatus() == RmaTaskStatus.RECEIVED) {
            throw invalid("RETURN_TASK_NOT_RECEIVABLE", taskId);
        }
        LinkedHashMap<Long, ReceiveRmaRequest.Line> requested = normalizeReceiptLines(request.lines());
        List<SalesReturnRmaLine> lines = rmaLineRepository.findAllByIdForUpdate(requested.keySet());
        if (lines.size() != requested.size()
            || lines.stream().anyMatch(line -> !line.getRma().getId().equals(rma.getId()))) {
            throw invalid("RMA_LINE_MISMATCH", taskId);
        }
        Map<Long, Location> locations = new HashMap<>();
        for (ReceiveRmaRequest.Line lineRequest : requested.values()) {
            Location location = locationRepository.findByIdForInventoryPosting(lineRequest.locationId())
                .orElseThrow(() -> notFound("locationId", lineRequest.locationId()));
            if (!Objects.equals(location.getWarehouse().getId(), task.getWarehouse().getId())
                || !Boolean.TRUE.equals(location.getEnabled())) {
                throw invalid("RETURN_LOCATION_OUTSIDE_TASK_WAREHOUSE", location.getId());
            }
            locations.put(location.getId(), location);
        }

        SalesReturnReceipt receipt = SalesReturnReceipt.builder()
            .rma(rma).task(task).receiptNumber(number("RRC"))
            .receivedBy(actorId).receivedByName(actor(actorName)).receivedAt(LocalDateTime.now())
            .remark(optional(request.remark(), "remark", 500)).build();
        int ordinal = 0;
        for (SalesReturnRmaLine line : lines) {
            ReceiveRmaRequest.Line lineRequest = requested.get(line.getId());
            int remaining = value(line.getAuthorizedQuantity()) - value(line.getReceivedQuantity());
            if (lineRequest.quantity() > remaining) {
                throw invalid("RECEIPT_QUANTITY_EXCEEDS_AUTHORIZED_REMAINDER", line.getId());
            }
            Location location = locations.get(lineRequest.locationId());
            String segment = "RMA-" + rma.getId() + "-" + (++ordinal) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            InventoryBatch original = line.getOriginalBatch();
            InventoryBatch returnBatch = batchRepository.saveAndFlush(InventoryBatch.builder()
                .batchCode(segment).locationCode(location.getLocationCode()).productSku(line.getProductSku())
                .location(location).quantity(lineRequest.quantity()).reservedQuantity(0)
                .qualityStatus(InventoryQualityStatus.RETURN_INSPECTION)
                .qualityParentBatchId(original.getId()).qualitySegmentNo(segment)
                .initialQuantity(lineRequest.quantity()).expiryDate(original.getExpiryDate())
                .productionDate(original.getProductionDate()).externalBatchCode(original.getExternalBatchCode())
                .entryDate(LocalDateTime.now()).active(true)
                .remark("Customer return received under " + rma.getRmaNumber()).build());
            SalesReturnReceiptLine receiptLine = SalesReturnReceiptLine.builder()
                .rmaLine(line).location(location).returnBatch(returnBatch).quantity(lineRequest.quantity()).build();
            receipt.addLine(receiptLine);
            line.setReceivedQuantity(value(line.getReceivedQuantity()) + lineRequest.quantity());
            rmaLineRepository.save(line);
            String postingId = "RMA-IN-" + rma.getId() + "-" + UUID.randomUUID();
            transactionRepository.save(StockTransaction.builder()
                .productSku(line.getProductSku()).location(location).warehouseId(task.getWarehouse().getId())
                .inventoryBatchId(returnBatch.getId()).batchCode(returnBatch.getBatchCode())
                .postingId(postingId).idempotencyKey(shortText(key, 120))
                .transactionType(TransactionType.IN).sourceType(SourceType.RETURN_IN)
                .quantity(lineRequest.quantity()).quantityBefore(0).quantityAfter(lineRequest.quantity())
                .sourceOrderId(shortText(rma.getRmaNumber(), 50)).operatorId(actorId)
                .operatorName(actor(actorName)).reasonCode("CUSTOMER_RETURN_RECEIVED")
                .remarks("Received into RETURN_INSPECTION; original dispatch batch " + original.getBatchCode())
                .remark("RMA receipt " + receipt.getReceiptNumber()).build());
            outboxService.append("INVENTORY_POSTED", "InventoryBatch", returnBatch.getId(),
                Map.of("postingId", postingId, "sourceType", SourceType.RETURN_IN.name(),
                    "rmaId", rma.getId(), "quantity", lineRequest.quantity()));
        }
        receipt = receiptRepository.saveAndFlush(receipt);
        refreshReceiptState(rma, task);
        rmaRepository.saveAndFlush(rma);
        taskRepository.saveAndFlush(task);
        appendEvent(rma, "RMA_RETURN_RECEIVED", Map.of("receiptId", receipt.getId(),
            "receiptNumber", receipt.getReceiptNumber()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 201, rma.getId().toString());
        return toResponse(rma);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse inspect(Long taskId, InspectRmaRequest request, String key,
                               Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, INSPECT + ":" + taskId, request);
        RmaResponse replay = replay(claim, INSPECT + ":" + taskId);
        if (replay != null) return replay;
        SalesReturnTask task = requireTask(taskId);
        SalesReturnRma rma = requireRmaForUpdate(task.getRma().getId());
        if (rma.getReceiptStatus() == RmaReceiptStatus.EXPECTED
            || rma.getReceiptStatus() == RmaReceiptStatus.NOT_EXPECTED) {
            throw invalid("RMA_HAS_NO_RECEIVED_GOODS", rma.getId());
        }
        List<Long> ids = request.lines().stream().map(InspectRmaRequest.Line::receiptLineId).distinct().toList();
        List<SalesReturnReceiptLine> receiptLines = receiptLineRepository.findAllByIdForUpdate(ids);
        if (receiptLines.size() != ids.size()
            || receiptLines.stream().anyMatch(line -> !line.getReceipt().getRma().getId().equals(rma.getId()))) {
            throw invalid("RECEIPT_LINE_MISMATCH", rma.getId());
        }
        Map<Long, SalesReturnReceiptLine> byId = receiptLines.stream()
            .collect(Collectors.toMap(SalesReturnReceiptLine::getId, Function.identity()));
        Map<Long, Integer> submittedByReceiptLine = new HashMap<>();
        for (InspectRmaRequest.Line line : request.lines()) {
            submittedByReceiptLine.merge(line.receiptLineId(), line.quantity(), Math::addExact);
        }
        for (Map.Entry<Long, Integer> entry : submittedByReceiptLine.entrySet()) {
            SalesReturnReceiptLine source = byId.get(entry.getKey());
            int prior = value(inspectionLineRepository.sumInspectedByReceiptLineId(entry.getKey()));
            if (entry.getValue() > source.getQuantity() - prior) {
                throw invalid("INSPECTION_QUANTITY_EXCEEDS_RECEIVED_REMAINDER", entry.getKey());
            }
        }

        SalesReturnInspection inspection = SalesReturnInspection.builder()
            .rma(rma).inspectionNumber(number("RIN")).inspectedBy(actorId)
            .inspectedByName(actor(actorName)).inspectedAt(LocalDateTime.now())
            .reasonDetail(optional(request.reasonDetail(), "reasonDetail", 500)).build();
        int ordinal = 0;
        for (InspectRmaRequest.Line requestLine : request.lines()) {
            SalesReturnReceiptLine receiptLine = byId.get(requestLine.receiptLineId());
            InventoryBatch source = batchRepository.findByIdForPosting(receiptLine.getReturnBatch().getId())
                .orElseThrow(() -> notFound("returnBatchId", receiptLine.getReturnBatch().getId()));
            if (source.getQualityStatus() != InventoryQualityStatus.RETURN_INSPECTION
                || !source.isActive() || source.getQuantity() < requestLine.quantity()) {
                throw invalid("RETURN_BATCH_NOT_INSPECTABLE", source.getId());
            }
            InventoryQualityStatus target = targetStatus(requestLine.disposition());
            InventoryBatch result = transitionQuantity(source, requestLine.quantity(), target,
                rma, inspection.getInspectionNumber(), ++ordinal, actorId, actorName);
            inspection.addLine(SalesReturnInspectionLine.builder()
                .receiptLine(receiptLine).disposition(requestLine.disposition())
                .quantity(requestLine.quantity()).refundEligibleQuantity(0)
                .resultBatch(result).build());
            SalesReturnRmaLine rmaLine = receiptLine.getRmaLine();
            rmaLine.setInspectedQuantity(value(rmaLine.getInspectedQuantity()) + requestLine.quantity());
            switch (requestLine.disposition()) {
                case RESTOCK_AVAILABLE -> {
                    rmaLine.setRestockQuantity(value(rmaLine.getRestockQuantity()) + requestLine.quantity());
                    backorderService.wakeProduct(rmaLine.getProductSku().getId());
                }
                case DAMAGED -> rmaLine.setDamagedQuantity(value(rmaLine.getDamagedQuantity()) + requestLine.quantity());
                case QUARANTINE -> rmaLine.setQuarantineQuantity(value(rmaLine.getQuarantineQuantity()) + requestLine.quantity());
                case REJECT_CUSTOMER -> rmaLine.setRejectedQuantity(value(rmaLine.getRejectedQuantity()) + requestLine.quantity());
            }
            rmaLineRepository.save(rmaLine);
        }
        inspection = inspectionRepository.saveAndFlush(inspection);
        refreshInspectionState(rma);
        if (rma.getLines().stream().mapToInt(line -> value(line.getRejectedQuantity())).sum() > 0
            && rma.getResolutionStatus() == RmaResolutionStatus.PENDING) {
            rma.setResolutionStatus(RmaResolutionStatus.REJECTED_GOODS_PENDING);
        }
        rmaRepository.saveAndFlush(rma);
        appendEvent(rma, "RMA_INSPECTION_RECORDED", Map.of("inspectionId", inspection.getId(),
            "inspectionNumber", inspection.getInspectionNumber()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 201, rma.getId().toString());
        return toResponse(rma);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse setRefundEligibility(Long id, SetRmaRefundEligibilityRequest request,
                                            String key, Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, ELIGIBILITY + ":" + id, request);
        RmaResponse replay = replay(claim, ELIGIBILITY + ":" + id);
        if (replay != null) return replay;
        SalesReturnRma rma = requireRmaForUpdate(id);
        if (rma.getRmaType() == RmaType.REFUND_ONLY) throw invalid("REFUND_ONLY_USES_AUTHORIZED_QUANTITY", id);
        if (rma.getInspectionStatus() == RmaInspectionStatus.PENDING
            || rma.getInspectionStatus() == RmaInspectionStatus.NOT_REQUIRED
            || rma.getResolutionStatus() != RmaResolutionStatus.PENDING
                && rma.getResolutionStatus() != RmaResolutionStatus.REJECTED_GOODS_PENDING) {
            throw invalid("REFUND_ELIGIBILITY_NOT_EDITABLE", id);
        }
        LinkedHashMap<Long, Integer> values = new LinkedHashMap<>();
        request.lines().forEach(line -> {
            if (values.putIfAbsent(line.rmaLineId(), line.eligibleQuantity()) != null) {
                throw validation("lines.rmaLineId");
            }
        });
        List<SalesReturnRmaLine> lines = rmaLineRepository.findAllByIdForUpdate(values.keySet());
        if (lines.size() != values.size()
            || lines.stream().anyMatch(line -> !line.getRma().getId().equals(rma.getId()))) {
            throw invalid("RMA_LINE_MISMATCH", id);
        }
        Map<Long, Integer> changes = new LinkedHashMap<>();
        for (SalesReturnRmaLine line : lines) {
            int eligible = values.get(line.getId());
            if (eligible > value(line.getInspectedQuantity())) {
                throw invalid("REFUND_ELIGIBILITY_EXCEEDS_INSPECTED", line.getId());
            }
            changes.put(line.getId(), eligible);
            line.setRefundEligibleQuantity(eligible);
            rmaLineRepository.save(line);
        }
        appendEvent(rma, "RMA_REFUND_ELIGIBILITY_SET", Map.of("lines", changes,
            "reason", request.reason()), actorId, actorName);
        idempotencyService.markSucceeded(claim.request(), 200, rma.getId().toString());
        return toResponse(rma);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse resolve(Long id, RequestRmaResolutionRequest request, String key,
                               Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, RESOLVE + ":" + id, request);
        RmaResponse replay = replay(claim, RESOLVE + ":" + id);
        if (replay != null) return replay;
        SalesReturnRma rma = requireRmaForUpdate(id);
        if (rma.getBusinessStatus() != RmaBusinessStatus.AUTHORIZED
            && rma.getBusinessStatus() != RmaBusinessStatus.CLOSED) {
            throw invalid("RMA_NOT_AUTHORIZED_FOR_RESOLUTION", id);
        }
        boolean unresolved = rma.getResolutionStatus() == RmaResolutionStatus.PENDING
            || rma.getResolutionStatus() == RmaResolutionStatus.REJECTED_GOODS_PENDING
                && rma.getRefundReference() == null && rma.getReplacementSalesOrderId() == null;
        if (!unresolved) {
            throw invalid("RMA_RESOLUTION_ALREADY_STARTED", id);
        }
        if (request.advance() && !policyService.get().advanceResolutionEnabled()) {
            throw invalid("ADVANCE_RESOLUTION_DISABLED", id);
        }
        if (!request.advance() && rma.getRmaType() != RmaType.REFUND_ONLY
            && rma.getInspectionStatus() != RmaInspectionStatus.COMPLETED) {
            throw invalid("INSPECTION_NOT_COMPLETED", id);
        }
        rma.setAdvanceResolution(request.advance());
        if (request.resolutionType() == RequestRmaResolutionRequest.ResolutionType.REFUND) {
            requestRefund(rma, request, actorId, actorName);
        } else {
            createExchange(rma, request, actorId, actorName);
        }
        rmaRepository.saveAndFlush(rma);
        idempotencyService.markSucceeded(claim.request(), 202, rma.getId().toString());
        return toResponse(rma);
    }

    @Transactional(rollbackFor = Exception.class)
    public RmaResponse confirmRefund(Long id, ConfirmRmaRefundRequest request, String key,
                                     Long actorId, String actorName) {
        IdempotencyClaim claim = claim(key, REFUND_CONFIRM + ":" + id, request);
        RmaResponse replay = replay(claim, REFUND_CONFIRM + ":" + id);
        if (replay != null) return replay;
        SalesReturnRma rma = requireRmaForUpdate(id);
        if (rma.getResolutionStatus() != RmaResolutionStatus.REFUND_REQUESTED
            && rma.getResolutionStatus() != RmaResolutionStatus.EXCEPTION) {
            throw invalid("REFUND_NOT_PENDING", id);
        }
        if (rma.getRefundAmount() == null || request.amount().compareTo(rma.getRefundAmount()) != 0
            || !request.currency().equals(rma.getRefundCurrency())) {
            throw invalid("REFUND_CONFIRMATION_DOES_NOT_MATCH_REQUEST", id);
        }
        rma.setRefundReference(required(request.reference(), "reference", 120));
        rma.setRefundAmount(request.amount());
        rma.setRefundCurrency(request.currency());
        if (request.succeeded()) {
            rma.setResolutionStatus(rma.getLines().stream().mapToInt(line -> value(line.getRejectedQuantity())).sum() > 0
                ? RmaResolutionStatus.REJECTED_GOODS_PENDING : RmaResolutionStatus.REFUND_CONFIRMED);
            appendEvent(rma, "RMA_REFUND_CONFIRMED", Map.of("reference", request.reference(),
                "amount", request.amount(), "currency", request.currency()), actorId, actorName);
        } else {
            rma.setResolutionStatus(RmaResolutionStatus.EXCEPTION);
            appendEvent(rma, "RMA_REFUND_FAILED", Map.of("reference", request.reference(),
                "failureReason", required(request.failureReason(), "failureReason", 500)), actorId, actorName);
        }
        rmaRepository.saveAndFlush(rma);
        idempotencyService.markSucceeded(claim.request(), 200, rma.getId().toString());
        return toResponse(rma);
    }

    private void requestRefund(SalesReturnRma rma, RequestRmaResolutionRequest request,
                               Long actorId, String actorName) {
        int eligibleQty = rma.getLines().stream().mapToInt(line -> request.advance()
            || rma.getRmaType() == RmaType.REFUND_ONLY
                ? value(line.getAuthorizedQuantity()) : value(line.getRefundEligibleQuantity())).sum();
        if (eligibleQty <= 0) throw invalid("NO_REFUND_ELIGIBLE_QUANTITY", rma.getId());
        BigDecimal ceiling = rma.getLines().stream()
            .map(line -> line.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(
                request.advance() || rma.getRmaType() == RmaType.REFUND_ONLY
                    ? value(line.getAuthorizedQuantity()) : value(line.getRefundEligibleQuantity()))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = request.refundAmount() == null ? ceiling : request.refundAmount();
        if (amount.signum() < 0 || amount.compareTo(ceiling) > 0) {
            throw invalid("REFUND_AMOUNT_EXCEEDS_ELIGIBLE_VALUE", rma.getId());
        }
        String currency = request.currency() == null ? "GBP" : request.currency();
        rma.setRefundAmount(amount);
        rma.setRefundCurrency(currency);
        rma.setResolutionStatus(RmaResolutionStatus.REFUND_REQUESTED);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rmaId", rma.getId());
        payload.put("rmaNumber", rma.getRmaNumber());
        payload.put("salesOrderId", rma.getSalesOrder().getId());
        payload.put("amount", amount);
        payload.put("currency", currency);
        payload.put("advance", request.advance());
        payload.put("reason", optional(request.reason(), "reason", 500));
        outboxService.append("RMA_REFUND_REQUESTED", "SalesReturnRma", rma.getId(), payload);
        appendEvent(rma, "RMA_REFUND_REQUESTED", payload, actorId, actorName);
    }

    private void createExchange(SalesReturnRma rma, RequestRmaResolutionRequest request,
                                Long actorId, String actorName) {
        if (rma.getRmaType() != RmaType.EXCHANGE) throw invalid("RMA_NOT_EXCHANGE_TYPE", rma.getId());
        if (rma.getReplacementSalesOrderId() != null) throw invalid("EXCHANGE_ALREADY_CREATED", rma.getId());
        if (request.exchangeLines() == null || request.exchangeLines().isEmpty()) throw validation("exchangeLines");
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        request.exchangeLines().forEach(line -> quantities.merge(line.rmaLineId(), line.quantity(), Math::addExact));
        Map<Long, SalesReturnRmaLine> available = rma.getLines().stream()
            .collect(Collectors.toMap(SalesReturnRmaLine::getId, Function.identity()));
        SalesOrder original = rma.getSalesOrder();
        SalesOrder replacement = SalesOrder.builder()
            .orderNo(exchangeOrderNumber(rma.getId()))
            .orderPurpose(SalesOrderPurpose.EXCHANGE_REPLACEMENT)
            .sourceRmaId(rma.getId())
            .customerId(original.getCustomerId())
            .totalAmount(BigDecimal.ZERO)
            .commercialStatus(CommercialStatus.DRAFT)
            .allocationStatus(SalesAllocationStatus.UNALLOCATED)
            .warehouseStatus(SalesWarehouseStatus.NOT_RELEASED)
            .logisticsStatus(SalesLogisticsStatus.NOT_CREATED)
            .approvalModeSnapshot(original.getApprovalModeSnapshot())
            .allocationPolicy(original.getAllocationPolicy())
            .applicantId(actorId).applicantName(actor(actorName))
            .channel("RMA_EXCHANGE").externalOrderId("RMA:" + rma.getId())
            .externalOrderNo(rma.getRmaNumber())
            .consigneeName(original.getConsigneeName()).consigneePhone(original.getConsigneePhone())
            .shipAddress1(original.getShipAddress1()).shipAddress2(original.getShipAddress2())
            .shipCity(original.getShipCity()).shipProvince(original.getShipProvince())
            .shipZip(original.getShipZip()).shipCountryCode(original.getShipCountryCode())
            .statusReason("Exchange replacement for " + rma.getRmaNumber())
            .statusChangedBy(actorId).statusChangedByName(actor(actorName)).statusChangedAt(LocalDateTime.now())
            .build();
        replacement = salesOrderRepository.saveAndFlush(replacement);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            SalesReturnRmaLine source = available.get(entry.getKey());
            if (source == null) throw invalid("EXCHANGE_LINE_NOT_IN_RMA", entry.getKey());
            int allowed = request.advance() ? value(source.getAuthorizedQuantity()) : value(source.getRefundEligibleQuantity());
            if (entry.getValue() > allowed) throw invalid("EXCHANGE_QUANTITY_EXCEEDS_ELIGIBLE", entry.getKey());
            BigDecimal subtotal = source.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(entry.getValue()));
            salesOrderItemRepository.save(SalesOrderItem.builder()
                .salesOrderId(replacement.getId()).productSkuId(source.getProductSku().getId())
                .quantity(entry.getValue()).requestedQty(entry.getValue()).allocatedQty(0)
                .shippedQty(0).backorderQty(0).cancelledQty(0)
                .unitPrice(source.getUnitPriceSnapshot()).subtotal(subtotal)
                .productPriceSnapshot(source.getSalesOrderItem().getProductPriceSnapshot())
                .rejectNearExpiry(source.getSalesOrderItem().getRejectNearExpiry())
                .remark("Exchange replacement for " + rma.getRmaNumber()).build());
            total = total.add(subtotal);
        }
        replacement.setTotalAmount(total);
        salesOrderRepository.saveAndFlush(replacement);
        rma.setReplacementSalesOrderId(replacement.getId());
        rma.setResolutionStatus(RmaResolutionStatus.EXCHANGE_CREATED);
        Map<String, Object> payload = Map.of("rmaId", rma.getId(),
            "replacementSalesOrderId", replacement.getId(), "advance", request.advance());
        outboxService.append("RMA_EXCHANGE_ORDER_CREATED", "SalesReturnRma", rma.getId(), payload);
        appendEvent(rma, "RMA_EXCHANGE_ORDER_CREATED", payload, actorId, actorName);
    }

    private InventoryBatch transitionQuantity(InventoryBatch source, int quantity,
                                               InventoryQualityStatus targetStatus,
                                               SalesReturnRma rma, String inspectionNumber, int ordinal,
                                               Long actorId, String actorName) {
        InventoryBatch result;
        if (quantity == source.getQuantity()) {
            result = source;
        } else {
            source.setQuantity(source.getQuantity() - quantity);
            batchRepository.saveAndFlush(source);
            String segment = "RQS-" + rma.getId() + "-" + ordinal + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            result = batchRepository.saveAndFlush(InventoryBatch.builder()
                .batchCode(segment).locationCode(source.getLocationCode())
                .purchaseOrderItem(source.getPurchaseOrderItem()).productSku(source.getProductSku())
                .location(source.getLocation()).quantity(quantity).reservedQuantity(0)
                .qualityStatus(source.getQualityStatus()).qualityParentBatchId(source.getId())
                .qualitySegmentNo(segment).initialQuantity(quantity).expiryDate(source.getExpiryDate())
                .productionDate(source.getProductionDate()).externalBatchCode(source.getExternalBatchCode())
                .entryDate(source.getEntryDate()).active(true)
                .remark("RMA inspection segment from " + inspectionNumber).build());
        }
        qualityStateService.transition(new InventoryQualityTransitionCommand(
            result.getId(), targetStatus, "RMA_INSPECTION", rma.getRmaNumber(),
            "RMA_" + targetStatus.name(), "Return inspection disposition", actorId, actor(actorName),
            "rma-q-" + rma.getId() + "-" + inspectionNumber + "-" + ordinal + "-" + result.getId()));
        return result;
    }

    private InventoryQualityStatus targetStatus(RmaDisposition disposition) {
        return switch (disposition) {
            case RESTOCK_AVAILABLE -> InventoryQualityStatus.AVAILABLE;
            case DAMAGED -> InventoryQualityStatus.DAMAGED;
            case QUARANTINE, REJECT_CUSTOMER -> InventoryQualityStatus.QUARANTINED;
        };
    }

    private void createExpectedTask(SalesReturnRma rma) {
        if (taskRepository.findByRmaId(rma.getId()).isPresent()) return;
        taskRepository.saveAndFlush(SalesReturnTask.builder()
            .rma(rma).warehouse(rma.getWarehouse()).taskNumber(number("RRT"))
            .status(RmaTaskStatus.EXPECTED).priority(50).build());
    }

    private void refreshReceiptState(SalesReturnRma rma, SalesReturnTask task) {
        int authorized = rma.getLines().stream().mapToInt(line -> value(line.getAuthorizedQuantity())).sum();
        int received = rma.getLines().stream().mapToInt(line -> value(line.getReceivedQuantity())).sum();
        boolean complete = authorized > 0 && received == authorized;
        rma.setReceiptStatus(complete ? RmaReceiptStatus.RECEIVED : RmaReceiptStatus.PARTIAL);
        task.setStatus(complete ? RmaTaskStatus.RECEIVED : RmaTaskStatus.PARTIAL);
    }

    private void refreshInspectionState(SalesReturnRma rma) {
        int received = rma.getLines().stream().mapToInt(line -> value(line.getReceivedQuantity())).sum();
        int inspected = rma.getLines().stream().mapToInt(line -> value(line.getInspectedQuantity())).sum();
        rma.setInspectionStatus(inspected == 0 ? RmaInspectionStatus.PENDING
            : inspected == received ? RmaInspectionStatus.COMPLETED : RmaInspectionStatus.PARTIAL);
    }

    private boolean outsideStandardWindow(SalesOrder order, SalesOrderShipment shipment,
                                          List<LineEvidence> evidence, int days) {
        LocalDateTime dispatchAt = shipment != null && shipment.getShippedAt() != null
            ? shipment.getShippedAt() : order.getShippedAt();
        if (dispatchAt == null) {
            dispatchAt = evidence.stream().map(line -> line.task().getUpdatedAt())
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now());
        }
        return ChronoUnit.DAYS.between(dispatchAt.toLocalDate(), LocalDate.now()) > days;
    }

    private LinkedHashMap<Long, Integer> normalizeCreateLines(List<CreateRmaRequest.Line> lines) {
        LinkedHashMap<Long, Integer> result = new LinkedHashMap<>();
        for (CreateRmaRequest.Line line : lines) {
            if (result.putIfAbsent(line.outboundTaskId(), line.quantity()) != null) {
                throw validation("lines.outboundTaskId");
            }
        }
        return result;
    }

    private LinkedHashMap<Long, ReceiveRmaRequest.Line> normalizeReceiptLines(List<ReceiveRmaRequest.Line> lines) {
        LinkedHashMap<Long, ReceiveRmaRequest.Line> result = new LinkedHashMap<>();
        for (ReceiveRmaRequest.Line line : lines) {
            if (result.putIfAbsent(line.rmaLineId(), line) != null) throw validation("lines.rmaLineId");
        }
        return result;
    }

    private void terminate(SalesReturnRma rma, String reason, Long actorId, String actorName) {
        rma.setTerminationReason(required(reason, "reason", 500));
        rma.setCancelledOrClosedBy(actorId);
        rma.setCancelledOrClosedByName(actor(actorName));
        rma.setCancelledOrClosedAt(LocalDateTime.now());
    }

    private IdempotencyClaim claim(String key, String operation, Object body) {
        IdempotencyClaim claim = idempotencyService.claim(key, operation, serialize(body));
        if (claim.decision() != IdempotencyClaimDecision.ACQUIRED
            && claim.decision() != IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            requireAcquired(claim, operation);
        }
        return claim;
    }

    private RmaResponse replay(IdempotencyClaim claim, String operation) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return null;
        if (claim.decision() != IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            requireAcquired(claim, operation);
        }
        return get(Long.valueOf(claim.request().getResponseBody()));
    }

    private void requireAcquired(IdempotencyClaim claim, String operation) {
        if (claim.decision() == IdempotencyClaimDecision.ACQUIRED) return;
        throw new BusinessException(claim.decision() == IdempotencyClaimDecision.IN_PROGRESS
            ? ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS : ErrorKeys.IDEMPOTENCY_CONFLICT,
            Map.of("operation", operation));
    }

    private void appendEvent(SalesReturnRma rma, String type, Object payload,
                             Long actorId, String actorName) {
        eventRepository.save(SalesReturnEvent.builder().rmaId(rma.getId()).eventType(type)
            .payloadJson(serialize(payload)).actorId(actorId).actorName(actor(actorName))
            .occurredAt(LocalDateTime.now()).build());
    }

    private RmaResponse toResponse(SalesReturnRma rma) {
        List<RmaResponse.Line> lines = rmaLineRepository.findByRmaIdOrderById(rma.getId()).stream()
            .map(line -> new RmaResponse.Line(line.getId(), line.getSalesOrderItem().getId(),
                line.getOutboundTask().getId(), line.getOriginalBatch().getId(), line.getOriginalBatch().getBatchCode(),
                line.getProductSku().getId(), line.getProductSku().getSkuCode(), line.getProductSku().getName(),
                line.getOutboundTask().getActualQty(), line.getRequestedQuantity(), line.getAuthorizedQuantity(),
                line.getReceivedQuantity(), line.getInspectedQuantity(), line.getRestockQuantity(),
                line.getDamagedQuantity(), line.getQuarantineQuantity(), line.getRejectedQuantity(),
                line.getRefundEligibleQuantity(), line.getUnitPriceSnapshot())).toList();
        RmaResponse.Task task = taskRepository.findByRmaId(rma.getId()).map(this::toTask).orElse(null);
        List<RmaResponse.Receipt> receipts = receiptRepository.findByRmaIdOrderById(rma.getId()).stream()
            .map(receipt -> new RmaResponse.Receipt(receipt.getId(), receipt.getReceiptNumber(),
                receipt.getReceivedBy(), receipt.getReceivedByName(), receipt.getReceivedAt(), receipt.getRemark(),
                receipt.getLines().stream().map(line -> new RmaResponse.ReceiptLine(line.getId(),
                    line.getRmaLine().getId(), line.getLocation().getId(), line.getLocation().getLocationCode(),
                    line.getReturnBatch().getId(), line.getReturnBatch().getBatchCode(),
                    line.getReturnBatch().getQualityStatus(), line.getQuantity(),
                    value(inspectionLineRepository.sumInspectedByReceiptLineId(line.getId())))).toList())).toList();
        List<RmaResponse.Inspection> inspections = inspectionRepository.findByRmaIdOrderById(rma.getId()).stream()
            .map(inspection -> new RmaResponse.Inspection(inspection.getId(), inspection.getInspectionNumber(),
                inspection.getInspectedBy(), inspection.getInspectedByName(), inspection.getInspectedAt(),
                inspection.getReasonDetail(), inspection.getLines().stream().map(line ->
                    new RmaResponse.InspectionLine(line.getId(), line.getReceiptLine().getId(),
                        line.getDisposition(), line.getQuantity(), line.getRefundEligibleQuantity(),
                        line.getResultBatch().getId(), line.getResultBatch().getBatchCode(),
                        line.getResultBatch().getQualityStatus())).toList())).toList();
        List<RmaResponse.Event> events = eventRepository.findByRmaIdOrderById(rma.getId()).stream()
            .map(event -> new RmaResponse.Event(event.getId(), event.getEventType(), event.getPayloadJson(),
                event.getActorId(), event.getActorName(), event.getOccurredAt())).toList();
        SalesOrder order = rma.getSalesOrder();
        return new RmaResponse(rma.getId(), rma.getVersion(), rma.getRmaNumber(), order.getId(), order.getOrderNo(),
            rma.getShipment() == null ? null : rma.getShipment().getId(), rma.getWarehouse().getId(),
            rma.getWarehouse().getName(), order.getCustomerId(), order.getCustomer().getName(), rma.getRmaType(),
            rma.getBusinessStatus(), rma.getReceiptStatus(), rma.getInspectionStatus(), rma.getResolutionStatus(),
            rma.getApprovalModeSnapshot(), rma.getCustomerTypeSnapshot(), rma.getStatutoryBasis(),
            rma.getStandardReturnDaysSnapshot(), Boolean.TRUE.equals(rma.getOutsideStandardWindow()),
            rma.getReasonCode(), rma.getReasonDetail(), rma.getCustomerShippedAt(), rma.getReturnCarrier(),
            rma.getReturnTrackingNo(), rma.getRefundAmount(), rma.getRefundCurrency(), rma.getRefundReference(),
            Boolean.TRUE.equals(rma.getAdvanceResolution()), rma.getReplacementSalesOrderId(), rma.getChannel(),
            rma.getExternalCaseId(), rma.getRequestedBy(), rma.getRequestedByName(), rma.getRequestedAt(),
            rma.getReviewedBy(), rma.getReviewedByName(), rma.getReviewedAt(), rma.getReviewComment(),
            lines, task, receipts, inspections, events);
    }

    private RmaResponse.Task toTask(SalesReturnTask task) {
        return new RmaResponse.Task(task.getId(), task.getVersion(), task.getTaskNumber(), task.getStatus(),
            task.getPriority(), task.getExpectedFrom(), task.getExpectedTo(), task.getAssignee(), task.getTeamName(),
            task.getOperationArea(), task.getWarehouseRemark());
    }

    private RmaWarehouseTaskResponse toWarehouseTask(SalesReturnTask task) {
        List<RmaWarehouseTaskResponse.Line> lines = rmaLineRepository.findByRmaIdOrderById(task.getRma().getId())
            .stream().map(line -> new RmaWarehouseTaskResponse.Line(line.getId(), line.getProductSku().getId(),
                line.getProductSku().getSkuCode(), line.getProductSku().getName(), line.getAuthorizedQuantity(),
                line.getReceivedQuantity())).toList();
        List<RmaWarehouseTaskResponse.ReceiptLine> receiptLines = receiptLineRepository
            .findByRmaId(task.getRma().getId()).stream().map(line ->
                new RmaWarehouseTaskResponse.ReceiptLine(line.getId(), line.getRmaLine().getId(),
                    line.getRmaLine().getProductSku().getId(), line.getRmaLine().getProductSku().getSkuCode(),
                    line.getRmaLine().getProductSku().getName(), line.getLocation().getId(),
                    line.getLocation().getLocationCode(), line.getReturnBatch().getId(),
                    line.getReturnBatch().getBatchCode(), line.getQuantity(),
                    value(inspectionLineRepository.sumInspectedByReceiptLineId(line.getId())))).toList();
        return new RmaWarehouseTaskResponse(task.getId(), task.getVersion(), task.getTaskNumber(),
            task.getRma().getRmaNumber(), task.getWarehouse().getId(), task.getWarehouse().getName(), task.getStatus(),
            task.getPriority(), task.getExpectedFrom(), task.getExpectedTo(), task.getAssignee(), task.getTeamName(),
            task.getOperationArea(), task.getWarehouseRemark(), lines, receiptLines);
    }

    private SalesReturnRma requireRma(Long id) {
        return rmaRepository.findById(id).orElseThrow(() -> notFound("rmaId", id));
    }

    private SalesReturnRma requireRmaForUpdate(Long id) {
        return rmaRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("rmaId", id));
    }

    private SalesReturnTask requireTask(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> notFound("returnTaskId", id));
    }

    private void requireStatus(SalesReturnRma rma, RmaBusinessStatus status) {
        if (rma.getBusinessStatus() != status) throw invalid("RMA_STATUS_INVALID", rma.getId());
    }

    private BusinessException notFound(String field, Object value) {
        return new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND, Map.of("resourceType", field, "resourceId", value));
    }

    private BusinessException invalid(String reason, Object id) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("reason", reason, "resourceId", id));
    }

    private BusinessException validation(String field) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
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

    private String actor(String name) {
        return name == null || name.isBlank() ? "unknown" : shortText(name.trim(), 100);
    }

    private String shortText(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw validation("request");
        }
    }

    private String number(String prefix) {
        return prefix + "-" + LocalDateTime.now().toString().replaceAll("[-:.T]", "").substring(0, 14)
            + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String exchangeOrderNumber(Long rmaId) {
        return "EX" + String.format("%010d", rmaId) + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private record LineEvidence(OutboundTask task, SalesOrderItem item,
                                InventoryBatch originalBatch, int quantity) { }
}
