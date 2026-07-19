package com.wms.system.repository;

import com.wms.system.entity.Category;
import com.wms.system.entity.Inventory;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Product;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("ProductSkuRepository Tests")
class ProductSkuRepositoryTest {

    @Autowired private ProductSkuRepository productSkuRepository;
    @Autowired private TestEntityManager entityManager;

    private Product spu;
    private ProductSku productTea;
    private ProductSku productJuice;
    private ProductSku disabledProduct;

    @BeforeEach
    void setUp() {
        Category rootCategory = Category.builder()
                .categoryCode("BEVERAGES")
                .categoryName("Beverages")
                .enabled(true)
                .build();
        entityManager.persist(rootCategory);

        Category leafCategory = Category.builder()
                .categoryCode("BEVERAGES-GENERAL")
                .categoryName("General Beverages")
                .parent(rootCategory)
                .enabled(true)
                .build();
        entityManager.persist(leafCategory);

        spu = Product.builder()
                .productCode("SPU-DRINK")
                .productName("Drinks")
                .category(leafCategory)
                .enabled(true)
                .build();
        entityManager.persist(spu);

        productTea = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .product(spu)
                .skuCode("SKU00000001")
                .skuName("Green Tea Box")
                .barcode("BAR-TEA-001")
                .name("Premium Green Tea 12-Box")
                .unitPrice(new BigDecimal("25.00"))
                .minStock(100)
                .leadTime(7)
                .enabled(true)
                .supplier("SupplierA")
                .isDeleted(false)
                .build();

        productJuice = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .product(spu)
                .skuCode("SKU00000002")
                .skuName("Orange Juice")
                .barcode("BAR-JUS-002")
                .name("Fresh Orange Juice 1L")
                .unitPrice(new BigDecimal("12.00"))
                .minStock(50)
                .leadTime(3)
                .enabled(true)
                .supplier("SupplierB")
                .isDeleted(false)
                .build();

        disabledProduct = ProductSku.builder()
                .skuCode(com.wms.system.support.TestCatalogFactory.nextSkuCode())
                .product(spu)
                .skuCode("SKU00000003")
                .skuName("Old Snack")
                .barcode("BAR-SNK-003")
                .name("Discontinued Snack Pack")
                .unitPrice(new BigDecimal("5.00"))
                .minStock(0)
                .leadTime(1)
                .enabled(false)
                .supplier("SupplierA")
                .isDeleted(false)
                .build();

        entityManager.persist(productTea);
        entityManager.persist(productJuice);
        entityManager.persist(disabledProduct);
        entityManager.flush();
    }

    // ========== findByBarcode ==========

    @Test
    @DisplayName("findByBarcode - returns product when barcode exists")
    void testFindByBarcode_Found() {
        Optional<ProductSku> found = productSkuRepository.findByBarcode("BAR-TEA-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Premium Green Tea 12-Box");
        assertThat(found.get().getSupplier()).isEqualTo("SupplierA");
    }

    @Test
    @DisplayName("findByBarcode - returns empty when barcode does not exist")
    void testFindByBarcode_NotFound() {
        Optional<ProductSku> found = productSkuRepository.findByBarcode("NONEXISTENT-BAR");

        assertThat(found).isEmpty();
    }

    // ========== existsByBarcode ==========

    @Test
    @DisplayName("existsByBarcode - returns true when barcode exists")
    void testExistsByBarcode_Exists() {
        boolean exists = productSkuRepository.existsByBarcode("BAR-TEA-001");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByBarcode - returns false when barcode does not exist")
    void testExistsByBarcode_NotExists() {
        boolean exists = productSkuRepository.existsByBarcode("NONEXISTENT-BAR");

        assertThat(exists).isFalse();
    }

    // ========== findByNameContaining ==========

    @Test
    @DisplayName("findByNameContaining - finds products matching keyword")
    void testFindByNameContaining_Found() {
        List<ProductSku> results = productSkuRepository.findByNameContaining("Tea");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBarcode()).isEqualTo("BAR-TEA-001");
    }

    @Test
    @DisplayName("findByNameContaining - returns empty list when no match")
    void testFindByNameContaining_NoMatch() {
        List<ProductSku> results = productSkuRepository.findByNameContaining("ZZZNOTFOUND");

        assertThat(results).isEmpty();
    }

    // ========== findByEnabledTrue ==========

    @Test
    @DisplayName("findByEnabledTrue - returns only enabled products")
    void testFindByEnabledTrue() {
        List<ProductSku> enabled = productSkuRepository.findByEnabledTrue();

        assertThat(enabled).hasSize(2);
        assertThat(enabled)
                .extracting(ProductSku::getBarcode)
                .containsExactlyInAnyOrder("BAR-TEA-001", "BAR-JUS-002");
        assertThat(enabled).allMatch(ProductSku::getEnabled);
    }

    // ========== findByProductCategoryId ==========

    @Test
    @DisplayName("findByProductCategoryId - returns SKUs inherited from the product category")
    void testFindByCategory() {
        List<ProductSku> beverages = productSkuRepository.findByProductCategoryId(spu.getCategory().getId());

        assertThat(beverages).hasSize(3);
        assertThat(beverages)
                .extracting(productSku -> productSku.getProduct().getCategory().getCategoryCode())
                .containsOnly("BEVERAGES-GENERAL");
    }

    @Test
    @DisplayName("findByProductCategoryId - returns empty for an unknown category")
    void testFindByCategory_Snacks() {
        List<ProductSku> snacks = productSkuRepository.findByProductCategoryId(Long.MAX_VALUE);

        assertThat(snacks).isEmpty();
    }

    // ========== findBySupplier ==========

    @Test
    @DisplayName("findBySupplier - returns products from given supplier")
    void testFindBySupplier() {
        List<ProductSku> supplierAProducts = productSkuRepository.findBySupplier("SupplierA");

        assertThat(supplierAProducts).hasSize(2);
        assertThat(supplierAProducts)
                .extracting(ProductSku::getSupplier)
                .containsOnly("SupplierA");
    }

    @Test
    @DisplayName("findBySupplier - returns empty when supplier has no products")
    void testFindBySupplier_NotFound() {
        List<ProductSku> results = productSkuRepository.findBySupplier("UnknownSupplier");

        assertThat(results).isEmpty();
    }

    // ========== findLowStockProducts ==========

    @Test
    @DisplayName("findLowStockProducts - returns product when stock is below minStock")
    void testFindLowStockProducts_BelowThreshold() {
        // Create warehouse → location → inventory with low quantity
        Warehouse warehouse = Warehouse.builder()
                .code("WH01")
                .name("Main Warehouse")
                .isActive(true)
                .build();
        entityManager.persist(warehouse);

        Location location = Location.builder()
                .warehouse(warehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("A01")
                .positionNumber("001")
                .enabled(true)
                .build();
        entityManager.persist(location);

        // productTea has minStock=100; inventory quantity=10 → low stock
        InventoryBatch lowInventory = InventoryBatch.builder()
                .batchCode("LOW-STOCK-BATCH")
                .productSku(productTea)
                .location(location)
                .locationCode(location.getLocationCode())
                .quantity(10)
                .initialQuantity(10)
                .reservedQuantity(0)
                .expiryDate(java.time.LocalDate.now().plusMonths(6))
                .active(true)
                .build();
        entityManager.persist(lowInventory);
        entityManager.flush();

        List<ProductSku> lowStock = productSkuRepository.findLowStockProducts();

        assertThat(lowStock).isNotEmpty();
        assertThat(lowStock)
                .extracting(ProductSku::getBarcode)
                .contains("BAR-TEA-001");
    }

    @Test
    @DisplayName("findLowStockProducts - does not return product when stock is above minStock")
    void testFindLowStockProducts_AboveThreshold() {
        // Create warehouse → location → inventory with sufficient quantity
        Warehouse warehouse = Warehouse.builder()
                .code("WH02")
                .name("Secondary Warehouse")
                .isActive(true)
                .build();
        entityManager.persist(warehouse);

        Location location = Location.builder()
                .warehouse(warehouse)
                .warehouseCode("WH02")
                .zone(Zone.ZONE_B)
                .shelfNumber("B01")
                .positionNumber("001")
                .enabled(true)
                .build();
        entityManager.persist(location);

        // productJuice has minStock=50; inventory quantity=200 → sufficient
        Inventory sufficientInventory = Inventory.builder()
                .productSku(productJuice)
                .location(location)
                .quantity(200)
                .build();
        entityManager.persist(sufficientInventory);
        entityManager.flush();

        List<ProductSku> lowStock = productSkuRepository.findLowStockProducts();

        assertThat(lowStock)
                .extracting(ProductSku::getBarcode)
                .doesNotContain("BAR-JUS-002");
    }
}
