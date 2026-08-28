package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.PurchaseOrderItem;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.ReceivingPlanRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivingPlanServiceTest {

    @Mock private ReceivingPlanRepository receivingPlanRepository;
    @Mock private ReceivingTaskRepository receivingTaskRepository;
    @Mock private PurchaseOrderAllocationService allocationService;
    @Mock private PurchaseOrderAllocationRepository allocationRepository;

    private ReceivingPlanService service;

    @BeforeEach
    void setUp() {
        service = new ReceivingPlanService(
            receivingPlanRepository,
            receivingTaskRepository,
            allocationService,
            allocationRepository,
            new ReceivingResponseMapper()
        );
    }

    @Test
    void workQueueLoadsWarehouseSafeAllocationItemsInOneBatch() throws Exception {
        PurchaseOrder order = PurchaseOrder.builder()
            .id(10L)
            .poNumber("PO-10")
            .supplier("SECRET SUPPLIER")
            .build();
        Warehouse warehouse = Warehouse.builder()
            .id(20L).code("WH20").name("Main warehouse").build();
        ProductSku sku = ProductSku.builder()
            .id(30L).skuCode("SKU-30").name("Warehouse item").build();
        PurchaseOrderItem orderItem = PurchaseOrderItem.builder()
            .id(40L).purchaseOrder(order).productSku(sku).orderedQuantity(12).build();
        PurchaseOrderAllocation allocation = PurchaseOrderAllocation.builder()
            .id(50L)
            .purchaseOrderItem(orderItem)
            .warehouse(warehouse)
            .plannedQuantity(12)
            .shippedQuantity(5)
            .receivedQuantity(2)
            .closedQuantity(1)
            .status(PurchaseOrderAllocationStatus.ACTIVE)
            .build();
        ReceivingPlan plan = ReceivingPlan.builder()
            .id(60L)
            .version(3L)
            .planNumber("RPL-10-20")
            .purchaseOrder(order)
            .warehouse(warehouse)
            .status(ReceivingPlanStatus.WAITING_SHIPMENT)
            .createdBy(1L)
            .createdByName("planner")
            .build();
        List<ReceivingPlanStatus> activeStatuses = List.of(
            ReceivingPlanStatus.WAITING_SHIPMENT,
            ReceivingPlanStatus.IN_TRANSIT,
            ReceivingPlanStatus.PARTIALLY_RECEIVED
        );
        when(receivingPlanRepository.findByStatusInAndWarehouseIdInOrderByIdDesc(
            activeStatuses, List.of(20L))).thenReturn(List.of(plan));
        when(allocationRepository.findForReceivingPlanProjection(List.of(60L)))
            .thenReturn(List.of(allocation));

        var responses = service.list(activeStatuses, List.of(20L));

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.purchaseOrderNumber()).isEqualTo("PO-10");
            assertThat(response.warehouseId()).isEqualTo(20L);
            assertThat(response.items()).singleElement().satisfies(item -> {
                assertThat(item.allocationId()).isEqualTo(50L);
                assertThat(item.purchaseOrderItemId()).isEqualTo(40L);
                assertThat(item.productSkuId()).isEqualTo(30L);
                assertThat(item.skuCode()).isEqualTo("SKU-30");
                assertThat(item.productName()).isEqualTo("Warehouse item");
                assertThat(item.plannedQuantity()).isEqualTo(12);
                assertThat(item.confirmedShippedQuantity()).isEqualTo(5);
                assertThat(item.remainingToShipQuantity()).isEqualTo(7);
            });
        });
        verify(allocationRepository).findForReceivingPlanProjection(List.of(60L));

        String json = new ObjectMapper().registerModule(new JavaTimeModule())
            .writeValueAsString(responses.get(0)).toLowerCase();
        assertThat(json)
            .doesNotContain("supplier")
            .doesNotContain("price")
            .doesNotContain("carrier")
            .doesNotContain("tracking")
            .doesNotContain("asnnumber");
    }

    @Test
    void emptyWorkQueueDoesNotIssueAllocationProjectionQuery() {
        when(receivingPlanRepository.findAllByOrderByIdDesc()).thenReturn(List.of());

        assertThat(service.list((ReceivingPlanStatus) null)).isEmpty();

        verifyNoInteractions(allocationRepository);
    }

    @Test
    void emptyWarehouseScopeDoesNotFallBackToTenantWideQuery() {
        assertThat(service.list(
            List.of(ReceivingPlanStatus.WAITING_SHIPMENT), List.of())).isEmpty();

        verifyNoInteractions(receivingPlanRepository, allocationRepository);
    }
}
