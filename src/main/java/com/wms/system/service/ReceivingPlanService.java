package com.wms.system.service;

import com.wms.system.dto.receiving.ReceivingPlanResponse;
import com.wms.system.dto.receiving.UpdateReceivingPlanOperationsRequest;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.ReceivingPlanRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Collection;

@Service
@RequiredArgsConstructor
public class ReceivingPlanService {

    private final ReceivingPlanRepository receivingPlanRepository;
    private final ReceivingTaskRepository receivingTaskRepository;
    private final PurchaseOrderAllocationService allocationService;
    private final PurchaseOrderAllocationRepository allocationRepository;
    private final ReceivingResponseMapper mapper;

    /**
     * Purchase workflow integration point: call exactly when a PO becomes
     * effective. One WAITING_SHIPMENT plan is materialized per allocated warehouse.
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ReceivingPlan> ensureWaitingShipmentPlans(
        PurchaseOrder order,
        Long actorId,
        String actorName
    ) {
        List<PurchaseOrderAllocation> allocations = allocationService.requireCompletePlan(order);
        Map<Long, Warehouse> warehouses = new LinkedHashMap<>();
        allocations.forEach(allocation -> warehouses.put(
            allocation.getWarehouse().getId(), allocation.getWarehouse()));

        return warehouses.values().stream().map(warehouse -> receivingPlanRepository
            .findByPurchaseOrderAndWarehouseForUpdate(order.getId(), warehouse.getId())
            .map(existing -> reopenWithdrawnPlan(existing))
            .orElseGet(() -> receivingPlanRepository.save(ReceivingPlan.builder()
                .planNumber(planNumber(order, warehouse))
                .purchaseOrder(order)
                .warehouse(warehouse)
                .status(ReceivingPlanStatus.WAITING_SHIPMENT)
                .createdBy(actorId)
                .createdByName(normalizeActor(actorName))
                .expectedWindowStart(order.getExpectedDate() == null
                    ? null : order.getExpectedDate().atStartOfDay())
                .expectedWindowEnd(order.getExpectedDate() == null
                    ? null : order.getExpectedDate().atTime(LocalTime.MAX))
                .build())))
            .toList();
    }

    private ReceivingPlan reopenWithdrawnPlan(ReceivingPlan plan) {
        if (plan.getStatus() != ReceivingPlanStatus.CLOSED) {
            return plan;
        }
        boolean onlyCancelledTasks = receivingTaskRepository.findByReceivingPlanId(plan.getId())
            .stream().allMatch(task -> task.getStatus()
                == com.wms.system.entity.enums.ReceivingTaskStatus.CANCELLED);
        if (!onlyCancelledTasks) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "REOPEN_RECEIVING_PLAN",
                    "resourceId", plan.getId(), "reason", "LIVE_OR_HISTORICAL_TASK_EXISTS")
            );
        }
        plan.setStatus(ReceivingPlanStatus.WAITING_SHIPMENT);
        return plan;
    }

    @Transactional(readOnly = true)
    public List<ReceivingPlanResponse> listForPurchaseOrder(Long purchaseOrderId) {
        return toResponses(receivingPlanRepository
            .findByPurchaseOrderIdOrderByIdAsc(purchaseOrderId));
    }

    @Transactional(readOnly = true)
    public List<ReceivingPlanResponse> listForPurchaseOrder(
        Long purchaseOrderId,
        Collection<Long> warehouseIds
    ) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return List.of();
        }
        return toResponses(receivingPlanRepository
            .findByPurchaseOrderIdAndWarehouseIdInOrderByIdAsc(
                purchaseOrderId, warehouseIds));
    }

    @Transactional(readOnly = true)
    public List<ReceivingPlanResponse> list(ReceivingPlanStatus status) {
        List<ReceivingPlan> plans = status == null
            ? receivingPlanRepository.findAllByOrderByIdDesc()
            : receivingPlanRepository.findByStatusOrderByIdDesc(status);
        return toResponses(plans);
    }

    @Transactional(readOnly = true)
    public List<ReceivingPlanResponse> list(Collection<ReceivingPlanStatus> statuses) {
        List<ReceivingPlan> plans = statuses == null || statuses.isEmpty()
            ? receivingPlanRepository.findAllByOrderByIdDesc()
            : receivingPlanRepository.findByStatusInOrderByIdDesc(statuses);
        return toResponses(plans);
    }

    @Transactional(readOnly = true)
    public List<ReceivingPlanResponse> list(
        ReceivingPlanStatus status,
        Collection<Long> warehouseIds
    ) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return List.of();
        }
        List<ReceivingPlan> plans = status == null
            ? receivingPlanRepository.findByWarehouseIdInOrderByIdDesc(warehouseIds)
            : receivingPlanRepository.findByStatusAndWarehouseIdInOrderByIdDesc(
                status, warehouseIds);
        return toResponses(plans);
    }

    @Transactional(readOnly = true)
    public List<ReceivingPlanResponse> list(
        Collection<ReceivingPlanStatus> statuses,
        Collection<Long> warehouseIds
    ) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return List.of();
        }
        List<ReceivingPlan> plans = statuses == null || statuses.isEmpty()
            ? receivingPlanRepository.findByWarehouseIdInOrderByIdDesc(warehouseIds)
            : receivingPlanRepository.findByStatusInAndWarehouseIdInOrderByIdDesc(
                statuses, warehouseIds);
        return toResponses(plans);
    }

    @Transactional(readOnly = true)
    public Long getWarehouseId(Long planId) {
        return receivingPlanRepository.findById(planId)
            .map(plan -> plan.getWarehouse().getId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "ReceivingPlan", "resourceId", planId)
            ));
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceivingPlanResponse updateOperations(
        Long planId,
        UpdateReceivingPlanOperationsRequest request
    ) {
        ReceivingPlan plan = receivingPlanRepository.findByIdForUpdate(planId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "ReceivingPlan", "resourceId", planId)
            ));
        if (!Objects.equals(plan.getVersion(), request.version())) {
            throw new BusinessException(
                ErrorKeys.PO_EDIT_CONFLICT,
                Map.of("resourceType", "ReceivingPlan", "resourceId", planId,
                    "expectedVersion", request.version(),
                    "currentVersion", plan.getVersion())
            );
        }
        if (plan.getStatus() == ReceivingPlanStatus.COMPLETED
            || plan.getStatus() == ReceivingPlanStatus.CLOSED) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "UPDATE_RECEIVING_PLAN",
                    "resourceId", planId, "currentStatus", plan.getStatus().name())
            );
        }
        if (request.expectedWindowStart() != null && request.expectedWindowEnd() != null
            && request.expectedWindowEnd().isBefore(request.expectedWindowStart())) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "expectedWindowEnd", "constraint", "must-not-precede-start")
            );
        }
        plan.setPriority(request.priority() == null ? 0 : request.priority());
        plan.setExpectedWindowStart(request.expectedWindowStart());
        plan.setExpectedWindowEnd(request.expectedWindowEnd());
        plan.setAssignedReceiverId(request.assignedReceiverId());
        plan.setAssignedReceiverName(trim(request.assignedReceiverName()));
        plan.setAssignedTeam(trim(request.assignedTeam()));
        plan.setDockArea(trim(request.dockArea()));
        plan.setWorkArea(trim(request.workArea()));
        plan.setWarehouseNotes(trim(request.warehouseNotes()));
        return toResponses(List.of(receivingPlanRepository.save(plan))).get(0);
    }

    /** Withdraws plans only while no live shipment/receipt task remains. */
    @Transactional(rollbackFor = Exception.class)
    public void withdrawUnstartedPlans(Long purchaseOrderId) {
        for (ReceivingPlan plan : receivingPlanRepository
            .findByPurchaseOrderIdOrderByIdAsc(purchaseOrderId)) {
            boolean hasLiveTask = receivingTaskRepository.findByReceivingPlanId(plan.getId())
                .stream().anyMatch(task -> task.getStatus() != com.wms.system.entity.enums.ReceivingTaskStatus.CANCELLED);
            if (!hasLiveTask && (plan.getStatus() == ReceivingPlanStatus.WAITING_SHIPMENT
                || plan.getStatus() == ReceivingPlanStatus.IN_TRANSIT)) {
                plan.setStatus(ReceivingPlanStatus.CLOSED);
            }
        }
    }

    private String planNumber(PurchaseOrder order, Warehouse warehouse) {
        return "RPL-" + order.getId() + "-" + warehouse.getId();
    }

    private List<ReceivingPlanResponse> toResponses(List<ReceivingPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<PlanKey, List<PurchaseOrderAllocation>> allocationsByPlan =
            allocationRepository.findForReceivingPlanProjection(
                plans.stream().map(ReceivingPlan::getId).toList()
            ).stream().collect(Collectors.groupingBy(
                allocation -> new PlanKey(
                    allocation.getPurchaseOrderItem().getPurchaseOrder().getId(),
                    allocation.getWarehouse().getId()
                ),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        return plans.stream().map(plan -> mapper.toPlan(
            plan,
            allocationsByPlan.getOrDefault(new PlanKey(
                plan.getPurchaseOrder().getId(), plan.getWarehouse().getId()),
                List.of())
        )).toList();
    }

    private record PlanKey(Long purchaseOrderId, Long warehouseId) {
    }

    private String normalizeActor(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
