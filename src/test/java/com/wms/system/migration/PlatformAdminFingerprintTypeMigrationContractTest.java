package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminFingerprintTypeMigrationContractTest {
    @Test
    void migrationAlignsBothFingerprintColumnsWithoutMutatingSecurityRows() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_72__Repair_Platform_Admin_Fingerprint_Types.sql"
        ));

        assertThat(sql).contains(
            "ALTER TABLE platform_admin_commands",
            "ALTER COLUMN request_fingerprint TYPE VARCHAR(64)",
            "ALTER TABLE platform_mfa_challenges",
            "ALTER COLUMN action_context_hash TYPE VARCHAR(64)",
            "USING btrim(request_fingerprint)",
            "USING btrim(action_context_hash)"
        );
        assertThat(sql).doesNotContain(
            "UPDATE platform_users",
            "INSERT INTO platform_users",
            "DELETE FROM platform_users",
            "UPDATE platform_user_roles",
            "DELETE FROM platform_user_roles",
            "UPDATE platform_access_grants",
            "DELETE FROM platform_access_grants",
            "UPDATE tenants",
            "DELETE FROM tenants"
        );
    }
}
