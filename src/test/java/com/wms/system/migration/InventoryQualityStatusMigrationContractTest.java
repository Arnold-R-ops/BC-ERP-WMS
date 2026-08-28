package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryQualityStatusMigrationContractTest {

    @Test
    void migrationBackfillsWithoutStockReplayAndCreatesTenantSafeImmutableLineage()
        throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_78__Inventory_Quality_Status_Truth.sql"
        )).replace("\r\n", "\n");

        assertThat(sql).contains(
            "ADD COLUMN IF NOT EXISTS quality_status VARCHAR(30)",
            "SET quality_status = 'AVAILABLE'",
            "ALTER COLUMN quality_status SET NOT NULL",
            "ck_inventory_batch_non_saleable_unreserved",
            "CREATE TABLE public.inventory_status_event",
            "FOREIGN KEY (company_id, inventory_batch_id)",
            "UNIQUE (company_id, idempotency_key)",
            "ALTER TABLE public.inventory_status_event FORCE ROW LEVEL SECURITY",
            "CREATE POLICY company_isolation ON public.inventory_status_event",
            "inventory_status_event is append-only",
            "current_setting('app.tenant_purge', true) = 'on'"
        );
        assertThat(sql).doesNotContain(
            "INSERT INTO public.stock_transactions",
            "INSERT INTO public.inventory_status_event",
            "UPDATE public.stock_transactions"
        );
    }
}
