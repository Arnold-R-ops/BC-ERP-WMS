package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TenantScopedBackfillMigrationContractTest {

    private static final Path MIGRATIONS = Path.of(
        "src", "main", "resources", "db", "migration"
    );

    @Test
    void receivingAndSalesBackfillsNeverRelaxForceRls() throws IOException {
        String receiving = Files.readString(MIGRATIONS.resolve(
            "V4_80__Receiving_Inspection_And_Release.sql"
        ));
        String sales = Files.readString(MIGRATIONS.resolve(
            "V4_82__Sales_Four_Layer_Status_And_Command_Governance.sql"
        ));

        assertThat(receiving)
            .contains("FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP")
            .contains("set_config('app.company_id', company_record.id::TEXT, TRUE)")
            .contains("WHERE company_id = company_record.id")
            .doesNotContain("NO FORCE ROW LEVEL SECURITY");
        assertThat(sales)
            .contains("DO $sales_status_backfill$")
            .contains("DO $sales_shipment_status_backfill$")
            .contains("DO $sales_shipment_event_backfill$")
            .contains("WHERE sales_order.company_id = company_record.id")
            .contains("WHERE company_id = company_record.id")
            .doesNotContain("NO FORCE ROW LEVEL SECURITY");
    }
}
