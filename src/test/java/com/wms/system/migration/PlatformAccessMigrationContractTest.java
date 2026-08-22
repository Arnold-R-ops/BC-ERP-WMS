package com.wms.system.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformAccessMigrationContractTest {
    @Test void exportJobSchemaIsPlatformControlPlaneOnly() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V4_54__Platform_Tenant_Read_Export.sql"));
        assertThat(sql).contains("CREATE TABLE platform_export_jobs", "target_tenant_id BIGINT NOT NULL",
            "platform_user_id BIGINT NOT NULL", "expires_at TIMESTAMPTZ NOT NULL");
        assertThat(sql).doesNotContain("company_id");
    }
}
