package com.wms.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.*;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InventoryBatchController 集成测试
 *
 * 测试范围：
 * 1. POST /api/inventory/batches/outbound - FIFO 出库
 * 2. GET /api/inventory/batches/total-stock/{productId} - 获取总库存
 * 3. 零头优先策略验证
 * 4. 库存不足异常处理
 * 5. 过期批次检测
 *
 * @author WMS Team
 * @since 2026-01-28
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("库存批次控制器集成测试")
class InventoryBatchControllerIntegrationTest {

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

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    private Product testProduct;
    private Warehouse testWarehouse;
    private Location testLocation;
    private InventoryBatch looseBatch;
    private InventoryBatch fullPackBatch;

    @BeforeEach
    void setUp() {
        // 清理数据
        stockTransactionRepository.deleteAll();
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

        // 创建测试产品（1箱 = 12袋）
        testProduct = Product.builder()
            .spu(testSpu)  // 关联 SPU
            .barcode("6901234567890")
            .name("测试茶叶")
            .skuName("绿茶-箱装")  // V3.3 必填字段
            .packUnit("袋")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build();
        testProduct = productRepository.save(testProduct);

        // 创建散货批次（8袋）
        looseBatch = InventoryBatch.builder()
            .batchCode("BATCH001")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(8)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .build();
        looseBatch = inventoryBatchRepository.save(looseBatch);

        // 创建整箱批次（24袋 = 2箱）
        fullPackBatch = InventoryBatch.builder()
            .batchCode("BATCH002")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(24)
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build();
        fullPackBatch = inventoryBatchRepository.save(fullPackBatch);
    }

    // ========== FIFO 出库测试 ==========

    @Test
    @DisplayName("POST /api/inventory/batches/outbound - FIFO 出库成功（只用散货）")
    void fifoOutbound_OnlyLooseBatch() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-001")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionCount").value(1))
            .andExpect(jsonPath("$.transactionIds", hasSize(1)));

        // 验证库存变化
        InventoryBatch updatedBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        assertThat(updatedBatch.getQuantity()).isEqualTo(3);  // 8 - 5 = 3
    }

    @Test
    @DisplayName("POST /api/inventory/batches/outbound - FIFO 出库（散货不足，拆整箱）")
    void fifoOutbound_LooseInsufficientNeedUnpack() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "20")  // 需要 8袋散货 + 12袋整箱
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-002")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionCount").value(2));  // 2个批次

        // 验证库存变化
        InventoryBatch updatedLooseBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        InventoryBatch updatedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(updatedLooseBatch.getQuantity()).isEqualTo(0);  // 散货用完
        assertThat(updatedFullPackBatch.getQuantity()).isEqualTo(12);  // 24 - 12 = 12
    }

    @Test
    @DisplayName("POST /api/inventory/batches/outbound - 库存不足")
    void fifoOutbound_InsufficientStock() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "100")  // 总库存只有 32袋
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-003")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());

        // 验证库存未变化
        InventoryBatch unchangedLooseBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        InventoryBatch unchangedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(unchangedLooseBatch.getQuantity()).isEqualTo(8);  // 未变化
        assertThat(unchangedFullPackBatch.getQuantity()).isEqualTo(24);  // 未变化
    }

    @Test
    @DisplayName("POST /api/inventory/batches/outbound - 产品不存在")
    void fifoOutbound_ProductNotFound() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", "99999")
                .param("quantity", "10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-004")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/inventory/batches/outbound - 缺少必填参数")
    void fifoOutbound_MissingRequiredParams() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                // 缺少 quantity 参数
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-005")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/inventory/batches/outbound - 数量为负数")
    void fifoOutbound_NegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "-10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-006")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/inventory/batches/outbound - 过期批次检测")
    void fifoOutbound_ExpiredBatchDetected() throws Exception {
        // 创建过期批次
        InventoryBatch expiredBatch = InventoryBatch.builder()
            .batchCode("BATCH003")
            .product(testProduct)
            .location(testLocation)
            .locationCode(testLocation.getLocationCode())
            .quantity(12)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().minusDays(1))  // 已过期
            .active(true)
            .build();
        inventoryBatchRepository.save(expiredBatch);

        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-007")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    // ========== 获取总库存测试 ==========

    @Test
    @DisplayName("GET /api/inventory/batches/total-stock/{productId} - 获取总库存")
    void getTotalStock_Success() throws Exception {
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", testProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(testProduct.getId()))
            .andExpect(jsonPath("$.totalStock").value(32));  // 8 + 24 = 32
    }

    @Test
    @DisplayName("GET /api/inventory/batches/total-stock/{productId} - 产品无库存")
    void getTotalStock_NoStock() throws Exception {
        // 创建一个没有库存的产品
        Product emptyProduct = Product.builder()
            .barcode("6901234567891")
            .name("空产品")
            .skuName("空产品-SKU")  // V3.3 必填字段
            .packUnit("袋")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build();
        emptyProduct = productRepository.save(emptyProduct);

        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", emptyProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(emptyProduct.getId()))
            .andExpect(jsonPath("$.totalStock").value(0));
    }

    @Test
    @DisplayName("GET /api/inventory/batches/total-stock/{productId} - 产品不存在")
    void getTotalStock_ProductNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    // ========== 零头优先策略验证 ==========

    @Test
    @DisplayName("零头优先策略 - 优先使用散货批次")
    void looseItemFirst_PreferLooseBatch() throws Exception {
        // 请求 5袋，应该只使用散货批次
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-008")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionCount").value(1));  // 只用了1个批次

        // 验证整箱批次未动
        InventoryBatch unchangedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(unchangedFullPackBatch.getQuantity()).isEqualTo(24);  // 未变化
    }

    @Test
    @DisplayName("零头优先策略 - 散货不足时才拆整箱")
    void looseItemFirst_UnpackOnlyWhenNecessary() throws Exception {
        // 请求 10袋，散货 8袋不足，需要拆 2袋
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-009")
                .param("operatorId", "1")
                .param("operatorName", "测试员工")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionCount").value(2));  // 用了2个批次

        // 验证库存变化
        InventoryBatch updatedLooseBatch = inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow();
        InventoryBatch updatedFullPackBatch = inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow();
        assertThat(updatedLooseBatch.getQuantity()).isEqualTo(0);  // 散货用完
        assertThat(updatedFullPackBatch.getQuantity()).isEqualTo(22);  // 24 - 2 = 22
    }

    // ========== 业务流程测试 ==========

    @Test
    @DisplayName("完整业务流程 - 多次出库直到库存耗尽")
    void completeWorkflow_MultipleOutboundsUntilEmpty() throws Exception {
        // 第一次出库：5袋（使用散货）
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "5")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-010")
                .param("operatorId", "1")
                .param("operatorName", "测试员工"))
            .andExpect(status().isOk());

        // 第二次出库：10袋（使用剩余散货 + 部分整箱）
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "10")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-011")
                .param("operatorId", "1")
                .param("operatorName", "测试员工"))
            .andExpect(status().isOk());

        // 第三次出库：17袋（使用剩余整箱）
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "17")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-012")
                .param("operatorId", "1")
                .param("operatorName", "测试员工"))
            .andExpect(status().isOk());

        // 验证总库存为 0
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productId}", testProduct.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalStock").value(0));

        // 第四次出库：应该失败（库存不足）
        mockMvc.perform(post("/api/inventory/batches/outbound")
                .param("productId", testProduct.getId().toString())
                .param("quantity", "1")
                .param("sourceType", "SALE_OUT")
                .param("sourceOrderId", "SO-013")
                .param("operatorId", "1")
                .param("operatorName", "测试员工"))
            .andExpect(status().isBadRequest());
    }
}
