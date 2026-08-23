package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminStatusMigrationContractTest {
    @Test
    void migrationAddsOnlyStatusCommandInfrastructureWithoutChangingIdentityRows() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_69__Platform_Admin_Status_Commands.sql"));

        assertThat(sql).contains(
            "ADMIN_STATUS_CHANGE",
            "action_context_hash",
            "target_security_version",
            "CREATE TABLE IF NOT EXISTS platform_admin_commands",
            "uk_platform_admin_command_actor_key",
            "ON platform_admin_commands(target_platform_user_id, created_at DESC)",
            "target_platform_user_id BIGINT",
            "idx_platform_audit_target_user_created",
            "ADMIN_DISABLED",
            "ADMIN_ENABLED"
        );
        assertThat(sql).containsOnlyOnce("CREATE TABLE IF NOT EXISTS platform_admin_commands");
        assertThat(sql).doesNotContain(
            "UPDATE platform_users",
            "INSERT INTO platform_users",
            "DELETE FROM platform_users",
            "UPDATE platform_user_roles",
            "INSERT INTO platform_user_roles",
            "DELETE FROM platform_user_roles",
            "UPDATE platform_access_grants",
            "DELETE FROM platform_access_grants",
            "platform_admin_invitation_roles",
            "platform_admin_invitation_active_emails"
        );
    }
}
