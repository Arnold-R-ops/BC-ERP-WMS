package com.wms.system.repository;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import com.wms.system.entity.enums.InventoryQualityStatus;
import com.wms.system.support.TestCatalogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
class InventoryBatchExpiryOrderingRepositoryTest {

    @Autowired private InventoryBatchRepository repository;
    @Autowired private TestEntityManager entityManager;

    private Long productSkuId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        Product product = Product.builder()
            .category(TestCatalogFactory.persistLeafCategory(entityManager))
            .productCode("SPU-EXPIRY-ORDER")
            .productName("Expiry order product")
            .enabled(true)
            .build();
        entityManager.persist(product);

        ProductSku productSku = ProductSku.builder()
            .skuCode(TestCatalogFactory.nextSkuCode())
            .product(product)
            .skuName("Expiry order SKU")
            .barcode("EXPIRY-ORDER-BARCODE")
            .name("Expiry order SKU")
            .unitPrice(BigDecimal.ONE)
            .isDeleted(false)
            .build();
        entityManager.persist(productSku);

        Warehouse warehouse = Warehouse.builder()
            .code("WH-EXPIRY")
            .name("Expiry warehouse")
            .isActive(true)
            .build();
        entityManager.persist(warehouse);
        Location location = Location.builder()
            .warehouse(warehouse)
            .warehouseCode("WH-EXPIRY")
            .zone(Zone.ZONE_A)
            .shelfNumber("A01")
            .positionNumber("001")
            .enabled(true)
            .build();
        entityManager.persist(location);
        entityManager.flush();

        persistBatch("DATED-LATER", productSku, location, today.plusDays(60));
        persistBatch("UNDATED", productSku, location, null);
        persistBatch("DATED-EARLIER", productSku, location, today.plusDays(30));
        persistBatch("EXPIRED", productSku, location, today.minusDays(1));
        InventoryBatch quarantined = persistBatch(
            "QUARANTINED", productSku, location, today.plusDays(20));
        quarantined.setQualityStatus(InventoryQualityStatus.QUARANTINED);
        entityManager.flush();
        entityManager.clear();
        productSkuId = productSku.getId();
    }

    @Test
    void fefoPlacesDatedBatchesFirstAndUndatedBatchesLast() {
        assertThat(repository.findByProductSkuIdAndActiveOrderByExpiryDateAsc(
            productSkuId,
            true
        ))
            .extracting(InventoryBatch::getBatchCode)
            .containsExactly(
                "EXPIRED",
                "QUARANTINED",
                "DATED-EARLIER",
                "DATED-LATER",
                "UNDATED"
            );
    }

    @Test
    void expiryAlertsNeverClassifyUndatedBatches() {
        assertThat(repository.findExpiredBatches(today, true))
            .extracting(InventoryBatch::getBatchCode)
            .containsExactly("EXPIRED");

        assertThat(repository.findSoonToExpireBatches(
            today,
            today.plusDays(45),
            true
        ))
            .extracting(InventoryBatch::getBatchCode)
            .containsExactly("QUARANTINED", "DATED-EARLIER");
    }

    @Test
    void saleableFefoAndAvailableAggregationExcludeQualityHeldAndExpiredStock() {
        assertThat(repository.findSaleableByProductSkuIdOrderByExpiryDateAsc(productSkuId))
            .extracting(InventoryBatch::getBatchCode)
            .containsExactly("DATED-EARLIER", "DATED-LATER", "UNDATED");

        assertThat(repository.sumQuantityByProductSku(productSkuId)).isEqualTo(50);
        assertThat(repository.sumAvailableQuantityByProductSku(productSkuId)).isEqualTo(30);

        InventoryBatchRepository.InventorySummaryRow summary = repository
            .findInventorySummaryRows(1L, "", PageRequest.of(0, 20))
            .getContent()
            .stream()
            .filter(row -> row.getProductSkuId().equals(productSkuId))
            .findFirst()
            .orElseThrow();
        assertThat(summary.getTotalQuantity().intValue()).isEqualTo(50);
        assertThat(summary.getTotalAvailableQuantity().intValue()).isEqualTo(30);
        assertThat(summary.getQuarantinedQuantity().intValue()).isEqualTo(10);
    }

    private InventoryBatch persistBatch(
        String batchCode,
        ProductSku productSku,
        Location location,
        LocalDate expiryDate
    ) {
        InventoryBatch batch = InventoryBatch.builder()
            .batchCode(batchCode)
            .productSku(productSku)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(10)
            .reservedQuantity(0)
            .initialQuantity(10)
            .expiryDate(expiryDate)
            .active(true)
            .build();
        entityManager.persist(batch);
        return batch;
    }
}
