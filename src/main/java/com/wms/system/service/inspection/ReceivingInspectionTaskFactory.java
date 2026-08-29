package com.wms.system.service.inspection;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.ReceivingInspectionTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Joins the caller's receipt transaction; a task can never outlive a failed receipt. */
@Service
@RequiredArgsConstructor
public class ReceivingInspectionTaskFactory {
    private final ReceivingInspectionTaskRepository taskRepository;
    private final ReceivingInspectionPolicyService policyService;

    public ReceivingInspectionTask createForReceipt(ReceiptEvent event) {
        if (event.getItems().stream().noneMatch(
            item -> Boolean.TRUE.equals(item.getInspectionRequired()))) {
            return null;
        }
        ReceivingTask receivingTask = event.getReceivingTask();
        ReceivingInspectionTask task = ReceivingInspectionTask.builder()
            .taskNumber(number("IQT"))
            .sourceType(ReceivingInspectionSourceType.RECEIPT)
            .receiptEvent(event)
            .receivingTask(receivingTask)
            .warehouse(receivingTask.getWarehouse())
            .sourceReference(safeReference(receivingTask))
            .status(ReceivingInspectionTaskStatus.PENDING)
            .reviewModeSnapshot(policyService.reviewMode())
            .createdBy(event.getReceivedBy())
            .createdByName(event.getReceivedByName())
            .build();
        for (ReceiptEventItem item : event.getItems()) {
            if (!Boolean.TRUE.equals(item.getInspectionRequired())) continue;
            task.addLine(ReceivingInspectionTaskLine.builder()
                .receiptEventItem(item)
                .productSku(item.getProductSku())
                .sourceBatch(item.getInventoryBatch())
                .location(item.getLocation())
                .skuCode(item.getProductSku().getSkuCode())
                .productName(item.getProductSku().getName())
                .batchCode(item.getBatchCode())
                .locationCode(item.getLocation().getLocationCode())
                .plannedQuantity(item.getQuantity())
                .pendingQuantity(item.getQuantity())
                .build());
        }
        return taskRepository.saveAndFlush(task);
    }

    private String safeReference(ReceivingTask task) {
        if (task.getReceivingPlan() != null
            && task.getReceivingPlan().getPurchaseOrder() != null) {
            return task.getReceivingPlan().getPurchaseOrder().getPoNumber();
        }
        if (task.getOtherInboundApplication() != null) {
            return task.getOtherInboundApplication().getApplicationNumber();
        }
        return task.getTaskNumber();
    }

    public static String number(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 20).toUpperCase();
    }
}
