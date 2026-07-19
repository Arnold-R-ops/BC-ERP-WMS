package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.integration.ShopifyReconciliationRequest;
import com.wms.system.dto.sales.SalesOrderResponse;
import com.wms.system.entity.ChannelRawEvent;
import com.wms.system.entity.ChannelSkuMapping;
import com.wms.system.entity.Customer;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.entity.ProductSku;
import com.wms.system.integration.ShopifyApiClient;
import com.wms.system.repository.ChannelSkuMappingRepository;
import com.wms.system.repository.CustomerRepository;
import com.wms.system.repository.IntegrationConfigRepository;
import com.wms.system.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopifyReconciliationServiceTest {

    private static final String ORDER_JSON = """
        {
          "id": 9002,
          "name": "#9002",
          "email": "buyer@example.com",
          "financial_status": "paid",
          "fulfillment_status": null,
          "cancelled_at": null,
          "line_items": [
            {"id": 1, "sku": "EXT-TEA", "name": "Tea", "quantity": 2, "price": "25.00"}
          ]
        }
        """;

    @Mock
    private IntegrationConfigRepository integrationConfigRepository;
    @Mock
    private ShopifyApiClient shopifyApiClient;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ChannelSkuMappingRepository channelSkuMappingRepository;
    @Mock
    private ChannelRawEventService rawEventService;
    @Mock
    private SalesSubmissionService salesSubmissionService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private ShopifyReconciliationService reconciliationService;

    private IntegrationConfig config;

    @BeforeEach
    void setUp() {
        config = IntegrationConfig.builder()
            .id(7L)
            .platform("SHOPIFY")
            .storeUrl("test-store.myshopify.com")
            .isActive(true)
            .build();
    }

    @Test
    void reconcile_IsReportOnlyAndRecordsMissingOrders() {
        ShopifyReconciliationRequest request = new ShopifyReconciliationRequest();
        request.setConfigId(7L);
        request.setDays(3);
        when(integrationConfigRepository.findById(7L)).thenReturn(Optional.of(config));
        when(shopifyApiClient.fetchOrdersForReconciliationRaw(eq(config), any()))
            .thenReturn("{\"orders\":[{\"id\":9001,\"name\":\"#9001\"}," + ORDER_JSON + "]}");
        when(salesOrderRepository.findExistingExternalOrderIds("SHOPIFY", List.of("9001", "9002")))
            .thenReturn(List.of("9001"));
        when(salesOrderRepository.countByChannelAndExternalOrderIdIsNotNull("SHOPIFY"))
            .thenReturn(12L);
        when(rawEventService.findLatest(
            "SHOPIFY", "test-store.myshopify.com", ChannelRawEvent.SOURCE_RECONCILE,
            ChannelRawEvent.TYPE_ORDER, "9002"
        )).thenReturn(Optional.empty());
        when(rawEventService.record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                String eventType = invocation.getArgument(3);
                return ChannelRawEvent.builder()
                    .id(ChannelRawEvent.TYPE_RECONCILE_REPORT.equals(eventType) ? 202L : 201L)
                    .channel("SHOPIFY")
                    .source(ChannelRawEvent.SOURCE_RECONCILE)
                    .eventType(eventType)
                    .externalId(invocation.getArgument(4))
                    .payload(invocation.getArgument(5))
                    .build();
            });

        var report = reconciliationService.reconcile(request);

        assertThat(report.isReportOnly()).isTrue();
        assertThat(report.getRemoteOrderCount()).isEqualTo(2);
        assertThat(report.getMissingOrderCount()).isEqualTo(1);
        assertThat(report.getMissingOrders().get(0).getExternalOrderId()).isEqualTo("9002");
        assertThat(report.getMissingOrders().get(0).isRepairEligible()).isTrue();
        assertThat(report.getReportEventId()).isEqualTo(202L);
        verify(rawEventService).markProcessed(202L);
        verify(customerRepository, never()).save(any(Customer.class));
        verify(channelSkuMappingRepository, never()).save(any(ChannelSkuMapping.class));
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
            any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void repair_UsesOnlyExistingCustomerAndMappingAndCreatesPendingApprovalOrder() {
        ChannelRawEvent event = repairEvent(201L, ORDER_JSON);
        Customer customer = Customer.builder()
            .id(31L)
            .email("buyer@example.com")
            .isActive(true)
            .build();
        ProductSku productSku = ProductSku.builder()
            .id(41L)
            .skuCode("SKU00000041")
            .name("Tea")
            .enabled(true)
            .build();
        ChannelSkuMapping mapping = ChannelSkuMapping.builder()
            .channel("SHOPIFY")
            .externalSku("EXT-TEA")
            .normalizedSku("EXT-TEA")
            .mappingType(ChannelSkuMapping.TYPE_PRODUCT)
            .productSku(productSku)
            .quantityRatio(1)
            .status(ChannelSkuMapping.STATUS_ACTIVE)
            .build();
        SalesOrderResponse response = new SalesOrderResponse();
        response.setId(501L);
        response.setStatus("PENDING_APPROVAL");

        when(rawEventService.requireById(201L)).thenReturn(event);
        when(salesOrderRepository.findByExternalOrderId("9002")).thenReturn(Optional.empty());
        when(customerRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(customer));
        when(channelSkuMappingRepository.findByChannelAndExternalSkuAndStatus(
            "SHOPIFY", "EXT-TEA", ChannelSkuMapping.STATUS_ACTIVE
        )).thenReturn(Optional.of(mapping));
        when(salesSubmissionService.createChannelOrderPendingApproval(
            any(), eq(77L), eq("repair-operator"), eq("SHOPIFY"), eq("9002"), eq("#9002")
        )).thenReturn(response);

        var result = reconciliationService.repair(List.of(201L), 77L, "repair-operator");

        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getBlocked()).isZero();
        assertThat(result.getItems().get(0).getSalesOrderId()).isEqualTo(501L);
        verify(salesSubmissionService).createChannelOrderPendingApproval(
            org.mockito.ArgumentMatchers.argThat(request ->
                request.getCustomerId().equals(31L)
                    && request.getItems().size() == 1
                    && request.getItems().get(0).getProductSkuId().equals(41L)
                    && request.getItems().get(0).getQuantity() == 2
            ),
            eq(77L), eq("repair-operator"), eq("SHOPIFY"), eq("9002"), eq("#9002")
        );
        verify(rawEventService).markProcessed(201L);
        verify(customerRepository, never()).save(any(Customer.class));
        verify(channelSkuMappingRepository, never()).save(any(ChannelSkuMapping.class));
    }

    @Test
    void repair_BlocksUnknownCustomerWithoutCreatingMasterData() {
        ChannelRawEvent event = repairEvent(201L, ORDER_JSON);
        when(rawEventService.requireById(201L)).thenReturn(event);
        when(salesOrderRepository.findByExternalOrderId("9002")).thenReturn(Optional.empty());
        when(customerRepository.findByEmail("buyer@example.com")).thenReturn(Optional.empty());

        var result = reconciliationService.repair(List.of(201L), 77L, "repair-operator");

        assertThat(result.getCreated()).isZero();
        assertThat(result.getBlocked()).isEqualTo(1);
        verify(customerRepository, never()).save(any(Customer.class));
        verify(channelSkuMappingRepository, never()).save(any(ChannelSkuMapping.class));
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
            any(), anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(rawEventService).markFailed(eq(201L), anyString());
    }

    @Test
    void repair_BlocksUnknownSkuMappingWithoutCreatingProduct() {
        ChannelRawEvent event = repairEvent(201L, ORDER_JSON);
        Customer customer = Customer.builder()
            .id(31L)
            .email("buyer@example.com")
            .isActive(true)
            .build();
        when(rawEventService.requireById(201L)).thenReturn(event);
        when(salesOrderRepository.findByExternalOrderId("9002")).thenReturn(Optional.empty());
        when(customerRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(customer));
        when(channelSkuMappingRepository.findByChannelAndExternalSkuAndStatus(
            "SHOPIFY", "EXT-TEA", ChannelSkuMapping.STATUS_ACTIVE
        )).thenReturn(Optional.empty());
        when(channelSkuMappingRepository.findFirstByChannelAndNormalizedSkuAndStatus(
            "SHOPIFY", "EXT-TEA", ChannelSkuMapping.STATUS_ACTIVE
        )).thenReturn(Optional.empty());

        var result = reconciliationService.repair(List.of(201L), 77L, "repair-operator");

        assertThat(result.getBlocked()).isEqualTo(1);
        verify(channelSkuMappingRepository, never()).save(any(ChannelSkuMapping.class));
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
            any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void repair_BlocksNonReconciliationEventWithoutChangingAuditStatus() {
        ChannelRawEvent event = repairEvent(301L, ORDER_JSON);
        event.setSource(ChannelRawEvent.SOURCE_WEBHOOK);
        event.setStatus(ChannelRawEvent.STATUS_PROCESSED);
        when(rawEventService.requireById(301L)).thenReturn(event);

        var result = reconciliationService.repair(List.of(301L), 77L, "repair-operator");

        assertThat(result.getCreated()).isZero();
        assertThat(result.getBlocked()).isEqualTo(1);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo("BLOCKED");
        assertThat(event.getStatus()).isEqualTo(ChannelRawEvent.STATUS_PROCESSED);
        verify(rawEventService, never()).markFailed(anyLong(), anyString());
        verify(salesSubmissionService, never()).createChannelOrderPendingApproval(
            any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    private ChannelRawEvent repairEvent(Long id, String payload) {
        return ChannelRawEvent.builder()
            .id(id)
            .channel("SHOPIFY")
            .storeIdentifier("test-store.myshopify.com")
            .source(ChannelRawEvent.SOURCE_RECONCILE)
            .eventType(ChannelRawEvent.TYPE_ORDER)
            .externalId("9002")
            .payload(payload)
            .status(ChannelRawEvent.STATUS_RECEIVED)
            .build();
    }
}
