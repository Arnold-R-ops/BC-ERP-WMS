package com.wms.system.service.inventory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryDispositionMigrationContractTest {
    @Test
    void migrationCreatesTenantSafeImmutableDispositionEvidenceWithoutBinaryAttachments() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_81__Inventory_Damage_Expiry_And_Disposition.sql"));

        assertThat(sql)
            .contains(
                "inventory_disposition_order",
                "inventory_disposition_line",
                "inventory_disposition_execution",
                "inventory_disposition_execution_line",
                "inventory_disposition_reservation_impact",
                "'DRAFT'", "'PARTIALLY_EXECUTED'", "'QUALITY_EXCEPTION'",
                "ENABLE ROW LEVEL SECURITY", "FORCE ROW LEVEL SECURITY",
                "prevent_inventory_disposition_evidence_mutation",
                "inventory_disposition:report", "inventory_disposition:execute",
                "inventory_disposition:review", "inventory_disposition:policy")
            .doesNotContain("supplier_id", "carrier", "tracking_no", "attachment_blob",
                "photo_data", "base64");
    }
}
