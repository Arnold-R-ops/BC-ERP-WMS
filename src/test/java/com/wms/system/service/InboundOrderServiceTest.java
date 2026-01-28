package com.wms.system.service;

import com.wms.system.dto.inbound.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.InboundOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InboundOrderService 单元测试
 *
 * 测试入库单管理服务的核心功能
 *
 * 测试场景：
 * 1. 创建入库单（成功/供应商不存在/产品不存在）
 * 2. 总经理审批（成功/状态错误/权限不足）
 * 3. 采购员确认（成功/状态错误/生成批次码）
 * 4. 仓库收货（成功/状态错误/更新库存）
 * 5. 拒绝入库单（成功/状态错误）
 * 6. 查询入库单（按ID/按状态）
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InboundOrderService 单元测试")
class InboundOrderServiceTest {

    @Mock
    private InboundOrderRepository inboundOrderRepository;

    @Mock
    private InboundOrderItemRepository inboundOrderItemRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @Mock
    private SpuSkuDateBatchCodeGenerator batchCodeGenerator;

    @InjectMocks
    private InboundOrderService inboundOrderService;

    private Supplier testSupplier;
    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InboundOrder testOrder;
    private InboundOrderItem testItem;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        testSupplier = Supplier.builder()
                .id(1L)
                .code("SUP001")
                .name("Test Supplier")
                .isActive(true)
                .build();

        testProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .barcode("BARCODE001")
                .spu(ProductSpu.builder().id(1L).spuCode("SPU001").spuName("Test SPU").build())
                .build();

        testWarehouse = Warehouse.builder()
                .id(1L)
                .code("WH01")
                .name("Main Warehouse")
                .isActive(true)
                .build();

        testLocation = Location.builder()
                .id(1L)
                .locationCode("WH01-A-01-001")
                .warehouse(testWarehouse)
                .build();

        testItem = InboundOrderItem.builder()
                .id(1L)
                .product(testProduct)
                .planQty(100)
                .confirmedQty(null)
                .actualQty(0)
                .expiryDate(LocalDate.now().plusMonths(6))  // 添加过期日期
                .build();

        testOrder = InboundOrder.builder()
                .id(1L)
                .orderNo("IB202601260001")
                .supplier(testSupplier)
                .status(InboundOrderStatus.PENDING_APPROVAL)
                .totalPlanQty(100)
                .applicantId(1L)
                .applicantName("Test User")
                .items(new ArrayList<>(List.of(testItem)))
                .build();

        testItem.setInboundOrder(testOrder);
    }

    // ==================== 创建入库单测试 ====================

    @Test
    @DisplayName("创建入库单 - 成功")
    void createInboundOrder_Success() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(1L);
        request.setExpectedDate(LocalDate.now().plusDays(7));
        request.setRemark("Test order");

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductId(1L);
        itemReq.setPlanQty(100);
        itemReq.setTargetWarehouseId(1L);
        itemReq.setTargetLocationId(1L);
        request.setItems(List.of(itemReq));

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(testSupplier));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(inboundOrderRepository.save(any(InboundOrder.class))).thenReturn(testOrder);

        // When
        InboundOrderResponse response = inboundOrderService.createInboundOrder(
                request, 1L, "Test User");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getOrderNo()).isEqualTo("IB202601260001");
        assertThat(response.getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);
        assertThat(response.getTotalPlanQty()).isEqualTo(100);

        // Verify
        verify(supplierRepository).findById(1L);
        verify(productRepository).findById(1L);
        verify(inboundOrderRepository).save(any(InboundOrder.class));
    }

    @Test
    @DisplayName("创建入库单 - 供应商不存在")
    void createInboundOrder_SupplierNotFound() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(999L);
        request.setItems(List.of());

        when(supplierRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.createInboundOrder(
                request, 1L, "Test User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "SUPPLIER_NOT_FOUND");

        verify(supplierRepository).findById(999L);
        verify(inboundOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("创建入库单 - 供应商未激活")
    void createInboundOrder_SupplierNotActive() {
        // Given
        testSupplier.setIsActive(false);
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(1L);
        request.setItems(List.of());

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(testSupplier));

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.createInboundOrder(
                request, 1L, "Test User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "SUPPLIER_NOT_ACTIVE");
    }

    @Test
    @DisplayName("创建入库单 - 产品不存在")
    void createInboundOrder_ProductNotFound() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(1L);

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductId(999L);
        itemReq.setPlanQty(100);
        request.setItems(List.of(itemReq));

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(testSupplier));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.createInboundOrder(
                request, 1L, "Test User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "PRODUCT_NOT_FOUND");
    }

    // ==================== 总经理审批测试 ====================

    @Test
    @DisplayName("总经理审批 - 成功")
    void approvePlan_Success() {
        // Given
        String comment = "Approved";

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(inboundOrderRepository.save(any(InboundOrder.class))).thenReturn(testOrder);

        // When
        InboundOrderResponse response = inboundOrderService.approvePlan(
                1L, 2L, "GM User", comment);

        // Then
        assertThat(response).isNotNull();

        // Verify order status changed
        ArgumentCaptor<InboundOrder> orderCaptor = ArgumentCaptor.forClass(InboundOrder.class);
        verify(inboundOrderRepository).save(orderCaptor.capture());
        InboundOrder savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getStatus()).isEqualTo(InboundOrderStatus.APPROVED_PLAN);
        assertThat(savedOrder.getGmApprovedBy()).isEqualTo(2L);
        assertThat(savedOrder.getGmApprovedAt()).isNotNull();
        assertThat(savedOrder.getGmApprovalComment()).isEqualTo("Approved");
    }

    @Test
    @DisplayName("总经理审批 - 入库单不存在")
    void approvePlan_OrderNotFound() {
        // Given
        when(inboundOrderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.approvePlan(
                999L, 2L, "GM User", "Approved"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INBOUND_ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("总经理审批 - 状态错误")
    void approvePlan_InvalidStatus() {
        // Given
        testOrder.setStatus(InboundOrderStatus.COMPLETED);

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.approvePlan(
                1L, 2L, "GM User", "Approved"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INVALID_STATUS_FOR_APPROVAL");
    }

    // ==================== 采购员确认测试 ====================

    @Test
    @DisplayName("采购员确认 - 成功并生成批次码")
    void confirmOrder_Success() {
        // Given
        testOrder.setStatus(InboundOrderStatus.APPROVED_PLAN);

        ConfirmOrderRequest request = new ConfirmOrderRequest();
        request.setComment("Confirmed");

        ConfirmOrderRequest.ItemConfirmation confirmation = new ConfirmOrderRequest.ItemConfirmation();
        confirmation.setItemId(1L);
        confirmation.setConfirmedQty(90);
        confirmation.setExpiryDate(LocalDate.now().plusMonths(6));
        confirmation.setProductionDate(LocalDate.now());
        confirmation.setTargetWarehouseId(1L);
        confirmation.setTargetLocationId(1L);
        request.setConfirmations(List.of(confirmation));

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(testWarehouse));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(batchCodeGenerator.generateUnique(any(Product.class), any(LocalDate.class)))
                .thenReturn("SPU001-SKU001-20260126");
        when(inboundOrderRepository.save(any(InboundOrder.class))).thenReturn(testOrder);

        // When
        InboundOrderResponse response = inboundOrderService.confirmOrder(
                1L, request, 3L, "Buyer User");

        // Then
        assertThat(response).isNotNull();

        // Verify
        ArgumentCaptor<InboundOrder> orderCaptor = ArgumentCaptor.forClass(InboundOrder.class);
        verify(inboundOrderRepository).save(orderCaptor.capture());
        InboundOrder savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getStatus()).isEqualTo(InboundOrderStatus.AWAITING_RECEIVAL);
        assertThat(savedOrder.getConfirmedBy()).isEqualTo(3L);
        assertThat(savedOrder.getConfirmedAt()).isNotNull();
        assertThat(savedOrder.getTotalConfirmedQty()).isEqualTo(90);

        // Verify batch code generated
        InboundOrderItem item = savedOrder.getItems().get(0);
        assertThat(item.getBatchCode()).isEqualTo("SPU001-SKU001-20260126");
        assertThat(item.getConfirmedQty()).isEqualTo(90);
    }

    @Test
    @DisplayName("采购员确认 - 状态错误")
    void confirmOrder_InvalidStatus() {
        // Given
        testOrder.setStatus(InboundOrderStatus.PENDING_APPROVAL);
        ConfirmOrderRequest request = new ConfirmOrderRequest();
        request.setConfirmations(List.of());

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.confirmOrder(
                1L, request, 3L, "Buyer User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INVALID_STATUS_FOR_CONFIRMATION");
    }

    // ==================== 仓库收货测试 ====================

    @Test
    @DisplayName("仓库收货 - 成功并更新库存")
    void receiveGoods_Success() {
        // Given
        testOrder.setStatus(InboundOrderStatus.AWAITING_RECEIVAL);
        testItem.setConfirmedQty(90);
        testItem.setBatchCode("SPU001-SKU001-20260126");

        ReceiveGoodsRequest request = new ReceiveGoodsRequest();
        ReceiveGoodsRequest.ItemReceipt receipt = new ReceiveGoodsRequest.ItemReceipt();
        receipt.setItemId(1L);
        receipt.setActualQty(88);
        receipt.setLocationId(1L);
        request.setReceipts(List.of(receipt));

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(inventoryBatchRepository.findByBatchCodeAndLocation(anyString(), any(Location.class)))
                .thenReturn(List.of());
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
                .thenReturn(InventoryBatch.builder().build());
        when(stockTransactionRepository.save(any(StockTransaction.class)))
                .thenReturn(StockTransaction.builder().build());
        when(inboundOrderRepository.save(any(InboundOrder.class))).thenReturn(testOrder);

        // When
        InboundOrderResponse response = inboundOrderService.receiveGoods(
                1L, request, 4L, "Warehouse User");

        // Then
        assertThat(response).isNotNull();

        // Verify
        ArgumentCaptor<InboundOrder> orderCaptor = ArgumentCaptor.forClass(InboundOrder.class);
        verify(inboundOrderRepository).save(orderCaptor.capture());
        InboundOrder savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getStatus()).isEqualTo(InboundOrderStatus.COMPLETED);
        assertThat(savedOrder.getReceivedBy()).isEqualTo(4L);
        assertThat(savedOrder.getReceivedAt()).isNotNull();
        assertThat(savedOrder.getTotalActualQty()).isEqualTo(88);

        // Verify inventory batch created
        verify(inventoryBatchRepository).save(any(InventoryBatch.class));

        // Verify stock transaction created
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("仓库收货 - 实收数量超过确认数量")
    void receiveGoods_ActualQtyExceedsConfirmedQty() {
        // Given
        testOrder.setStatus(InboundOrderStatus.AWAITING_RECEIVAL);
        testItem.setConfirmedQty(90);

        ReceiveGoodsRequest request = new ReceiveGoodsRequest();
        ReceiveGoodsRequest.ItemReceipt receipt = new ReceiveGoodsRequest.ItemReceipt();
        receipt.setItemId(1L);
        receipt.setActualQty(100); // Exceeds confirmed qty
        receipt.setLocationId(1L);
        request.setReceipts(List.of(receipt));

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.receiveGoods(
                1L, request, 4L, "Warehouse User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "ACTUAL_QTY_EXCEEDS_CONFIRMED_QTY");
    }

    @Test
    @DisplayName("仓库收货 - 状态错误")
    void receiveGoods_InvalidStatus() {
        // Given
        testOrder.setStatus(InboundOrderStatus.PENDING_APPROVAL);
        ReceiveGoodsRequest request = new ReceiveGoodsRequest();
        request.setReceipts(List.of());

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.receiveGoods(
                1L, request, 4L, "Warehouse User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INVALID_STATUS_FOR_RECEIVING");
    }

    // ==================== 拒绝入库单测试 ====================

    @Test
    @DisplayName("拒绝入库单 - 成功")
    void rejectOrder_Success() {
        // Given
        String reason = "Budget insufficient";

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(inboundOrderRepository.save(any(InboundOrder.class))).thenReturn(testOrder);

        // When
        InboundOrderResponse response = inboundOrderService.rejectOrder(
                1L, reason, 2L, "GM User");

        // Then
        assertThat(response).isNotNull();

        // Verify
        ArgumentCaptor<InboundOrder> orderCaptor = ArgumentCaptor.forClass(InboundOrder.class);
        verify(inboundOrderRepository).save(orderCaptor.capture());
        InboundOrder savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getStatus()).isEqualTo(InboundOrderStatus.REJECTED);
    }

    @Test
    @DisplayName("拒绝入库单 - 状态错误")
    void rejectOrder_InvalidStatus() {
        // Given
        testOrder.setStatus(InboundOrderStatus.COMPLETED);
        String reason = "Test";

        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.rejectOrder(
                1L, reason, 2L, "GM User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INVALID_STATUS_FOR_REJECTION");
    }

    // ==================== 查询测试 ====================

    @Test
    @DisplayName("根据ID查询入库单 - 成功")
    void getInboundOrderById_Success() {
        // Given
        when(inboundOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        InboundOrderResponse response = inboundOrderService.getInboundOrderById(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOrderNo()).isEqualTo("IB202601260001");
    }

    @Test
    @DisplayName("根据ID查询入库单 - 不存在")
    void getInboundOrderById_NotFound() {
        // Given
        when(inboundOrderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.getInboundOrderById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INBOUND_ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("根据状态查询入库单 - 待审批")
    void getInboundOrdersByStatus_PendingApproval() {
        // Given
        when(inboundOrderRepository.findByStatusOrderByCreatedAtDesc(InboundOrderStatus.PENDING_APPROVAL))
                .thenReturn(List.of(testOrder));

        // When
        List<InboundOrderResponse> responses = inboundOrderService
                .getInboundOrdersByStatus(InboundOrderStatus.PENDING_APPROVAL);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(InboundOrderStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("根据状态查询入库单 - 待确认")
    void getInboundOrdersByStatus_ApprovedPlan() {
        // Given
        testOrder.setStatus(InboundOrderStatus.APPROVED_PLAN);
        when(inboundOrderRepository.findByStatusOrderByCreatedAtDesc(InboundOrderStatus.APPROVED_PLAN))
                .thenReturn(List.of(testOrder));

        // When
        List<InboundOrderResponse> responses = inboundOrderService
                .getInboundOrdersByStatus(InboundOrderStatus.APPROVED_PLAN);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(InboundOrderStatus.APPROVED_PLAN);
    }

    @Test
    @DisplayName("根据状态查询入库单 - 待收货")
    void getInboundOrdersByStatus_AwaitingReceival() {
        // Given
        testOrder.setStatus(InboundOrderStatus.AWAITING_RECEIVAL);
        when(inboundOrderRepository.findByStatusOrderByCreatedAtDesc(InboundOrderStatus.AWAITING_RECEIVAL))
                .thenReturn(List.of(testOrder));

        // When
        List<InboundOrderResponse> responses = inboundOrderService
                .getInboundOrdersByStatus(InboundOrderStatus.AWAITING_RECEIVAL);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(InboundOrderStatus.AWAITING_RECEIVAL);
    }
}
