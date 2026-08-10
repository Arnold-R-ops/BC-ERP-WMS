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
import org.mockito.Spy;
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
    @Mock private SupplierRepository supplierRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Spy private ProductSkuOperationalPolicy productSkuOperationalPolicy = new ProductSkuOperationalPolicy();
    @Mock private LocationRepository locationRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;
    @Mock private BatchCodeGenerator batchCodeGenerator;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private ProductSku testProduct;
    private Supplier testSupplier;
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

        testSupplier = Supplier.builder()
                .id(1L)
                .code("SUP-TEST")
                .name("Test Supplier")
                .isActive(true)
                .isDeleted(false)
                .build();

        orderingPurchaseOrder = PurchaseOrder.builder()
                .id(1L)
                .poNumber("PO-20260101-001")
                .supplier("Test Supplier")
                .supplierReference(testSupplier)
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
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L))
                .thenReturn(Optional.of(testSupplier));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(orderingPurchaseOrder);

        PurchaseOrder result = purchaseOrderService.createPurchaseOrder(
                1L, List.of(itemData), LocalDate.now(), 1L, "Admin", null
        );

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PurchaseOrderStatus.ORDERING);
        verify(purchaseOrderRepository).save(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("createPurchaseOrder - rejects disabled SKU")
    void testCreatePurchaseOrder_DisabledSku() {
        testProduct.setEnabled(false);
        PurchaseOrderService.PurchaseOrderItemData itemData =
            PurchaseOrderService.PurchaseOrderItemData.builder()
                .productSkuId(1L)
                .orderedQuantity(100)
                .unitCost(new BigDecimal("10.00"))
                .build();

        when(purchaseOrderRepository.findLatestByDatePrefix(anyString()))
            .thenReturn(new ArrayList<>());
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L))
            .thenReturn(Optional.of(testSupplier));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(
            1L, List.of(itemData), LocalDate.now(), 1L, "Admin", null
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue(
                "errorKey",
                com.wms.system.exception.ErrorKeys.PRODUCT_SKU_DISABLED
            );

        verify(purchaseOrderRepository, never()).save(any());
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
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L))
                .thenReturn(Optional.of(testSupplier));
        when(productSkuRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(
                1L, List.of(itemData), LocalDate.now(), 1L, "Admin", null
        )).isInstanceOf(BusinessException.class);

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPurchaseOrder - rejects missing supplier master")
    void testCreatePurchaseOrder_SupplierNotFound() {
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(99L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(
                99L, List.of(), LocalDate.now(), 1L, "Admin", null
        )).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", com.wms.system.exception.ErrorKeys.SUPPLIER_NOT_FOUND);

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPurchaseOrder - rejects inactive supplier master")
    void testCreatePurchaseOrder_SupplierInactive() {
        testSupplier.setIsActive(false);
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L))
                .thenReturn(Optional.of(testSupplier));

        assertThatThrownBy(() -> purchaseOrderService.createPurchaseOrder(
                1L, List.of(), LocalDate.now(), 1L, "Admin", null
        )).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", com.wms.system.exception.ErrorKeys.SUPPLIER_NOT_ACTIVE);

        verify(purchaseOrderRepository, never()).save(any());
    }

    // ========== Stage 2: confirmAndGenerateBatchCodes ==========

    // ========== Stage 1: updateOrderingPurchaseOrder ==========

    @Test
    @DisplayName("updateOrderingPurchaseOrder - edits ORDERING fields and recalculates totals")
    void testUpdateOrderingPurchaseOrder_Success() {
        orderingPurchaseOrder.setVersion(3L);
        PurchaseOrderItem existingItem = PurchaseOrderItem.builder()
                .id(10L)
                .purchaseOrder(orderingPurchaseOrder)
                .productSku(testProduct)
                .orderedQuantity(100)
                .receivedQuantity(0)
                .unitCost(new BigDecimal("10.00"))
                .build();
        orderingPurchaseOrder.getItems().add(existingItem);

        PurchaseOrderService.PurchaseOrderItemUpdateData update =
                PurchaseOrderService.PurchaseOrderItemUpdateData.builder()
                        .id(10L)
                        .productSkuId(1L)
                        .orderedQuantity(12)
                        .unitCost(new BigDecimal("8.50"))
                        .remark("updated line")
                        .build();

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(orderingPurchaseOrder));
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L))
                .thenReturn(Optional.of(testSupplier));
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.existsByPurchaseOrderItemId(10L)).thenReturn(false);
        when(purchaseOrderRepository.saveAndFlush(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = purchaseOrderService.updateOrderingPurchaseOrder(
                1L, 3L, 1L, List.of(update), LocalDate.of(2026, 8, 30),
                7L, "buyer", "updated order"
        );

        assertThat(result.getTotalQuantity()).isEqualTo(12);
        assertThat(result.getTotalCost()).isEqualByComparingTo("102.00");
        assertThat(result.getExpectedDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(result.getRemark()).isEqualTo("updated order");
        assertThat(result.getAuditLog()).contains("ORDERING purchase order edited by buyer");
        verify(inventoryBatchRepository, never()).save(any());
        verify(stockTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateOrderingPurchaseOrder - rejects non-ORDERING status")
    void testUpdateOrderingPurchaseOrder_WrongStatus() {
        orderingPurchaseOrder.setStatus(PurchaseOrderStatus.IN_TRANSIT);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(orderingPurchaseOrder));

        assertThatThrownBy(() -> purchaseOrderService.updateOrderingPurchaseOrder(
                1L, 0L, 1L, List.of(), null, 7L, "buyer", null
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", com.wms.system.exception.ErrorKeys.PO_INVALID_STATUS);
    }

    @Test
    @DisplayName("updateOrderingPurchaseOrder - rejects stale version")
    void testUpdateOrderingPurchaseOrder_StaleVersion() {
        orderingPurchaseOrder.setVersion(4L);
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(orderingPurchaseOrder));

        assertThatThrownBy(() -> purchaseOrderService.updateOrderingPurchaseOrder(
                1L, 3L, 1L, List.of(), null, 7L, "buyer", null
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", com.wms.system.exception.ErrorKeys.PO_EDIT_CONFLICT);
        verify(purchaseOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("updateOrderingPurchaseOrder - preserves a line with batch history")
    void testUpdateOrderingPurchaseOrder_CannotRemoveHistoricalLine() {
        orderingPurchaseOrder.setVersion(1L);
        PurchaseOrderItem historicalItem = PurchaseOrderItem.builder()
                .id(10L)
                .purchaseOrder(orderingPurchaseOrder)
                .productSku(testProduct)
                .orderedQuantity(100)
                .receivedQuantity(0)
                .build();
        orderingPurchaseOrder.getItems().add(historicalItem);

        ProductSku newProduct = ProductSku.builder()
                .id(2L)
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .name("New SKU")
                .enabled(true)
                .build();
        PurchaseOrderService.PurchaseOrderItemUpdateData newItem =
                PurchaseOrderService.PurchaseOrderItemUpdateData.builder()
                        .productSkuId(2L)
                        .orderedQuantity(1)
                        .build();

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(orderingPurchaseOrder));
        when(supplierRepository.findByIdAndCompanyIdAndIsDeletedFalse(1L, 1L))
                .thenReturn(Optional.of(testSupplier));
        when(productSkuRepository.findById(2L)).thenReturn(Optional.of(newProduct));
        when(inventoryBatchRepository.existsByPurchaseOrderItemId(10L)).thenReturn(true);

        assertThatThrownBy(() -> purchaseOrderService.updateOrderingPurchaseOrder(
                1L, 1L, 1L, List.of(newItem), null, 7L, "buyer", null
        ))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", com.wms.system.exception.ErrorKeys.PO_ITEM_HISTORY_LOCKED);
        verify(purchaseOrderRepository, never()).saveAndFlush(any());
    }

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
