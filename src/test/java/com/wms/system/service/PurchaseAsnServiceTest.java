package com.wms.system.service;

import com.wms.system.dto.receiving.UpdatePurchaseAsnDraftRequest;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseAsnItem;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.PurchaseOrderItem;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.PurchaseOrderReceivingRepository;
import com.wms.system.repository.ReceivingPlanRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseAsnServiceTest {

    @Mock private PurchaseAsnRepository purchaseAsnRepository;
    @Mock private PurchaseOrderReceivingRepository purchaseOrderRepository;
    @Mock private PurchaseOrderAllocationRepository allocationRepository;
    @Mock private ReceivingPlanRepository receivingPlanRepository;
    @Mock private ReceivingTaskRepository receivingTaskRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ReceivingPlanService receivingPlanService;
    @Mock private PurchaseGovernancePolicyService governancePolicyService;
    @Mock private ProductSkuExpiryPolicy expiryPolicy;

    private PurchaseAsnService service;
    private PurchaseOrder order;
    private PurchaseOrderItem orderItem;
    private ProductSku sku;
    private Warehouse oldWarehouse;
    private Warehouse targetWarehouse;
    private PurchaseAsn asn;

    @BeforeEach
    void setUp() {
        service = new PurchaseAsnService(
            purchaseAsnRepository,
            purchaseOrderRepository,
            allocationRepository,
            receivingPlanRepository,
            receivingTaskRepository,
            warehouseRepository,
            receivingPlanService,
            governancePolicyService,
            expiryPolicy,
            new ReceivingResponseMapper()
        );
        oldWarehouse = Warehouse.builder()
            .id(20L).code("WH20").name("Old warehouse").isActive(true).build();
        targetWarehouse = Warehouse.builder()
            .id(21L).code("WH21").name("Target warehouse").isActive(true).build();
        sku = ProductSku.builder()
            .id(30L).skuCode("SKU-30").name("Warehouse item")
            .expiryManagementEnabled(false).build();
        order = PurchaseOrder.builder()
            .id(10L).poNumber("PO-10").status(PurchaseOrderStatus.AWAITING_SHIPMENT)
            .build();
        orderItem = PurchaseOrderItem.builder()
            .id(40L).productSku(sku).orderedQuantity(100).build();
        order.addItem(orderItem);
        PurchaseOrderAllocation oldAllocation = allocation(50L, oldWarehouse);
        asn = PurchaseAsn.builder()
            .id(60L).version(3L).asnNumber("ASN-60").purchaseOrder(order)
            .warehouse(oldWarehouse).status(PurchaseAsnStatus.DRAFT)
            .supplierShipmentNo("OLD").createdBy(1L).createdByName("creator")
            .build();
        asn.addItem(PurchaseAsnItem.builder()
            .id(70L).purchaseOrderItem(orderItem).purchaseOrderAllocation(oldAllocation)
            .productSku(sku).shippedQuantity(4).build());
    }

    @Test
    void updateDraftReplacesEditableDataAndAdvancesVersion() {
        PurchaseOrderAllocation targetAllocation = PurchaseOrderAllocation.builder()
            .id(51L).purchaseOrderItem(orderItem).warehouse(targetWarehouse)
            .plannedQuantity(100).shippedQuantity(60).receivedQuantity(50).closedQuantity(10)
            .status(PurchaseOrderAllocationStatus.ACTIVE).build();
        LocalDate productionDate = LocalDate.of(2026, 8, 1);
        LocalDate expiryDate = LocalDate.of(2027, 8, 1);
        UpdatePurchaseAsnDraftRequest request = new UpdatePurchaseAsnDraftRequest(
            3L,
            21L,
            " SHIPMENT-NEW ",
            " Carrier ",
            " TRACK-1 ",
            LocalDate.of(2026, 8, 30),
            " revised ",
            java.util.List.of(new UpdatePurchaseAsnDraftRequest.Item(
                40L, 40, productionDate, expiryDate, " LOT-9 ", " line note "))
        );
        when(purchaseAsnRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(asn));
        when(purchaseOrderRepository.findByIdForReceiving(10L)).thenReturn(Optional.of(order));
        when(warehouseRepository.findById(21L)).thenReturn(Optional.of(targetWarehouse));
        when(allocationRepository.findByItemAndWarehouseForUpdate(40L, 21L))
            .thenReturn(Optional.of(targetAllocation));
        when(purchaseAsnRepository.saveAndFlush(any(PurchaseAsn.class)))
            .thenAnswer(invocation -> {
                PurchaseAsn saved = invocation.getArgument(0);
                saved.setVersion(4L);
                return saved;
            });

        var response = service.updateDraft(60L, request);

        assertThat(response.version()).isEqualTo(4L);
        assertThat(response.warehouseId()).isEqualTo(21L);
        assertThat(response.supplierShipmentNo()).isEqualTo("SHIPMENT-NEW");
        assertThat(response.carrier()).isEqualTo("Carrier");
        assertThat(response.trackingNo()).isEqualTo("TRACK-1");
        assertThat(response.remark()).isEqualTo("revised");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(70L);
            // The first ASN shipped 60, received 50 and closed 10. The closed
            // remainder is already part of shipped, so all remaining 40 can be drafted.
            assertThat(item.shippedQuantity()).isEqualTo(40);
            assertThat(item.externalBatchCode()).isEqualTo("LOT-9");
            assertThat(item.remark()).isEqualTo("line note");
        });
        assertThat(asn.getItems().get(0).getPurchaseOrderAllocation())
            .isSameAs(targetAllocation);
        verify(expiryPolicy).validateForInventoryReceipt(sku, productionDate, expiryDate);
    }

    @Test
    void updateDraftRejectsStaleClientVersionBeforeChangingAnything() {
        when(purchaseAsnRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(asn));
        UpdatePurchaseAsnDraftRequest request = requestWithVersion(2L);

        assertThatThrownBy(() -> service.updateDraft(60L, request))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.PO_EDIT_CONFLICT);
                assertThat(error.getParam("resourceType")).isEqualTo("PurchaseAsn");
                assertThat(error.getParam("currentVersion")).isEqualTo(3L);
            });

        verify(purchaseOrderRepository, never()).findByIdForReceiving(any());
        verify(purchaseAsnRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateDraftRejectsConfirmedAsnEvenWhenVersionMatches() {
        asn.setStatus(PurchaseAsnStatus.CONFIRMED);
        when(purchaseAsnRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(asn));

        assertThatThrownBy(() -> service.updateDraft(60L, requestWithVersion(3L)))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.OPERATION_NOT_ALLOWED));

        verify(purchaseAsnRepository, never()).saveAndFlush(any());
    }

    private UpdatePurchaseAsnDraftRequest requestWithVersion(Long version) {
        return new UpdatePurchaseAsnDraftRequest(
            version, 21L, null, null, null, null, null,
            java.util.List.of(new UpdatePurchaseAsnDraftRequest.Item(
                40L, 1, null, null, null, null))
        );
    }

    private PurchaseOrderAllocation allocation(Long id, Warehouse warehouse) {
        return PurchaseOrderAllocation.builder()
            .id(id).purchaseOrderItem(orderItem).warehouse(warehouse)
            .plannedQuantity(100).shippedQuantity(0).receivedQuantity(0).closedQuantity(0)
            .status(PurchaseOrderAllocationStatus.ACTIVE).build();
    }
}
