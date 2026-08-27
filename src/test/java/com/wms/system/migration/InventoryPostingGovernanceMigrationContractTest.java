package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPostingGovernanceMigrationContractTest {

    @Test
    void migrationAddsTraceabilityWorkflowIsolationAndLeastPrivilegePermissions()
        throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/"
                + "V4_73__Inventory_Posting_And_Correction_Governance.sql"
        )).replace("\r\n", "\n");

        assertThat(sql).contains(
            "ADD COLUMN IF NOT EXISTS warehouse_id BIGINT",
            "ADD COLUMN IF NOT EXISTS inventory_batch_id BIGINT",
            "ADD COLUMN IF NOT EXISTS posting_id VARCHAR(120)",
            "ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120)",
            "ADD COLUMN IF NOT EXISTS lease_token VARCHAR(64)",
            "ADD COLUMN IF NOT EXISTS approval_mode_snapshot VARCHAR(20)",
            "ADD COLUMN IF NOT EXISTS submission_comment VARCHAR(500)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_emergency_correction_company_posting",
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_emergency_correction_company_idempotency",
            "ALTER TABLE public.inventory_correction_policy_audit FORCE ROW LEVEL SECURITY",
            "current_setting('app.tenant_purge', true) = 'on'",
            "inventory:correction:execute",
            "/api/emergency-stock-corrections/**",
            "/api/emergency-stock-corrections/**',\n            '*',\n            'ALL'",
            "backorder:wake",
            "/api/backorders/wake/**",
            "permission_code = 'inventory:adjust'",
            "status = 'INACTIVE'",
            "CHECK (quantity >= reserved_quantity) NOT VALID",
            "role.role_code = 'GENERAL_MANAGER'",
            "AND permission.permission_code IN (\n"
                + "                      'menu:inventory-correction',\n"
                + "                      'inventory:correction:view'\n"
                + "                  )",
            "WITH RECURSIVE affected_roles AS (",
            "role.role_code IN (\n"
                + "                      'TENANT_ADMIN',\n"
                + "                      'WAREHOUSE_ADMIN',\n"
                + "                      'GENERAL_MANAGER'\n"
                + "                  )",
            "JOIN affected_roles parent",
            "UPDATE public.users account",
            "SET security_version = COALESCE(account.security_version, 0) + 1"
        );
        assertThat(sql).doesNotContain(
            "/api/inventory/adjust",
            "/api/inventory/batches/{batchCode}/adjust",
            "/api/inventory/batches/outbound"
        );
    }
}
