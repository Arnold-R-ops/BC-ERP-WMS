package com.wms.system.service;

import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.OtherInboundApplication;
import com.wms.system.entity.OtherInboundApplicationItem;
import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseAsnItem;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.ReceiptEvent;
import com.wms.system.entity.ReceiptEventItem;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.ReceivingTaskItem;
import com.wms.system.entity.enums.OtherInboundStatus;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.entity.enums.ReceiptEventStatus;
import com.wms.system.entity.enums.InventoryQualityStatus;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.OtherInboundApplicationRepository;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.PurchaseOrderReceivingRepository;
import com.wms.system.repository.ReceiptEventRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.service.inventory.InventoryPostingCommand;
import com.wms.system.service.inventory.InventoryPostingService;
import com.wms.system.service.inventory.PostingContext;
import com.wms.system.service.inspection.ReceivingInspectionPolicyService;
import com.wms.system.service.inspection.ReceivingInspectionTaskFactory;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one atomic warehouse receipt executor for purchase ASN and controlled
 * other-inbound tasks. Source-specific workflow state is advanced in the same
 * transaction as batch creation and inventory posting.
 */
@Service
@RequiredArgsConstructor
public class ReceivingReceiptExecutor {

    private final ReceivingTaskRepository receivingTaskRepository;
    private final PurchaseOrderReceivingRepository purchaseOrderRepository;
    private final PurchaseAsnRepository purchaseAsnRepository;
    private final PurchaseOrderAllocationRepository allocationRepository;
    private final OtherInboundApplicationRepository otherInboundApplicationRepository;
    private final ReceiptEventRepository receiptEventRepository;
    private final LocationRepository locationRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryPostingService inventoryPostingService;
    private final ProductSkuExpiryPolicy expiryPolicy;
    private final PurchaseAsnService purchaseAsnService;
    private final BackorderService backorderService;
    private final ReceivingResponseMapper mapper;
    private final ReceivingInspectionPolicyService inspectionPolicyService;
    private final ReceivingInspectionTaskFactory inspectionTaskFactory;

    @Transactional(rollbackFor = Exception.class)
    public ReceiptEventResponse execute(
        Long taskId,
        String idempotencyKey,
        SubmitReceivingReceiptRequest request,
        Long actorId,
        String actorName
    ) {
        ReceiptEvent replay = receiptEventRepository.findByIdempotencyKey(idempotencyKey)
            .orElse(null);
        if (replay != null) {
            if (!replay.getReceivingTask().getId().equals(taskId)) {
                throw new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT);
            }
            return mapper.toReceipt(replay);
        }

        LockedSource locked = lockSourceAndTask(taskId);
        ReceivingTask task = locked.task();
        if (!task.getStatus().acceptsReceipts()) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of(
                    "operation", "RECEIVE",
                    "taskId", taskId,
                    "currentStatus", task.getStatus().name()
                )
            );
        }
        validateSource(task, locked);

        Map<Long, ReceivingTaskItem> taskItems = task.getItems().stream()
            .collect(Collectors.toMap(ReceivingTaskItem::getId, Function.identity()));
        Map<Long, Integer> requestedByItem = new HashMap<>();
        Set<Long> locationIds = new LinkedHashSet<>();
        for (SubmitReceivingReceiptRequest.Line line : request.lines()) {
            ReceivingTaskItem item = taskItems.get(line.receivingTaskItemId());
            if (item == null) {
                throw validation("lines.receivingTaskItemId", line.receivingTaskItemId());
            }
            requestedByItem.merge(item.getId(), line.quantity(), Math::addExact);
            locationIds.add(line.locationId());
        }
        requestedByItem.forEach((itemId, quantity) -> {
            ReceivingTaskItem item = taskItems.get(itemId);
            if (quantity > item.remainingQuantity()) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                        "field", "lines.quantity",
                        "receivingTaskItemId", itemId,
                        "requestedQuantity", quantity,
                        "remainingQuantity", item.remainingQuantity()
                    )
                );
            }
        });

        Map<Long, Location> locations = locationRepository.findAllById(locationIds).stream()
            .collect(Collectors.toMap(Location::getId, Function.identity()));
        for (Long locationId : locationIds) {
            Location location = locations.get(locationId);
            if (location == null
                || !Boolean.TRUE.equals(location.getEnabled())
                || location.getWarehouse() == null
                || !location.getWarehouse().getId().equals(task.getWarehouse().getId())) {
                throw new BusinessException(
                    ErrorKeys.LOCATION_NOT_FOUND, Map.of("locationId", locationId));
            }
        }

        Map<Long, PurchaseOrderAllocation> allocations = lockAllocations(task, taskItems);
        ReceiptEvent event = ReceiptEvent.builder()
            .eventNumber(uniqueNumber("RCE"))
            .receivingTask(task)
            .idempotencyKey(idempotencyKey)
            .status(ReceiptEventStatus.APPLIED)
            .receivedBy(actorId)
            .receivedByName(normalizeActor(actorName))
            .receivedAt(LocalDateTime.now())
            .remark(trim(request.remark()))
            .build();
        receiptEventRepository.saveAndFlush(event);

        List<ValidatedLine> lines = validateLines(
            request, taskItems, locations, allocations, task.getSourceType());
        Set<Long> receivedSkuIds = new LinkedHashSet<>();
        int sequence = 0;
        for (ValidatedLine line : lines) {
            String batchCode = uniqueNumber("RCB");
            boolean inspectionRequired =
                inspectionPolicyService.requiresInspection(line.taskItem().getProductSku());
            InventoryQualityStatus receiptQualityStatus = inspectionRequired
                ? InventoryQualityStatus.PENDING_INSPECTION
                : InventoryQualityStatus.AVAILABLE;
            InventoryBatch batch = InventoryBatch.builder()
                .batchCode(batchCode)
                .purchaseOrderItem(line.taskItem().getPurchaseOrderItem())
                .productSku(line.taskItem().getProductSku())
                .location(line.location())
                .locationCode(line.location().getLocationCode())
                .quantity(0)
                .reservedQuantity(0)
                .qualityStatus(receiptQualityStatus)
                .initialQuantity(line.request().quantity())
                .productionDate(line.productionDate())
                .expiryDate(line.expiryDate())
                .externalBatchCode(line.externalBatchCode())
                .entryDate(LocalDateTime.now())
                .active(true)
                .remark(trim(request.remark()))
                .build();
            batch = inventoryBatchRepository.saveAndFlush(batch);

            String postingId = "RECEIPT:" + event.getEventNumber() + ":" + (++sequence);
            inventoryPostingService.post(new InventoryPostingCommand(
                batch.getId(),
                line.taskItem().getProductSku().getId(),
                line.location().getId(),
                line.request().quantity(),
                new PostingContext(
                    CompanyScope.currentCompanyId(),
                    task.getWarehouse().getId(),
                    postingSource(task),
                    sourceReference(locked),
                    postingReason(task),
                    trim(request.remark()),
                    actorId,
                    normalizeActor(actorName),
                    postingId,
                    idempotencyKey
                )
            ));

            event.addItem(ReceiptEventItem.builder()
                .receivingTaskItem(line.taskItem())
                .productSku(line.taskItem().getProductSku())
                .inventoryBatch(batch)
                .location(line.location())
                .quantity(line.request().quantity())
                .batchCode(batchCode)
                .productionDate(line.productionDate())
                .expiryDate(line.expiryDate())
                .externalBatchCode(line.externalBatchCode())
                .postingId(postingId)
                .inspectionRequired(inspectionRequired)
                .qualityStatusAtReceipt(receiptQualityStatus)
                .build());

            applyQuantities(
                task.getSourceType(), line.taskItem(), line.allocation(),
                line.request().quantity());
            if (!inspectionRequired) {
                receivedSkuIds.add(line.taskItem().getProductSku().getId());
            }
        }

        updateStatuses(locked, allocations.values());
        receiptEventRepository.saveAndFlush(event);
        inspectionTaskFactory.createForReceipt(event);
        receivedSkuIds.forEach(backorderService::wakeProduct);
        return mapper.toReceipt(event);
    }

    @Transactional(readOnly = true)
    public ReceiptEventResponse replay(String idempotencyKey) {
        return receiptEventRepository.findByIdempotencyKey(idempotencyKey)
            .map(mapper::toReceipt)
            .orElseThrow(() -> new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT));
    }

    private LockedSource lockSourceAndTask(Long taskId) {
        ReceivingTask preview = receivingTaskRepository.findById(taskId)
            .orElseThrow(() -> taskNotFound(taskId));
        if (preview.getSourceType() == ReceivingTaskSourceType.PURCHASE_ASN) {
            if (preview.getPurchaseAsn() == null
                || preview.getPurchaseAsn().getPurchaseOrder() == null) {
                throw invalidSource(taskId);
            }
            PurchaseOrder order = purchaseOrderRepository.findByIdForReceiving(
                preview.getPurchaseAsn().getPurchaseOrder().getId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.PURCHASE_ORDER_NOT_FOUND,
                    Map.of("purchaseOrderId",
                        preview.getPurchaseAsn().getPurchaseOrder().getId())
                ));
            PurchaseAsn asn = purchaseAsnRepository.findByIdForUpdate(
                preview.getPurchaseAsn().getId())
                .orElseThrow(() -> invalidSource(taskId));
            ReceivingTask task = receivingTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> taskNotFound(taskId));
            return new LockedSource(task, order, asn, null);
        }
        if (preview.getSourceType() == ReceivingTaskSourceType.OTHER_INBOUND) {
            if (preview.getOtherInboundApplication() == null) {
                throw invalidSource(taskId);
            }
            OtherInboundApplication application = otherInboundApplicationRepository
                .findByIdForUpdate(preview.getOtherInboundApplication().getId())
                .orElseThrow(() -> invalidSource(taskId));
            ReceivingTask task = receivingTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> taskNotFound(taskId));
            return new LockedSource(task, null, null, application);
        }
        throw invalidSource(taskId);
    }

    private void validateSource(ReceivingTask task, LockedSource locked) {
        if (task.getSourceType() == ReceivingTaskSourceType.PURCHASE_ASN) {
            if (task.getPurchaseAsn() == null
                || task.getReceivingPlan() == null
                || task.getOtherInboundApplication() != null
                || locked.purchaseAsn() == null
                || locked.purchaseOrder() == null
                || task.getPurchaseAsn().getStatus() != PurchaseAsnStatus.CONFIRMED
                    && task.getPurchaseAsn().getStatus()
                        != PurchaseAsnStatus.PARTIALLY_RECEIVED) {
                throw invalidSource(task.getId());
            }
            return;
        }
        if (task.getSourceType() == ReceivingTaskSourceType.OTHER_INBOUND) {
            if (task.getPurchaseAsn() != null
                || task.getReceivingPlan() != null
                || task.getOtherInboundApplication() == null
                || locked.otherInboundApplication() == null
                || !Objects.equals(
                    task.getOtherInboundApplication().getId(),
                    locked.otherInboundApplication().getId())
                || locked.otherInboundApplication().getStatus() != OtherInboundStatus.CONFIRMED
                    && locked.otherInboundApplication().getStatus()
                        != OtherInboundStatus.PARTIALLY_RECEIVED) {
                throw invalidSource(task.getId());
            }
            validateOtherInboundTaskItems(task, locked.otherInboundApplication());
            return;
        }
        throw invalidSource(task.getId());
    }

    private void validateOtherInboundTaskItems(
        ReceivingTask task,
        OtherInboundApplication application
    ) {
        if (task.getItems() == null || task.getItems().isEmpty()) {
            throw invalidSource(task.getId());
        }
        for (ReceivingTaskItem taskItem : task.getItems()) {
            OtherInboundApplicationItem sourceItem =
                taskItem.getOtherInboundApplicationItem();
            if (sourceItem == null
                || sourceItem.getApplication() == null
                || taskItem.getProductSku() == null
                || sourceItem.getProductSku() == null
                || taskItem.getPurchaseAsnItem() != null
                || taskItem.getPurchaseOrderItem() != null
                || !Objects.equals(
                    sourceItem.getApplication().getId(), application.getId())
                || !Objects.equals(
                    taskItem.getProductSku().getId(), sourceItem.getProductSku().getId())) {
                throw invalidSource(task.getId());
            }
        }
    }

    private Map<Long, PurchaseOrderAllocation> lockAllocations(
        ReceivingTask task,
        Map<Long, ReceivingTaskItem> taskItems
    ) {
        if (task.getSourceType() != ReceivingTaskSourceType.PURCHASE_ASN) {
            return Map.of();
        }
        List<Long> allocationIds = taskItems.values().stream()
            .map(ReceivingTaskItem::getPurchaseAsnItem)
            .map(item -> {
                if (item == null || item.getPurchaseOrderAllocation() == null) {
                    throw invalidSource(task.getId());
                }
                return item.getPurchaseOrderAllocation().getId();
            })
            .distinct()
            .sorted()
            .toList();
        Map<Long, PurchaseOrderAllocation> allocations = new LinkedHashMap<>();
        for (Long allocationId : allocationIds) {
            allocations.put(
                allocationId,
                allocationRepository.findByIdForUpdate(allocationId)
                    .orElseThrow(() -> validation("allocation", allocationId))
            );
        }
        return allocations;
    }

    private List<ValidatedLine> validateLines(
        SubmitReceivingReceiptRequest request,
        Map<Long, ReceivingTaskItem> taskItems,
        Map<Long, Location> locations,
        Map<Long, PurchaseOrderAllocation> allocations,
        ReceivingTaskSourceType sourceType
    ) {
        List<ValidatedLine> lines = new ArrayList<>();
        for (SubmitReceivingReceiptRequest.Line line : request.lines()) {
            ReceivingTaskItem taskItem = taskItems.get(line.receivingTaskItemId());
            LocalDate productionDate = line.productionDate() == null
                ? taskItem.getProductionDate() : line.productionDate();
            LocalDate expiryDate = line.expiryDate() == null
                ? taskItem.getExpiryDate() : line.expiryDate();
            String externalBatch = trim(line.externalBatchCode()) == null
                ? taskItem.getExternalBatchCode() : trim(line.externalBatchCode());
            expiryPolicy.validateForInventoryReceipt(
                taskItem.getProductSku(), productionDate, expiryDate);

            PurchaseOrderAllocation allocation = null;
            if (sourceType == ReceivingTaskSourceType.PURCHASE_ASN) {
                allocation = allocations.get(
                    taskItem.getPurchaseAsnItem().getPurchaseOrderAllocation().getId());
                if (allocation == null) {
                    throw invalidSource(taskItem.getReceivingTask().getId());
                }
            } else if (taskItem.getOtherInboundApplicationItem() == null) {
                throw invalidSource(taskItem.getReceivingTask().getId());
            }
            lines.add(new ValidatedLine(
                line,
                taskItem,
                locations.get(line.locationId()),
                allocation,
                productionDate,
                expiryDate,
                externalBatch
            ));
        }
        return lines;
    }

    private void applyQuantities(
        ReceivingTaskSourceType sourceType,
        ReceivingTaskItem taskItem,
        PurchaseOrderAllocation allocation,
        int quantity
    ) {
        taskItem.setReceivedQuantity(taskItem.getReceivedQuantity() + quantity);
        if (sourceType == ReceivingTaskSourceType.PURCHASE_ASN) {
            PurchaseAsnItem asnItem = taskItem.getPurchaseAsnItem();
            asnItem.setReceivedQuantity(asnItem.getReceivedQuantity() + quantity);
            allocation.setReceivedQuantity(allocation.getReceivedQuantity() + quantity);
            if (allocation.getReceivedQuantity() > allocation.getShippedQuantity()
                || allocation.getReceivedQuantity() + allocation.getClosedQuantity()
                    > allocation.getPlannedQuantity()) {
                throw validation("lines.quantity", quantity);
            }
            try {
                taskItem.getPurchaseOrderItem().increaseReceivedQuantity(quantity);
            } catch (IllegalArgumentException exception) {
                throw validation("lines.quantity", quantity);
            }
            return;
        }

        OtherInboundApplicationItem applicationItem =
            taskItem.getOtherInboundApplicationItem();
        applicationItem.setReceivedQuantity(
            applicationItem.getReceivedQuantity() + quantity);
        if (applicationItem.getReceivedQuantity()
            + applicationItem.getClosedQuantity()
            > applicationItem.getPlannedQuantity()) {
            throw validation("lines.quantity", quantity);
        }
    }

    private void updateStatuses(
        LockedSource locked,
        Iterable<PurchaseOrderAllocation> allocations
    ) {
        ReceivingTask task = locked.task();
        boolean taskDone = task.getItems().stream()
            .allMatch(item -> item.remainingQuantity() == 0);
        task.setStatus(taskDone
            ? ReceivingTaskStatus.COMPLETED
            : ReceivingTaskStatus.PARTIALLY_RECEIVED);
        if (taskDone) {
            task.setCompletedAt(LocalDateTime.now());
        }

        if (task.getSourceType() == ReceivingTaskSourceType.OTHER_INBOUND) {
            locked.otherInboundApplication().setStatus(taskDone
                ? OtherInboundStatus.COMPLETED
                : OtherInboundStatus.PARTIALLY_RECEIVED);
            return;
        }

        PurchaseAsn asn = locked.purchaseAsn();
        asn.setStatus(taskDone
            ? PurchaseAsnStatus.RECEIVED
            : PurchaseAsnStatus.PARTIALLY_RECEIVED);
        allocations.forEach(purchaseAsnService::updateAllocationStatus);
        purchaseAsnService.updatePurchaseOrderStatus(locked.purchaseOrder());
        updatePlanStatus(task.getReceivingPlan());
    }

    private void updatePlanStatus(ReceivingPlan plan) {
        List<ReceivingTask> tasks = receivingTaskRepository.findByReceivingPlanId(plan.getId());
        if (tasks.stream().allMatch(
            candidate -> candidate.getStatus() == ReceivingTaskStatus.COMPLETED)) {
            plan.setStatus(ReceivingPlanStatus.COMPLETED);
        } else if (tasks.stream().allMatch(candidate ->
            candidate.getStatus() == ReceivingTaskStatus.COMPLETED
                || candidate.getStatus() == ReceivingTaskStatus.CLOSED
                || candidate.getStatus() == ReceivingTaskStatus.CANCELLED)) {
            plan.setStatus(ReceivingPlanStatus.CLOSED);
        } else if (tasks.stream().anyMatch(candidate ->
            candidate.getStatus() == ReceivingTaskStatus.PARTIALLY_RECEIVED
                || candidate.getStatus() == ReceivingTaskStatus.COMPLETED)) {
            plan.setStatus(ReceivingPlanStatus.PARTIALLY_RECEIVED);
        } else {
            plan.setStatus(ReceivingPlanStatus.IN_TRANSIT);
        }
    }

    private SourceType postingSource(ReceivingTask task) {
        return task.getSourceType() == ReceivingTaskSourceType.PURCHASE_ASN
            ? SourceType.PURCHASE_IN : SourceType.INBOUND_IN;
    }

    private String sourceReference(LockedSource locked) {
        return locked.purchaseOrder() != null
            ? locked.purchaseOrder().getPoNumber()
            : locked.otherInboundApplication().getApplicationNumber();
    }

    private String postingReason(ReceivingTask task) {
        return task.getSourceType() == ReceivingTaskSourceType.PURCHASE_ASN
            ? "PURCHASE_RECEIPT" : "OTHER_INBOUND_RECEIPT";
    }

    private BusinessException taskNotFound(Long id) {
        return new BusinessException(
            ErrorKeys.RESOURCE_NOT_FOUND,
            Map.of("resourceType", "ReceivingTask", "resourceId", id)
        );
    }

    private BusinessException invalidSource(Long taskId) {
        return new BusinessException(
            ErrorKeys.OPERATION_NOT_ALLOWED,
            Map.of("operation", "RECEIVE", "taskId", taskId, "reason", "INVALID_TASK_SOURCE")
        );
    }

    private BusinessException validation(String field, Object value) {
        return new BusinessException(
            ErrorKeys.VALIDATION_FAILED, Map.of("field", field, "value", value));
    }

    private String uniqueNumber(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString()
            .replace("-", "").substring(0, 16).toUpperCase();
    }

    private String normalizeActor(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record LockedSource(
        ReceivingTask task,
        PurchaseOrder purchaseOrder,
        PurchaseAsn purchaseAsn,
        OtherInboundApplication otherInboundApplication
    ) {
    }

    private record ValidatedLine(
        SubmitReceivingReceiptRequest.Line request,
        ReceivingTaskItem taskItem,
        Location location,
        PurchaseOrderAllocation allocation,
        LocalDate productionDate,
        LocalDate expiryDate,
        String externalBatchCode
    ) {
    }
}
