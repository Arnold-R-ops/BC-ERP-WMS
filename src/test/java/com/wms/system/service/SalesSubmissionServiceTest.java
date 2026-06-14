package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.sales.CreateSalesOrderRequest;
import com.wms.system.dto.sales.SalesOrderResponse;
import com.wms.system.dto.sales.UpdateSalesOrderRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.SalesOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SalesSubmissionService Unit Test
 *
 * Test Coverage:
 * 1. Create sales order success
 * 2. Low price trigger approval
 * 3. High amount trigger approval
 * 4. Auto approve
 * 5. Approve sales order success
 * 6. Reject sales order success
 * 7. Update sales order success
 * 8. Update sales order invalid status
 * 9. Cancel sales order success
 * 10. Cancel sales order release inventory
 *
 * @author WMS Team
 * @since 2026-01-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class SalesSubmissionServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;

    @Mock
    private OutboundTaskRepository outboundTaskRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private AllocationService allocationService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SalesSubmissionService salesSubmissionService;

    private Product testProduct;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        // Create test product
        testProduct = Product.builder()
            .id(1L)
            .name("Test Product")
            .barcode("TEST001")
            .minSalesPrice(new BigDecimal("10.00"))
            .build();

        // Create test customer
        testCustomer = Customer.builder()
            .id(1L)
            .name("Test Customer")
            .isActive(true)
            .build();
    }

    // ========== Test 1: Create Sales Order Success ==========

    @Test
    @DisplayName("case-2")
    void testCreateSalesOrder_Success() {
        // Given
        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(1L);

        CreateSalesOrderRequest.SalesOrderItemData itemData = new CreateSalesOrderRequest.SalesOrderItemData();
        itemData.setProductId(1L);
        itemData.setQuantity(10);
        itemData.setUnitPrice(new BigDecimal("15.00"));
        request.setItems(List.of(itemData));

        doNothing().when(customerService).validateCustomerActive(1L);
        when(customerService.getCustomerName(1L)).thenReturn("Test Customer");
        when(salesOrderRepository.existsByOrderNo(anyString())).thenReturn(false);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> {
                SalesOrder order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(salesOrderItemRepository.saveAll(org.mockito.ArgumentMatchers.<SalesOrderItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(systemConfigService.getSalesApprovalAmountThreshold()).thenReturn(new BigDecimal("10000.00"));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());
        when(allocationService.allocateInventory(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.createSalesOrder(request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getApplicantName()).isEqualTo("Test User");
        assertThat(response.getCustomerName()).isEqualTo("Test Customer");

        verify(salesOrderRepository, atLeastOnce()).save(any(SalesOrder.class));
        verify(salesOrderItemRepository).saveAll(org.mockito.ArgumentMatchers.<SalesOrderItem>anyList());
    }

    // ========== Test 2: Low Price Trigger Approval ==========

    @Test
    @DisplayName("case-3")
    void testCreateSalesOrder_LowPriceTriggerApproval() {
        // Given: Unit price below min sales price
        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(1L);

        CreateSalesOrderRequest.SalesOrderItemData itemData = new CreateSalesOrderRequest.SalesOrderItemData();
        itemData.setProductId(1L);
        itemData.setQuantity(10);
        itemData.setUnitPrice(new BigDecimal("8.00")); // Below min price 10.00
        request.setItems(List.of(itemData));

        doNothing().when(customerService).validateCustomerActive(1L);
        when(salesOrderRepository.existsByOrderNo(anyString())).thenReturn(false);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> {
                SalesOrder order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(salesOrderItemRepository.saveAll(org.mockito.ArgumentMatchers.<SalesOrderItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(systemConfigService.getSalesApprovalAmountThreshold()).thenReturn(new BigDecimal("10000.00"));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.createSalesOrder(request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");

        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(SalesOrderStatus.PENDING_APPROVAL);

        verify(allocationService, never()).allocateInventory(anyLong());
    }

    // ========== Test 3: High Amount Trigger Approval ==========

    @Test
    @DisplayName("case-4")
    void testCreateSalesOrder_HighAmountTriggerApproval() {
        // Given: Total amount exceeds threshold
        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(1L);

        CreateSalesOrderRequest.SalesOrderItemData itemData = new CreateSalesOrderRequest.SalesOrderItemData();
        itemData.setProductId(1L);
        itemData.setQuantity(1000);
        itemData.setUnitPrice(new BigDecimal("15.00"));
        request.setItems(List.of(itemData));

        doNothing().when(customerService).validateCustomerActive(1L);
        when(salesOrderRepository.existsByOrderNo(anyString())).thenReturn(false);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> {
                SalesOrder order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(salesOrderItemRepository.saveAll(org.mockito.ArgumentMatchers.<SalesOrderItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(systemConfigService.getSalesApprovalAmountThreshold()).thenReturn(new BigDecimal("1000.00"));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.createSalesOrder(request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");

        verify(allocationService, never()).allocateInventory(anyLong());
    }

    // ========== Test 4: Auto Approve ==========

    @Test
    @DisplayName("case-5")
    void testCreateSalesOrder_AutoApprove() {
        // Given: Price and amount within limits
        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(1L);

        CreateSalesOrderRequest.SalesOrderItemData itemData = new CreateSalesOrderRequest.SalesOrderItemData();
        itemData.setProductId(1L);
        itemData.setQuantity(10);
        itemData.setUnitPrice(new BigDecimal("15.00"));
        request.setItems(List.of(itemData));

        doNothing().when(customerService).validateCustomerActive(1L);
        when(salesOrderRepository.existsByOrderNo(anyString())).thenReturn(false);
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> {
                SalesOrder order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(salesOrderItemRepository.saveAll(org.mockito.ArgumentMatchers.<SalesOrderItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(systemConfigService.getSalesApprovalAmountThreshold()).thenReturn(new BigDecimal("10000.00"));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());
        when(allocationService.allocateInventory(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.createSalesOrder(request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("APPROVED_AWAITING_SHIPMENT");

        verify(allocationService).allocateInventory(1L);
    }

    // ========== Test 5: Approve Sales Order ==========

    @Test
    @DisplayName("case-6")
    void testApproveSalesOrder_Success() {
        // Given
        SalesOrder order = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.PENDING_APPROVAL)
            .totalAmount(new BigDecimal("100.00"))
            .build();

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(allocationService.allocateInventory(1L)).thenReturn(new ArrayList<>());
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.approveSalesOrder(1L, 1L, "Manager", "Approved");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("APPROVED_AWAITING_SHIPMENT");
        assertThat(response.getReviewComment()).isEqualTo("Approved");

        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT);

        verify(allocationService).allocateInventory(1L);
    }

    // ========== Test 6: Reject Sales Order ==========

    @Test
    @DisplayName("case-7")
    void testRejectSalesOrder_Success() {
        // Given
        SalesOrder order = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.PENDING_APPROVAL)
            .totalAmount(new BigDecimal("100.00"))
            .build();

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.rejectSalesOrder(1L, 1L, "Manager", "Price too low");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getReviewComment()).isEqualTo("Price too low");

        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(SalesOrderStatus.REJECTED);

        verify(allocationService, never()).allocateInventory(anyLong());
    }

    // ========== Test 7: Update Sales Order ==========

    @Test
    @DisplayName("case-8")
    void testUpdateSalesOrder_Success() {
        // Given
        SalesOrder order = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.DRAFT)
            .totalAmount(new BigDecimal("100.00"))
            .build();

        UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
        request.setCustomerId(1L);

        UpdateSalesOrderRequest.SalesOrderItemData itemData = new UpdateSalesOrderRequest.SalesOrderItemData();
        itemData.setProductId(1L);
        itemData.setQuantity(20);
        itemData.setUnitPrice(new BigDecimal("15.00"));
        request.setItems(List.of(itemData));

        doNothing().when(customerService).validateCustomerActive(1L);
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(salesOrderItemRepository.saveAll(org.mockito.ArgumentMatchers.<SalesOrderItem>anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(systemConfigService.getSalesApprovalAmountThreshold()).thenReturn(new BigDecimal("10000.00"));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());
        when(allocationService.allocateInventory(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.updateSalesOrder(1L, request, 1L);

        // Then
        assertThat(response).isNotNull();

        verify(salesOrderItemRepository).deleteBySalesOrderId(1L);
        verify(salesOrderItemRepository).saveAll(org.mockito.ArgumentMatchers.<SalesOrderItem>anyList());
        verify(salesOrderRepository, atLeastOnce()).save(any(SalesOrder.class));
    }

    // ========== Test 8: Update Invalid Status ==========

    @Test
    @DisplayName("case-9")
    void testUpdateSalesOrder_InvalidStatus() {
        // Given: Order already shipped
        SalesOrder order = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.SHIPPED)
            .totalAmount(new BigDecimal("100.00"))
            .build();

        UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
        request.setItems(new ArrayList<>());

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        // When & Then
        assertThatThrownBy(() -> salesSubmissionService.updateSalesOrder(1L, request, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SALES_ORDER_CANNOT_MODIFY);

        verify(salesOrderItemRepository, never()).deleteBySalesOrderId(anyLong());
    }

    // ========== Test 9: Cancel Sales Order ==========

    @Test
    @DisplayName("case-10")
    void testCancelSalesOrder_Success() {
        // Given
        SalesOrder order = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.DRAFT)
            .totalAmount(new BigDecimal("100.00"))
            .build();

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.cancelSalesOrder(1L, "Customer request", 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CANCELLED");

        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(SalesOrderStatus.CANCELLED);
    }

    // ========== Test 10: Cancel Release Inventory ==========

    @Test
    @DisplayName("case-11")
    void testCancelSalesOrder_ReleaseInventory() {
        // Given: Order in APPROVED_AWAITING_SHIPMENT status
        SalesOrder order = SalesOrder.builder()
            .id(1L)
            .orderNo("SO20260129001")
            .customerId(1L)
            .status(SalesOrderStatus.APPROVED_AWAITING_SHIPMENT)
            .totalAmount(new BigDecimal("100.00"))
            .build();

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderItemRepository.findBySalesOrderId(1L)).thenReturn(new ArrayList<>());

        // When
        SalesOrderResponse response = salesSubmissionService.cancelSalesOrder(1L, "Customer request", 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CANCELLED");

        verify(outboundTaskRepository).deleteBySalesOrderId(1L); // Should release inventory
    }
}

