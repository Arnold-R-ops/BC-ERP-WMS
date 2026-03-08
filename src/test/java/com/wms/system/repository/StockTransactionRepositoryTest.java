package com.wms.system.repository;

import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSpu;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@DisplayName("StockTransactionRepository Tests")
class StockTransactionRepositoryTest {

    @Autowired private StockTransactionRepository stockTransactionRepository;
    @Autowired private TestEntityManager entityManager;

    private Product product;
    private Product otherProduct;
    private Location location;
    private StockTransaction inTransaction;
    private StockTransaction outTransaction;
    private StockTransaction adjustTransaction;

    @BeforeEach
    void setUp() {
        ProductSpu spu = ProductSpu.builder()
                .spuCode("SPU-TEST")
                .spuName("Test SPU")
                .enabled(true)
                .build();
        entityManager.persist(spu);

        product = Product.builder()
                .spu(spu)
                .skuName("Test SKU")
                .barcode("BAR-TEST-001")
                .name("Test Product A")
                .unitPrice(new BigDecimal("10.00"))
                .isDeleted(false)
                .build();

        otherProduct = Product.builder()
                .spu(spu)
                .skuName("Other SKU")
                .barcode("BAR-TEST-002")
                .name("Test Product B")
                .unitPrice(new BigDecimal("20.00"))
                .isDeleted(false)
                .build();

        entityManager.persist(product);
        entityManager.persist(otherProduct);

        Warehouse warehouse = Warehouse.builder()
                .code("WH01")
                .name("Test Warehouse")
                .isActive(true)
                .build();
        entityManager.persist(warehouse);

        location = Location.builder()
                .warehouse(warehouse)
                .warehouseCode("WH01")
                .zone(Zone.ZONE_A)
                .shelfNumber("A01")
                .positionNumber("001")
                .enabled(true)
                .build();
        entityManager.persist(location);
        entityManager.flush();
        entityManager.clear();

        // Re-fetch to get generated locationCode
        location = entityManager.find(Location.class, location.getId());
        product = entityManager.find(Product.class, product.getId());
        otherProduct = entityManager.find(Product.class, otherProduct.getId());

        // Create transactions
        inTransaction = StockTransaction.builder()
                .product(product)
                .location(location)
                .transactionType(TransactionType.IN)
                .sourceType(SourceType.PURCHASE_IN)
                .quantity(100)
                .quantityBefore(0)
                .quantityAfter(100)
                .sourceOrderId("PO-20260101-001")
                .build();

        outTransaction = StockTransaction.builder()
                .product(product)
                .location(location)
                .transactionType(TransactionType.OUT)
                .sourceType(SourceType.SALE_OUT)
                .quantity(30)
                .quantityBefore(100)
                .quantityAfter(70)
                .sourceOrderId("SO-20260101-001")
                .build();

        adjustTransaction = StockTransaction.builder()
                .product(otherProduct)
                .location(location)
                .transactionType(TransactionType.ADJUST)
                .sourceType(SourceType.MANUAL_ADJUST)
                .quantity(-5)
                .quantityBefore(50)
                .quantityAfter(45)
                .sourceOrderId("ADJ-20260101-001")
                .build();

        entityManager.persist(inTransaction);
        entityManager.persist(outTransaction);
        entityManager.persist(adjustTransaction);
        entityManager.flush();
    }

    // ========== findByProduct ==========

    @Test
    @DisplayName("findByProduct - returns transactions for the given product")
    void testFindByProduct() {
        List<StockTransaction> txs = stockTransactionRepository.findByProduct(product);

        assertThat(txs).hasSize(2);
        assertThat(txs)
                .extracting(StockTransaction::getSourceOrderId)
                .containsExactlyInAnyOrder("PO-20260101-001", "SO-20260101-001");
    }

    @Test
    @DisplayName("findByProduct - returns empty for product with no transactions")
    void testFindByProduct_Empty() {
        ProductSpu spu = entityManager.find(ProductSpu.class,
                entityManager.persistAndGetId(ProductSpu.builder()
                        .spuCode("SPU-EMPTY")
                        .spuName("Empty SPU")
                        .enabled(true)
                        .build()));
        Product newProduct = Product.builder()
                .spu(spu)
                .skuName("No Tx SKU")
                .barcode("BAR-NOTX-999")
                .name("Product With No Transactions")
                .unitPrice(BigDecimal.ONE)
                .isDeleted(false)
                .build();
        entityManager.persist(newProduct);
        entityManager.flush();

        List<StockTransaction> txs = stockTransactionRepository.findByProduct(newProduct);

        assertThat(txs).isEmpty();
    }

    // ========== findByProduct_IdOrderByCreatedAtDesc ==========

    @Test
    @DisplayName("findByProduct_IdOrderByCreatedAtDesc - returns transactions ordered by createdAt desc")
    void testFindByProductIdOrderByCreatedAtDesc() {
        List<StockTransaction> txs = stockTransactionRepository
                .findByProduct_IdOrderByCreatedAtDesc(product.getId());

        assertThat(txs).hasSize(2);
        // Should not be empty and should be ordered (most recent first)
        assertThat(txs).extracting(StockTransaction::getSourceOrderId)
                .containsExactlyInAnyOrder("PO-20260101-001", "SO-20260101-001");
    }

    // ========== findByTransactionType ==========

    @Test
    @DisplayName("findByTransactionType - returns only IN transactions")
    void testFindByTransactionType_IN() {
        List<StockTransaction> inTxs = stockTransactionRepository
                .findByTransactionType(TransactionType.IN);

        assertThat(inTxs).hasSize(1);
        assertThat(inTxs.get(0).getTransactionType()).isEqualTo(TransactionType.IN);
        assertThat(inTxs.get(0).getSourceOrderId()).isEqualTo("PO-20260101-001");
    }

    @Test
    @DisplayName("findByTransactionType - returns only OUT transactions")
    void testFindByTransactionType_OUT() {
        List<StockTransaction> outTxs = stockTransactionRepository
                .findByTransactionType(TransactionType.OUT);

        assertThat(outTxs).hasSize(1);
        assertThat(outTxs.get(0).getQuantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("findByTransactionType - returns ADJUST transactions")
    void testFindByTransactionType_ADJUST() {
        List<StockTransaction> adjustTxs = stockTransactionRepository
                .findByTransactionType(TransactionType.ADJUST);

        assertThat(adjustTxs).hasSize(1);
        assertThat(adjustTxs.get(0).getSourceOrderId()).isEqualTo("ADJ-20260101-001");
    }

    // ========== findBySourceType ==========

    @Test
    @DisplayName("findBySourceType - returns transactions with PURCHASE_IN source")
    void testFindBySourceType_PurchaseIn() {
        List<StockTransaction> txs = stockTransactionRepository
                .findBySourceType(SourceType.PURCHASE_IN);

        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getTransactionType()).isEqualTo(TransactionType.IN);
    }

    @Test
    @DisplayName("findBySourceType - returns transactions with SALE_OUT source")
    void testFindBySourceType_SaleOut() {
        List<StockTransaction> txs = stockTransactionRepository
                .findBySourceType(SourceType.SALE_OUT);

        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getQuantity()).isEqualTo(30);
    }

    // ========== sumOutboundQuantity ==========

    @Test
    @DisplayName("sumOutboundQuantity - sums OUT quantity for product in date range")
    void testSumOutboundQuantity_HasTransactions() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusMinutes(5);

        Integer total = stockTransactionRepository.sumOutboundQuantity(
                product.getId(), start, end);

        assertThat(total).isEqualTo(30);
    }

    @Test
    @DisplayName("sumOutboundQuantity - returns null when no OUT transactions in range")
    void testSumOutboundQuantity_NoTransactions() {
        LocalDateTime start = LocalDateTime.now().minusDays(365);
        LocalDateTime end = LocalDateTime.now().minusDays(364);

        Integer total = stockTransactionRepository.sumOutboundQuantity(
                product.getId(), start, end);

        assertThat(total).isNull();
    }

    // ========== findByCreatedAtBetween ==========

    @Test
    @DisplayName("findByCreatedAtBetween - returns transactions within date range")
    void testFindByCreatedAtBetween_Found() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(5);
        LocalDateTime end = LocalDateTime.now().plusMinutes(5);

        List<StockTransaction> txs = stockTransactionRepository
                .findByCreatedAtBetween(start, end);

        assertThat(txs).hasSize(3);
    }

    @Test
    @DisplayName("findByCreatedAtBetween - returns empty list outside date range")
    void testFindByCreatedAtBetween_OutOfRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(365);
        LocalDateTime end = LocalDateTime.now().minusDays(364);

        List<StockTransaction> txs = stockTransactionRepository
                .findByCreatedAtBetween(start, end);

        assertThat(txs).isEmpty();
    }

    // ========== countByProduct_Id ==========

    @Test
    @DisplayName("countByProduct_Id - returns correct transaction count for product")
    void testCountByProductId() {
        long count = stockTransactionRepository.countByProduct_Id(product.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countByProduct_Id - returns 0 for product with no transactions")
    void testCountByProductId_Zero() {
        long count = stockTransactionRepository.countByProduct_Id(9999L);

        assertThat(count).isEqualTo(0);
    }
}
