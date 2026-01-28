package com.wms.system.controller;

import com.wms.system.dto.inbound.*;
import com.wms.system.entity.User;
import com.wms.system.entity.enums.InboundOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.InboundOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InboundOrderController 单元测试
 *
 * 使用 Mockito 模拟所有依赖，专注于测试控制器逻辑
 *
 * 测试覆盖：
 * 1. 创建入库单
 * 2. 总经理审批
 * 3. 采购员确认
 * 4. 仓库收货
 * 5. 拒绝入库单
 * 6. 查询入库单（按ID/按状态）
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InboundOrderController 单元测试")
class InboundOrderControllerTest {

    @Mock
    private InboundOrderService inboundOrderService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private InboundOrderController inboundOrderController;

    private InboundOrderResponse testResponse;
    private SecurityUser securityUser;

    @BeforeEach
    void setUp() {
        // Mock SecurityUser
        User mockUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("password")
                .enabled(true)
                .build();

        securityUser = new SecurityUser(mockUser);

        // Mock authentication (lenient because not all tests use it)
        lenient().when(authentication.getPrincipal()).thenReturn(securityUser);

        // Create test response
        testResponse = InboundOrderResponse.builder()
                .id(1L)
                .orderNo("IB202601260001")
                .status(InboundOrderStatus.PENDING_APPROVAL)
                .supplierId(1L)
                .supplierCode("SUP001")
                .supplierName("Test Supplier")
                .totalPlanQty(100)
                .applicantId(1L)
                .applicantName("Test User")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== 创建入库单测试 ====================

    @Test
    @DisplayName("创建入库单 - 成功")
    void createInboundOrder_Success() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(1L);
        request.setExpectedDate(LocalDate.now().plusDays(7));

        when(inboundOrderService.createInboundOrder(any(), anyLong(), anyString()))
                .thenReturn(testResponse);

        // When
        ResponseEntity<InboundOrderResponse> response = inboundOrderController
                .createInboundOrder(request, authentication);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderNo()).isEqualTo("IB202601260001");
        assertThat(response.getBody().getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);

        verify(inboundOrderService).createInboundOrder(request, 1L, "testuser");
    }

    @Test
    @DisplayName("创建入库单 - 供应商不存在")
    void createInboundOrder_SupplierNotFound() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(999L);

        when(inboundOrderService.createInboundOrder(any(), anyLong(), anyString()))
                .thenThrow(new BusinessException("SUPPLIER_NOT_FOUND", null));

        // When & Then
        assertThatThrownBy(() -> inboundOrderController.createInboundOrder(request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "SUPPLIER_NOT_FOUND");
    }

    // ==================== 总经理审批测试 ====================

    @Test
    @DisplayName("总经理审批 - 成功")
    void approvePlan_Success() {
        // Given
        ApprovalRequest request = new ApprovalRequest();
        request.setComment("Approved");

        testResponse.setStatus(InboundOrderStatus.APPROVED_PLAN);
        testResponse.setGmApprovedBy(1L);
        testResponse.setGmApprovedAt(LocalDateTime.now());

        when(inboundOrderService.approvePlan(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(testResponse);

        // When
        ResponseEntity<InboundOrderResponse> response = inboundOrderController
                .approvePlan(1L, request, authentication);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(InboundOrderStatus.APPROVED_PLAN);
        assertThat(response.getBody().getGmApprovedBy()).isEqualTo(1L);

        verify(inboundOrderService).approvePlan(1L, 1L, "testuser", "Approved");
    }

    @Test
    @DisplayName("总经理审批 - 入库单不存在")
    void approvePlan_OrderNotFound() {
        // Given
        ApprovalRequest request = new ApprovalRequest();
        request.setComment("Test comment");

        when(inboundOrderService.approvePlan(anyLong(), anyLong(), anyString(), anyString()))
                .thenThrow(new BusinessException("INBOUND_ORDER_NOT_FOUND", null));

        // When & Then
        assertThatThrownBy(() -> inboundOrderController.approvePlan(999L, request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INBOUND_ORDER_NOT_FOUND");
    }

    // ==================== 采购员确认测试 ====================

    @Test
    @DisplayName("采购员确认 - 成功")
    void confirmOrder_Success() {
        // Given
        ConfirmOrderRequest request = new ConfirmOrderRequest();
        request.setComment("Confirmed");

        ConfirmOrderRequest.ItemConfirmation confirmation = new ConfirmOrderRequest.ItemConfirmation();
        confirmation.setItemId(1L);
        confirmation.setConfirmedQty(90);
        request.setConfirmations(List.of(confirmation));

        testResponse.setStatus(InboundOrderStatus.AWAITING_RECEIVAL);
        testResponse.setConfirmedBy(1L);
        testResponse.setConfirmedAt(LocalDateTime.now());
        testResponse.setTotalConfirmedQty(90);

        when(inboundOrderService.confirmOrder(anyLong(), any(), anyLong(), anyString()))
                .thenReturn(testResponse);

        // When
        ResponseEntity<InboundOrderResponse> response = inboundOrderController
                .confirmOrder(1L, request, authentication);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(InboundOrderStatus.AWAITING_RECEIVAL);
        assertThat(response.getBody().getConfirmedBy()).isEqualTo(1L);
        assertThat(response.getBody().getTotalConfirmedQty()).isEqualTo(90);

        verify(inboundOrderService).confirmOrder(1L, request, 1L, "testuser");
    }

    @Test
    @DisplayName("采购员确认 - 状态错误")
    void confirmOrder_InvalidStatus() {
        // Given
        ConfirmOrderRequest request = new ConfirmOrderRequest();

        when(inboundOrderService.confirmOrder(anyLong(), any(), anyLong(), anyString()))
                .thenThrow(new BusinessException("INVALID_STATUS_FOR_CONFIRMATION", null));

        // When & Then
        assertThatThrownBy(() -> inboundOrderController.confirmOrder(1L, request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INVALID_STATUS_FOR_CONFIRMATION");
    }

    // ==================== 仓库收货测试 ====================

    @Test
    @DisplayName("仓库收货 - 成功")
    void receiveGoods_Success() {
        // Given
        ReceiveGoodsRequest request = new ReceiveGoodsRequest();
        ReceiveGoodsRequest.ItemReceipt receipt = new ReceiveGoodsRequest.ItemReceipt();
        receipt.setItemId(1L);
        receipt.setActualQty(88);
        receipt.setLocationId(1L);
        request.setReceipts(List.of(receipt));

        testResponse.setStatus(InboundOrderStatus.COMPLETED);
        testResponse.setReceivedBy(1L);
        testResponse.setReceivedAt(LocalDateTime.now());
        testResponse.setTotalActualQty(88);

        when(inboundOrderService.receiveGoods(anyLong(), any(), anyLong(), anyString()))
                .thenReturn(testResponse);

        // When
        ResponseEntity<InboundOrderResponse> response = inboundOrderController
                .receiveGoods(1L, request, authentication);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(InboundOrderStatus.COMPLETED);
        assertThat(response.getBody().getReceivedBy()).isEqualTo(1L);
        assertThat(response.getBody().getTotalActualQty()).isEqualTo(88);

        verify(inboundOrderService).receiveGoods(1L, request, 1L, "testuser");
    }

    @Test
    @DisplayName("仓库收货 - 实收数量超过确认数量")
    void receiveGoods_ActualQtyExceedsConfirmedQty() {
        // Given
        ReceiveGoodsRequest request = new ReceiveGoodsRequest();

        when(inboundOrderService.receiveGoods(anyLong(), any(), anyLong(), anyString()))
                .thenThrow(new BusinessException("ACTUAL_QTY_EXCEEDS_CONFIRMED_QTY", null));

        // When & Then
        assertThatThrownBy(() -> inboundOrderController.receiveGoods(1L, request, authentication))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "ACTUAL_QTY_EXCEEDS_CONFIRMED_QTY");
    }

    // ==================== 拒绝入库单测试 ====================

    @Test
    @DisplayName("拒绝入库单 - 成功")
    void rejectOrder_Success() {
        // Given
        RejectRequest request = new RejectRequest();
        request.setReason("Budget insufficient");

        testResponse.setStatus(InboundOrderStatus.REJECTED);

        when(inboundOrderService.rejectOrder(anyLong(), anyString(), anyLong(), anyString()))
                .thenReturn(testResponse);

        // When
        ResponseEntity<InboundOrderResponse> response = inboundOrderController
                .rejectOrder(1L, request, authentication);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(InboundOrderStatus.REJECTED);

        verify(inboundOrderService).rejectOrder(1L, "Budget insufficient", 1L, "testuser");
    }

    // ==================== 查询测试 ====================

    @Test
    @DisplayName("根据ID查询入库单 - 成功")
    void getInboundOrderById_Success() {
        // Given
        when(inboundOrderService.getInboundOrderById(1L)).thenReturn(testResponse);

        // When
        ResponseEntity<InboundOrderResponse> response = inboundOrderController
                .getInboundOrderById(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(1L);
        assertThat(response.getBody().getOrderNo()).isEqualTo("IB202601260001");

        verify(inboundOrderService).getInboundOrderById(1L);
    }

    @Test
    @DisplayName("根据ID查询入库单 - 不存在")
    void getInboundOrderById_NotFound() {
        // Given
        when(inboundOrderService.getInboundOrderById(999L))
                .thenThrow(new BusinessException("INBOUND_ORDER_NOT_FOUND", null));

        // When & Then
        assertThatThrownBy(() -> inboundOrderController.getInboundOrderById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INBOUND_ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("查询待审批的入库单")
    void getPendingApprovalOrders_Success() {
        // Given
        when(inboundOrderService.getInboundOrdersByStatus(InboundOrderStatus.PENDING_APPROVAL))
                .thenReturn(List.of(testResponse));

        // When
        ResponseEntity<List<InboundOrderResponse>> response = inboundOrderController
                .getPendingApprovalOrders();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getStatus())
                .isEqualTo(InboundOrderStatus.PENDING_APPROVAL);

        verify(inboundOrderService).getInboundOrdersByStatus(InboundOrderStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("查询待确认的入库单")
    void getPendingConfirmationOrders_Success() {
        // Given
        testResponse.setStatus(InboundOrderStatus.APPROVED_PLAN);
        when(inboundOrderService.getInboundOrdersByStatus(InboundOrderStatus.APPROVED_PLAN))
                .thenReturn(List.of(testResponse));

        // When
        ResponseEntity<List<InboundOrderResponse>> response = inboundOrderController
                .getPendingConfirmationOrders();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getStatus())
                .isEqualTo(InboundOrderStatus.APPROVED_PLAN);

        verify(inboundOrderService).getInboundOrdersByStatus(InboundOrderStatus.APPROVED_PLAN);
    }

    @Test
    @DisplayName("查询待收货的入库单")
    void getPendingReceivalOrders_Success() {
        // Given
        testResponse.setStatus(InboundOrderStatus.AWAITING_RECEIVAL);
        when(inboundOrderService.getInboundOrdersByStatus(InboundOrderStatus.AWAITING_RECEIVAL))
                .thenReturn(List.of(testResponse));

        // When
        ResponseEntity<List<InboundOrderResponse>> response = inboundOrderController
                .getPendingReceivalOrders();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getStatus())
                .isEqualTo(InboundOrderStatus.AWAITING_RECEIVAL);

        verify(inboundOrderService).getInboundOrdersByStatus(InboundOrderStatus.AWAITING_RECEIVAL);
    }

}
