package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyRlsMigrationContractTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V4_52__Enforce_Company_Row_Level_Security.sql"
    );

    @Test
    void tenantTablesUseForcedFailClosedCompanyPolicies() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
            .contains("current_setting('app.company_id', true)")
            .contains("ENABLE ROW LEVEL SECURITY")
            .contains("FORCE ROW LEVEL SECURITY")
            .contains("USING (company_id = bcwms_current_company_id())")
            .contains("WITH CHECK (company_id = bcwms_current_company_id())")
            .contains("'users'")
            .contains("'inventory_batch'")
            .contains("'sys_role_permission'");
    }

    @Test
    void controlPlaneTablesAreNotAddedToTenantPolicyList() throws Exception {
        String sql = Files.readString(MIGRATION);
        String tableList = sql.substring(
            sql.indexOf("tenant_tables CONSTANT"),
            sql.indexOf("];", sql.indexOf("tenant_tables CONSTANT"))
        );

        assertThat(tableList)
            .doesNotContain("'tenants'")
            .doesNotContain("'platform_users'")
            .doesNotContain("'platform_audit_logs'")
            .doesNotContain("'signup_requests'");
    }
}
