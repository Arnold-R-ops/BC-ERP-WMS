package com.wms.system.repository;

import com.wms.system.entity.Inventory;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSpu;
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
@DisplayName("ProductRepository Tests")
class ProductRepositoryTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private TestEntityManager entityManager;

    private ProductSpu spu;
    private Product productTea;
    private Product productJuice;
    private Product disabledProduct;

    @BeforeEach
    void setUp() {
        spu = ProductSpu.builder()
                .spuCode("SPU-DRINK")
                .spuName("Drinks")
                .category("beverages")
                .enabled(true)
                .build();
        entityManager.persist(spu);

        productTea = Product.builder()
                .spu(spu)
                .skuName("Green Tea Box")
                .barcode("BAR-TEA-001")
                .name("Premium Green Tea 12-Box")
                .unitPrice(new BigDecimal("25.00"))
                .minStock(100)
                .leadTime(7)
                .enabled(true)
                .category("beverages")
                .supplier("SupplierA")
                .isDeleted(false)
                .build();

        productJuice = Product.builder()
                .spu(spu)
                .skuName("Orange Juice")
                .barcode("BAR-JUS-002")
                .name("Fresh Orange Juice 1L")
                .unitPrice(new BigDecimal("12.00"))
                .minStock(50)
                .leadTime(3)
                .enabled(true)
                .category("beverages")
                .supplier("SupplierB")
                .isDeleted(false)
                .build();

        disabledProduct = Product.builder()
                .spu(spu)
                .skuName("Old Snack")
                .barcode("BAR-SNK-003")
                .name("Discontinued Snack Pack")
                .unitPrice(new BigDecimal("5.00"))
                .minStock(0)
                .leadTime(1)
                .enabled(false)
                .category("snacks")
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
        Optional<Product> found = productRepository.findByBarcode("BAR-TEA-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Premium Green Tea 12-Box");
        assertThat(found.get().getSupplier()).isEqualTo("SupplierA");
    }

    @Test
    @DisplayName("findByBarcode - returns empty when barcode does not exist")
    void testFindByBarcode_NotFound() {
        Optional<Product> found = productRepository.findByBarcode("NONEXISTENT-BAR");

        assertThat(found).isEmpty();
    }

    // ========== existsByBarcode ==========

    @Test
    @DisplayName("existsByBarcode - returns true when barcode exists")
    void testExistsByBarcode_Exists() {
        boolean exists = productRepository.existsByBarcode("BAR-TEA-001");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByBarcode - returns false when barcode does not exist")
    void testExistsByBarcode_NotExists() {
        boolean exists = productRepository.existsByBarcode("NONEXISTENT-BAR");

        assertThat(exists).isFalse();
    }

    // ========== findByNameContaining ==========

    @Test
    @DisplayName("findByNameContaining - finds products matching keyword")
    void testFindByNameContaining_Found() {
        List<Product> results = productRepository.findByNameContaining("Tea");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBarcode()).isEqualTo("BAR-TEA-001");
    }

    @Test
    @DisplayName("findByNameContaining - returns empty list when no match")
    void testFindByNameContaining_NoMatch() {
        List<Product> results = productRepository.findByNameContaining("ZZZNOTFOUND");

        assertThat(results).isEmpty();
    }

    // ========== findByEnabledTrue ==========

    @Test
    @DisplayName("findByEnabledTrue - returns only enabled products")
    void testFindByEnabledTrue() {
        List<Product> enabled = productRepository.findByEnabledTrue();

        assertThat(enabled).hasSize(2);
        assertThat(enabled)
                .extracting(Product::getBarcode)
                .containsExactlyInAnyOrder("BAR-TEA-001", "BAR-JUS-002");
        assertThat(enabled).allMatch(Product::getEnabled);
    }

    // ========== findByCategory ==========

    @Test
    @DisplayName("findByCategory - returns products in given category")
    void testFindByCategory() {
        List<Product> beverages = productRepository.findByCategory("beverages");

        assertThat(beverages).hasSize(2);
        assertThat(beverages)
                .extracting(Product::getCategory)
                .containsOnly("beverages");
    }

    @Test
    @DisplayName("findByCategory - returns products in snacks category")
    void testFindByCategory_Snacks() {
        List<Product> snacks = productRepository.findByCategory("snacks");

        assertThat(snacks).hasSize(1);
        assertThat(snacks.get(0).getBarcode()).isEqualTo("BAR-SNK-003");
    }

    // ========== findBySupplier ==========

    @Test
    @DisplayName("findBySupplier - returns products from given supplier")
    void testFindBySupplier() {
        List<Product> supplierAProducts = productRepository.findBySupplier("SupplierA");

        assertThat(supplierAProducts).hasSize(2);
        assertThat(supplierAProducts)
                .extracting(Product::getSupplier)
                .containsOnly("SupplierA");
    }

    @Test
    @DisplayName("findBySupplier - returns empty when supplier has no products")
    void testFindBySupplier_NotFound() {
        List<Product> results = productRepository.findBySupplier("UnknownSupplier");

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
        Inventory lowInventory = Inventory.builder()
                .product(productTea)
                .location(location)
                .quantity(10)
                .build();
        entityManager.persist(lowInventory);
        entityManager.flush();

        List<Product> lowStock = productRepository.findLowStockProducts();

        assertThat(lowStock).isNotEmpty();
        assertThat(lowStock)
                .extracting(Product::getBarcode)
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
                .product(productJuice)
                .location(location)
                .quantity(200)
                .build();
        entityManager.persist(sufficientInventory);
        entityManager.flush();

        List<Product> lowStock = productRepository.findLowStockProducts();

        assertThat(lowStock)
                .extracting(Product::getBarcode)
                .doesNotContain("BAR-JUS-002");
    }
}
