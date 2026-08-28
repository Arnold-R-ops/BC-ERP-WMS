package com.wms.system.service;

import com.wms.system.dto.receiving.PurchaseAsnResponse;
import com.wms.system.dto.receiving.PurchaseOrderAllocationResponse;
import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.ReceivingPlanResponse;
import com.wms.system.dto.receiving.ReceivingTaskResponse;
import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.ReceiptEvent;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.OtherInboundApplication;
import org.springframework.stereotype.Component;

@Component
public class ReceivingResponseMapper {

    public PurchaseAsnResponse toAsn(PurchaseAsn asn) {
        return new PurchaseAsnResponse(
            asn.getId(), asn.getVersion(), asn.getAsnNumber(), asn.getPurchaseOrder().getId(),
            asn.getPurchaseOrder().getPoNumber(), asn.getWarehouse().getId(),
            asn.getWarehouse().getCode(), asn.getStatus(),
            asn.getCorrectionOf() == null ? null : asn.getCorrectionOf().getId(),
            asn.getSupplierShipmentNo(), asn.getCarrier(), asn.getTrackingNo(),
            asn.getExpectedArrivalDate(), asn.getRemark(), asn.getConfirmedAt(),
            asn.getClosedAt(), asn.getCloseReason(), asn.getCancelledAt(),
            asn.getCancelReason(), asn.getItems().stream().map(item ->
                new PurchaseAsnResponse.Item(
                    item.getId(), item.getPurchaseOrderItem().getId(),
                    item.getProductSku().getId(), item.getProductSku().getSkuCode(),
                    item.getProductSku().getName(), item.getShippedQuantity(),
                    item.getReceivedQuantity(), item.getClosedQuantity(),
                    item.remainingQuantity(), item.getProductionDate(), item.getExpiryDate(),
                    item.getExternalBatchCode(), item.getRemark()
                )).toList()
        );
    }

    public PurchaseOrderAllocationResponse toAllocation(PurchaseOrderAllocation allocation) {
        return new PurchaseOrderAllocationResponse(
            allocation.getId(), allocation.getPurchaseOrderItem().getId(),
            allocation.getPurchaseOrderItem().getProductSku().getId(),
            allocation.getPurchaseOrderItem().getProductSku().getSkuCode(),
            allocation.getWarehouse().getId(), allocation.getWarehouse().getCode(),
            allocation.getPlannedQuantity(), allocation.getShippedQuantity(),
            allocation.getReceivedQuantity(), allocation.getClosedQuantity(),
            allocation.availableToShipQuantity(), allocation.getStatus()
        );
    }

    public ReceivingPlanResponse toPlan(
        ReceivingPlan plan,
        java.util.List<PurchaseOrderAllocation> allocations
    ) {
        return new ReceivingPlanResponse(
            plan.getId(), plan.getVersion(), plan.getPlanNumber(), plan.getPurchaseOrder().getId(),
            plan.getPurchaseOrder().getPoNumber(), plan.getWarehouse().getId(),
            plan.getWarehouse().getCode(), plan.getWarehouse().getName(), plan.getStatus(), plan.getPriority(),
            plan.getExpectedWindowStart(), plan.getExpectedWindowEnd(),
            plan.getAssignedReceiverId(), plan.getAssignedReceiverName(),
            plan.getAssignedTeam(), plan.getDockArea(), plan.getWorkArea(),
            plan.getWarehouseNotes(), allocations.stream().map(allocation ->
                new ReceivingPlanResponse.Item(
                    allocation.getId(), allocation.getPurchaseOrderItem().getId(),
                    allocation.getPurchaseOrderItem().getProductSku().getId(),
                    allocation.getPurchaseOrderItem().getProductSku().getSkuCode(),
                    allocation.getPurchaseOrderItem().getProductSku().getName(),
                    allocation.getPlannedQuantity(), allocation.getShippedQuantity(),
                    allocation.availableToShipQuantity()
                )).toList()
        );
    }

    public ReceivingTaskResponse toTask(ReceivingTask task) {
        ReceivingPlan plan = task.getReceivingPlan();
        OtherInboundApplication otherInbound = task.getOtherInboundApplication();
        return new ReceivingTaskResponse(
            task.getId(), task.getTaskNumber(), task.getSourceType(), task.getStatus(),
            plan == null ? null : plan.getPurchaseOrder().getId(),
            plan == null ? null : plan.getPurchaseOrder().getPoNumber(),
            otherInbound == null ? null : otherInbound.getId(),
            otherInbound == null ? null : otherInbound.getApplicationNumber(),
            otherInbound == null ? null : otherInbound.getBusinessType(),
            otherInbound == null ? null : otherInbound.getSourceReference(),
            otherInbound == null ? null : otherInbound.getBusinessReason(),
            task.getWarehouse().getId(), task.getWarehouse().getCode(),
            task.getWarehouse().getName(), plan == null ? null : plan.getPriority(),
            plan == null ? null : plan.getExpectedWindowStart(),
            plan == null ? null : plan.getExpectedWindowEnd(),
            plan == null ? null : plan.getAssignedReceiverId(),
            plan == null ? null : plan.getAssignedReceiverName(),
            plan == null ? null : plan.getAssignedTeam(),
            plan == null ? null : plan.getDockArea(),
            plan == null ? null : plan.getWorkArea(),
            plan == null ? null : plan.getWarehouseNotes(), task.getCreatedAt(),
            task.getCompletedAt(), task.getClosedAt(), task.getCloseReason(),
            task.getItems().stream().map(item -> new ReceivingTaskResponse.Item(
                item.getId(), item.getProductSku().getId(), item.getProductSku().getSkuCode(),
                item.getProductSku().getBarcode(), item.getProductSku().getName(),
                item.getProductSku().getExpiryManagementEnabled(),
                item.getExpectedQuantity(), item.getReceivedQuantity(),
                item.getClosedQuantity(), item.remainingQuantity(), item.getProductionDate(),
                item.getExpiryDate(), item.getExternalBatchCode(), item.getRemark()
            )).toList()
        );
    }

    public ReceiptEventResponse toReceipt(ReceiptEvent event) {
        return new ReceiptEventResponse(
            event.getId(), event.getEventNumber(), event.getReceivingTask().getId(),
            event.getReceivingTask().getTaskNumber(), event.getReceivedAt(),
            event.getReceivedByName(), event.getRemark(), event.getItems().stream().map(item ->
                new ReceiptEventResponse.Line(
                    item.getId(), item.getReceivingTaskItem().getId(),
                    item.getProductSku().getId(), item.getProductSku().getSkuCode(),
                    item.getLocation().getId(), item.getLocation().getLocationCode(),
                    item.getInventoryBatch().getId(), item.getBatchCode(), item.getQuantity(),
                    item.getProductionDate(), item.getExpiryDate(), item.getExternalBatchCode()
                )).toList()
        );
    }
}
