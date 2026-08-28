package com.wms.system.service;

import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.entity.OtherInboundApplication;
import com.wms.system.entity.OtherInboundApplicationItem;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.ReceivingTaskItem;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.OtherInboundBusinessType;
import com.wms.system.entity.enums.OtherInboundStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.OtherInboundApplicationRepository;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.PurchaseOrderReceivingRepository;
import com.wms.system.repository.ReceiptEventRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.service.inventory.InventoryPostingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivingReceiptExecutorTest {

    @Mock ReceivingTaskRepository receivingTaskRepository;
    @Mock PurchaseOrderReceivingRepository purchaseOrderRepository;
    @Mock PurchaseAsnRepository purchaseAsnRepository;
    @Mock PurchaseOrderAllocationRepository allocationRepository;
    @Mock OtherInboundApplicationRepository otherInboundApplicationRepository;
    @Mock ReceiptEventRepository receiptEventRepository;
    @Mock LocationRepository locationRepository;
    @Mock InventoryBatchRepository inventoryBatchRepository;
    @Mock InventoryPostingService inventoryPostingService;
    @Mock ProductSkuExpiryPolicy expiryPolicy;
    @Mock PurchaseAsnService purchaseAsnService;
    @Mock BackorderService backorderService;
    @Mock ReceivingResponseMapper mapper;

    @InjectMocks ReceivingReceiptExecutor executor;

    @Test
    void rejectsAggregateOverReceiptBeforeCreatingAnyBatchOrPostingInventory() {
        ReceivingTaskItem item = ReceivingTaskItem.builder()
            .id(11L).expectedQuantity(5).receivedQuantity(0).closedQuantity(0).build();
        PurchaseOrder order = PurchaseOrder.builder().id(2L).poNumber("PO-2").build();
        PurchaseAsn asn = PurchaseAsn.builder()
            .id(3L).purchaseOrder(order).status(PurchaseAsnStatus.CONFIRMED).build();
        ReceivingTask task = ReceivingTask.builder()
            .id(7L)
            .sourceType(ReceivingTaskSourceType.PURCHASE_ASN)
            .purchaseAsn(asn)
            .receivingPlan(ReceivingPlan.builder().id(4L).build())
            .warehouse(Warehouse.builder().id(2L).build())
            .status(ReceivingTaskStatus.PENDING)
            .items(List.of(item))
            .build();
        when(receiptEventRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(receivingTaskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(purchaseOrderRepository.findByIdForReceiving(2L)).thenReturn(Optional.of(order));
        when(purchaseAsnRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(asn));
        when(receivingTaskRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(task));

        SubmitReceivingReceiptRequest request = new SubmitReceivingReceiptRequest(
            null,
            List.of(
                new SubmitReceivingReceiptRequest.Line(11L, 20L, 3, null, null, null),
                new SubmitReceivingReceiptRequest.Line(11L, 21L, 3, null, null, null)
            )
        );

        assertThrows(BusinessException.class, () ->
            executor.execute(7L, "key-1", request, 9L, "receiver"));
        verify(inventoryBatchRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(inventoryPostingService, never()).post(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsOtherInboundTaskItemLinkedToAnotherApplicationBeforePosting() {
        ProductSku sku = ProductSku.builder()
            .id(51L).skuCode("SKU-51").name("Sample").enabled(true).build();
        OtherInboundApplication lockedApplication = application(31L, "OIN-31");
        OtherInboundApplication foreignApplication = application(32L, "OIN-32");
        OtherInboundApplicationItem foreignItem = OtherInboundApplicationItem.builder()
            .id(41L).productSku(sku).plannedQuantity(5).build();
        foreignApplication.addItem(foreignItem);
        ReceivingTask task = otherInboundTask(lockedApplication, foreignItem, sku);
        stubOtherInboundLocks(task, lockedApplication, "other-key-foreign");

        assertThrows(BusinessException.class, () -> executor.execute(
            7L,
            "other-key-foreign",
            receiptRequest(),
            9L,
            "receiver"
        ));

        verify(receiptEventRepository, never())
            .saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(inventoryBatchRepository, never())
            .saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(inventoryPostingService, never())
            .post(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsOtherInboundTaskItemWhoseSkuDiffersFromSourceItemBeforePosting() {
        ProductSku sourceSku = ProductSku.builder()
            .id(51L).skuCode("SKU-51").name("Source SKU").enabled(true).build();
        ProductSku wrongTaskSku = ProductSku.builder()
            .id(52L).skuCode("SKU-52").name("Wrong SKU").enabled(true).build();
        OtherInboundApplication application = application(31L, "OIN-31");
        OtherInboundApplicationItem sourceItem = OtherInboundApplicationItem.builder()
            .id(41L).productSku(sourceSku).plannedQuantity(5).build();
        application.addItem(sourceItem);
        ReceivingTask task = otherInboundTask(application, sourceItem, wrongTaskSku);
        stubOtherInboundLocks(task, application, "other-key-sku");

        assertThrows(BusinessException.class, () -> executor.execute(
            7L,
            "other-key-sku",
            receiptRequest(),
            9L,
            "receiver"
        ));

        verify(receiptEventRepository, never())
            .saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(inventoryBatchRepository, never())
            .saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(inventoryPostingService, never())
            .post(org.mockito.ArgumentMatchers.any());
    }

    private OtherInboundApplication application(Long id, String number) {
        return OtherInboundApplication.builder()
            .id(id)
            .applicationNumber(number)
            .businessType(OtherInboundBusinessType.FREE_SAMPLE)
            .sourceReference("SAMPLE-REF")
            .businessReason("Controlled sample receipt")
            .status(OtherInboundStatus.CONFIRMED)
            .createdBy(1L)
            .createdByName("applicant")
            .build();
    }

    private ReceivingTask otherInboundTask(
        OtherInboundApplication application,
        OtherInboundApplicationItem sourceItem,
        ProductSku taskSku
    ) {
        ReceivingTask task = ReceivingTask.builder()
            .id(7L)
            .taskNumber("RCV-7")
            .sourceType(ReceivingTaskSourceType.OTHER_INBOUND)
            .otherInboundApplication(application)
            .warehouse(Warehouse.builder().id(2L).build())
            .status(ReceivingTaskStatus.PENDING)
            .createdBy(1L)
            .createdByName("planner")
            .build();
        task.addItem(ReceivingTaskItem.builder()
            .id(11L)
            .otherInboundApplicationItem(sourceItem)
            .productSku(taskSku)
            .expectedQuantity(5)
            .build());
        return task;
    }

    private void stubOtherInboundLocks(
        ReceivingTask task,
        OtherInboundApplication application,
        String idempotencyKey
    ) {
        when(receiptEventRepository.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());
        when(receivingTaskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(otherInboundApplicationRepository.findByIdForUpdate(application.getId()))
            .thenReturn(Optional.of(application));
        when(receivingTaskRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(task));
    }

    private SubmitReceivingReceiptRequest receiptRequest() {
        return new SubmitReceivingReceiptRequest(
            null,
            List.of(new SubmitReceivingReceiptRequest.Line(
                11L, 20L, 1, null, null, null))
        );
    }
}
