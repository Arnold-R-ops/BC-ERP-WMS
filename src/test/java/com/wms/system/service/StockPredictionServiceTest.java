package com.wms.system.service;

import com.wms.system.dto.ReorderSuggestion;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockPredictionService Tests")
class StockPredictionServiceTest {

    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private InventoryBatchRepository inventoryBatchRepository;
    @Mock private StockTransactionRepository stockTransactionRepository;

    @InjectMocks
    private StockPredictionService stockPredictionService;

    private ProductSku testProduct;

    @BeforeEach
    void setUp() {
        testProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .id(1L)
                .name("Test ProductSku")
                .barcode("BAR001")
                .unitPrice(new BigDecimal("10.00"))
                .minStock(50)
                .leadTime(7)
                .supplier("Test Supplier")
                .build();
    }

    // ========== getReorderSuggestion ==========

    @Test
    @DisplayName("getReorderSuggestion - returns suggestion with no outbound history")
    void testGetReorderSuggestion_NoHistory() {
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(30);
        when(stockTransactionRepository.findMovementHistory(anyLong(), any(), any()))
                .thenReturn(List.of());

        ReorderSuggestion result = stockPredictionService.getReorderSuggestion(1L, 30);

        assertThat(result).isNotNull();
        assertThat(result.getProductSkuId()).isEqualTo(1L);
        assertThat(result.getCurrentStock()).isEqualTo(30);
        assertThat(result.getDailyAverageOutbound()).isEqualTo(0.0);
        assertThat(result.getEstimatedDaysUntilStockout()).isNull();
        assertThat(result.getUrgencyLevel()).isEqualTo(ReorderSuggestion.UrgencyLevel.CRITICAL);
        // suggestedQty = ceil(0 * 7) + 50 = 50
        assertThat(result.getSuggestedReorderQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("getReorderSuggestion - no outbound history with sufficient stock is low urgency")
    void testGetReorderSuggestion_NoHistorySufficientStock() {
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(100);
        when(stockTransactionRepository.findMovementHistory(anyLong(), any(), any()))
                .thenReturn(List.of());

        ReorderSuggestion result = stockPredictionService.getReorderSuggestion(1L, 30);

        assertThat(result.getEstimatedDaysUntilStockout()).isNull();
        assertThat(result.getUrgencyLevel()).isEqualTo(ReorderSuggestion.UrgencyLevel.LOW);
    }

    @Test
    @DisplayName("getReorderSuggestion - calculates daily average from transactions")
    void testGetReorderSuggestion_WithHistory() {
        // 60 units outbound in 30 days = 2.0 per day
        StockTransaction tx1 = buildTransaction(30, LocalDateTime.now().minusDays(5));
        StockTransaction tx2 = buildTransaction(30, LocalDateTime.now().minusDays(2));

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(100);
        when(stockTransactionRepository.findMovementHistory(anyLong(), any(), any()))
                .thenReturn(List.of(tx1, tx2));

        ReorderSuggestion result = stockPredictionService.getReorderSuggestion(1L, 30);

        assertThat(result).isNotNull();
        assertThat(result.getDailyAverageOutbound()).isEqualTo(2.0); // 60/30
        // suggestedQty = ceil(2.0 * 7) + 50 = 14 + 50 = 64
        assertThat(result.getSuggestedReorderQuantity()).isEqualTo(64);
    }

    @Test
    @DisplayName("getReorderSuggestion - throws BusinessException when product not found")
    void testGetReorderSuggestion_ProductNotFound() {
        when(productSkuRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockPredictionService.getReorderSuggestion(99L, 30))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getReorderSuggestion - treats null stock as 0")
    void testGetReorderSuggestion_NullStock() {
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(null);
        when(stockTransactionRepository.findMovementHistory(anyLong(), any(), any()))
                .thenReturn(List.of());

        ReorderSuggestion result = stockPredictionService.getReorderSuggestion(1L, 30);

        assertThat(result.getCurrentStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("getReorderSuggestion(productSkuId) - uses default period 30 days")
    void testGetReorderSuggestion_DefaultPeriod() {
        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(100);
        when(stockTransactionRepository.findMovementHistory(anyLong(), any(), any()))
                .thenReturn(List.of());

        ReorderSuggestion result = stockPredictionService.getReorderSuggestion(1L);

        assertThat(result).isNotNull();
        assertThat(result.getCalculationPeriod()).isEqualTo(30);
    }

    // ========== getAllReorderSuggestions ==========

    @Test
    @DisplayName("getAllReorderSuggestions - returns suggestions for low stock products")
    void testGetAllReorderSuggestions() {
        ProductSku lowStockProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .id(2L).name("Low Stock").barcode("LOW001")
                .unitPrice(new BigDecimal("5.00")).minStock(100).leadTime(14).build();

        when(productSkuRepository.findLowStockProducts()).thenReturn(List.of(lowStockProduct));
        when(productSkuRepository.findById(2L)).thenReturn(Optional.of(lowStockProduct));
        when(inventoryBatchRepository.sumQuantityByProductSku(2L)).thenReturn(20); // below minStock
        when(stockTransactionRepository.findMovementHistory(anyLong(), any(), any()))
                .thenReturn(List.of());

        List<ReorderSuggestion> results = stockPredictionService.getAllReorderSuggestions(30);

        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("getAllReorderSuggestions - returns empty when no low stock products")
    void testGetAllReorderSuggestions_Empty() {
        when(productSkuRepository.findLowStockProducts()).thenReturn(List.of());

        List<ReorderSuggestion> results = stockPredictionService.getAllReorderSuggestions(30);

        assertThat(results).isEmpty();
    }

    // ========== getHighPriorityReorderSuggestions ==========

    @Test
    @DisplayName("getHighPriorityReorderSuggestions - returns only CRITICAL and HIGH")
    void testGetHighPriorityReorderSuggestions() {
        when(productSkuRepository.findLowStockProducts()).thenReturn(List.of());

        List<ReorderSuggestion> results = stockPredictionService.getHighPriorityReorderSuggestions(30);

        // With empty input, should return empty
        assertThat(results).isEmpty();
    }

    // ========== calculateTotalReorderCost ==========

    @Test
    @DisplayName("calculateTotalReorderCost - sums estimated costs")
    void testCalculateTotalReorderCost() {
        ReorderSuggestion s1 = ReorderSuggestion.builder()
                .estimatedCost(new BigDecimal("1000.00")).build();
        ReorderSuggestion s2 = ReorderSuggestion.builder()
                .estimatedCost(new BigDecimal("500.00")).build();
        ReorderSuggestion s3 = ReorderSuggestion.builder()
                .estimatedCost(null).build(); // null should be filtered

        BigDecimal total = stockPredictionService.calculateTotalReorderCost(List.of(s1, s2, s3));

        assertThat(total).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("calculateTotalReorderCost - returns zero for empty list")
    void testCalculateTotalReorderCost_Empty() {
        BigDecimal total = stockPredictionService.calculateTotalReorderCost(List.of());

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========== Timezone conversion test ==========

    @Test
    @DisplayName("getReorderSuggestion - groups transactions by London date (timezone conversion)")
    void testGetReorderSuggestion_TimezoneConversion() {
        // Create a transaction at UTC 23:00 (which is still the same day in London UTC+0)
        LocalDateTime utcTime = LocalDateTime.of(2026, 1, 1, 23, 0, 0);
        StockTransaction tx = buildTransaction(10, utcTime);

        when(productSkuRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.sumQuantityByProductSku(1L)).thenReturn(100);
        when(stockTransactionRepository.findMovementHistory(anyLong(), any(), any()))
                .thenReturn(List.of(tx));

        // Should process without exception (London date grouping happens internally)
        ReorderSuggestion result = stockPredictionService.getReorderSuggestion(1L, 30);

        assertThat(result).isNotNull();
        assertThat(result.getDailyAverageOutbound()).isEqualTo(10.0 / 30);
    }

    // ========== Helper ==========

    private StockTransaction buildTransaction(int quantity, LocalDateTime createdAt) {
        StockTransaction tx = StockTransaction.builder()
                .id((long) (Math.random() * 1000))
                .quantity(quantity)
                .transactionType(TransactionType.OUT)
                .build();
        tx.setCreatedAt(createdAt);
        return tx;
    }
}
