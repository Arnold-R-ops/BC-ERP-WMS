package com.wms.system.repository;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import com.wms.system.support.TestCatalogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
class InventoryBatchCorrectionCandidateRepositoryTest {

    @Autowired private InventoryBatchRepository repository;
    @Autowired private TestEntityManager entityManager;

    private Long productSkuId;

    @BeforeEach
    void setUp() {
        Product product = Product.builder()
            .category(TestCatalogFactory.persistLeafCategory(entityManager))
            .productCode("SPU-CORRECTION-CANDIDATE")
            .productName("Correction candidate product")
            .enabled(true)
            .build();
        entityManager.persist(product);

        ProductSku productSku = ProductSku.builder()
            .skuCode(TestCatalogFactory.nextSkuCode())
            .product(product)
            .skuName("Correction candidate SKU")
            .barcode("CORRECTION-CANDIDATE-BARCODE")
            .name("Correction candidate SKU")
            .unitPrice(BigDecimal.ONE)
            .isDeleted(false)
            .build();
        entityManager.persist(productSku);

        Warehouse warehouse = Warehouse.builder()
            .code("WH-CORR")
            .name("Correction warehouse")
            .isActive(true)
            .build();
        entityManager.persist(warehouse);
        Location location = Location.builder()
            .warehouse(warehouse)
            .warehouseCode("WH-CORR")
            .zone(Zone.ZONE_A)
            .shelfNumber("A01")
            .positionNumber("001")
            .enabled(true)
            .build();
        entityManager.persist(location);
        entityManager.flush();

        persistBatch("ACTIVE-POSITIVE", productSku, location, true, 10);
        persistBatch("ACTIVE-ZERO", productSku, location, true, 0);
        persistBatch("INACTIVE-ZERO", productSku, location, false, 0);
        persistBatch("INACTIVE-POSITIVE", productSku, location, false, 5);
        entityManager.flush();
        entityManager.clear();
        productSkuId = productSku.getId();
    }

    @Test
    void correctionQueryWidensOnlyToInactiveZeroQuantityBatches() {
        assertThat(repository.findByProductSkuIdAndActiveOrderByExpiryDateAsc(productSkuId, true))
            .extracting(InventoryBatch::getBatchCode)
            .containsExactly("ACTIVE-POSITIVE");

        assertThat(repository.findCorrectionCandidatesByProductSkuId(productSkuId))
            .extracting(InventoryBatch::getBatchCode)
            .containsExactly("ACTIVE-POSITIVE", "INACTIVE-ZERO");
    }

    private void persistBatch(
        String batchCode,
        ProductSku productSku,
        Location location,
        boolean active,
        int quantity
    ) {
        entityManager.persist(InventoryBatch.builder()
            .batchCode(batchCode)
            .productSku(productSku)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(quantity)
            .reservedQuantity(0)
            .initialQuantity(Math.max(quantity, 10))
            .expiryDate(LocalDate.now().plusDays(30))
            .active(active)
            .build());
    }
}
