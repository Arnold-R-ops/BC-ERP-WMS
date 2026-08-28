package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OtherInboundMigrationContractTest {

    @Test
    void migrationArchivesLegacyFactsWithoutPostingStockAndRetiresOldAuthorities()
        throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_76__Controlled_Other_Inbound_Applications.sql"
        )).replace("\r\n", "\n");

        assertThat(sql).contains(
            "CREATE TABLE IF NOT EXISTS public.other_inbound_application",
            "CREATE TABLE IF NOT EXISTS public.other_inbound_application_item",
            "ALTER TABLE public.other_inbound_application FORCE ROW LEVEL SECURITY",
            "ALTER TABLE public.other_inbound_application_item FORCE ROW LEVEL SECURITY",
            "ADD COLUMN IF NOT EXISTS other_inbound_application_id BIGINT",
            "ADD COLUMN IF NOT EXISTS other_inbound_application_item_id BIGINT",
            "source_type = 'OTHER_INBOUND'",
            "other_inbound_application_id IS NOT NULL",
            "FOR company_record IN SELECT id FROM public.tenants ORDER BY id LOOP",
            "'Migrated read-only legacy inbound history'",
            "'Archived during P0-02 legacy migration; no inventory was replayed'",
            "'other_inbound:manage'",
            "'/api/other-inbound-applications/**;/api/other-inbound-policy'",
            "permission.permission_code IN (",
            "'inbound:create', 'inbound:approve_plan', 'inbound:reject'",
            "status = 'INACTIVE'"
        );
        assertThat(sql).doesNotContain(
            "INSERT INTO public.inventory_batches",
            "INSERT INTO public.stock_transactions",
            "UPDATE public.inventory_batches"
        );
        assertThat(sql).doesNotContain("/api/other-inbound-feature-policy");
    }
}
