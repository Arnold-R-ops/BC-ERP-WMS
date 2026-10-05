package com.wms.system.migration;

import com.wms.system.service.*;
import com.wms.system.repository.*;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.platform.service.PlatformDatasetCatalog;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "upgrade.dbtest", matches = "true")
class SalesBinderUpgradeApplicationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:55519/salesbinder_upgrade_test");
        properties.add("spring.datasource.username", () -> "wms_upgrade_app");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> false);
        properties.add("wms.legacy-admin-bootstrap-enabled", () -> false);
        properties.add("wms.tenancy.enabled", () -> false);
        properties.add("wms.tenancy.require-safe-database-role", () -> true);
        properties.add("spring.task.scheduling.enabled", () -> false);
        properties.add("logging.level.com.wms.system", () -> "WARN");
    }
    @Autowired ReportQueryService reports;
    @Autowired ReportCsvService csv;
    @Autowired StockPredictionService predictions;
    @Autowired InventoryBatchRepository inventory;
    @Autowired PurchaseOrderAllocationRepository purchases;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional(readOnly = true)
    void executesEveryPlatformDatasetAgainstTheMergedErpSchema() {
        assertThat(entityManager.createNativeQuery("select current_database()").getSingleResult())
            .isEqualTo("salesbinder_upgrade_test");
        assertThat(((Number) entityManager.createNativeQuery("select inet_server_port()").getSingleResult()).intValue())
            .isEqualTo(55519);

        for (var dataset : PlatformDatasetCatalog.all()) {
            assertThatCode(() -> {
                var count = entityManager.createQuery(dataset.countJpql(), Long.class)
                    .setMaxResults(1).getSingleResult();
                assertThat(count).isNotNegative();
                var rows = entityManager.createQuery(dataset.selectJpql(), Object[].class)
                    .setMaxResults(1).getResultList();
                assertThat(rows).hasSizeLessThanOrEqualTo(1);
                for (var row : rows) {
                    assertThat(dataset.headers()).hasSize(row.length);
                }
            }).as("Platform dataset %s must query the merged ERP schema", dataset.code())
                .doesNotThrowAnyException();
        }
    }

    @Test void runsNewQueriesAgainstMigratedSchemaUsingAnUnprivilegedRole() {
        var start = LocalDate.of(2026,9,18);
        assertThat(reports.getSalesOverview(start, start).getTotalAmount()).isEqualByComparingTo("1200.00");
        assertThat(csv.sales(start, start)).contains("1200.00", "APPROVED/COMPLETED/CLOSED");
        var allSuggestions = predictions.getReorderSuggestions(30, null, true);
        assertThat(allSuggestions).isNotEmpty().allSatisfy(suggestion ->
            assertThat(suggestion.getSuggestedReorderQuantity()).isNotNegative());
        assertThat(predictions.getReorderSuggestions(30, null, false))
            .extracting(suggestion -> suggestion.getProductSkuId())
            .containsExactlyElementsOf(allSuggestions.stream().filter(suggestion -> suggestion.needsReorder())
                .map(suggestion -> suggestion.getProductSkuId()).toList());
        assertThat(inventory.sumAvailableQuantityByProductSkuAndWarehouse(-1L, -1L)).isZero();
        assertThat(purchases.sumEffectiveOutstanding(-1L, null, List.of(PurchaseOrderStatus.AWAITING_SHIPMENT))).isZero();
    }
}

