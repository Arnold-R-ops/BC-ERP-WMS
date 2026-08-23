package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminJobsMigrationContractTest {
    @Test
    void migrationAddsJobsAtomicInvitationStagingAndRoleChangeVocabularyOnly() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_71__Platform_Admin_Jobs_And_Invitations.sql"));

        assertThat(sql).contains(
            "PLATFORM_OPERATIONS_ADMIN",
            "PLATFORM_SECURITY_AUDITOR",
            "invitation_type",
            "platform_admin_invitation_active_emails",
            "platform_admin_invitation_activations",
            "ADMIN_ROLES_CHANGE",
            "ADMIN_ROLES_CHANGED"
        );
        assertThat(sql).doesNotContain(
            "UPDATE platform_users",
            "INSERT INTO platform_users",
            "DELETE FROM platform_users",
            "UPDATE platform_user_roles",
            "INSERT INTO platform_user_roles",
            "DELETE FROM platform_user_roles",
            "UPDATE platform_access_grants",
            "INSERT INTO platform_access_grants",
            "DELETE FROM platform_access_grants",
            "UPDATE tenants",
            "DELETE FROM tenants"
        );
    }
}
