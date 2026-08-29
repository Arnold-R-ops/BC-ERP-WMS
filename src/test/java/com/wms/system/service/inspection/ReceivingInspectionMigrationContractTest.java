package com.wms.system.service.inspection;

import org.junit.jupiter.api.Test;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivingInspectionMigrationContractTest {
    @Test
    void migrationEstablishesTenantSafeInspectionTruthWithoutAttachmentStorage() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_80__Receiving_Inspection_And_Release.sql"));

        assertThat(sql)
            .contains("receiving_inspection_task", "receiving_inspection_result",
                "receiving_arrival_exception", "PENDING_INSPECTION",
                "receiving_inspection:execute", "receiving_inspection:correct",
                "ENABLE ROW LEVEL SECURITY", "FORCE ROW LEVEL SECURITY")
            .doesNotContain("supplier_id", "carrier", "tracking_no", "attachment_blob",
                "photo_data", "base64");
    }
}
