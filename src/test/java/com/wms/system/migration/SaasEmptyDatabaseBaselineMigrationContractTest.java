package com.wms.system.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SaasEmptyDatabaseBaselineMigrationContractTest {
    private static final Path BASELINE = Path.of(
        "src/main/resources/db/migration/B4_71__SaaS_Empty_Database_Baseline.sql"
    );

    @Test
    void baselineCapturesTheValidatedV471SchemaState() throws Exception {
        String sql = Files.readString(BASELINE);

        assertThat(count(sql, "(?m)^CREATE TABLE public\\.")).isEqualTo(77);
        assertThat(count(sql, "(?m)^CREATE SEQUENCE public\\.")).isEqualTo(75);
        assertThat(count(sql, "(?m)^CREATE FUNCTION public\\.")).isEqualTo(7);
        assertThat(count(sql, "(?m)^CREATE TRIGGER ")).isEqualTo(5);
        assertThat(count(sql, "(?m)^CREATE POLICY company_isolation ")).isEqualTo(51);
        assertThat(count(sql, " FORCE ROW LEVEL SECURITY;")).isEqualTo(51);
        assertThat(count(sql, " ENABLE ROW LEVEL SECURITY;")).isEqualTo(51);

        assertThat(sql).contains(
            "CREATE TABLE public.tenants",
            "CREATE TABLE public.platform_users",
            "CREATE TABLE public.platform_admin_commands",
            "CREATE TABLE public.platform_admin_invitation_active_emails",
            "CREATE TABLE public.platform_admin_invitation_activations",
            "CREATE SEQUENCE public.product_sku_code_seq",
            "CREATE FUNCTION public.bcwms_current_company_id()",
            "CREATE FUNCTION public.bcwms_purge_company_data(p_company_id bigint)",
            "CREATE TRIGGER trg_platform_audit_append_only",
            "ALTER TABLE ONLY public.users FORCE ROW LEVEL SECURITY",
            "CREATE POLICY company_isolation ON public.users"
        );
    }

    @Test
    void baselineContainsOnlyTheApprovedGlobalReferenceRows() throws Exception {
        String sql = Files.readString(BASELINE);
        Matcher matcher = Pattern.compile("(?m)^INSERT INTO public\\.([a-z_]+)").matcher(sql);
        Set<String> insertTargets = new LinkedHashSet<>();
        while (matcher.find()) {
            insertTargets.add(matcher.group(1));
        }

        assertThat(insertTargets).containsExactly(
            "platform_roles", "subscription_plans", "plan_limits"
        );
        assertThat(sql).contains(
            "('PLATFORM_SUPER_ADMIN', '平台超级管理员')",
            "('PLATFORM_OPERATIONS_ADMIN', '平台运营管理员')",
            "('PLATFORM_SECURITY_AUDITOR', '安全审计员')",
            "('PLATFORM_TENANT_READ', '公司数据读取')",
            "('PLATFORM_TENANT_EXPORT', '公司数据导出')",
            "('PLATFORM_TENANT_WRITE', '公司数据修改')",
            "('PLATFORM_TENANT_DELETE', '公司数据删除')",
            "('FREE', '免费版', TRUE, NULL, NULL)",
            "('TRIAL', '30 天全功能试用版', TRUE, 30, NULL)",
            "SELECT id, 'ALL_SERVICES', 'ENTITLEMENT', TRUE, NULL"
        );
        assertThat(sql).doesNotContain(
            "INSERT INTO public.tenants",
            "INSERT INTO public.tenant_domains",
            "INSERT INTO public.user_identities",
            "INSERT INTO public.tenant_memberships",
            "INSERT INTO public.users",
            "INSERT INTO public.platform_users",
            "INSERT INTO public.platform_user_roles",
            "INSERT INTO public.platform_admin_invitations",
            "example.invalid",
            "T7_SYNTHETIC_001"
        );
    }

    @Test
    void baselineIsPortableAndDoesNotOwnFlywayMetadata() throws Exception {
        String sql = Files.readString(BASELINE);

        assertThat(sql).doesNotContain(
            "flyway_schema_history",
            "OWNER TO",
            "GRANT ",
            "REVOKE ",
            "CREATE DATABASE",
            "CREATE SCHEMA",
            "CREATE EXTENSION",
            "COPY ",
            "\\restrict",
            "\\unrestrict",
            "SET transaction_timeout"
        );
    }

    private static long count(String value, String regex) {
        return Pattern.compile(regex).matcher(value).results().count();
    }
}
