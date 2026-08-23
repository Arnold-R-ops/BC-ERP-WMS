package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminSecurityMigrationContractTest {
    @Test
    void migrationOnlyExpandsSecurityCommandVocabularyWithoutMutatingIdentityRows() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_70__Platform_Admin_Security_Commands.sql"));

        assertThat(sql).contains(
            "ADMIN_SESSIONS_REVOKE",
            "ADMIN_MFA_RESET",
            "ADMIN_SESSIONS_REVOKED",
            "ck_platform_mfa_challenge_purpose",
            "ck_platform_admin_command_action",
            "ck_platform_audit_action"
        );
        assertThat(sql).doesNotContain(
            "UPDATE platform_users",
            "INSERT INTO platform_users",
            "DELETE FROM platform_users",
            "UPDATE platform_user_roles",
            "INSERT INTO platform_user_roles",
            "DELETE FROM platform_user_roles",
            "UPDATE platform_access_grants",
            "DELETE FROM platform_access_grants",
            "CREATE TABLE"
        );
    }
}
