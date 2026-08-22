package com.wms.system.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class TenantDataPurgeMigrationContractTest {
    @Test void purgeIsStatusAndRlsBoundAndRetainsPlatformEvidence() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_55__Tenant_Expired_Data_Purge.sql"));
        assertThat(sql).contains(
            "CREATE TABLE tenant_purge_jobs",
            "CREATE TABLE tenant_purge_audit_logs",
            "bcwms_current_company_id() IS DISTINCT FROM p_company_id",
            "tenant_state IS DISTINCT FROM 'PURGE_PENDING'",
            "tenant_purge_due_at > CURRENT_TIMESTAMP",
            "current_setting('app.tenant_purge', true) = 'on'",
            "DELETE FROM tenant_memberships WHERE tenant_id = p_company_id",
            "DELETE FROM platform_export_jobs WHERE target_tenant_id = p_company_id");
        assertThat(sql).doesNotContain(
            "DELETE FROM tenants",
            "DELETE FROM platform_audit_logs",
            "TRUNCATE public.");
    }
}
