package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SaasControlPlaneMigrationContractTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V4_50__SaaS_Control_Plane_Foundation.sql"
    );

    @Test
    void migrationDefinesControlPlaneWithoutDefaultCompanyColumns() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tenants"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tenant_domains"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS subscription_plans"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS signup_requests"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS platform_roles"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS platform_user_roles"));
        assertFalse(sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS tenants"),
            sql.indexOf("CREATE TABLE IF NOT EXISTS tenant_domains")).contains("company_id"));
    }

    @Test
    void migrationEncodesConfirmedTrialAndRetentionRules() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("INTERVAL '30 days'"));
        assertTrue(sql.contains("'TRIAL', '30 天全功能试用版', TRUE, 30, NULL"));
        assertTrue(sql.contains("limit_kind IN ('ENTITLEMENT', 'QUOTA')"));
        assertFalse(sql.contains("('MAX_USERS', 1::BIGINT)"));
        assertFalse(sql.contains("('MAX_WAREHOUSES', 1::BIGINT)"));
        assertFalse(sql.contains("('MAX_SKUS', 100::BIGINT)"));
        assertTrue(sql.contains("'ALL_SERVICES', 'ENTITLEMENT', TRUE"));
    }
}
