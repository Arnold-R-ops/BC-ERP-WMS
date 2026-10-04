package com.wms.system.service;

import com.wms.system.dto.receiving.CreatePurchaseAsnRequest;
import com.wms.system.dto.receiving.PurchaseAsnResponse;
import com.wms.system.dto.receiving.UpdatePurchaseAsnDraftRequest;
import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseAsnItem;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.PurchaseOrderItem;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.ReceivingTaskItem;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.entity.enums.ShortReceiptClosureMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.PurchaseOrderReceivingRepository;
import com.wms.system.repository.ReceivingPlanRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseAsnService {

    private final PurchaseAsnRepository purchaseAsnRepository;
    private final PurchaseOrderReceivingRepository purchaseOrderRepository;
    private final PurchaseOrderAllocationRepository allocationRepository;
    private final ReceivingPlanRepository receivingPlanRepository;
    private final ReceivingTaskRepository receivingTaskRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReceivingPlanService receivingPlanService;
    private final PurchaseGovernancePolicyService governancePolicyService;
    private final ProductSkuExpiryPolicy expiryPolicy;
    private final ReceivingResponseMapper mapper;

    @Transactional(readOnly = true)
    public List<PurchaseAsnResponse> list(Long purchaseOrderId) {
        requireOrder(purchaseOrderId);
        return purchaseAsnRepository.findByPurchaseOrderIdOrderByIdAsc(purchaseOrderId)
            .stream().map(mapper::toAsn).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseAsnResponse get(Long id) {
        return mapper.toAsn(requireAsn(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse create(
        Long purchaseOrderId,
        CreatePurchaseAsnRequest request,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForReceiving(purchaseOrderId)
            .orElseThrow(() -> orderNotFound(purchaseOrderId));
        if (!order.getStatus().canConfirmShipment()) {
            throw invalidOrderStatus(order);
        }
        Warehouse warehouse = requireActiveWarehouse(request.warehouseId());

        PurchaseAsn asn = PurchaseAsn.builder()
            .asnNumber(uniqueNumber("ASN"))
            .purchaseOrder(order)
            .warehouse(warehouse)
            .status(PurchaseAsnStatus.DRAFT)
            .supplierShipmentNo(trim(request.supplierShipmentNo()))
            .carrier(trim(request.carrier()))
            .trackingNo(trim(request.trackingNo()))
            .expectedArrivalDate(request.expectedArrivalDate())
            .createdBy(actorId)
            .createdByName(normalizeActor(actorName))
            .remark(trim(request.remark()))
            .build();

        replaceDraftItems(asn, order, warehouse, request.items().stream()
            .map(item -> new DraftItemInput(
                item.purchaseOrderItemId(), item.shippedQuantity(), item.productionDate(),
                item.expiryDate(), item.externalBatchCode(), item.remark()))
            .toList());
        return mapper.toAsn(purchaseAsnRepository.save(asn));
    }

    /** Replaces an ASN draft while enforcing the version last observed by the client. */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse updateDraft(Long id, UpdatePurchaseAsnDraftRequest request) {
        PurchaseAsn asn = requireAsnForUpdate(id);
        if (!java.util.Objects.equals(asn.getVersion(), request.version())) {
            throw new BusinessException(
                ErrorKeys.PO_EDIT_CONFLICT,
                Map.of(
                    "resourceType", "PurchaseAsn",
                    "resourceId", id,
                    "expectedVersion", request.version(),
                    "currentVersion", asn.getVersion()
                )
            );
        }
        requireNoReceipts(asn, "UPDATE");
        if (asn.getStatus() != PurchaseAsnStatus.DRAFT) {
            throw invalidAsnStatus(asn, "DRAFT");
        }
        PurchaseOrder order = purchaseOrderRepository.findByIdForReceiving(
            asn.getPurchaseOrder().getId()
        ).orElseThrow(() -> orderNotFound(asn.getPurchaseOrder().getId()));
        if (!order.getStatus().canConfirmShipment()) {
            throw invalidOrderStatus(order);
        }
        Warehouse warehouse = requireActiveWarehouse(request.warehouseId());

        asn.setWarehouse(warehouse);
        asn.setSupplierShipmentNo(trim(request.supplierShipmentNo()));
        asn.setCarrier(trim(request.carrier()));
        asn.setTrackingNo(trim(request.trackingNo()));
        asn.setExpectedArrivalDate(request.expectedArrivalDate());
        asn.setRemark(trim(request.remark()));
        replaceDraftItems(asn, order, warehouse, request.items().stream()
            .map(item -> new DraftItemInput(
                item.purchaseOrderItemId(), item.shippedQuantity(), item.productionDate(),
                item.expiryDate(), item.externalBatchCode(), item.remark()))
            .toList());

        // A line-only edit must also advance the aggregate's optimistic-lock token.
        asn.setUpdatedAt(LocalDateTime.now());
        return mapper.toAsn(purchaseAsnRepository.saveAndFlush(asn));
    }

    /** Ordinary-tenant happy path: draft construction and confirmation are one transaction. */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse quickConfirm(
        Long purchaseOrderId,
        CreatePurchaseAsnRequest request,
        Long actorId,
        String actorName
    ) {
        PurchaseAsnResponse draft = create(purchaseOrderId, request, actorId, actorName);
        return confirm(draft.id(), actorId, actorName);
    }

    @Transactional(readOnly = true)
    public boolean hasConfirmedOrReceipt(Long purchaseOrderId) {
        return purchaseAsnRepository.findByPurchaseOrderIdOrderByIdAsc(purchaseOrderId)
            .stream().anyMatch(asn -> asn.hasReceipts()
                || (asn.getStatus() != PurchaseAsnStatus.DRAFT
                    && asn.getStatus() != PurchaseAsnStatus.CANCELLED
                    && asn.getStatus() != PurchaseAsnStatus.CORRECTED));
    }

    /** A void is allowed only when no ASN/task/receipt plan has ever been materialized. */
    @Transactional(readOnly = true)
    public boolean hasAnyDownstream(Long purchaseOrderId) {
        return !purchaseAsnRepository.findByPurchaseOrderIdOrderByIdAsc(purchaseOrderId).isEmpty()
            || !receivingPlanRepository.findByPurchaseOrderIdOrderByIdAsc(purchaseOrderId).isEmpty();
    }

    /**
     * Purchase-cancel integration point. Every child is cancelled atomically;
     * any physical receipt rejects the whole operation.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelPurchaseDownstream(
        Long purchaseOrderId,
        String reason,
        Long actorId,
        String actorName
    ) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForReceiving(purchaseOrderId)
            .orElseThrow(() -> orderNotFound(purchaseOrderId));
        List<PurchaseAsn> asns = purchaseAsnRepository
            .findByPurchaseOrderIdOrderByIdAsc(purchaseOrderId);
        if (asns.stream().anyMatch(PurchaseAsn::hasReceipts)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "CANCEL_PURCHASE", "reason", "PURCHASE_HAS_RECEIPTS")
            );
        }
        for (PurchaseAsn asn : asns) {
            if (asn.getStatus() == PurchaseAsnStatus.CONFIRMED) {
                releaseShipment(asn);
                cancelTask(asn);
            }
            if (asn.getStatus() == PurchaseAsnStatus.DRAFT
                || asn.getStatus() == PurchaseAsnStatus.CONFIRMED) {
                asn.setStatus(PurchaseAsnStatus.CANCELLED);
                asn.setCancelledBy(actorId);
                asn.setCancelledByName(normalizeActor(actorName));
                asn.setCancelledAt(LocalDateTime.now());
                asn.setCancelReason(trim(reason));
            }
        }
        receivingPlanService.withdrawUnstartedPlans(purchaseOrderId);
        updatePurchaseOrderStatus(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse confirm(Long id, Long actorId, String actorName) {
        PurchaseAsn asn = requireAsnForUpdate(id);
        if (asn.getStatus() != PurchaseAsnStatus.DRAFT) {
            throw invalidAsnStatus(asn, "DRAFT");
        }
        PurchaseOrder order = purchaseOrderRepository.findByIdForReceiving(asn.getPurchaseOrder().getId())
            .orElseThrow(() -> orderNotFound(asn.getPurchaseOrder().getId()));
        if (!order.getStatus().canConfirmShipment()) {
            throw invalidOrderStatus(order);
        }

        List<PurchaseAsnItem> orderedItems = asn.getItems().stream()
            .sorted(Comparator.comparing(item -> item.getPurchaseOrderAllocation().getId()))
            .toList();
        for (PurchaseAsnItem item : orderedItems) {
            PurchaseOrderAllocation allocation = allocationRepository
                .findByIdForUpdate(item.getPurchaseOrderAllocation().getId())
                .orElseThrow(() -> validation("allocation", item.getPurchaseOrderAllocation().getId()));
            if (!allocation.getWarehouse().getId().equals(asn.getWarehouse().getId())
                || !allocation.getPurchaseOrderItem().getId().equals(item.getPurchaseOrderItem().getId())) {
                throw validation("allocation", allocation.getId());
            }
            if (item.getShippedQuantity() > allocation.availableToShipQuantity()) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                        "field", "items.shippedQuantity",
                        "purchaseOrderItemId", item.getPurchaseOrderItem().getId(),
                        "requestedQuantity", item.getShippedQuantity(),
                        "availableQuantity", allocation.availableToShipQuantity()
                    )
                );
            }
            expiryPolicy.validateSuppliedDates(
                item.getProductSku(), item.getProductionDate(), item.getExpiryDate());
            allocation.setShippedQuantity(allocation.getShippedQuantity() + item.getShippedQuantity());
            allocation.setStatus(PurchaseOrderAllocationStatus.ACTIVE);
        }

        List<ReceivingPlan> plans = receivingPlanService.ensureWaitingShipmentPlans(
            order, actorId, actorName);
        ReceivingPlan plan = plans.stream()
            .filter(candidate -> candidate.getWarehouse().getId().equals(asn.getWarehouse().getId()))
            .findFirst()
            .orElseThrow(() -> validation("warehouseId", asn.getWarehouse().getId()));
        plan.setStatus(ReceivingPlanStatus.IN_TRANSIT);

        ReceivingTask task = ReceivingTask.builder()
            .taskNumber(uniqueNumber("RCV"))
            .receivingPlan(plan)
            .sourceType(ReceivingTaskSourceType.PURCHASE_ASN)
            .purchaseAsn(asn)
            .warehouse(asn.getWarehouse())
            .status(ReceivingTaskStatus.PENDING)
            .createdBy(actorId)
            .createdByName(normalizeActor(actorName))
            .build();
        orderedItems.forEach(item -> task.addItem(ReceivingTaskItem.builder()
            .purchaseAsnItem(item)
            .purchaseOrderItem(item.getPurchaseOrderItem())
            .productSku(item.getProductSku())
            .expectedQuantity(item.getShippedQuantity())
            .productionDate(item.getProductionDate())
            .expiryDate(item.getExpiryDate())
            .externalBatchCode(item.getExternalBatchCode())
            .remark(item.getRemark())
            .build()));
        receivingTaskRepository.save(task);

        asn.setStatus(PurchaseAsnStatus.CONFIRMED);
        asn.setConfirmedBy(actorId);
        asn.setConfirmedByName(normalizeActor(actorName));
        asn.setConfirmedAt(LocalDateTime.now());
        updatePurchaseOrderStatus(order);
        return mapper.toAsn(asn);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse correct(Long id, String reason, Long actorId, String actorName) {
        PurchaseAsn source = requireAsnForUpdate(id);
        requireNoReceipts(source, "CORRECT");
        if (source.getStatus() != PurchaseAsnStatus.CONFIRMED) {
            throw invalidAsnStatus(source, "CONFIRMED");
        }
        releaseShipment(source);
        ReceivingPlan affectedPlan = cancelTask(source);
        source.setStatus(PurchaseAsnStatus.CORRECTED);
        source.setCancelReason(trim(reason));
        source.setCancelledBy(actorId);
        source.setCancelledByName(normalizeActor(actorName));
        source.setCancelledAt(LocalDateTime.now());

        PurchaseAsn clone = PurchaseAsn.builder()
            .asnNumber(uniqueNumber("ASN"))
            .purchaseOrder(source.getPurchaseOrder())
            .warehouse(source.getWarehouse())
            .status(PurchaseAsnStatus.DRAFT)
            .correctionOf(source)
            .supplierShipmentNo(source.getSupplierShipmentNo())
            .carrier(source.getCarrier())
            .trackingNo(source.getTrackingNo())
            .expectedArrivalDate(source.getExpectedArrivalDate())
            .createdBy(actorId)
            .createdByName(normalizeActor(actorName))
            .remark(source.getRemark())
            .build();
        source.getItems().forEach(item -> clone.addItem(PurchaseAsnItem.builder()
            .purchaseOrderItem(item.getPurchaseOrderItem())
            .purchaseOrderAllocation(item.getPurchaseOrderAllocation())
            .productSku(item.getProductSku())
            .shippedQuantity(item.getShippedQuantity())
            .productionDate(item.getProductionDate())
            .expiryDate(item.getExpiryDate())
            .externalBatchCode(item.getExternalBatchCode())
            .remark(item.getRemark())
            .build()));
        updatePurchaseOrderStatus(source.getPurchaseOrder());
        refreshPlanAfterShipmentRemoval(affectedPlan, source.getPurchaseOrder());
        return mapper.toAsn(purchaseAsnRepository.save(clone));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse cancel(Long id, String reason, Long actorId, String actorName) {
        PurchaseAsn asn = requireAsnForUpdate(id);
        requireNoReceipts(asn, "CANCEL");
        ReceivingPlan affectedPlan = null;
        if (asn.getStatus() == PurchaseAsnStatus.CONFIRMED) {
            releaseShipment(asn);
            affectedPlan = cancelTask(asn);
        } else if (asn.getStatus() != PurchaseAsnStatus.DRAFT) {
            throw invalidAsnStatus(asn, "DRAFT,CONFIRMED");
        }
        asn.setStatus(PurchaseAsnStatus.CANCELLED);
        asn.setCancelledBy(actorId);
        asn.setCancelledByName(normalizeActor(actorName));
        asn.setCancelledAt(LocalDateTime.now());
        asn.setCancelReason(trim(reason));
        updatePurchaseOrderStatus(asn.getPurchaseOrder());
        refreshPlanAfterShipmentRemoval(affectedPlan, asn.getPurchaseOrder());
        return mapper.toAsn(asn);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse closeRemainder(Long id, String reason, Long actorId, String actorName) {
        if (governancePolicyService.effectiveShortReceiptClosureMode()
            == ShortReceiptClosureMode.APPROVAL_REQUIRED) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "CLOSE_SHORT_RECEIPT", "reason", "APPROVAL_REQUIRED")
            );
        }
        return closeRemainderCore(id, reason, actorId, actorName);
    }

    /** Approval workflow integration point; policy was already enforced by its approver. */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseAsnResponse closeRemainderAfterApproval(
        Long id,
        String reason,
        Long actorId,
        String actorName
    ) {
        return closeRemainderCore(id, reason, actorId, actorName);
    }

    private PurchaseAsnResponse closeRemainderCore(
        Long id,
        String reason,
        Long actorId,
        String actorName
    ) {
        PurchaseAsn asn = requireAsnForUpdate(id);
        if (asn.getStatus() != PurchaseAsnStatus.PARTIALLY_RECEIVED || !asn.hasReceipts()) {
            throw invalidAsnStatus(asn, "PARTIALLY_RECEIVED");
        }
        ReceivingTask task = receivingTaskRepository.findByPurchaseAsnId(asn.getId())
            .orElseThrow(() -> validation("receivingTask", asn.getId()));
        Map<Long, ReceivingTaskItem> taskItems = task.getItems().stream().collect(Collectors.toMap(
            item -> item.getPurchaseAsnItem().getId(), Function.identity()));
        for (PurchaseAsnItem item : asn.getItems()) {
            int remaining = item.remainingQuantity();
            if (remaining <= 0) {
                continue;
            }
            PurchaseOrderAllocation allocation = allocationRepository
                .findByIdForUpdate(item.getPurchaseOrderAllocation().getId())
                .orElseThrow(() -> validation("allocation", item.getPurchaseOrderAllocation().getId()));
            item.setClosedQuantity(item.getClosedQuantity() + remaining);
            allocation.setClosedQuantity(allocation.getClosedQuantity() + remaining);
            updateAllocationStatus(allocation);
            ReceivingTaskItem taskItem = taskItems.get(item.getId());
            taskItem.setClosedQuantity(taskItem.getClosedQuantity() + remaining);
        }
        LocalDateTime now = LocalDateTime.now();
        asn.setStatus(PurchaseAsnStatus.CLOSED);
        asn.setClosedBy(actorId);
        asn.setClosedByName(normalizeActor(actorName));
        asn.setClosedAt(now);
        asn.setCloseReason(trim(reason));
        task.setStatus(ReceivingTaskStatus.CLOSED);
        task.setClosedBy(actorId);
        task.setClosedByName(normalizeActor(actorName));
        task.setClosedAt(now);
        task.setCloseReason(trim(reason));
        updatePurchaseOrderStatus(asn.getPurchaseOrder());
        updatePlanStatus(task.getReceivingPlan());
        return mapper.toAsn(asn);
    }

    private void releaseShipment(PurchaseAsn asn) {
        asn.getItems().stream()
            .sorted(Comparator.comparing(item -> item.getPurchaseOrderAllocation().getId()))
            .forEach(item -> {
                PurchaseOrderAllocation allocation = allocationRepository
                    .findByIdForUpdate(item.getPurchaseOrderAllocation().getId())
                    .orElseThrow(() -> validation("allocation", item.getPurchaseOrderAllocation().getId()));
                allocation.setShippedQuantity(Math.max(
                    allocation.getReceivedQuantity(),
                    allocation.getShippedQuantity() - item.getShippedQuantity()
                ));
                updateAllocationStatus(allocation);
            });
    }

    private ReceivingPlan cancelTask(PurchaseAsn asn) {
        return receivingTaskRepository.findByPurchaseAsnId(asn.getId())
            .map(task -> {
                task.setStatus(ReceivingTaskStatus.CANCELLED);
                return task.getReceivingPlan();
            })
            .orElse(null);
    }

    private void refreshPlanAfterShipmentRemoval(ReceivingPlan plan, PurchaseOrder order) {
        if (plan == null) {
            return;
        }
        if (order.getStatus() == PurchaseOrderStatus.COMPLETED) {
            plan.setStatus(ReceivingPlanStatus.COMPLETED);
            return;
        }
        if (order.getStatus() == PurchaseOrderStatus.CLOSED) {
            plan.setStatus(ReceivingPlanStatus.CLOSED);
            return;
        }
        List<ReceivingTask> tasks = receivingTaskRepository.findByReceivingPlanId(plan.getId());
        if (tasks.stream().anyMatch(task -> task.getStatus() == ReceivingTaskStatus.PARTIALLY_RECEIVED
            || task.getStatus() == ReceivingTaskStatus.COMPLETED
            || task.getStatus() == ReceivingTaskStatus.CLOSED)) {
            plan.setStatus(ReceivingPlanStatus.PARTIALLY_RECEIVED);
        } else if (tasks.stream().anyMatch(task -> task.getStatus() == ReceivingTaskStatus.PENDING)) {
            plan.setStatus(ReceivingPlanStatus.IN_TRANSIT);
        } else {
            plan.setStatus(ReceivingPlanStatus.WAITING_SHIPMENT);
        }
    }

    private void requireNoReceipts(PurchaseAsn asn, String operation) {
        if (asn.hasReceipts()) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", operation + "_ASN", "reason", "ASN_HAS_RECEIPTS")
            );
        }
    }

    void updatePurchaseOrderStatus(PurchaseOrder order) {
        List<PurchaseOrderAllocation> allocations = allocationRepository
            .findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(order.getId());
        int planned = allocations.stream().mapToInt(PurchaseOrderAllocation::getPlannedQuantity).sum();
        int shipped = allocations.stream().mapToInt(PurchaseOrderAllocation::getShippedQuantity).sum();
        int received = allocations.stream().mapToInt(PurchaseOrderAllocation::getReceivedQuantity).sum();
        int closed = allocations.stream().mapToInt(PurchaseOrderAllocation::getClosedQuantity).sum();
        if (planned > 0 && received == planned) {
            order.setStatus(PurchaseOrderStatus.COMPLETED);
            order.setActualEntryDate(LocalDateTime.now());
        } else if (received > 0 && received + closed >= planned) {
            order.setStatus(PurchaseOrderStatus.CLOSED);
        } else if (received > 0) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        } else if (planned > 0 && shipped >= planned) {
            order.setStatus(PurchaseOrderStatus.IN_TRANSIT);
        } else if (shipped > 0) {
            order.setStatus(PurchaseOrderStatus.PARTIALLY_SHIPPED);
        } else {
            order.setStatus(PurchaseOrderStatus.AWAITING_SHIPMENT);
        }
    }

    void updateAllocationStatus(PurchaseOrderAllocation allocation) {
        if (allocation.getReceivedQuantity().equals(allocation.getPlannedQuantity())) {
            allocation.setStatus(PurchaseOrderAllocationStatus.RECEIVED);
        } else if (allocation.getReceivedQuantity() + allocation.getClosedQuantity()
            >= allocation.getPlannedQuantity()) {
            allocation.setStatus(PurchaseOrderAllocationStatus.CLOSED);
        } else {
            allocation.setStatus(PurchaseOrderAllocationStatus.ACTIVE);
        }
    }

    private void updatePlanStatus(ReceivingPlan plan) {
        if (plan == null) {
            return;
        }
        List<ReceivingTask> tasks = receivingTaskRepository.findByReceivingPlanId(plan.getId());
        if (tasks.stream().allMatch(task -> task.getStatus() == ReceivingTaskStatus.COMPLETED)) {
            plan.setStatus(ReceivingPlanStatus.COMPLETED);
        } else if (tasks.stream().allMatch(task -> task.getStatus() == ReceivingTaskStatus.COMPLETED
            || task.getStatus() == ReceivingTaskStatus.CLOSED
            || task.getStatus() == ReceivingTaskStatus.CANCELLED)) {
            plan.setStatus(ReceivingPlanStatus.CLOSED);
        } else if (tasks.stream().anyMatch(task -> task.getStatus() == ReceivingTaskStatus.PARTIALLY_RECEIVED)) {
            plan.setStatus(ReceivingPlanStatus.PARTIALLY_RECEIVED);
        }
    }

    private Warehouse requireActiveWarehouse(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
            .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.WAREHOUSE_NOT_FOUND, Map.of("warehouseId", warehouseId)));
    }

    /** Shared create/update validation and replacement of the editable draft lines. */
    private void replaceDraftItems(
        PurchaseAsn asn,
        PurchaseOrder order,
        Warehouse warehouse,
        List<DraftItemInput> requestedItems
    ) {
        Map<Long, PurchaseOrderItem> orderItems = order.getItems().stream()
            .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        Map<Long, PurchaseAsnItem> existingItems = asn.getItems().stream()
            .collect(Collectors.toMap(
                item -> item.getPurchaseOrderItem().getId(), Function.identity()));
        Set<Long> distinctItems = new HashSet<>();
        List<PurchaseAsnItem> desiredItems = new ArrayList<>();

        for (DraftItemInput requested : requestedItems) {
            PurchaseOrderItem orderItem = orderItems.get(requested.purchaseOrderItemId());
            if (orderItem == null || !distinctItems.add(requested.purchaseOrderItemId())) {
                throw validation("items.purchaseOrderItemId", requested.purchaseOrderItemId());
            }
            if (requested.shippedQuantity() == null || requested.shippedQuantity() <= 0) {
                throw validation(
                    "items.shippedQuantity",
                    requested.shippedQuantity() == null ? "null" : requested.shippedQuantity()
                );
            }
            PurchaseOrderAllocation allocation = allocationRepository
                .findByItemAndWarehouseForUpdate(orderItem.getId(), warehouse.getId())
                .orElseThrow(() -> new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                        "field", "items.purchaseOrderItemId",
                        "reason", "WAREHOUSE_NOT_ALLOCATED",
                        "purchaseOrderItemId", orderItem.getId(),
                        "warehouseId", warehouse.getId()
                    )
                ));
            if (requested.shippedQuantity() > allocation.availableToShipQuantity()) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                        "field", "items.shippedQuantity",
                        "purchaseOrderItemId", orderItem.getId(),
                        "requestedQuantity", requested.shippedQuantity(),
                        "availableQuantity", allocation.availableToShipQuantity()
                    )
                );
            }
            expiryPolicy.validateSuppliedDates(
                orderItem.getProductSku(), requested.productionDate(), requested.expiryDate());

            PurchaseAsnItem item = existingItems.remove(orderItem.getId());
            if (item == null) {
                item = PurchaseAsnItem.builder()
                    .purchaseOrderItem(orderItem)
                    .productSku(orderItem.getProductSku())
                    .build();
            }
            item.setPurchaseOrderAllocation(allocation);
            item.setShippedQuantity(requested.shippedQuantity());
            item.setProductionDate(requested.productionDate());
            item.setExpiryDate(requested.expiryDate());
            item.setExternalBatchCode(trim(requested.externalBatchCode()));
            item.setRemark(trim(requested.remark()));
            desiredItems.add(item);
        }

        asn.getItems().clear();
        desiredItems.forEach(asn::addItem);
    }

    private PurchaseOrder requireOrder(Long id) {
        return purchaseOrderRepository.findById(id).orElseThrow(() -> orderNotFound(id));
    }

    private PurchaseAsn requireAsn(Long id) {
        return purchaseAsnRepository.findById(id).orElseThrow(() -> asnNotFound(id));
    }

    private PurchaseAsn requireAsnForUpdate(Long id) {
        return purchaseAsnRepository.findByIdForUpdate(id).orElseThrow(() -> asnNotFound(id));
    }

    private BusinessException asnNotFound(Long id) {
        return new BusinessException(
            ErrorKeys.RESOURCE_NOT_FOUND, Map.of("resourceType", "PurchaseAsn", "resourceId", id));
    }

    private BusinessException orderNotFound(Long id) {
        return new BusinessException(ErrorKeys.PURCHASE_ORDER_NOT_FOUND, Map.of("purchaseOrderId", id));
    }

    private BusinessException invalidOrderStatus(PurchaseOrder order) {
        return new BusinessException(ErrorKeys.PO_INVALID_STATUS, Map.of(
            "purchaseOrderId", order.getId(), "currentStatus", order.getStatus().name(),
            "requiredStatus", "AWAITING_SHIPMENT,PARTIALLY_SHIPPED,IN_TRANSIT,PARTIALLY_RECEIVED"));
    }

    private BusinessException invalidAsnStatus(PurchaseAsn asn, String required) {
        return new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED, Map.of(
            "operation", "ASN_STATUS_TRANSITION", "resourceId", asn.getId(),
            "currentStatus", asn.getStatus().name(), "requiredStatus", required));
    }

    private BusinessException validation(String field, Object value) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field, "value", value));
    }

    private String uniqueNumber(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String normalizeActor(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DraftItemInput(
        Long purchaseOrderItemId,
        Integer shippedQuantity,
        LocalDate productionDate,
        LocalDate expiryDate,
        String externalBatchCode,
        String remark
    ) {
    }
}
