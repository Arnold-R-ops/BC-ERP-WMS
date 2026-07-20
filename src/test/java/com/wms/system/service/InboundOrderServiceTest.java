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
 * InboundOrderService 闂佸憡顨嗗ú鏍储閹捐秮鍦偓锝庡幘濡?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偛绀傞柕澶堝劤濮樸劑鏌涘Δ浣圭妞ゆ挻鎮傞幃鍫曞幢濡崵鐤€闂佸憡妫戠槐鏇炩枔閹达箑鍐€缂佸娉曟俊鍥煕閺冨倸鏋欓柛?
 *
 * 濠电偞娼欓鍫ユ儊椤栫偛鎹堕柣鎴炆戦悵顖炴煥?
 * 1. 闂佸憡甯楃粙鎴犵磽閹捐绀傞柕澶堝劤濮樸劑鏌涘Δ浣圭┛缂佽鲸鐟╅獮瀣箛椤掆偓椤?婵炴挻纰嶇粙鎴犺姳閺屻儱鐤柛鈩冩礈閻熸繈鎮楀☉娅亜锕?婵炲瓨绫傞崘鈺傚剬婵炴垶鎸哥粔鎾偤閵娾晛鎹舵い顓熷笧缁€?
 * 2. 闂佽鍓濆畷鐢靛垝閿熺姵鍋犻柛鈩冾殢閸氣偓闂佸湱鏁稿▍銏㈡濞嗘挸绠ｉ柟閭﹀墮椤?闂佺粯顭堥崺鏍焵椤戣法绐旈柡浣革功閹?闂佸搫顦崯鏉戭瀶閻戞鈻旂€广儱鐗嗛崰鏇㈡煥?
 * 3. 闂備焦褰冨ú鈺呭窗濮椻偓瀹曘劌螣鐏忔牑鍋撳Ο鍏煎闁靛牆绻掔粈鍕煙鐎涙ê濮囧┑?闂佺粯顭堥崺鏍焵椤戣法绐旈柡浣革功閹?闂佹眹鍨婚崰鎰板垂濮樿泛绠ラ悷娆忓閸嬔囨煟椤旇崵绛忕紒?
 * 4. 婵炲濮甸幐鍝ヨ姳闁秴缁╅柟顖滃瑜版盯鏌ㄥ☉妯煎闁搞劍宀稿畷?闂佺粯顭堥崺鏍焵椤戣法绐旈柡浣革功閹?闂佸搫娲ら悺銊╁蓟婵犲啯鍎熼柟鎯х－閹界娀鏌?
 * 5. 闂佸綊鏀辩敮鐐靛垝閻戣棄绀傞柕澶堝劤濮樸劑鏌涘Δ浣圭┛缂佽鲸鐟╅獮瀣箛椤掆偓椤?闂佺粯顭堥崺鏍焵椤戣法绐旈柡浣革功閹风娀顢涢妶鍥╊槴
 * 6. 闂佸搫琚崕鎾敋濡ゅ懎绀傞柕澶堝劤濮樸劑鏌涘Δ浣圭┛缂佽鲸鐟╅獮鎰箙閸?闂佸湱顭堥ˇ杈ㄦ叏閹间礁绠戝〒姘功缁€?
 *
 * @author WMS Team
 * @since 2026-01-26 (Phase 3.5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("case-1")
class InboundOrderServiceTest {

    @Mock
    private InboundOrderRepository inboundOrderRepository;

    @Mock
    private InboundOrderItemRepository inboundOrderItemRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductSkuRepository productSkuRepository;

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

    @Mock
    private UserRepository userRepository;

    @Mock
    private DomainOutboxService domainOutboxService;

    @Mock
    private BackorderService backorderService;

    @Mock
    private LocationOccupancyService locationOccupancyService;

    @InjectMocks
    private InboundOrderService inboundOrderService;

    private Supplier testSupplier;
    private ProductSku testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InboundOrder testOrder;
    private InboundOrderItem testItem;

    @BeforeEach
    void setUp() {
        // 闂佸憡甯楃换鍌烇綖閹版澘绀岄柡宥冨妿閵堟挳鎮归崶銊︾闁哄棛鍠栭獮?
        testSupplier = Supplier.builder()
                .id(1L)
                .code("SUP001")
                .name("Test Supplier")
                .isActive(true)
                .build();

        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .id(1L)
                .name("Test ProductSku")
                .barcode("BARCODE001")
                .product(Product.builder().id(1L).productCode("SPU001").productName("Test SPU").build())
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
                .productSku(testProduct)
                .planQty(100)
                .confirmedQty(null)
                .actualQty(0)
                .expiryDate(LocalDate.now().plusMonths(6))  // 濠电儑缍€椤曆勬叏閻愬瓨浜ら柛銉ｅ妽閸╁倿鏌￠崘锕€鍔滄繝鈧?
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

    // ==================== 闂佸憡甯楃粙鎴犵磽閹捐绀傞柕澶堝劤濮樸劑鏌涘Δ浣圭缂佷礁顕幏?====================

    @Test
    @DisplayName("case-2")
    void createInboundOrder_Success() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(1L);
        request.setExpectedDate(LocalDate.now().plusDays(7));
        request.setRemark("Test order");

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductSkuId(1L);
        itemReq.setPlanQty(100);
        itemReq.setTargetWarehouseId(1L);
        itemReq.setTargetLocationId(1L);
        request.setItems(List.of(itemReq));

        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L)).thenReturn(Optional.of(testSupplier));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
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
        verify(supplierRepository).findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L);
        verify(productSkuRepository).findById(1L);
        verify(inboundOrderRepository).save(any(InboundOrder.class));
    }

    @Test
    @DisplayName("case-3")
    void createInboundOrder_SupplierNotFound() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(999L);
        request.setItems(List.of());

        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(999L, 1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.createInboundOrder(
                request, 1L, "Test User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "SUPPLIER_NOT_FOUND");

        verify(supplierRepository).findByIdAndCompanyIdAndIsDeletedFalse(999L, 1L);
        verify(inboundOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("case-4")
    void createInboundOrder_SupplierNotActive() {
        // Given
        testSupplier.setIsActive(false);
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(1L);
        request.setItems(List.of());

        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L)).thenReturn(Optional.of(testSupplier));

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.createInboundOrder(
                request, 1L, "Test User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "SUPPLIER_NOT_ACTIVE");
    }

    @Test
    @DisplayName("case-5")
    void createInboundOrder_ProductNotFound() {
        // Given
        CreateInboundOrderRequest request = new CreateInboundOrderRequest();
        request.setSupplierId(1L);

        CreateInboundOrderRequest.InboundOrderItemRequest itemReq = new CreateInboundOrderRequest.InboundOrderItemRequest();
        itemReq.setProductSkuId(999L);
        itemReq.setPlanQty(100);
        request.setItems(List.of(itemReq));

        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L)).thenReturn(Optional.of(testSupplier));
        when(productSkuRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.createInboundOrder(
                request, 1L, "Test User"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "PRODUCT_SKU_NOT_FOUND");
    }

    // ==================== 闂佽鍓濆畷鐢靛垝閿熺姵鍋犻柛鈩冾殢閸氣偓闂侀€涚祷椤绮婄€靛憡瀚?====================

    @Test
    @DisplayName("case-6")
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
    @DisplayName("case-7")
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
    @DisplayName("case-8")
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

    // ==================== 闂備焦褰冨ú鈺呭窗濮椻偓瀹曘劌螣鐏忔牑鍋撳Ο鍏煎闁靛牆妫涢妶鎾偣?====================

    @Test
    @DisplayName("case-9")
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
        when(batchCodeGenerator.generateUnique(any(ProductSku.class), any(LocalDate.class)))
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
    @DisplayName("case-10")
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

    // ==================== 婵炲濮甸幐鍝ヨ姳闁秴缁╅柟顖滃瑜版稒绻涢弶鎴創闁?====================

    @Test
    @DisplayName("case-11")
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
    @DisplayName("case-12")
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
    @DisplayName("case-13")
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

    // ==================== 闂佸綊鏀辩敮鐐靛垝閻戣棄绀傞柕澶堝劤濮樸劑鏌涘Δ浣圭缂佷礁顕幏?====================

    @Test
    @DisplayName("case-14")
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
    @DisplayName("case-15")
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

    // ==================== 闂佸搫琚崕鎾敋濡も偓闇夐悗锝庡幘濡?====================

    @Test
    @DisplayName("case-16")
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
    @DisplayName("case-17")
    void getInboundOrderById_NotFound() {
        // Given
        when(inboundOrderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inboundOrderService.getInboundOrderById(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", "INBOUND_ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("case-18")
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
    @DisplayName("case-19")
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
    @DisplayName("case-20")
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
