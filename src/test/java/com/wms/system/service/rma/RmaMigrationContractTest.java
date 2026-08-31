package com.wms.system.service.rma;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RmaMigrationContractTest {
    @Test
    void migrationCreatesLinkedTenantSafeRmaAndImmutableWarehouseEvidence() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_83__Sales_Return_RMA_Closed_Loop.sql"));

        assertThat(sql)
            .contains("sales_return_rma", "sales_return_channel_case", "sales_return_rma_line", "sales_return_task",
                "sales_return_receipt", "sales_return_inspection", "sales_return_event",
                "outbound_task_id BIGINT NOT NULL", "original_batch_id BIGINT NOT NULL",
                "RETURN_INSPECTION", "ENABLE ROW LEVEL SECURITY", "FORCE ROW LEVEL SECURITY",
                "prevent_sales_return_evidence_mutation", "sales.rma.standard_return_days', '5'",
                "rma:advance-resolution", "EXCHANGE_REPLACEMENT",
                "UNMATCHED_PENDING", "MATCHED_PENDING", "RMA_CREATED")
            .doesNotContain("'CRITICAL', TRUE")
            .doesNotContain("rma:unlinked:create", "attachment_blob", "photo_data", "base64",
                "payment_gateway_secret");
    }
}
