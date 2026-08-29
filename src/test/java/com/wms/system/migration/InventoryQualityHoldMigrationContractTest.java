package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryQualityHoldMigrationContractTest {
    @Test
    void migrationAddsGovernedSegmentsActionsScopeAndUnassignedPermissions() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_79__Inventory_Quality_Hold_And_Release.sql"
        )).replace("\r\n", "\n");

        assertThat(sql).contains(
            "quality_parent_batch_id",
            "quality_segment_no",
            "CREATE TABLE public.inventory_quality_action",
            "CREATE TABLE public.inventory_quality_action_line",
            "CREATE TABLE public.inventory_quality_reservation_move",
            "APPROVAL_REQUIRED",
            "ck_inventory_quality_action_separation",
            "ALTER TABLE public.inventory_quality_action FORCE ROW LEVEL SECURITY",
            "ALTER TABLE public.inventory_quality_action_line FORCE ROW LEVEL SECURITY",
            "ALTER TABLE public.inventory_quality_reservation_move FORCE ROW LEVEL SECURITY",
            "'inventory_quality:view'",
            "'inventory_quality:hold'",
            "'inventory_quality:release'",
            "'inventory_quality:review'",
            "'inventory_quality:policy'"
        );
        assertThat(sql).doesNotContain(
            "INSERT INTO public.stock_transactions",
            "INSERT INTO public.sys_role_permission",
            "UPDATE public.inventory_batch SET quantity"
        );
    }
}
