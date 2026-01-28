package com.wms.system.service;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSpu;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.Zone;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * InventoryBatchService 单元测试
 *
 * 测试范围：
 * 1. FIFO 出库逻辑（先进先出）
 * 2. 零头优先策略（Loose Item First）
 * 3. 库存不足异常处理
 * 4. 过期批次检测
 * 5. 批次查询功能
 * 6. 库存汇总计算
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("库存批次服务单元测试")
class InventoryBatchServiceTest {

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @InjectMocks
    private InventoryBatchService inventoryBatchService;

    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch looseBatch;
    private InventoryBatch fullPackBatch;
    private InventoryBatch expiredBatch;

    @BeforeEach
    void setUp() {
        // 创建测试仓库
        testWarehouse = Warehouse.builder()
            .id(1L)
            .code("WH01")
            .name("测试仓库")
            .isActive(true)
            .build();

        // 创建测试库位
        testLocation = Location.builder()
            .id(1L)
            .warehouse(testWarehouse)
            .warehouseCode("WH01")
            .zone(Zone.ZONE_A)
            .shelfNumber("A-01")
            .positionNumber("001")
            .locationCode("WH01-ZONE_A-A-01-001")
            .enabled(true)
            .build();

        // 创建测试产品（1箱 = 12袋）
        testProduct = Product.builder()
            .id(1L)
            .barcode("6901234567890")
            .name("测试茶叶")
            .spu(ProductSpu.builder().id(1L).spuCode("SPU001").spuName("茶叶").build())
            .packUnit("袋")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build();

        // 创建散货批次（8袋，已开箱）
        looseBatch = InventoryBatch.builder()
            .id(1L)
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(8)  // 8袋散货
            .initialQuantity(12)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .version(0L)
            .build();

        // 创建整箱批次（24袋 = 2箱）
        fullPackBatch = InventoryBatch.builder()
            .id(2L)
            .batchCode("BATCH002")
            .product(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(24)  // 24袋 = 2箱
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .version(0L)
            .build();

        // 创建过期批次
        expiredBatch = InventoryBatch.builder()
            .id(3L)
            .batchCode("BATCH003")
            .product(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(12)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().minusDays(1))  // 已过期
            .active(true)
            .version(0L)
            .build();
    }

    // ========== FIFO 出库测试 ==========

    @Test
    @DisplayName("FIFO 出库 - 只使用散货批次")
    void fifoOutbound_OnlyLooseBatch() {
        // Given - 请求 5袋，散货批次有 8袋
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(looseBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 5, SourceType.SALE_OUT, "SO-001", 1L, "测试员工"
        );

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(looseBatch.getQuantity()).isEqualTo(3);  // 8 - 5 = 3
        verify(inventoryBatchRepository).save(looseBatch);
        verify(stockTransactionRepository).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("FIFO 出库 - 散货不足，需要拆整箱")
    void fifoOutbound_LooseInsufficientNeedUnpack() {
        // Given - 请求 20袋，散货 8袋 + 需要拆 1箱（12袋）
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.findFullPackBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 20, SourceType.SALE_OUT, "SO-002", 1L, "测试员工"
        );

        // Then
        assertThat(transactions).hasSize(2);  // 2个批次
        assertThat(looseBatch.getQuantity()).isEqualTo(0);  // 散货用完
        assertThat(fullPackBatch.getQuantity()).isEqualTo(12);  // 24 - 12 = 12
        verify(inventoryBatchRepository, times(2)).save(any(InventoryBatch.class));
    }

    @Test
    @DisplayName("FIFO 出库 - 只使用整箱批次")
    void fifoOutbound_OnlyFullPackBatch() {
        // Given - 请求 12袋，没有散货，只有整箱
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.findFullPackBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(fullPackBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 12, SourceType.SALE_OUT, "SO-003", 1L, "测试员工"
        );

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(fullPackBatch.getQuantity()).isEqualTo(12);  // 24 - 12 = 12
    }

    @Test
    @DisplayName("FIFO 出库 - 库存不足")
    void fifoOutbound_InsufficientStock() {
        // Given - 请求 100袋，但总库存只有 32袋（8 + 24）
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.findFullPackBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 100, SourceType.SALE_OUT, "SO-004", 1L, "测试员工"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.INSUFFICIENT_STOCK);

        // Verify no save operations
        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verify(stockTransactionRepository, never()).save(any(StockTransaction.class));
    }

    @Test
    @DisplayName("FIFO 出库 - 产品不存在")
    void fifoOutbound_ProductNotFound() {
        // Given
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            999L, 10, SourceType.SALE_OUT, "SO-005", 1L, "测试员工"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("FIFO 出库 - 检测到过期批次")
    void fifoOutbound_ExpiredBatchDetected() {
        // Given - 有过期批次
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(expiredBatch));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 5, SourceType.SALE_OUT, "SO-006", 1L, "测试员工"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.EXPIRED_BATCH_FOUND);
    }

    // ========== 零头优先策略测试 ==========

    @Test
    @DisplayName("零头优先 - 优先使用散货批次")
    void looseItemFirst_PreferLooseBatch() {
        // Given - 有散货和整箱，请求量可以由散货满足
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(looseBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        inventoryBatchService.outboundWithFifo(
            1L, 5, SourceType.SALE_OUT, "SO-007", 1L, "测试员工"
        );

        // Then - 只查询了散货批次，没有查询整箱批次
        verify(inventoryBatchRepository).findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true);
        verify(inventoryBatchRepository, never()).findFullPackBatchesByProductOrderByExpiryDateAsc(anyLong(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("零头优先 - 散货不足时才拆整箱")
    void looseItemFirst_UnpackOnlyWhenNecessary() {
        // Given - 散货 8袋，请求 10袋，需要拆 1袋
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.findFullPackBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(fullPackBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 10, SourceType.SALE_OUT, "SO-008", 1L, "测试员工"
        );

        // Then
        assertThat(transactions).hasSize(2);
        assertThat(looseBatch.getQuantity()).isEqualTo(0);  // 散货用完
        assertThat(fullPackBatch.getQuantity()).isEqualTo(22);  // 24 - 2 = 22
    }

    // ========== 多批次出库测试 ==========

    @Test
    @DisplayName("多批次出库 - 按有效期排序")
    void multipleBatches_SortedByExpiryDate() {
        // Given - 2个散货批次，不同有效期
        InventoryBatch looseBatch1 = InventoryBatch.builder()
            .id(1L)
            .batchCode("BATCH001")
            .product(testProduct)
            .quantity(5)
            .expiryDate(LocalDate.now().plusMonths(2))  // 较早过期
            .active(true)
            .build();

        InventoryBatch looseBatch2 = InventoryBatch.builder()
            .id(2L)
            .batchCode("BATCH002")
            .product(testProduct)
            .quantity(5)
            .expiryDate(LocalDate.now().plusMonths(4))  // 较晚过期
            .active(true)
            .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Arrays.asList(looseBatch1, looseBatch2));  // 已按有效期排序
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When - 请求 8袋
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 8, SourceType.SALE_OUT, "SO-009", 1L, "测试员工"
        );

        // Then - 先用完第一个批次，再用第二个批次
        assertThat(transactions).hasSize(2);
        assertThat(looseBatch1.getQuantity()).isEqualTo(0);  // 第一个批次用完
        assertThat(looseBatch2.getQuantity()).isEqualTo(2);  // 第二个批次剩余 2袋
    }

    // ========== 边界情况测试 ==========

    @Test
    @DisplayName("出库数量为 0")
    void outbound_ZeroQuantity() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 0, SourceType.SALE_OUT, "SO-010", 1L, "测试员工"
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("出库数量为负数")
    void outbound_NegativeQuantity() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, -10, SourceType.SALE_OUT, "SO-011", 1L, "测试员工"
        )).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("没有任何批次")
    void outbound_NoBatches() {
        // Given
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.findFullPackBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> inventoryBatchService.outboundWithFifo(
            1L, 10, SourceType.SALE_OUT, "SO-012", 1L, "测试员工"
        ))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("批次数量刚好满足请求")
    void outbound_ExactMatch() {
        // Given - 散货 8袋，请求 8袋
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(inventoryBatchRepository.findLooseBatchesByProductOrderByExpiryDateAsc(1L, 12, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenReturn(looseBatch);
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenReturn(new StockTransaction());

        // When
        List<StockTransaction> transactions = inventoryBatchService.outboundWithFifo(
            1L, 8, SourceType.SALE_OUT, "SO-013", 1L, "测试员工"
        );

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(looseBatch.getQuantity()).isEqualTo(0);  // 刚好用完
    }
}
