package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseTransferMigrationContractTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V4_84__Warehouse_Transfer_Closed_Loop.sql");

    @Test
    void migrationCreatesTenantSafeTransferTruthAndImmutableEvidence() throws Exception {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");

        assertThat(sql).contains(
            "CREATE TABLE public.transfer_order (",
            "CREATE TABLE public.transfer_order_line (",
            "CREATE TABLE public.transfer_allocation (",
            "CREATE TABLE public.transfer_warehouse_task (",
            "CREATE TABLE public.transfer_dispatch (",
            "CREATE TABLE public.transfer_dispatch_line (",
            "CREATE TABLE public.transfer_receipt (",
            "CREATE TABLE public.transfer_receipt_line (",
            "CREATE TABLE public.transfer_event (",
            "source_warehouse_id <> destination_warehouse_id",
            "REFERENCES public.transfer_order(company_id, id)",
            "REFERENCES public.inventory_batch(company_id, id)",
            "REFERENCES public.stock_transactions(company_id, id)",
            "ENABLE ROW LEVEL SECURITY",
            "FORCE ROW LEVEL SECURITY",
            "prevent_transfer_evidence_mutation",
            "TRANSFER_RECEIPT",
            "transfer.approval.mode",
            "transfer.receiving_inspection.enabled",
            "permission_code = 'inventory:transfer'",
            "'transfer:view'", "'transfer:create'", "'transfer:approve'",
            "'transfer:dispatch'", "'transfer:receive'", "'transfer:close'", "'transfer:policy'",
            "'transfer:create', '创建和维护调拨', 'API', '/api/transfers/**', '*'",
            "source_batch_id IS",
            "idx_transfer_order_line_backorder",
            "destination_batch_id is always a newly created batch row"
        );
        assertThat(sql).doesNotContain(
            "supplier_id BIGINT", "carrier_id BIGINT", "tracking_number VARCHAR",
            "customer_id BIGINT", "unit_cost NUMERIC", "'CRITICAL', TRUE",
            "uk_transfer_order_line_backorder UNIQUE", "'POST,PUT'"
        );
    }

    @Test
    void callbacksPreserveReleasedMigrationChecksumsAndRestorePermissionConstraint() throws Exception {
        String before = Files.readString(Path.of(
            "src/main/resources/db/migration/beforeMigrate__assignable_permission_convergence.sql"));
        String after = Files.readString(Path.of(
            "src/main/resources/db/migration/afterMigrate__assignable_permission_convergence.sql"));

        assertThat(before).contains(
            "DROP CONSTRAINT IF EXISTS ck_sys_permission_critical_not_custom");
        assertThat(after).contains(
            "risk_level = 'HIGH'",
            "risk_level = 'CRITICAL' AND custom_assignable = TRUE",
            "ADD CONSTRAINT ck_sys_permission_critical_not_custom",
            "risk_level <> 'CRITICAL' OR custom_assignable = FALSE"
        );
    }
}
