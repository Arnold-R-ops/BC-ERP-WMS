package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SalesFourLayerStatusMigrationContractTest {

    @Test
    void migrationRemovesLegacyTruthSourcesAndAddsTenantSafeGovernance()
        throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/"
                + "V4_82__Sales_Four_Layer_Status_And_Command_Governance.sql"
        )).replace("\r\n", "\n");

        assertThat(sql).contains(
            "ALTER TABLE public.sales_orders DROP COLUMN status",
            "ALTER TABLE public.sales_orders DROP COLUMN fulfillment_status",
            "ALTER TABLE public.sales_order_items DROP COLUMN fulfillment_status",
            "ADD CONSTRAINT ck_sales_orders_commercial_status",
            "ADD CONSTRAINT ck_sales_orders_allocation_status",
            "ADD CONSTRAINT ck_sales_orders_warehouse_status",
            "ADD CONSTRAINT ck_sales_orders_logistics_status",
            "CREATE TABLE public.sales_command_events",
            "UNIQUE (company_id, idempotency_key)",
            "ALTER TABLE public.sales_command_events FORCE ROW LEVEL SECURITY",
            "ALTER TABLE public.sales_shipment_logistics_events FORCE ROW LEVEL SECURITY",
            "trg_sales_command_events_immutable",
            "trg_sales_logistics_events_immutable",
            "sales.order.approval.mode', 'DIRECT'",
            "sales.logistics.detailed.enabled', 'false'",
            "'sales:close'",
            "'sales:logistics:update'",
            "'sales:policy:manage'",
            "'/api/sales-orders/policy', 'PUT', 'ALL', 'HIGH', TRUE",
            "'/api/outbound-tasks/*/dispatch;/api/outbound-tasks/batch-dispatch'"
        );
        assertThat(sql).doesNotContain(
            "'/api/sales-orders/policy', 'PUT', 'ALL', 'CRITICAL', TRUE",
            "/api/outbound-tasks/*/confirm",
            "/api/outbound-tasks/batch-confirm"
        );
    }
}
