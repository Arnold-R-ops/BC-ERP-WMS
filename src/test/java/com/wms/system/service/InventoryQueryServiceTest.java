package com.wms.system.service;

import com.wms.system.dto.inventory.InventoryDetailDto;
import com.wms.system.dto.inventory.InventorySummaryDto;
import com.wms.system.dto.inventory.LocationViewDto;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.StockStatus;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * InventoryQueryService 单元测试
 *
 * 测试范围：
 * 1. 第一层：SKU 聚合视图
 * 2. 第二层：批次详情视图
 * 3. 第三层：库位反查视图
 * 4. 智能数量计算
 * 5. 预警状态计算
 * 6. 有效期排序
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("库存查询服务单元测试")
class InventoryQueryServiceTest {

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private InventoryQueryService inventoryQueryService;

    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch testBatch1;
    private InventoryBatch testBatch2;

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

        // 创建测试产品
        testProduct = Product.builder()
            .id(1L)
            .barcode("6901234567890")
            .name("测试茶叶")
            .spu(ProductSpu.builder().id(1L).spuCode("SPU001").spuName("茶叶").build())
            .packUnit("袋")
            .conversionRate(12)  // 1箱 = 12袋
            .safetyStock(50)
            .build();

        // 创建测试批次1（整箱：24袋 = 2箱）
        testBatch1 = InventoryBatch.builder()
            .id(1L)
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(24)
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build();

        // 创建测试批次2（散货：8袋）
        testBatch2 = InventoryBatch.builder()
            .id(2L)
            .batchCode("BATCH002")
            .product(testProduct)
            .location(testLocation)
            .locationCode("WH01-ZONE_A-A-01-001")
            .quantity(8)
            .initialQuantity(8)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .build();
    }

    // ========== 第一层：SKU 聚合视图测试 ==========

    @Test
    @DisplayName("测试 getSummary - 正常情况")
    void testGetSummary_Success() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(pageable, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);

        InventorySummaryDto summary = result.getContent().get(0);
        assertThat(summary.getProductId()).isEqualTo(1L);
        assertThat(summary.getSkuInfo().getName()).isEqualTo("测试茶叶");
        assertThat(summary.getSkuInfo().getSkuCode()).isEqualTo("6901234567890");
        assertThat(summary.getDisplayQuantity()).isEqualTo("2箱 + 8袋");  // 24/12=2箱, 8袋散货
        assertThat(summary.getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);  // 32 < 50
        assertThat(summary.getFurthestExpiryDate()).isEqualTo(LocalDate.now().plusMonths(6));
    }

    @Test
    @DisplayName("测试 getSummary - 带搜索关键词")
    void testGetSummary_WithSearch() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findByNameContaining("茶叶"))
            .thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(pageable, "茶叶");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).findByNameContaining("茶叶");
    }

    @Test
    @DisplayName("测试智能数量显示 - 只有整箱")
    void testDisplayQuantity_OnlyFullBoxes() {
        // Given
        InventoryBatch fullBoxBatch = InventoryBatch.builder()
            .quantity(36)  // 3箱 = 36袋
            .product(testProduct)
            .active(true)
            .build();

        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(fullBoxBatch));
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(36);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("3箱 + 0袋");
    }

    @Test
    @DisplayName("测试智能数量显示 - 只有散货")
    void testDisplayQuantity_OnlyLooseItems() {
        // Given
        InventoryBatch looseBatch = InventoryBatch.builder()
            .quantity(8)  // 8袋散货
            .product(testProduct)
            .active(true)
            .build();

        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(looseBatch));
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(8);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("0箱 + 8袋");
    }

    @Test
    @DisplayName("测试智能数量显示 - 整箱加散货")
    void testDisplayQuantity_MixedBoxesAndLoose() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));  // 24袋(2箱) + 8袋
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("2箱 + 8袋");
    }

    @Test
    @DisplayName("测试预警状态 - 库存充足")
    void testStockStatus_Sufficient() {
        // Given
        testProduct.setSafetyStock(30);  // 安全库存 30
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);  // 实际库存 32

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.SUFFICIENT);
    }

    @Test
    @DisplayName("测试预警状态 - 库存不足")
    void testStockStatus_LowStock() {
        // Given
        testProduct.setSafetyStock(50);  // 安全库存 50
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(32);  // 实际库存 32

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
    }

    // ========== 第二层：批次详情视图测试 ==========

    @Test
    @DisplayName("测试 getDetailsBySkuId - 正常情况")
    void testGetDetailsBySkuId_Success() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result).hasSize(2);

        // 验证按有效期降序排列（最新鲜的在前）
        assertThat(result.get(0).getBatchCode()).isEqualTo("BATCH001");  // 6个月后过期
        assertThat(result.get(0).getExpiryDate()).isEqualTo(LocalDate.now().plusMonths(6));
        assertThat(result.get(1).getBatchCode()).isEqualTo("BATCH002");  // 3个月后过期
        assertThat(result.get(1).getExpiryDate()).isEqualTo(LocalDate.now().plusMonths(3));
    }

    @Test
    @DisplayName("测试批次详情 - 整箱状态显示")
    void testBatchDetail_FullBoxStatus() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(testBatch1));  // 24袋 = 2箱

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result.get(0).getPackageStatus()).isEqualTo("📦 整箱");
    }

    @Test
    @DisplayName("测试批次详情 - 散货状态显示")
    void testBatchDetail_LooseStatus() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.singletonList(testBatch2));  // 8袋散货

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result.get(0).getPackageStatus()).isEqualTo("📭 散货");
    }

    @Test
    @DisplayName("测试批次详情 - 空列表")
    void testGetDetailsBySkuId_EmptyList() {
        // Given
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.emptyList());

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result).isEmpty();
    }

    // ========== 第三层：库位反查视图测试 ==========

    @Test
    @DisplayName("测试 getLocationView - 正常情况")
    void testGetLocationView_Success() {
        // Given
        String locationCode = "WH01-ZONE_A-A-01-001";
        when(locationRepository.findByLocationCode(locationCode))
            .thenReturn(Optional.of(testLocation));
        when(inventoryBatchRepository.findByLocationCodeAndActive(locationCode, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));

        // When
        LocationViewDto result = inventoryQueryService.getLocationView(locationCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLocationCode()).isEqualTo(locationCode);
        assertThat(result.getWarehouseName()).isEqualTo("测试仓库");
        assertThat(result.getBatches()).hasSize(2);
    }

    @Test
    @DisplayName("测试 getLocationView - 库位不存在")
    void testGetLocationView_LocationNotFound() {
        // Given
        String locationCode = "INVALID-CODE";
        when(locationRepository.findByLocationCode(locationCode))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> inventoryQueryService.getLocationView(locationCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("库位不存在");
    }

    @Test
    @DisplayName("测试 getLocationView - 库位无批次")
    void testGetLocationView_NoBatches() {
        // Given
        String locationCode = "WH01-ZONE_A-A-01-001";
        when(locationRepository.findByLocationCode(locationCode))
            .thenReturn(Optional.of(testLocation));
        when(inventoryBatchRepository.findByLocationCodeAndActive(locationCode, true))
            .thenReturn(Collections.emptyList());

        // When
        LocationViewDto result = inventoryQueryService.getLocationView(locationCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getBatches()).isEmpty();
    }

    // ========== 边界情况测试 ==========

    @Test
    @DisplayName("测试空库存产品")
    void testEmptyInventory() {
        // Given
        when(productRepository.findAll()).thenReturn(Collections.singletonList(testProduct));
        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.sumQuantityByProduct(1L)).thenReturn(null);

        // When
        Page<InventorySummaryDto> result = inventoryQueryService.getSummary(PageRequest.of(0, 20), null);

        // Then
        assertThat(result.getContent().get(0).getDisplayQuantity()).isEqualTo("0箱 + 0袋");
        assertThat(result.getContent().get(0).getStockStatus()).isEqualTo(StockStatus.LOW_STOCK);
        assertThat(result.getContent().get(0).getFurthestExpiryDate()).isNull();
    }

    @Test
    @DisplayName("测试有效期为null的批次")
    void testBatchWithNullExpiryDate() {
        // Given
        testBatch1.setExpiryDate(null);
        testBatch2.setExpiryDate(null);

        when(inventoryBatchRepository.findByProductIdAndActive(1L, true))
            .thenReturn(Arrays.asList(testBatch1, testBatch2));

        // When
        List<InventoryDetailDto> result = inventoryQueryService.getDetailsBySkuId(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getExpiryDate()).isNull();
        assertThat(result.get(1).getExpiryDate()).isNull();
    }

    @Test
    @DisplayName("测试分页功能")
    void testPagination() {
        // Given
        ProductSpu spu = ProductSpu.builder().id(1L).spuCode("SPU001").spuName("测试SPU").build();
        Product product1 = Product.builder().id(1L).barcode("001").name("产品1").spu(spu).packUnit("袋").conversionRate(12).safetyStock(50).build();
        Product product2 = Product.builder().id(2L).barcode("002").name("产品2").spu(spu).packUnit("袋").conversionRate(12).safetyStock(50).build();
        Product product3 = Product.builder().id(3L).barcode("003").name("产品3").spu(spu).packUnit("袋").conversionRate(12).safetyStock(50).build();

        when(productRepository.findAll()).thenReturn(Arrays.asList(product1, product2, product3));
        when(inventoryBatchRepository.findByProductIdAndActive(anyLong(), eq(true)))
            .thenReturn(Collections.emptyList());
        when(inventoryBatchRepository.sumQuantityByProduct(anyLong())).thenReturn(0);

        // When - 第一页，每页2条
        Page<InventorySummaryDto> page1 = inventoryQueryService.getSummary(PageRequest.of(0, 2), null);

        // Then
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(3);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        // When - 第二页
        Page<InventorySummaryDto> page2 = inventoryQueryService.getSummary(PageRequest.of(1, 2), null);

        // Then
        assertThat(page2.getContent()).hasSize(1);
    }
}
