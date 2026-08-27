package com.wms.system.controller;

import com.wms.system.config.TestSecurityConfig;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.CategoryRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.support.TestCatalogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestSecurityConfig.class)
@DisplayName("InventoryBatchController integration")
class InventoryBatchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductSkuRepository productSkuRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    private ProductSku testProduct;
    private InventoryBatch looseBatch;
    private InventoryBatch fullPackBatch;

    @BeforeEach
    void setUp() {
        inventoryBatchRepository.deleteAll();
        locationRepository.deleteAll();
        productSkuRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();

        Warehouse warehouse = warehouseRepository.save(Warehouse.builder()
            .code("WH01")
            .name("Main Warehouse")
            .address("Test Address 1")
            .isActive(true)
            .build());

        Location location = locationRepository.save(Location.builder()
            .warehouse(warehouse)
            .warehouseCode(warehouse.getCode())
            .zone(Zone.ZONE_A)
            .shelfNumber("A-01")
            .positionNumber("001")
            .enabled(true)
            .build());

        Product product = productRepository.save(Product.builder()
            .productCode("SPU-TEA-001")
            .productName("Test Tea SPU")
            .category(TestCatalogFactory.saveLeafCategory(categoryRepository))
            .description("Test product spu description")
            .build());

        testProduct = productSkuRepository.save(ProductSku.builder()
            .skuCode(TestCatalogFactory.nextSkuCode())
            .product(product)
            .barcode("6901234567890")
            .name("Test Green Tea")
            .skuName("Test Green Tea SKU")
            .packUnit("BOX")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build());

        looseBatch = inventoryBatchRepository.save(InventoryBatch.builder()
            .batchCode("BATCH001")
            .productSku(testProduct)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(8)
            .initialQuantity(12)
            .expiryDate(LocalDate.now().plusMonths(3))
            .active(true)
            .build());

        fullPackBatch = inventoryBatchRepository.save(InventoryBatch.builder()
            .batchCode("BATCH002")
            .productSku(testProduct)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(24)
            .initialQuantity(24)
            .expiryDate(LocalDate.now().plusMonths(6))
            .active(true)
            .build());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("retired direct batch write routes have no handler")
    void directBatchWriteRoutesAreNotMapped() throws Exception {
        assertRetiredRoute(post("/api/inventory/batches/outbound"));
        assertRetiredRoute(put("/api/inventory/batches/BATCH001/adjust"));

        assertThat(inventoryBatchRepository.findById(looseBatch.getId()).orElseThrow().getQuantity())
            .isEqualTo(8);
        assertThat(inventoryBatchRepository.findById(fullPackBatch.getId()).orElseThrow().getQuantity())
            .isEqualTo(24);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("returns the aggregate stock for an existing SKU")
    void getTotalStockSuccess() throws Exception {
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productSkuId}", testProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productSkuId").value(testProduct.getId()))
            .andExpect(jsonPath("$.totalStock").value(32));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("returns zero when an existing SKU has no batches")
    void getTotalStockNoStock() throws Exception {
        ProductSku emptyProduct = productSkuRepository.save(ProductSku.builder()
            .skuCode(TestCatalogFactory.nextSkuCode())
            .product(testProduct.getProduct())
            .barcode("6901234567891")
            .name("Empty Stock ProductSku")
            .skuName("Empty Stock SKU")
            .packUnit("BOX")
            .conversionRate(12)
            .safetyStock(50)
            .minStock(30)
            .leadTime(7)
            .unitPrice(new BigDecimal("99.99"))
            .enabled(true)
            .build());

        mockMvc.perform(get("/api/inventory/batches/total-stock/{productSkuId}", emptyProduct.getId())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productSkuId").value(emptyProduct.getId()))
            .andExpect(jsonPath("$.totalStock").value(0));
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"TENANT_ADMIN"})
    @DisplayName("returns not found when the SKU does not exist")
    void getTotalStockProductNotFound() throws Exception {
        mockMvc.perform(get("/api/inventory/batches/total-stock/{productSkuId}", 99999L)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    private void assertRetiredRoute(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {
        ResultActions result = mockMvc.perform(request);
        result.andExpect(response -> assertThat(response.getResponse().getStatus()).isIn(404, 405));
    }
}
