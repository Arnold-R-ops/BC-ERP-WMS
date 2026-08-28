package com.wms.system.service;

import com.wms.system.dto.receiving.PurchaseOrderAllocationResponse;
import com.wms.system.dto.receiving.SavePurchaseOrderAllocationsRequest;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.PurchaseOrderItem;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.PurchaseOrderReceivingRepository;
import com.wms.system.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Maintains the pre-submission, per-line and per-warehouse purchase plan. */
@Service
@RequiredArgsConstructor
public class PurchaseOrderAllocationService {

    private final PurchaseOrderReceivingRepository purchaseOrderRepository;
    private final PurchaseOrderAllocationRepository allocationRepository;
    private final WarehouseRepository warehouseRepository;
    private final ReceivingResponseMapper mapper;

    @Transactional(readOnly = true)
    public List<PurchaseOrderAllocationResponse> list(Long purchaseOrderId) {
        requireOrder(purchaseOrderId);
        return allocationRepository
            .findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(purchaseOrderId)
            .stream().map(mapper::toAllocation).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<PurchaseOrderAllocationResponse> replace(
        Long purchaseOrderId,
        SavePurchaseOrderAllocationsRequest request
    ) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForReceiving(purchaseOrderId)
            .orElseThrow(() -> notFound(purchaseOrderId));
        if (!order.getStatus().isEditableDraft()) {
            throw invalidStatus(order, "DRAFT,REJECTED");
        }
        if (!Objects.equals(order.getVersion(), request.purchaseOrderVersion())) {
            throw new BusinessException(
                ErrorKeys.PO_EDIT_CONFLICT,
                Map.of("purchaseOrderId", purchaseOrderId,
                    "expectedVersion", request.purchaseOrderVersion(),
                    "currentVersion", order.getVersion())
            );
        }

        Map<Long, PurchaseOrderItem> orderItems = order.getItems().stream()
            .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        Map<Long, Warehouse> warehouses = loadWarehouses(request);
        Map<AllocationKey, SavePurchaseOrderAllocationsRequest.Row> requested = new LinkedHashMap<>();
        Map<Long, Integer> plannedByItem = new HashMap<>();

        for (SavePurchaseOrderAllocationsRequest.Row row : request.allocations()) {
            PurchaseOrderItem item = orderItems.get(row.purchaseOrderItemId());
            if (item == null) {
                throw validation("allocations.purchaseOrderItemId", row.purchaseOrderItemId());
            }
            Warehouse warehouse = warehouses.get(row.warehouseId());
            if (warehouse == null || !Boolean.TRUE.equals(warehouse.getIsActive())) {
                throw new BusinessException(
                    ErrorKeys.WAREHOUSE_NOT_FOUND,
                    Map.of("warehouseId", row.warehouseId())
                );
            }
            AllocationKey key = new AllocationKey(item.getId(), warehouse.getId());
            if (requested.putIfAbsent(key, row) != null) {
                throw validation("allocations", "duplicate-item-warehouse");
            }
            plannedByItem.merge(item.getId(), row.plannedQuantity(), Math::addExact);
        }
        validateCompletePlan(order, plannedByItem);

        List<PurchaseOrderAllocation> existing = allocationRepository
            .findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(purchaseOrderId);
        if (existing.stream().anyMatch(this::hasExecutionHistory)) {
            throw new BusinessException(
                ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "REPLACE_PURCHASE_ALLOCATIONS", "reason", "ALLOCATION_HAS_EXECUTION_HISTORY")
            );
        }
        Map<AllocationKey, PurchaseOrderAllocation> existingByKey = existing.stream()
            .collect(Collectors.toMap(
                a -> new AllocationKey(a.getPurchaseOrderItem().getId(), a.getWarehouse().getId()),
                Function.identity()
            ));

        Set<AllocationKey> retained = new HashSet<>();
        for (Map.Entry<AllocationKey, SavePurchaseOrderAllocationsRequest.Row> entry : requested.entrySet()) {
            AllocationKey key = entry.getKey();
            SavePurchaseOrderAllocationsRequest.Row row = entry.getValue();
            PurchaseOrderAllocation allocation = existingByKey.get(key);
            if (allocation == null) {
                allocation = PurchaseOrderAllocation.builder()
                    .purchaseOrderItem(orderItems.get(key.itemId()))
                    .warehouse(warehouses.get(key.warehouseId()))
                    .plannedQuantity(row.plannedQuantity())
                    .status(PurchaseOrderAllocationStatus.ACTIVE)
                    .build();
            } else {
                allocation.setPlannedQuantity(row.plannedQuantity());
                allocation.setStatus(PurchaseOrderAllocationStatus.ACTIVE);
            }
            allocationRepository.save(allocation);
            retained.add(key);
        }
        existing.stream()
            .filter(a -> !retained.contains(new AllocationKey(
                a.getPurchaseOrderItem().getId(), a.getWarehouse().getId())))
            .forEach(allocationRepository::delete);
        order.appendAuditLog(String.format(
            "Warehouse allocation plan replaced (%d rows)", requested.size()));
        allocationRepository.flush();

        return allocationRepository
            .findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(purchaseOrderId)
            .stream().map(mapper::toAllocation).toList();
    }

    /** Called by the purchase submit/effective boundary before workflow advances. */
    @Transactional(readOnly = true)
    public List<PurchaseOrderAllocation> requireCompletePlan(PurchaseOrder order) {
        List<PurchaseOrderAllocation> allocations = allocationRepository
            .findByPurchaseOrderItemPurchaseOrderIdOrderByIdAsc(order.getId());
        Map<Long, Integer> totals = allocations.stream().collect(Collectors.groupingBy(
            allocation -> allocation.getPurchaseOrderItem().getId(),
            Collectors.summingInt(PurchaseOrderAllocation::getPlannedQuantity)
        ));
        validateCompletePlan(order, totals);
        return allocations;
    }

    private Map<Long, Warehouse> loadWarehouses(SavePurchaseOrderAllocationsRequest request) {
        Set<Long> ids = request.allocations().stream()
            .map(SavePurchaseOrderAllocationsRequest.Row::warehouseId)
            .collect(Collectors.toSet());
        return warehouseRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private void validateCompletePlan(PurchaseOrder order, Map<Long, Integer> plannedByItem) {
        for (PurchaseOrderItem item : order.getItems()) {
            int planned = plannedByItem.getOrDefault(item.getId(), 0);
            if (planned != item.getOrderedQuantity()) {
                throw new BusinessException(
                    ErrorKeys.VALIDATION_FAILED,
                    Map.of(
                        "field", "allocations.plannedQuantity",
                        "purchaseOrderItemId", item.getId(),
                        "orderedQuantity", item.getOrderedQuantity(),
                        "plannedQuantity", planned
                    )
                );
            }
        }
    }

    private boolean hasExecutionHistory(PurchaseOrderAllocation allocation) {
        return allocation.getShippedQuantity() > 0
            || allocation.getReceivedQuantity() > 0
            || allocation.getClosedQuantity() > 0;
    }

    private PurchaseOrder requireOrder(Long id) {
        return purchaseOrderRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private BusinessException notFound(Long id) {
        return new BusinessException(ErrorKeys.PURCHASE_ORDER_NOT_FOUND, Map.of("purchaseOrderId", id));
    }

    private BusinessException invalidStatus(PurchaseOrder order, String required) {
        return new BusinessException(ErrorKeys.PO_INVALID_STATUS, Map.of(
            "purchaseOrderId", order.getId(), "currentStatus", order.getStatus().name(),
            "requiredStatus", required
        ));
    }

    private BusinessException validation(String field, Object value) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field, "value", value));
    }

    private record AllocationKey(Long itemId, Long warehouseId) {
    }
}
