package com.wms.system.service;

import com.wms.system.dto.StockAdjustmentRequest;
import com.wms.system.entity.Inventory;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.InventoryRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Tests")
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private ProductSku testProduct;
    private Location testLocation;
    private Inventory testInventory;

    @BeforeEach
    void setUp() {
        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .id(1L)
                .name("Test ProductSku")
                .barcode("BAR001")
                .minStock(10)
                .build();

        testLocation = Location.builder()
                .id(1L)
                .warehouseCode("WH01")
                .locationCode("WH01-ZONE_A-A-01-001")
                .build();

        testInventory = Inventory.builder()
                .id(1L)
                .productSku(testProduct)
                .location(testLocation)
                .quantity(100)
                .build();
    }

    // ========== adjustStock IN ==========

    @Test
    @DisplayName("adjustStock IN - success increases inventory")
    void testAdjustStock_IN_Success() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(1L)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(50)
                .sourceOrderId("PO20260101001")
                .build();

        StockTransaction savedTx = StockTransaction.builder()
                .id(1L)
                .productSku(testProduct)
                .location(testLocation)
                .transactionType(TransactionType.IN)
                .quantity(50)
                .quantityBefore(100)
                .quantityAfter(150)
                .build();

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(inventoryRepository.findByProductSkuAndLocation(testProduct, testLocation))
                .thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenReturn(savedTx);

        StockTransaction result = inventoryService.adjustStock(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(inventoryRepository).save(any(Inventory.class));
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("adjustStock OUT - success when stock sufficient")
    void testAdjustStock_OUT_Success() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(1L)
                .transactionType(TransactionType.OUT)
                .sourceType(SourceType.SALE_OUT)
                .quantity(30)
                .sourceOrderId("SO20260101001")
                .build();

        StockTransaction savedTx = StockTransaction.builder()
                .id(2L)
                .transactionType(TransactionType.OUT)
                .quantity(30)
                .quantityBefore(100)
                .quantityAfter(70)
                .build();

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(inventoryRepository.findByProductSkuAndLocation(testProduct, testLocation))
                .thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenReturn(savedTx);

        StockTransaction result = inventoryService.adjustStock(request);

        assertThat(result).isNotNull();
        verify(inventoryRepository).save(argThat(inv -> inv.getQuantity() == 70));
    }

    @Test
    @DisplayName("adjustStock OUT - throws BusinessException when stock insufficient")
    void testAdjustStock_OUT_InsufficientStock() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(1L)
                .transactionType(TransactionType.OUT)
                .sourceType(SourceType.SALE_OUT)
                .quantity(200) // more than current stock of 100
                .sourceOrderId("SO20260101001")
                .build();

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(inventoryRepository.findByProductSkuAndLocation(testProduct, testLocation))
                .thenReturn(Optional.of(testInventory));

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(BusinessException.class);

        verify(inventoryRepository, never()).save(any(Inventory.class));
        verify(stockTransactionRepository, never()).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("adjustStock ADJUST - success modifies quantity by delta")
    void testAdjustStock_ADJUST_Success() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(1L)
                .transactionType(TransactionType.ADJUST)
                .sourceType(SourceType.MANUAL_ADJUST)
                .quantity(5)
                .sourceOrderId("ST20260101001")
                .build();

        StockTransaction savedTx = StockTransaction.builder().id(3L).build();

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(inventoryRepository.findByProductSkuAndLocation(testProduct, testLocation))
                .thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(testInventory);
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenReturn(savedTx);

        StockTransaction result = inventoryService.adjustStock(request);

        assertThat(result).isNotNull();
        // ADJUST: quantity = 100 + 5 = 105
        verify(inventoryRepository).save(argThat(inv -> inv.getQuantity() == 105));
    }

    @Test
    @DisplayName("adjustStock - throws BusinessException when product not found")
    void testAdjustStock_ProductNotFound() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(99L)
                .locationId(1L)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(10)
                .sourceOrderId("PO001")
                .build();

        when(productSkuRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(BusinessException.class);

        verify(locationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("adjustStock - throws BusinessException when location not found")
    void testAdjustStock_LocationNotFound() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(99L)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(10)
                .sourceOrderId("PO001")
                .build();

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("adjustStock IN - creates new inventory when none exists")
    void testAdjustStock_IN_CreatesNewInventory() {
        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productSkuId(1L)
                .locationId(1L)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(20)
                .sourceOrderId("PO001")
                .build();

        Inventory newInventory = Inventory.builder()
                .productSku(testProduct)
                .location(testLocation)
                .quantity(0)
                .build();
        StockTransaction savedTx = StockTransaction.builder().id(5L).build();

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(testLocation));
        when(inventoryRepository.findByProductSkuAndLocation(testProduct, testLocation))
                .thenReturn(Optional.empty()); // no existing inventory
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(newInventory);
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenReturn(savedTx);

        StockTransaction result = inventoryService.adjustStock(request);

        assertThat(result).isNotNull();
        // new inventory starts at 0, IN 20 → should save with 20
        verify(inventoryRepository).save(argThat(inv -> inv.getQuantity() == 20));
    }

    // ========== getTotalStock ==========

    @Test
    @DisplayName("getTotalStock - returns value from repository")
    void testGetTotalStock_HasValue() {
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(350);

        Integer result = inventoryService.getTotalStock(1L);

        assertThat(result).isEqualTo(350);
    }

    @Test
    @DisplayName("getTotalStock - returns 0 when repository returns null")
    void testGetTotalStock_ReturnsZeroWhenNull() {
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(null);

        Integer result = inventoryService.getTotalStock(1L);

        assertThat(result).isEqualTo(0);
    }
}
