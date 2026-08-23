package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminDirectoryMigrationContractTest {
    @Test
    void migrationAddsOnlyDirectoryAuditAndIndexesWithoutMutatingIdentityData() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_68__Platform_Admin_Directory.sql"));

        assertThat(sql).contains(
            "ADMIN_DIRECTORY_READ",
            "idx_platform_users_admin_directory",
            "idx_platform_user_roles_role_user",
            "idx_platform_access_grants_active_grantee",
            "WHERE revoked_at IS NULL"
        );
        assertThat(sql).doesNotContain(
            "UPDATE platform_users",
            "INSERT INTO platform_users",
            "DELETE FROM platform_users",
            "UPDATE platform_user_roles",
            "INSERT INTO platform_user_roles",
            "DELETE FROM platform_user_roles",
            "UPDATE platform_access_grants",
            "DELETE FROM platform_access_grants"
        );
    }
}
