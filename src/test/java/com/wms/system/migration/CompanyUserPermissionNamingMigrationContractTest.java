package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyUserPermissionNamingMigrationContractTest {

    private static final Path REMEDIATION_MIGRATION = Path.of(
        "src/main/resources/db/migration/V4_58__Apply_Company_User_Permission_Migration_With_Rls_Context.sql"
    );

    @Test
    void migrationReplacesTheLegacyCompanyAdminAndSoftDeletesOtherCompanyUsers() throws Exception {
        String sql = Files.readString(REMEDIATION_MIGRATION);

        assertThat(sql).contains(
            "role_code = 'SUPER_ADMIN'",
            "role_code = 'TENANT_ADMIN'",
            "active_legacy_admin_count > 1",
            "set_config('app.company_id', company_record.id::TEXT, TRUE)",
            "is_deleted = TRUE",
            "enabled = FALSE",
            "DELETE FROM sys_user_role",
            "DELETE FROM sys_user_warehouse",
            "status = 'REVOKED'"
        );
    }

    @Test
    void migrationKeepsPlatformAccountsOutsideCompanyAccountProcessing() throws Exception {
        String sql = Files.readString(REMEDIATION_MIGRATION);

        assertThat(sql).contains("Companies already created with TENANT_ADMIN are intentionally untouched")
            .doesNotContain("DELETE FROM platform_users")
            .doesNotContain("UPDATE platform_users");
    }
}
