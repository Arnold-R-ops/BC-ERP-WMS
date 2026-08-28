package com.wms.system.service;

import com.wms.system.entity.OtherInboundApplication;
import com.wms.system.entity.OtherInboundApplicationItem;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.OtherInboundBusinessType;
import com.wms.system.entity.enums.OtherInboundStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.repository.OtherInboundApplicationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtherInboundApplicationServiceTest {

    @Mock private OtherInboundApplicationRepository applicationRepository;
    @Mock private ReceivingTaskRepository receivingTaskRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private OtherInboundFeaturePolicyService featurePolicyService;
    @Mock private ProductSkuExpiryPolicy expiryPolicy;

    private OtherInboundApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OtherInboundApplicationService(
            applicationRepository,
            receivingTaskRepository,
            warehouseRepository,
            productSkuRepository,
            featurePolicyService,
            new ProductSkuOperationalPolicy(),
            expiryPolicy
        );
    }

    @Test
    void confirmationCreatesOnlyOneUnifiedTaskWithStrongSourceLinks() {
        Warehouse warehouse = Warehouse.builder()
            .id(8L).code("WH-8").name("Main").isActive(true).build();
        ProductSku sku = ProductSku.builder()
            .id(11L).skuCode("SKU-11").name("Sample").barcode("690000000011")
            .enabled(true).expiryManagementEnabled(true).build();
        OtherInboundApplication application = OtherInboundApplication.builder()
            .id(5L)
            .version(2L)
            .applicationNumber("OIN-5")
            .businessType(OtherInboundBusinessType.FREE_SAMPLE)
            .sourceReference("SAMPLE-REF")
            .businessReason("Free sample received for evaluation")
            .warehouse(warehouse)
            .status(OtherInboundStatus.DRAFT)
            .createdBy(1L)
            .createdByName("applicant")
            .items(new ArrayList<>())
            .build();
        OtherInboundApplicationItem item = OtherInboundApplicationItem.builder()
            .id(9L)
            .productSku(sku)
            .plannedQuantity(6)
            .receivedQuantity(0)
            .closedQuantity(0)
            .productionDate(LocalDate.of(2026, 8, 1))
            .expiryDate(LocalDate.of(2027, 8, 1))
            .build();
        application.addItem(item);

        when(applicationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(application));
        when(warehouseRepository.findById(8L)).thenReturn(Optional.of(warehouse));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(receivingTaskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.confirm(5L, 2L, 7L, "planner");

        assertThat(response.getStatus()).isEqualTo(OtherInboundStatus.CONFIRMED);
        ArgumentCaptor<ReceivingTask> taskCaptor = ArgumentCaptor.forClass(ReceivingTask.class);
        verify(receivingTaskRepository).save(taskCaptor.capture());
        ReceivingTask task = taskCaptor.getValue();
        assertThat(task.getSourceType()).isEqualTo(ReceivingTaskSourceType.OTHER_INBOUND);
        assertThat(task.getOtherInboundApplication()).isSameAs(application);
        assertThat(task.getPurchaseAsn()).isNull();
        assertThat(task.getReceivingPlan()).isNull();
        assertThat(task.getItems()).singleElement().satisfies(taskItem -> {
            assertThat(taskItem.getOtherInboundApplicationItem()).isSameAs(item);
            assertThat(taskItem.getPurchaseAsnItem()).isNull();
            assertThat(taskItem.getPurchaseOrderItem()).isNull();
            assertThat(taskItem.getExpectedQuantity()).isEqualTo(6);
        });
        verify(expiryPolicy).validateForInventoryReceipt(
            sku, item.getProductionDate(), item.getExpiryDate());
    }
}
