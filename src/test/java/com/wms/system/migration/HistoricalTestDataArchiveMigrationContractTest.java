package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalTestDataArchiveMigrationContractTest {

    @Test
    void postgresStatusConstraintAcceptsVoidedArchiveTasks() throws IOException {
        String resource = "db/migration/V4_47__Allow_Voided_Outbound_Task_Status.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("migration resource %s", resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("DROP CONSTRAINT IF EXISTS outbound_tasks_status_check")
                .contains("'PENDING', 'PICKING', 'COMPLETED', 'VOIDED'");
        }
    }

    @Test
    void historicalRepairOnlyRebuildsDerivedSalesSummaryFacts() throws IOException {
        String resource = "db/migration/V4_48__Repair_Archived_Order_Sales_Summaries.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("migration resource %s", resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                .contains("INSERT INTO sales_daily_summary")
                .contains("historical_test_data_registry")
                .doesNotContain("UPDATE sales_orders")
                .doesNotContain("UPDATE outbound_tasks")
                .doesNotContain("UPDATE inventory")
                .doesNotContain("UPDATE inventory_batch")
                .doesNotContain("INSERT INTO stock_transactions");
        }
    }
}
