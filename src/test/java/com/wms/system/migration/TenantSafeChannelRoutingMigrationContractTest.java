package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSafeChannelRoutingMigrationContractTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V4_51__Tenant_Safe_Channel_Routing.sql"
    );

    @Test
    void activeChannelEndpointRoutesToOnlyOneCompany() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
            .contains("canonical_store_identifier")
            .contains("uk_integration_configs_active_store_route")
            .contains("ON integration_configs (platform, canonical_store_identifier)")
            .contains("WHERE is_active = TRUE")
            .contains("CREATE TABLE IF NOT EXISTS channel_webhook_routes")
            .contains("uk_channel_webhook_routes_active_store");
    }

    @Test
    void webhookDedupIsCompanyScopedAndHistoricalDuplicatesAreNeutralized() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
            .contains("PARTITION BY company_id, webhook_event_id")
            .contains("uk_channel_raw_events_company_webhook_id")
            .contains("ON channel_raw_events (company_id, webhook_event_id)");
    }
}
