package com.wms.system.service;

import com.wms.system.entity.*;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.*;
import com.wms.system.util.BatchCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOrderService Tests")
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private BatchCodeGenerator batchCodeGenerator;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private ProductSku testProduct;
    private PurchaseOrder orderingPurchaseOrder;

    @BeforeEach
    void setUp() {
        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .id(1L)
                .name("Test ProductSku")
                .barcode("BAR001")
                .unitPrice(new BigDecimal("10.00"))
                .build();

        orderingPurchaseOrder = PurchaseOrder.builder()
                .id(1L)
                .poNumber("PO-20260101-001")
                .supplier("Test Supplier")
                .status(PurchaseOrderStatus.ORDERING)
                .totalQuantity(100)
                .totalCost(new BigDecimal("1000.00"))
                .items(new ArrayList<>())
                .build();
    }

    // ========== Stage 1: createPurchaseOrder ==========

    @Test
    @DisplayName("createPurchaseOrder - success creates PO with ORDERING status")
    void testCreatePurchaseOrder_Success() {
        PurchaseOrderService.PurchaseOrderItemData itemData =
                PurchaseOrderService.PurchaseOrderItemData.builder()
                        .productSkuId(1L)
                        .orderedQuantity(100)
                        .unitCost(new BigDecimal("10.00"))
                        .build();

        // Mock PO number generation: findLatestByDatePrefix returns empty list
        when(purchaseOrderRepository.findLatestByDatePrefix(anyString()))
                .thenReturn(new ArrayList<PurchaseOrder>());
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(orderingPurchaseOrder);

        PurchaseOrder result = purchaseOrderService.createPurchaseOrder(
                "Test Supplier", List.of(itemData), LocalDate.now(), 1L, "Admin", null
        );

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PurchaseOrderStatus.ORDERING);
        verify(purchaseOrderRepository).save(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("createPurchaseOrder - throws when product not found")
    void testCreatePurchaseOrder_ProductNotFound() {
        PurchaseOrderService.PurchaseOrderItemData itemData =
                PurchaseOrderService.PurchaseOrderItemData.builder()
                        .productSkuId(99L)
                        .orderedQuantity(100)
                        .build();

        when(purchaseOrderRepository.findLatestByDatePrefix(anyString()))
                .thenReturn(new ArrayList<PurchaseOrder>());
        when(productSkuRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(
                "Supplier", List.of(itemData), LocalDate.now(), 1L, "Admin", null
        )).isInstanceOf(BusinessException.class);

        verify(purchaseOrderRepository, never()).save(any());
    }

    // ========== Stage 2: confirmAndGenerateBatchCodes ==========

    @Test
    @DisplayName("confirmAndGenerateBatchCodes - throws when status is not ORDERING")
    void testConfirmAndGenerateBatchCodes_WrongStatus() {
        PurchaseOrder inTransitOrder = PurchaseOrder.builder()
                .id(1L)
                .poNumber("PO-20260101-001")
                .status(PurchaseOrderStatus.IN_TRANSIT) // wrong status
                .items(new ArrayList<>())
                .build();

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(inTransitOrder));

        assertThatThrownBy(() -> purchaseOrderService.confirmAndGenerateBatchCodes(1L, List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("confirmAndGenerateBatchCodes - throws when PO not found")
    void testConfirmAndGenerateBatchCodes_NotFound() {
        when(purchaseOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseOrderService.confirmAndGenerateBatchCodes(99L, List.of()))
                .isInstanceOf(BusinessException.class);
    }

    // ========== Stage 3: receiveGoods - skipped (complex integration scenario) ==========

    // ========== rollbackToOrdering ==========

    @Test
    @DisplayName("rollbackToOrdering - throws when PO not in IN_TRANSIT status")
    void testRollbackToOrdering_WrongStatus() {
        PurchaseOrder orderingPO = PurchaseOrder.builder()
                .id(1L)
                .poNumber("PO-20260101-001")
                .status(PurchaseOrderStatus.ORDERING) // can't rollback from ORDERING
                .items(new ArrayList<>())
                .build();

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(orderingPO));

        assertThatThrownBy(() -> purchaseOrderService.rollbackToOrdering(1L, "Test reason"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("rollbackToOrdering - throws when PO not found")
    void testRollbackToOrdering_NotFound() {
        when(purchaseOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseOrderService.rollbackToOrdering(99L, "reason"))
                .isInstanceOf(BusinessException.class);
    }

    // ========== findById ==========

    @Test
    @DisplayName("findById - throws BusinessException when not found")
    void testFindById_NotFound() {
        when(purchaseOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseOrderService.findById(99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("findById - returns PO when found")
    void testFindById_Found() {
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(orderingPurchaseOrder));

        PurchaseOrder result = purchaseOrderService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getPoNumber()).isEqualTo("PO-20260101-001");
    }
}
