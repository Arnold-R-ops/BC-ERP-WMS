package com.wms.system.service;

import com.wms.system.dto.inbound.OtherInboundApplicationCreateRequest;
import com.wms.system.dto.inbound.OtherInboundApplicationItemRequest;
import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.OtherInboundApplication;
import com.wms.system.entity.ReceiptEvent;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.OtherInboundBusinessType;
import com.wms.system.entity.enums.OtherInboundStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.OtherInboundApplicationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.PurchaseOrderReceivingRepository;
import com.wms.system.repository.ReceiptEventRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.StockTransactionRepository;
import com.wms.system.repository.SystemConfigRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.service.inventory.InventoryPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtherInboundReceivingFlowServiceTest {

    private static final long COMPANY_ID = 1L;

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private OtherInboundApplicationRepository applicationRepository;
    @Mock private ReceivingTaskRepository receivingTaskRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private ProductSkuExpiryPolicy expiryPolicy;
    @Mock private PurchaseOrderReceivingRepository purchaseOrderRepository;
    @Mock private PurchaseAsnRepository purchaseAsnRepository;
    @Mock private PurchaseOrderAllocationRepository allocationRepository;
    @Mock private ReceiptEventRepository receiptEventRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private DomainOutboxService domainOutboxService;
    @Mock private PurchaseAsnService purchaseAsnService;
    @Mock private BackorderService backorderService;

    private OtherInboundApplicationService applicationService;
    private ReceivingReceiptExecutor receiptExecutor;

    private final AtomicReference<OtherInboundApplication> applicationRef =
        new AtomicReference<>();
    private final AtomicReference<ReceivingTask> taskRef = new AtomicReference<>();
    private final AtomicReference<InventoryBatch> batchRef = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        OtherInboundFeaturePolicyService featurePolicyService =
            new OtherInboundFeaturePolicyService(systemConfigRepository);
        applicationService = new OtherInboundApplicationService(
            applicationRepository,
            receivingTaskRepository,
            warehouseRepository,
            productSkuRepository,
            featurePolicyService,
            new ProductSkuOperationalPolicy(),
            expiryPolicy
        );

        InventoryPostingService inventoryPostingService = new InventoryPostingService(
            inventoryBatchRepository, stockTransactionRepository, domainOutboxService);
        receiptExecutor = new ReceivingReceiptExecutor(
            receivingTaskRepository,
            purchaseOrderRepository,
            purchaseAsnRepository,
            allocationRepository,
            applicationRepository,
            receiptEventRepository,
            locationRepository,
            inventoryBatchRepository,
            inventoryPostingService,
            expiryPolicy,
            purchaseAsnService,
            backorderService,
            new ReceivingResponseMapper()
        );
    }

    @Test
    void enabledApplicationConfirmsIntoUnifiedTaskAndReceiptMakesStockAvailable() {
        enableFreeSamplePolicy();
        Warehouse warehouse = warehouse();
        ProductSku sku = sku();
        Location location = location(warehouse);

        when(warehouseRepository.findById(8L)).thenReturn(Optional.of(warehouse));
        when(productSkuRepository.findById(11L)).thenReturn(Optional.of(sku));
        when(applicationRepository.save(any(OtherInboundApplication.class)))
            .thenAnswer(invocation -> persistApplication(invocation.getArgument(0)));

        var created = applicationService.create(createRequest(), 501L, "server-applicant");

        assertThat(created.getStatus()).isEqualTo(OtherInboundStatus.DRAFT);
        OtherInboundApplication application = applicationRef.get();
        assertThat(application.getCreatedBy()).isEqualTo(501L);
        assertThat(application.getCreatedByName()).isEqualTo("server-applicant");

        when(applicationRepository.findByIdForUpdate(5L))
            .thenReturn(Optional.of(application));
        when(receivingTaskRepository.save(any(ReceivingTask.class)))
            .thenAnswer(invocation -> persistTask(invocation.getArgument(0)));

        var confirmed = applicationService.confirm(
            5L, 0L, 502L, "server-confirmer");

        assertThat(confirmed.getStatus()).isEqualTo(OtherInboundStatus.CONFIRMED);
        assertThat(application.getConfirmedBy()).isEqualTo(502L);
        assertThat(application.getConfirmedByName()).isEqualTo("server-confirmer");
        ReceivingTask task = taskRef.get();
        assertThat(task.getSourceType()).isEqualTo(ReceivingTaskSourceType.OTHER_INBOUND);
        assertThat(task.getPurchaseAsn()).isNull();
        assertThat(task.getReceivingPlan()).isNull();
        assertThat(task.getCreatedBy()).isEqualTo(502L);
        assertThat(task.getCreatedByName()).isEqualTo("server-confirmer");
        assertThat(task.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getOtherInboundApplicationItem())
                .isSameAs(application.getItems().get(0));
            assertThat(item.getExpectedQuantity()).isEqualTo(4);
        });

        when(receiptEventRepository.findByIdempotencyKey("other-inbound-receipt-1"))
            .thenReturn(Optional.empty());
        when(receivingTaskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(receivingTaskRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(task));
        when(locationRepository.findAllById(
            org.mockito.ArgumentMatchers.<Iterable<Long>>any()
        )).thenReturn(List.of(location));
        when(receiptEventRepository.saveAndFlush(any(ReceiptEvent.class)))
            .thenAnswer(invocation -> persistReceiptEvent(invocation.getArgument(0)));
        when(inventoryBatchRepository.saveAndFlush(any(InventoryBatch.class)))
            .thenAnswer(invocation -> persistBatch(invocation.getArgument(0)));
        when(inventoryBatchRepository.findByIdForPosting(41L))
            .thenAnswer(invocation -> Optional.ofNullable(batchRef.get()));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenAnswer(invocation -> {
                StockTransaction transaction = invocation.getArgument(0);
                transaction.setId(901L);
                return transaction;
            });

        ReceiptEventResponse receipt = receiptExecutor.execute(
            7L,
            "other-inbound-receipt-1",
            new SubmitReceivingReceiptRequest(
                "received by camera scan",
                List.of(new SubmitReceivingReceiptRequest.Line(
                    17L, 21L, 4, null, null, "EXT-LOT-1"))
            ),
            503L,
            "server-receiver"
        );

        assertThat(receipt.receivedByName()).isEqualTo("server-receiver");
        assertThat(receipt.lines()).singleElement().satisfies(line -> {
            assertThat(line.quantity()).isEqualTo(4);
            assertThat(line.locationId()).isEqualTo(21L);
            assertThat(line.inventoryBatchId()).isEqualTo(41L);
        });
        assertThat(application.getStatus()).isEqualTo(OtherInboundStatus.COMPLETED);
        assertThat(application.getItems().get(0).getReceivedQuantity()).isEqualTo(4);
        assertThat(task.getStatus()).isEqualTo(ReceivingTaskStatus.COMPLETED);
        assertThat(task.getItems().get(0).getReceivedQuantity()).isEqualTo(4);

        InventoryBatch batch = batchRef.get();
        assertThat(batch.getQuantity()).isEqualTo(4);
        assertThat(batch.getReservedQuantity()).isZero();
        assertThat(batch.getActive()).isTrue();

        ArgumentCaptor<StockTransaction> transactionCaptor =
            ArgumentCaptor.forClass(StockTransaction.class);
        verify(stockTransactionRepository).save(transactionCaptor.capture());
        StockTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.IN);
        assertThat(transaction.getSourceType()).isEqualTo(SourceType.INBOUND_IN);
        assertThat(transaction.getSourceOrderId()).isEqualTo(application.getApplicationNumber());
        assertThat(transaction.getOperatorId()).isEqualTo(503L);
        assertThat(transaction.getOperatorName()).isEqualTo("server-receiver");
        assertThat(transaction.getQuantityBefore()).isZero();
        assertThat(transaction.getQuantityAfter()).isEqualTo(4);
        assertThat(transaction.getIdempotencyKey())
            .isEqualTo("other-inbound-receipt-1");
        verify(expiryPolicy, times(2)).validateForInventoryReceipt(
            sku, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        verify(backorderService).wakeProduct(11L);
    }

    private void enableFreeSamplePolicy() {
        SystemConfig enabled = SystemConfig.builder()
            .configKey(OtherInboundFeaturePolicyService.ENABLED_KEY)
            .configValue("true")
            .configType(SystemConfig.ConfigType.BOOLEAN.name())
            .build();
        SystemConfig types = SystemConfig.builder()
            .configKey(OtherInboundFeaturePolicyService.TYPES_KEY)
            .configValue(OtherInboundBusinessType.FREE_SAMPLE.name())
            .configType(SystemConfig.ConfigType.STRING.name())
            .build();
        when(systemConfigRepository.findByConfigKey(
            OtherInboundFeaturePolicyService.ENABLED_KEY)).thenReturn(Optional.of(enabled));
        when(systemConfigRepository.findByConfigKey(
            OtherInboundFeaturePolicyService.TYPES_KEY)).thenReturn(Optional.of(types));
    }

    private OtherInboundApplicationCreateRequest createRequest() {
        OtherInboundApplicationItemRequest item = new OtherInboundApplicationItemRequest();
        item.setProductSkuId(11L);
        item.setPlannedQuantity(4);
        item.setProductionDate(LocalDate.of(2026, 8, 1));
        item.setExpiryDate(LocalDate.of(2027, 8, 1));
        item.setExternalBatchCode("EXT-LOT-1");

        OtherInboundApplicationCreateRequest request =
            new OtherInboundApplicationCreateRequest();
        request.setBusinessType(OtherInboundBusinessType.FREE_SAMPLE);
        request.setSourceReference("SAMPLE-SHIPMENT-1");
        request.setBusinessReason("Controlled sample receipt");
        request.setWarehouseId(8L);
        request.setItems(List.of(item));
        request.setRemark("approved source evidence attached");
        return request;
    }

    private Warehouse warehouse() {
        Warehouse warehouse = Warehouse.builder()
            .id(8L).code("WH08").name("Main warehouse").isActive(true).build();
        warehouse.setCompanyId(COMPANY_ID);
        return warehouse;
    }

    private ProductSku sku() {
        ProductSku sku = ProductSku.builder()
            .id(11L)
            .skuCode("SKU-11")
            .skuName("Sample SKU")
            .name("Sample SKU")
            .barcode("690000000011")
            .enabled(true)
            .isDeleted(false)
            .expiryManagementEnabled(true)
            .build();
        sku.setCompanyId(COMPANY_ID);
        return sku;
    }

    private Location location(Warehouse warehouse) {
        Location location = Location.builder()
            .id(21L)
            .warehouse(warehouse)
            .warehouseCode(warehouse.getCode())
            .zone(Zone.ZONE_A)
            .shelfNumber("A01")
            .positionNumber("001")
            .locationCode("WH08-ZONE_A-A01-001")
            .enabled(true)
            .build();
        location.setCompanyId(COMPANY_ID);
        return location;
    }

    private OtherInboundApplication persistApplication(
        OtherInboundApplication application
    ) {
        if (application.getId() == null) {
            application.setId(5L);
        }
        application.setCompanyId(COMPANY_ID);
        application.getItems().forEach(item -> {
            if (item.getId() == null) {
                item.setId(13L);
            }
            item.setCompanyId(COMPANY_ID);
        });
        applicationRef.set(application);
        return application;
    }

    private ReceivingTask persistTask(ReceivingTask task) {
        task.setId(7L);
        task.setCompanyId(COMPANY_ID);
        task.getItems().forEach(item -> {
            item.setId(17L);
            item.setCompanyId(COMPANY_ID);
        });
        taskRef.set(task);
        return task;
    }

    private ReceiptEvent persistReceiptEvent(ReceiptEvent event) {
        event.setId(31L);
        event.setCompanyId(COMPANY_ID);
        return event;
    }

    private InventoryBatch persistBatch(InventoryBatch batch) {
        batch.setId(41L);
        batch.setCompanyId(COMPANY_ID);
        batchRef.set(batch);
        return batch;
    }
}
