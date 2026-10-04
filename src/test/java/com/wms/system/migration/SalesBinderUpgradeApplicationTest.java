package com.wms.system.migration;

import com.wms.system.service.*;
import com.wms.system.repository.*;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Test void runsNewQueriesAgainstMigratedSchemaUsingAnUnprivilegedRole() {
        var start = LocalDate.of(2026,9,18);
        assertThat(reports.getSalesOverview(start, start).getTotalAmount()).isEqualByComparingTo("1200.00");
        assertThat(csv.sales(start, start)).contains("1200.00", "APPROVED/COMPLETED/CLOSED");
        assertThat(predictions.getReorderSuggestions(30, null, true)).isEmpty();
        assertThat(inventory.sumAvailableQuantityByProductSkuAndWarehouse(-1L, -1L)).isZero();
        assertThat(purchases.sumEffectiveOutstanding(-1L, null, List.of(PurchaseOrderStatus.AWAITING_SHIPMENT))).isZero();
    }
}

