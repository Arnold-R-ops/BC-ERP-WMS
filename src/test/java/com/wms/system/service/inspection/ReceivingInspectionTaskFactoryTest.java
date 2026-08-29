package com.wms.system.service.inspection;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.ReceivingInspectionTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceivingInspectionTaskFactoryTest {
    @Mock ReceivingInspectionTaskRepository repository;
    @Mock ReceivingInspectionPolicyService policyService;

    @Test
    void createsOneWarehouseSafeTaskContainingOnlyInspectionLines() {
        Warehouse warehouse = Warehouse.builder().id(8L).code("WH-8").name("Main").build();
        PurchaseOrder order = PurchaseOrder.builder().id(9L).poNumber("PO-100").build();
        ReceivingPlan plan = ReceivingPlan.builder().id(10L)
            .purchaseOrder(order).warehouse(warehouse).build();
        ReceivingTask receivingTask = ReceivingTask.builder().id(11L).taskNumber("RCV-11")
            .sourceType(ReceivingTaskSourceType.PURCHASE_ASN).receivingPlan(plan)
            .warehouse(warehouse).build();
        ReceiptEvent event = ReceiptEvent.builder().id(12L).receivingTask(receivingTask)
            .receivedBy(7L).receivedByName("receiver").build();
        event.addItem(item(1L, true, warehouse));
        event.addItem(item(2L, false, warehouse));
        when(policyService.reviewMode()).thenReturn(ReceivingInspectionReviewMode.DIRECT);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReceivingInspectionTask task =
            new ReceivingInspectionTaskFactory(repository, policyService).createForReceipt(event);

        assertThat(task.getSourceReference()).isEqualTo("PO-100");
        assertThat(task.getLines()).singleElement()
            .satisfies(line -> assertThat(line.getProductSku().getId()).isEqualTo(1L));
        assertThat(task.getSourceType()).isEqualTo(ReceivingInspectionSourceType.RECEIPT);
    }

    private ReceiptEventItem item(Long id, boolean inspection, Warehouse warehouse) {
        ProductSku sku = ProductSku.builder().id(id).skuCode("SKU-" + id)
            .name("Product " + id).build();
        Location location = Location.builder().id(id + 20).locationCode("L-" + id)
            .warehouse(warehouse).build();
        InventoryBatch batch = InventoryBatch.builder().id(id + 30)
            .batchCode("B-" + id).productSku(sku).location(location)
            .locationCode(location.getLocationCode()).quantity(5).reservedQuantity(0)
            .qualityStatus(inspection ? InventoryQualityStatus.PENDING_INSPECTION
                : InventoryQualityStatus.AVAILABLE).active(true).build();
        return ReceiptEventItem.builder().id(id).productSku(sku).location(location)
            .inventoryBatch(batch).batchCode(batch.getBatchCode()).quantity(5)
            .inspectionRequired(inspection).build();
    }
}
