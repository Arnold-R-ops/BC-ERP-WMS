package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.HistoricalTestDataArchivePreview;
import com.wms.system.dto.sales.HistoricalTestDataArchiveRequest;
import com.wms.system.dto.sales.HistoricalTestDataArchiveResult;
import com.wms.system.entity.Customer;
import com.wms.system.entity.HistoricalTestDataArchiveAudit;
import com.wms.system.entity.HistoricalTestDataRegistry;
import com.wms.system.entity.OutboundTask;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.SalesOrderItem;
import com.wms.system.entity.SalesOrderShipment;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.FulfillmentStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.entity.enums.ShipmentStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.event.SalesOrderFactsChangedEvent;
import com.wms.system.repository.BackorderLineRepository;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.HistoricalTestDataArchiveAuditRepository;
import com.wms.system.repository.HistoricalTestDataRegistryRepository;
import com.wms.system.repository.InventoryReservationRepository;
import com.wms.system.repository.OutboundTaskRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.SalesOrderItemRepository;
import com.wms.system.repository.SalesOrderRepository;
import com.wms.system.repository.SalesOrderShipmentRepository;
import com.wms.system.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalTestDataArchiveServiceTest {

    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private SalesOrderItemRepository salesOrderItemRepository;
    @Mock private OutboundTaskRepository outboundTaskRepository;
    @Mock private InventoryReservationRepository inventoryReservationRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private SalesOrderShipmentRepository salesOrderShipmentRepository;
    @Mock private BackorderLineRepository backorderLineRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private HistoricalTestDataRegistryRepository registryRepository;
    @Mock private HistoricalTestDataArchiveAuditRepository auditRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private HistoricalTestDataArchiveService service;

    private SalesOrder order;
    private SalesOrderItem item;
    private OutboundTask task;

    @BeforeEach
    void setUp() {
        order = SalesOrder.builder()
            .id(13L)
            .orderNo("SO-V4.4-TEST-13")
            .customerId(7L)
            .applicantId(1L)
            .applicantName("tester")
            .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
            .commercialStatus(CommercialStatus.APPROVED)
            .fulfillmentStatus(FulfillmentStatus.RESERVED)
            .reviewReason("V4.4测试历史订单")
            .auditLog("[]")
            .build();
        order.setCreatedAt(LocalDateTime.of(2026, 6, 10, 8, 0));
        order.setCompanyId(1L);

        item = SalesOrderItem.builder()
            .id(21L)
            .salesOrderId(13L)
            .productSkuId(31L)
            .quantity(5)
            .requestedQty(5)
            .allocatedQty(5)
            .shippedQty(0)
            .backorderQty(0)
            .cancelledQty(0)
            .fulfillmentStatus(FulfillmentStatus.RESERVED)
            .remark("V4.4测试商品明细")
            .build();

        task = OutboundTask.builder()
            .id(1L)
            .salesOrderId(13L)
            .salesOrderItemId(21L)
            .assignedBatchId(6L)
            .locationId(9L)
            .planQty(5)
            .actualQty(0)
            .status(OutboundTaskStatus.PENDING)
            .version(0)
            .build();

        Customer customer = Customer.builder()
            .id(7L)
            .code("V4.4-TEST-CUSTOMER")
            .name("V4.4测试客户")
            .isActive(true)
            .build();
        ProductSku product = ProductSku.builder()
            .id(31L)
            .skuCode("V4.4-TEST-SKU")
            .skuName("V4.4 Test SKU")
            .name("V4.4 Test Product")
            .barcode("V44TEST")
            .build();
        HistoricalTestDataRegistry registration = HistoricalTestDataRegistry.builder()
            .id(1L)
            .companyId(1L)
            .salesOrderId(13L)
            .orderNo(order.getOrderNo())
            .sourceVersion("V4.4")
            .registrationReason("Approved historical test evidence")
            .registeredBy("FLYWAY_TEST")
            .build();

        when(salesOrderRepository.findById(13L)).thenReturn(Optional.of(order));
        lenient().when(salesOrderRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(order));
        when(salesOrderItemRepository.findBySalesOrderId(13L)).thenReturn(List.of(item));
        when(outboundTaskRepository.findBySalesOrderId(13L)).thenReturn(List.of(task));
        lenient().when(outboundTaskRepository.findBySalesOrderIdForUpdate(13L)).thenReturn(List.of(task));
        when(inventoryReservationRepository.findBySalesOrderId(13L)).thenReturn(List.of());
        lenient().when(inventoryReservationRepository.findBySalesOrderIdForUpdate(13L)).thenReturn(List.of());
        when(stockTransactionRepository.findByCompanyIdAndSourceOrderId(1L, order.getOrderNo()))
            .thenReturn(List.of());
        when(salesOrderShipmentRepository.findBySalesOrderIdOrderByIdAsc(13L)).thenReturn(List.of());
        when(backorderLineRepository.findBySalesOrderId(13L)).thenReturn(List.of());
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(productSkuRepository.findAllById(List.of(31L))).thenReturn(List.of(product));
        when(registryRepository.findByCompanyIdAndSalesOrderId(1L, 13L))
            .thenReturn(Optional.of(registration));
        lenient().when(auditRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            HistoricalTestDataArchiveAudit audit = invocation.getArgument(0);
            audit.setId(100L);
            return audit;
        });
    }

    @Test
    void previewReturnsEligibleSnapshotWithoutWrites() {
        HistoricalTestDataArchivePreview preview = service.preview(13L);

        assertThat(preview.isEligible()).isTrue();
        assertThat(preview.getTaskIds()).containsExactly(1L);
        assertThat(preview.getSnapshotFingerprint()).hasSize(64);
        verify(salesOrderRepository, never()).save(any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void archivePreservesTasksAndReportsZeroInventoryImpact() {
        String fingerprint = service.preview(13L).getSnapshotFingerprint();
        HistoricalTestDataArchiveRequest request = HistoricalTestDataArchiveRequest.builder()
            .reason("Archive confirmed V4.4 historical test data")
            .confirmationOrderNo(order.getOrderNo())
            .expectedFingerprint(fingerprint)
            .build();

        HistoricalTestDataArchiveResult result = service.archive(13L, request, 9L, "admin");

        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.VOIDED);
        assertThat(item.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.VOIDED);
        assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.VOIDED);
        assertThat(task.getArchivedBy()).isEqualTo(9L);
        assertThat(result.getArchivedTaskIds()).containsExactly(1L);
        assertThat(result.isInventoryChanged()).isFalse();
        assertThat(result.isReservationsChanged()).isFalse();
        assertThat(result.isStockTransactionsCreated()).isFalse();
        assertThat(result.getAuditId()).isEqualTo(100L);
        org.mockito.ArgumentCaptor<SalesOrderFactsChangedEvent> eventCaptor =
            org.mockito.ArgumentCaptor.forClass(SalesOrderFactsChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().companyId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().salesOrderId()).isEqualTo(13L);
        assertThat(eventCaptor.getValue().summaryDate()).isEqualTo(java.time.LocalDate.of(2026, 6, 10));
        verify(outboundTaskRepository, never()).deleteBySalesOrderId(any());
        verify(stockTransactionRepository, never()).save(any());
    }

    @Test
    void archiveRejectsSnapshotChangedAfterPreview() {
        String fingerprint = service.preview(13L).getSnapshotFingerprint();
        task.setPlanQty(6);
        HistoricalTestDataArchiveRequest request = HistoricalTestDataArchiveRequest.builder()
            .reason("Archive confirmed V4.4 historical test data")
            .confirmationOrderNo(order.getOrderNo())
            .expectedFingerprint(fingerprint)
            .build();

        assertThatThrownBy(() -> service.archive(13L, request, 9L, "admin"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.HISTORICAL_ARCHIVE_SNAPSHOT_STALE)
            );
        verify(auditRepository, never()).save(any());
    }

    @Test
    void archiveRejectsAnyStockTransaction() {
        StockTransaction transaction = StockTransaction.builder()
            .id(88L)
            .sourceOrderId(order.getOrderNo())
            .transactionType(TransactionType.OUT)
            .sourceType(SourceType.SALE_OUT)
            .quantity(1)
            .quantityBefore(1)
            .quantityAfter(0)
            .build();
        when(stockTransactionRepository.findByCompanyIdAndSourceOrderId(1L, order.getOrderNo()))
            .thenReturn(List.of(transaction));

        HistoricalTestDataArchivePreview preview = service.preview(13L);

        assertThat(preview.isEligible()).isFalse();
        assertThat(preview.getBlockers()).contains("STOCK_TRANSACTIONS_PRESENT:1");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void archiveRequiresExactTypedOrderNumber() {
        String fingerprint = service.preview(13L).getSnapshotFingerprint();
        HistoricalTestDataArchiveRequest request = HistoricalTestDataArchiveRequest.builder()
            .reason("Archive confirmed V4.4 historical test data")
            .confirmationOrderNo("WRONG")
            .expectedFingerprint(fingerprint)
            .build();

        assertThatThrownBy(() -> service.archive(13L, request, 9L, "admin"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.HISTORICAL_ARCHIVE_CONFIRMATION_MISMATCH)
            );
    }

    @Test
    void previewRejectsOrderWithoutImmutableRegistration() {
        when(registryRepository.findByCompanyIdAndSalesOrderId(1L, 13L))
            .thenReturn(Optional.empty());

        HistoricalTestDataArchivePreview preview = service.preview(13L);

        assertThat(preview.isEligible()).isFalse();
        assertThat(preview.getBlockers())
            .contains("ORDER_NOT_REGISTERED_AS_HISTORICAL_TEST_DATA");
    }

    @Test
    void previewAllowsVoidedShipmentAndReportsItSeparately() {
        SalesOrderShipment shipment = SalesOrderShipment.builder()
            .id(2L)
            .salesOrderId(13L)
            .trackingNo("VOIDED-HISTORICAL-SHIPMENT")
            .status(ShipmentStatus.VOIDED)
            .build();
        when(salesOrderShipmentRepository.findBySalesOrderIdOrderByIdAsc(13L))
            .thenReturn(List.of(shipment));

        HistoricalTestDataArchivePreview preview = service.preview(13L);

        assertThat(preview.isEligible()).isTrue();
        assertThat(preview.getShipmentCount()).isEqualTo(1);
        assertThat(preview.getActiveShipmentCount()).isZero();
        assertThat(preview.getVoidedShipmentCount()).isEqualTo(1);
    }

    @Test
    void previewRejectsAnyActiveShipment() {
        SalesOrderShipment shipment = SalesOrderShipment.builder()
            .id(3L)
            .salesOrderId(13L)
            .trackingNo("ACTIVE-SHIPMENT")
            .status(ShipmentStatus.ACTIVE)
            .build();
        when(salesOrderShipmentRepository.findBySalesOrderIdOrderByIdAsc(13L))
            .thenReturn(List.of(shipment));

        HistoricalTestDataArchivePreview preview = service.preview(13L);

        assertThat(preview.isEligible()).isFalse();
        assertThat(preview.getBlockers()).contains("ACTIVE_SHIPMENTS_PRESENT:1");
        assertThat(preview.getActiveShipmentCount()).isEqualTo(1);
        assertThat(preview.getVoidedShipmentCount()).isZero();
    }
}
