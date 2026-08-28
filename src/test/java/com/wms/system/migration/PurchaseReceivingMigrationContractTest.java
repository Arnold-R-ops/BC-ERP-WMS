package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseReceivingMigrationContractTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V4_74__Purchase_ASN_And_Unified_Receiving.sql");

    @Test
    void migrationContainsTenantIsolationAtomicKeysAndNoExpiryColumnRewrite() throws Exception {
        String sql = Files.readString(MIGRATION);
        for (String table : new String[]{
            "purchase_order_allocation", "purchase_asn", "purchase_asn_item",
            "receiving_plan", "receiving_task", "receiving_task_item",
            "receipt_event", "receipt_event_item"
        }) {
            assertTrue(sql.contains("public." + table));
        }
        assertTrue(sql.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("uk_receipt_event_company_idempotency"));
        assertTrue(sql.contains("uk_receipt_event_item_posting"));
        assertTrue(sql.contains("source_type = 'OTHER_INBOUND'"));
        assertTrue(sql.contains("batch.entry_date IS NULL"));
        assertTrue(sql.contains("batch.active = TRUE"));
        assertTrue(sql.contains("WHEN status IN ('ORDERING', 'IN_TRANSIT') THEN 'DRAFT'"));
        assertTrue(sql.contains("WHEN status = 'PARTIALLY_RECEIVED' THEN 'CLOSED'"));
        assertTrue(sql.contains("no stock replay"));
        assertTrue(sql.contains("('purchase:asn:update','修改采购ASN草稿','/api/purchase-asns/*','PUT'"));
        assertTrue(sql.contains("'purchase:asn:view','purchase:asn:create','purchase:asn:update','purchase:asn:confirm'"));
        assertTrue(sql.contains("role.role_code IN ('TENANT_ADMIN','GENERAL_MANAGER')"));
        assertFalse(sql.contains("ALTER COLUMN expiry_date DROP NOT NULL"));
    }
}
