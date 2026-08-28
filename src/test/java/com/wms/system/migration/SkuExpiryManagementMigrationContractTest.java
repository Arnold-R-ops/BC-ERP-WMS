package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkuExpiryManagementMigrationContractTest {

    @Test
    void migrationPreservesLegacySemanticsAndDefaultsNewSkusToUnmanaged()
        throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_75__SKU_Expiry_Management.sql"
        )).replace("\r\n", "\n");

        assertThat(sql).contains(
            "ADD COLUMN IF NOT EXISTS expiry_management_enabled BOOLEAN",
            "FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP",
            "PERFORM set_config('app.company_id', company_record.id::TEXT, TRUE)",
            "SET expiry_management_enabled = TRUE",
            "WHERE company_id = company_record.id",
            "AND expiry_management_enabled IS NULL",
            "ALTER COLUMN expiry_management_enabled SET DEFAULT FALSE",
            "ALTER COLUMN expiry_management_enabled SET NOT NULL",
            "ALTER COLUMN expiry_date DROP NOT NULL"
        );
    }
}
