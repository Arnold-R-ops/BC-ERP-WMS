package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSpuRepository;
import com.wms.system.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InventoryQueryController 集成测试
 *
 * 测试范围：
 * 1. GET /api/inventory/summary - SKU 聚合视图
 * 2. GET /api/inventory/details/{skuId} - 批次详情视图
 * 3. GET /api/inventory/location/{locationCode} - 库位反查视图
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("库存查询控制器集成测试")
class InventoryQueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch testBatch1;
    private InventoryBatch testBatch2;

    @BeforeEach
    void setUp() {
        // 清理数据
        inventoryBatchRepository.deleteAll();
        locationRepository.deleteAll();
        productRepository.deleteAll();
        productSpuRepository.deleteAll();
        warehouseRepository.deleteAll();

        // 创建测试仓库
        testWarehouse = Warehouse.builder()
            .code("WH01")
            .name("测试仓库")
            .address("测试地址")
            .isActive(true)
            .build();
        testWarehouse = warehouseRepository.save(testWarehouse);

        // 创建测试库位
        testLocation = Location.builder()
            .warehouse(testWarehouse)
            .warehouseCode("WH01")
            .zone(Zone.ZONE_A)
            .shelfNumber("A-01")
            .positionNumber("001")
            .enabled(true)
            .build();
        testLocation = locationRepository.save(testLocation);

        // 创建测试 SPU
        ProductSpu testSpu = ProductSpu.builder()
            .spuCode("SPU-TEA-001")
            .spuName("茶叶")
            .category("饮品")
            .description("各类茶叶产品")
            .build();
        testSpu = productSpuRepository.save(testSpu);

        // 创建测试产品
        testProduct = Product.builder()
            .spu(testSpu)  // 关联 SPU
            .barcode("6901234567890")
            .name("测试茶叶")
            .skuName("绿茶-箱装")  // V3.3 必填字段
            .packUnit("袋")
            .conversionRate(12)  // 1箱 = 12袋
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new java.math.BigDecimal("99.99"))
            .enabled(true)
            .build();
        testProduct = productRepository.save(testProduct);

        // 创建测试批次1（整箱：24袋 = 2箱）
        testBatch1 = InventoryBatch.builder()
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(24)
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build();
        testBatch1 = inventoryBatchRepository.save(testBatch1);

        // 创建测试批次2（散货：8袋）
        testBatch2 = InventoryBatch.builder()
            .batchCode("BATCH002")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(8)
            .initialQuantity(8)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .build();
        testBatch2 = inventoryBatchRepository.save(testBatch2);
    }

    // ========== 第一层：SKU 聚合视图测试 ==========

    @Test
    @DisplayName("GET /api/inventory/summary - 获取库存汇总")
    void testGetSummary_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].productId").value(testProduct.getId()))
            .andExpect(jsonPath("$.content[0].skuInfo.name").value("测试茶叶"))
            .andExpect(jsonPath("$.content[0].skuInfo.skuCode").value("6901234567890"))
            .andExpect(jsonPath("$.content[0].displayQuantity").value("2箱 + 8袋"))
            .andExpect(jsonPath("$.content[0].stockStatus").value("LOW_STOCK"))  // 32 < 50
            .andExpect(jsonPath("$.content[0].warehouseNames", hasSize(1)))
            .andExpect(jsonPath("$.content[0].warehouseNames[0]").value("测试仓库"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/inventory/summary - 带搜索关键词")
    void testGetSummary_WithSearch() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("search", "茶叶")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].skuInfo.name").value("测试茶叶"));
    }

    @Test
    @DisplayName("GET /api/inventory/summary - 搜索不存在的产品")
    void testGetSummary_SearchNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("search", "不存在的产品")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/inventory/summary - 测试分页")
    void testGetSummary_Pagination() throws Exception {
        // 创建第二个 SPU
        ProductSpu spu2 = ProductSpu.builder()
            .spuCode("SPU-COFFEE-001")
            .spuName("咖啡")
            .category("饮品")
            .description("各类咖啡产品")
            .build();
        spu2 = productSpuRepository.save(spu2);

        // 创建第二个产品
        Product product2 = Product.builder()
            .spu(spu2)  // 关联 SPU
            .barcode("6901234567891")
            .name("测试咖啡")
            .skuName("咖啡-袋装")  // V3.3 必填字段
            .packUnit("袋")
            .conversionRate(10)
            .safetyStock(30)
            .minStock(20)
            .leadTime(5)
            .unitPrice(new java.math.BigDecimal("79.99"))
            .enabled(true)
            .build();
        productRepository.save(product2);

        // 测试第一页（每页1条）
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "0")
                .param("size", "1")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));

        // 测试第二页
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "1")
                .param("size", "1")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/inventory/summary - 测试库存充足状态")
    void testGetSummary_SufficientStock() throws Exception {
        // 修改安全库存为 30（当前库存 32 > 30）
        testProduct.setSafetyStock(30);
        productRepository.save(testProduct);

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].stockStatus").value("SUFFICIENT"));
    }

    @Test
    @DisplayName("GET /api/inventory/summary - 测试只有整箱")
    void testGetSummary_OnlyFullBoxes() throws Exception {
        // 删除散货批次
        inventoryBatchRepository.delete(testBatch2);

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].displayQuantity").value("2箱 + 0袋"));
    }

    @Test
    @DisplayName("GET /api/inventory/summary - 测试只有散货")
    void testGetSummary_OnlyLooseItems() throws Exception {
        // 删除整箱批次
        inventoryBatchRepository.delete(testBatch1);

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].displayQuantity").value("0箱 + 8袋"));
    }

    // ========== 第二层：批次详情视图测试 ==========

    @Test
    @DisplayName("GET /api/inventory/details/{skuId} - 获取批次详情")
    void testGetDetails_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/details/{skuId}", testProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            // 验证按有效期降序排列（最新鲜的在前）
            .andExpect(jsonPath("$[0].batchCode").value("BATCH001"))  // 6个月后过期
            .andExpect(jsonPath("$[0].quantity").value(24))
            .andExpect(jsonPath("$[0].packageStatus").value("📦 整箱"))
            .andExpect(jsonPath("$[0].warehouseName").value("测试仓库"))
            .andExpect(jsonPath("$[0].locationCode").value(testLocation.getLocationCode()))
            .andExpect(jsonPath("$[1].batchCode").value("BATCH002"))  // 3个月后过期
            .andExpect(jsonPath("$[1].quantity").value(8))
            .andExpect(jsonPath("$[1].packageStatus").value("📭 散货"));
    }

    @Test
    @DisplayName("GET /api/inventory/details/{skuId} - 产品无批次")
    void testGetDetails_NoBatches() throws Exception {
        // 创建一个空 SPU
        ProductSpu emptySpu = ProductSpu.builder()
            .spuCode("SPU-EMPTY-001")
            .spuName("空产品系列")
            .category("测试")
            .description("测试用空产品")
            .build();
        emptySpu = productSpuRepository.save(emptySpu);

        // 创建一个没有批次的产品
        Product emptyProduct = Product.builder()
            .spu(emptySpu)  // 关联 SPU
            .barcode("6901234567892")
            .name("空产品")
            .skuName("空产品-SKU")  // V3.3 必填字段
            .packUnit("袋")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new java.math.BigDecimal("99.99"))
            .enabled(true)
            .build();
        emptyProduct = productRepository.save(emptyProduct);

        mockMvc.perform(get("/api/inventory/details/{skuId}", emptyProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/inventory/details/{skuId} - 产品不存在")
    void testGetDetails_ProductNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/details/{skuId}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/inventory/details/{skuId} - 验证有效期排序")
    void testGetDetails_ExpiryDateSorting() throws Exception {
        // 创建第三个批次（最远有效期）
        InventoryBatch batch3 = InventoryBatch.builder()
            .batchCode("BATCH003")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(12)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().plusMonths(12))  // 最远有效期
            .active(true)
            .build();
        inventoryBatchRepository.save(batch3);

        mockMvc.perform(get("/api/inventory/details/{skuId}", testProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            // 验证降序排列：12个月 > 6个月 > 3个月
            .andExpect(jsonPath("$[0].batchCode").value("BATCH003"))
            .andExpect(jsonPath("$[1].batchCode").value("BATCH001"))
            .andExpect(jsonPath("$[2].batchCode").value("BATCH002"));
    }

    // ========== 第三层：库位反查视图测试 ==========

    @Test
    @DisplayName("GET /api/inventory/location/{locationCode} - 获取库位视图")
    void testGetLocationView_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/location/{locationCode}", testLocation.getLocationCode())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationCode").value(testLocation.getLocationCode()))
            .andExpect(jsonPath("$.warehouseName").value("测试仓库"))
            .andExpect(jsonPath("$.batches", hasSize(2)))
            .andExpect(jsonPath("$.batches[0].batchCode").value("BATCH001"))
            .andExpect(jsonPath("$.batches[1].batchCode").value("BATCH002"));
    }

    @Test
    @DisplayName("GET /api/inventory/location/{locationCode} - 库位不存在")
    void testGetLocationView_LocationNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/location/{locationCode}", "INVALID-CODE")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isInternalServerError());  // 会抛出 IllegalArgumentException
    }

    @Test
    @DisplayName("GET /api/inventory/location/{locationCode} - 库位无批次")
    void testGetLocationView_NoBatches() throws Exception {
        // 创建一个空库位
        Location emptyLocation = Location.builder()
            .warehouse(testWarehouse)
            .warehouseCode("WH01")
            .zone(Zone.ZONE_B)
            .shelfNumber("B-01")
            .positionNumber("001")
            .enabled(true)
            .build();
        emptyLocation = locationRepository.save(emptyLocation);

        mockMvc.perform(get("/api/inventory/location/{locationCode}", emptyLocation.getLocationCode())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationCode").value(emptyLocation.getLocationCode()))
            .andExpect(jsonPath("$.warehouseName").value("测试仓库"))
            .andExpect(jsonPath("$.batches", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/inventory/location/{locationCode} - 多个产品在同一库位")
    void testGetLocationView_MultipleProducts() throws Exception {
        // 创建第二个产品
        Product product2 = Product.builder()
            .barcode("6901234567893")
            .name("测试咖啡")
            .skuName("咖啡-袋装2")  // V3.3 必填字段
            .packUnit("袋")
            .conversionRate(10)
            .safetyStock(30)
            .minStock(20)
            .leadTime(5)
            .unitPrice(new java.math.BigDecimal("79.99"))
            .enabled(true)
            .build();
        product2 = productRepository.save(product2);

        // 在同一库位创建第二个产品的批次
        InventoryBatch batch3 = InventoryBatch.builder()
            .batchCode("BATCH003")
            .product(product2)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(20)
            .initialQuantity(20)
            .expiryDate(LocalDate.now().plusMonths(4))
            .active(true)
            .build();
        inventoryBatchRepository.save(batch3);

        mockMvc.perform(get("/api/inventory/location/{locationCode}", testLocation.getLocationCode())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batches", hasSize(3)));  // 3个批次（2个茶叶 + 1个咖啡）
    }

    // ========== 边界情况测试 ==========

    @Test
    @DisplayName("GET /api/inventory/summary - 空数据库")
    void testGetSummary_EmptyDatabase() throws Exception {
        // 清空所有数据
        inventoryBatchRepository.deleteAll();
        productRepository.deleteAll();

        mockMvc.perform(get("/api/inventory/summary")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/inventory/summary - 测试无效的分页参数")
    void testGetSummary_InvalidPagination() throws Exception {
        mockMvc.perform(get("/api/inventory/summary")
                .param("page", "-1")
                .param("size", "0")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk());  // Spring 会自动修正为有效值
    }

    @Test
    @DisplayName("GET /api/inventory/details/{skuId} - 无效的 SKU ID")
    void testGetDetails_InvalidSkuId() throws Exception {
        mockMvc.perform(get("/api/inventory/details/{skuId}", "invalid")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());  // 类型转换错误
    }
}
