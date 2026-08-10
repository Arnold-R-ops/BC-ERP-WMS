package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OrdinaryBusinessRoleBaselineMigrationContractTest {

    @Test
    void v449DefinesProvisionalBusinessTemplatesAndInvalidatesAffectedSessions() throws Exception {
        String sql = readMigration();

        assertThat(sql)
            .contains("('GENERAL_MANAGER', 'sales:approve')")
            .contains("('GENERAL_MANAGER', 'sales:reject')")
            .contains("('GENERAL_MANAGER', 'sales:cancel')")
            .contains("('GENERAL_MANAGER', 'inventory:correction:approve')")
            .contains("('SALESPERSON', 'customer:create')")
            .contains("('SALESPERSON', 'customer:edit')")
            .contains("('SALESPERSON', 'sales:shipment:edit')")
            .contains("SET security_version = COALESCE(security_version, 0) + 1")
            .doesNotContain("('GENERAL_MANAGER', 'sales:void')")
            .doesNotContain("('SALESPERSON', 'sales:approve')")
            .doesNotContain("('SALESPERSON', 'sales:cancel')");
    }

    @Test
    void v449KeepsVoidProtectedAndDoesNotRewriteWarehouseRoleBaselines() throws Exception {
        String sql = readMigration();

        assertThat(sql)
            .contains("('sales:void', '作废销售订单', '/api/sales-orders/*/void', 'POST', 'CRITICAL', FALSE")
            .contains("role_code IN ('GENERAL_MANAGER', 'SALESPERSON')")
            .doesNotContain("('WAREHOUSE_STAFF',")
            .doesNotContain("('WAREHOUSE_ADMIN',");
    }

    @Test
    void v449PublishesCheckboxAssignableActionCatalogue() throws Exception {
        String sql = readMigration();

        assertThat(sql)
            .contains("('purchase:create'")
            .contains("('purchase:confirm'")
            .contains("('inbound:confirm_order'")
            .contains("('stocktake:review'")
            .contains("('warehouse:manage'")
            .contains("('location:manage'")
            .contains("('menu:integration-reconciliation'");
    }

    private String readMigration() throws Exception {
        String resource = "db/migration/V4_49__Complete_Ordinary_Business_Role_Baselines.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("migration resource %s", resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
