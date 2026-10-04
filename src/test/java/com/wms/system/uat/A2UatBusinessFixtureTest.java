package com.wms.system.uat;

import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.Tenant;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit A2-only business fixture initializer.
 *
 * <p>This is deliberately a test utility, not the product's still-pending
 * versioned tenant baseline initializer. It refuses every database except the
 * isolated A2 naming convention, keeps credentials in runtime environment
 * variables, runs as the low-privilege application role and is safe to repeat
 * without creating duplicate business rows.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "A2_UAT_FIXTURES", matches = "true")
class A2UatBusinessFixtureTest {

    private static final String TENANT_A_SLUG = "a2-ordinary";
    private static final String TENANT_B_SLUG = "a2-optional";

    private static final List<PermissionSeed> BUSINESS_PERMISSION_CATALOGUE = List.of(
        menu("menu:product-catalog", "/catalog"),
        menu("menu:customers", "/master-data/clients"),
        menu("menu:suppliers", "/master-data/suppliers"),
        menu("menu:inventory", "/inventory"),
        menu("menu:sales", "/sales"),
        menu("menu:purchase", "/purchasing"),
        menu("menu:outbound", "/outbound"),
        menu("menu:stocktake", "/inventory-governance/stocktake"),
        menu("menu:inventory-correction", "/inventory-governance/corrections"),
        menu("menu:warehouse-setup", "/warehouse-setup"),
        menu("menu:warehouse-mobile", "/mobile"),
        menu("menu:reports", "/analytics"),

        api("category:create", "/api/categories/**", "*", "NORMAL"),
        api("category:update", "/api/categories/**", "*", "NORMAL"),
        api("category:delete", "/api/categories/**", "*", "HIGH"),
        api("category:view", "/api/categories/**", "GET", "NORMAL"),
        api("product:create", "/api/products/**", "*", "NORMAL"),
        api("product:edit", "/api/products/**", "*", "NORMAL"),
        api("product:status", "/api/products/**", "*", "HIGH"),
        api("product:view", "/api/products/**", "GET", "NORMAL"),
        api("product-sku:create", "/api/product-skus/**", "*", "NORMAL"),
        api("product-sku:edit", "/api/product-skus/**", "*", "NORMAL"),
        api("product-sku:status", "/api/product-skus/**", "*", "HIGH"),
        api("product-sku:view", "/api/product-skus/**", "GET", "NORMAL"),

        api("customer:create", "/api/customers/**", "*", "NORMAL"),
        api("customer:view", "/api/customers/**", "GET", "NORMAL"),
        api("customer:edit", "/api/customers/**", "*", "NORMAL"),
        api("customer:delete", "/api/customers/**", "DELETE", "HIGH"),
        api("supplier:create", "/api/suppliers/**", "*", "NORMAL"),
        api("supplier:view", "/api/suppliers/**", "GET", "NORMAL"),
        api("supplier:update", "/api/suppliers/**", "*", "NORMAL"),
        api("supplier:delete", "/api/suppliers/**", "DELETE", "HIGH"),

        api("inventory:view", "/api/inventory/**", "GET", "NORMAL"),
        api("inventory:correction:view", "/api/inventory-corrections/**;/api/emergency-stock-corrections/**", "GET", "NORMAL"),
        api("inventory:correction:execute", "/api/emergency-stock-corrections/**", "*", "HIGH"),
        api("inventory:correction:review", "/api/emergency-stock-corrections/**", "*", "HIGH"),
        api("inventory:correction:approve", "/api/emergency-stock-corrections/**", "*", "HIGH"),
        api("inventory:correction:reject", "/api/emergency-stock-corrections/**", "*", "HIGH"),

        api("purchase:view", "/api/purchase-orders/**;/api/purchase-asns/**;/api/purchase-governance-policy", "GET", "NORMAL"),
        api("purchase:create", "/api/purchase-orders/**", "*", "NORMAL"),
        api("purchase:update", "/api/purchase-orders/**", "*", "NORMAL"),
        api("purchase:submit", "/api/purchase-orders/**", "*", "NORMAL"),
        api("purchase:approve", "/api/purchase-orders/**", "*", "HIGH"),
        api("purchase:cancel", "/api/purchase-orders/**", "*", "HIGH"),
        api("purchase:void", "/api/purchase-orders/**", "*", "CRITICAL"),
        api("purchase:allocation:view", "/api/purchase-orders/**", "GET", "NORMAL"),
        api("purchase:allocation:edit", "/api/purchase-orders/**", "*", "HIGH"),
        api("purchase:asn:view", "/api/purchase-asns/**;/api/purchase-orders/**", "GET", "NORMAL"),
        api("purchase:asn:create", "/api/purchase-asns/**;/api/purchase-orders/**", "*", "NORMAL"),
        api("purchase:asn:update", "/api/purchase-asns/**", "*", "NORMAL"),
        api("purchase:asn:confirm", "/api/purchase-asns/**;/api/purchase-orders/**", "*", "HIGH"),
        api("purchase:asn:cancel", "/api/purchase-asns/**", "*", "HIGH"),
        api("purchase:asn:close", "/api/purchase-asns/**", "*", "HIGH"),
        api("purchase:request_close_remaining", "/api/purchase-orders/**;/api/purchase-asns/**", "*", "HIGH"),
        api("purchase:approve_close_remaining", "/api/purchase-orders/**;/api/purchase-asns/**", "*", "HIGH"),
        api("receiving:view", "/api/receiving-tasks/**;/api/receiving-plans/**;/api/purchase-orders/**", "GET", "NORMAL"),
        api("receiving:execute", "/api/receiving-tasks/**", "*", "HIGH"),
        api("receiving:plan:edit", "/api/receiving-plans/**", "*", "NORMAL"),
        api("other_inbound:manage", "/api/other-inbound-applications/**;/api/other-inbound-policy;/api/inbound-references/**", "*", "HIGH"),

        api("sales:create", "/api/sales-orders/**", "*", "NORMAL"),
        api("sales:view", "/api/sales-orders/**;/api/backorders/**", "GET", "NORMAL"),
        api("sales:edit", "/api/sales-orders/**", "*", "NORMAL"),
        api("sales:shipment:edit", "/api/sales-orders/**", "*", "NORMAL"),
        api("sales:approve", "/api/sales-orders/**", "*", "HIGH"),
        api("sales:reject", "/api/sales-orders/**", "*", "HIGH"),
        api("sales:cancel", "/api/sales-orders/**", "*", "HIGH"),
        api("sales:void", "/api/sales-orders/**", "*", "CRITICAL"),
        api("sales:close", "/api/sales-orders/**", "*", "HIGH"),
        api("sales:logistics:update", "/api/sales-orders/**", "*", "HIGH"),
        api("sales:policy:manage", "/api/sales-orders/policy", "*", "HIGH"),
        api("outbound:view", "/api/outbound-tasks/**", "GET", "NORMAL"),
        api("outbound:pick", "/api/outbound-tasks/**", "*", "HIGH"),

        api("stocktake:view", "/api/stocktake/**", "GET", "NORMAL"),
        api("stocktake:create", "/api/stocktake/**", "*", "NORMAL"),
        api("stocktake:count", "/api/stocktake/**", "*", "HIGH"),
        api("stocktake:review", "/api/stocktake/**", "*", "HIGH"),
        api("warehouse:view", "/api/warehouses/**", "GET", "NORMAL"),
        api("warehouse:manage", "/api/warehouses/**", "*", "HIGH"),
        api("location:view", "/api/locations/**", "GET", "NORMAL"),
        api("location:manage", "/api/locations/**", "*", "HIGH"),
        api("global:view", "/api/reports/**", "GET", "NORMAL"),
        api("integration:reconcile:view", "/api/integrations/reconciliation/**", "GET", "NORMAL")
    );

    private static final RoleSeed PURCHASER = role("PURCHASER", "采购员", false,
        "menu:product-catalog", "menu:suppliers", "menu:inventory", "menu:purchase",
        "category:view", "product:view", "product-sku:view", "supplier:create", "supplier:view", "supplier:update",
        "inventory:view", "purchase:view", "purchase:create", "purchase:update", "purchase:submit", "purchase:cancel",
        "purchase:allocation:view", "purchase:allocation:edit", "purchase:asn:view", "purchase:asn:create",
        "purchase:asn:update", "purchase:asn:confirm", "purchase:asn:cancel", "purchase:asn:close",
        "purchase:request_close_remaining", "receiving:view", "other_inbound:manage");

    private static final RoleSeed WAREHOUSE_ADMIN = role("WAREHOUSE_ADMIN", "仓库管理员", false,
        "menu:warehouse-mobile", "menu:product-catalog", "menu:inventory", "menu:outbound", "menu:stocktake", "menu:warehouse-setup",
        "category:create", "category:update", "category:delete", "category:view",
        "product:create", "product:edit", "product:status", "product:view",
        "product-sku:create", "product-sku:edit", "product-sku:status", "product-sku:view",
        "inventory:view", "receiving:view", "receiving:execute", "receiving:plan:edit",
        "outbound:view", "outbound:pick", "stocktake:view", "stocktake:create", "stocktake:count", "stocktake:review",
        "warehouse:view", "warehouse:manage", "location:view", "location:manage",
        "inventory_quality:view", "inventory_quality:hold", "inventory_quality:release",
        "receiving_inspection:view", "receiving_inspection:execute",
        "inventory_disposition:view", "inventory_disposition:report", "inventory_disposition:create", "inventory_disposition:execute",
        "rma:receive", "rma:inspect", "transfer:view", "transfer:create", "transfer:dispatch", "transfer:receive");

    private static final RoleSeed WAREHOUSE_STAFF = role("WAREHOUSE_STAFF", "仓库作业员", false,
        "menu:warehouse-mobile", "receiving:view", "receiving:execute", "outbound:view", "outbound:pick",
        "stocktake:view", "stocktake:count", "inventory_quality:view", "inventory_quality:hold",
        "receiving_inspection:view", "receiving_inspection:execute", "inventory_disposition:view",
        "rma:receive", "rma:inspect", "transfer:dispatch", "transfer:receive");

    private static final RoleSeed SALESPERSON = role("SALESPERSON", "销售员", false,
        "menu:product-catalog", "menu:customers", "menu:inventory", "menu:sales",
        "category:view", "product:view", "product-sku:view", "customer:create", "customer:view", "customer:edit",
        "inventory:view", "sales:create", "sales:view", "sales:edit", "sales:shipment:edit",
        "rma:view", "rma:create", "rma:resolve");

    private static final RoleSeed GENERAL_MANAGER = role("GENERAL_MANAGER", "总经理", false,
        "menu:product-catalog", "menu:customers", "menu:suppliers", "menu:inventory", "menu:sales", "menu:purchase",
        "menu:outbound", "menu:stocktake", "menu:inventory-correction", "menu:warehouse-setup", "menu:reports",
        "category:view", "product:view", "product-sku:view", "customer:view", "supplier:view", "inventory:view",
        "sales:view", "sales:approve", "sales:reject", "sales:cancel", "sales:close", "purchase:view", "purchase:approve",
        "purchase:approve_close_remaining", "receiving:view", "outbound:view", "stocktake:view", "stocktake:create", "stocktake:review",
        "inventory:correction:view", "inventory:correction:review", "inventory:correction:approve", "inventory:correction:reject",
        "warehouse:view", "location:view", "global:view", "rma:view", "rma:approve", "rma:resolve",
        "transfer:view", "transfer:approve", "transfer:close");

    private static final RoleSeed QUALITY_REVIEWER = role("QUALITY_REVIEWER", "审批与质检复核员", true,
        "menu:inventory", "menu:purchase", "inventory:view", "purchase:view", "purchase:approve", "purchase:approve_close_remaining",
        "inventory_quality:view", "inventory_quality:review", "receiving_inspection:view", "receiving_inspection:review",
        "receiving_inspection:correct", "inventory_disposition:view", "inventory_disposition:review",
        "rma:view", "rma:approve", "transfer:view", "transfer:approve");

    @DynamicPropertySource
    static void configureIsolatedDatabase(DynamicPropertyRegistry registry) {
        String url = required("A2_UAT_DB_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/wms_a2_uat_[0-9]{8}.*")) {
            throw new IllegalStateException("A2 UAT fixtures refuse a non-isolated database URL");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> required("A2_UAT_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("A2_UAT_DB_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("wms.legacy-admin-bootstrap-enabled", () -> false);
        registry.add("wms.tenancy.enabled", () -> false);
        registry.add("spring.main.web-application-type", () -> "none");
    }

    @Autowired TenantRepository tenantRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void seedAndVerifyTheConfirmedA2BusinessFixturesIdempotently() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
            .matches("wms_a2_uat_[0-9]{8}");
        Map<String, Object> roleSafety = jdbc.queryForMap("""
            select rolsuper, rolbypassrls from pg_roles where rolname = current_user
            """);
        assertThat(roleSafety.get("rolsuper")).isEqualTo(false);
        assertThat(roleSafety.get("rolbypassrls")).isEqualTo(false);

        Tenant tenantA = requireTenant(TENANT_A_SLUG);
        Tenant tenantB = requireTenant(TENANT_B_SLUG);
        String passwordPrefix = required("A2_UAT_STAFF_PASSWORD_PREFIX");

        seedTenant(tenantA, FixtureKind.ORDINARY, passwordPrefix);
        seedTenant(tenantB, FixtureKind.OPTIONAL_GOVERNANCE, passwordPrefix);
        FixtureSnapshot firstA = snapshot(tenantA);
        FixtureSnapshot firstB = snapshot(tenantB);

        seedTenant(tenantA, FixtureKind.ORDINARY, passwordPrefix);
        seedTenant(tenantB, FixtureKind.OPTIONAL_GOVERNANCE, passwordPrefix);
        FixtureSnapshot secondA = snapshot(tenantA);
        FixtureSnapshot secondB = snapshot(tenantB);

        assertThat(secondA).isEqualTo(firstA);
        assertThat(secondB).isEqualTo(firstB);
        assertThat(secondA).isEqualTo(new FixtureSnapshot(7, 6, 2, 8, 1, 1, 4, 1, 1));
        assertThat(secondB).isEqualTo(new FixtureSnapshot(4, 4, 1, 4, 1, 1, 4, 1, 1));
        assertTenantIsolation(tenantA, tenantB);
    }

    private void seedTenant(Tenant tenant, FixtureKind kind, String passwordPrefix) {
        withTenantTransaction(tenant, () -> {
            long companyId = tenant.getId();
            long ownerId = requireTenantAdminUser(companyId);
            seedBusinessPermissions(companyId);

            List<RoleSeed> roles = kind == FixtureKind.ORDINARY
                ? List.of(PURCHASER, WAREHOUSE_ADMIN, WAREHOUSE_STAFF, SALESPERSON, GENERAL_MANAGER)
                : List.of(PURCHASER, WAREHOUSE_STAFF, QUALITY_REVIEWER);
            for (RoleSeed role : roles) {
                long roleId = upsertRole(companyId, role);
                convergeRolePermissions(companyId, ownerId, roleId, role.permissions());
            }

            long firstWarehouseId = upsertWarehouse(companyId, "A2-WH-A", kind == FixtureKind.ORDINARY
                ? "A2 Ordinary Warehouse A" : "A2 Optional Warehouse");
            seedLocations(companyId, firstWarehouseId, "A2-WH-A");
            Long secondWarehouseId = null;
            if (kind == FixtureKind.ORDINARY) {
                secondWarehouseId = upsertWarehouse(companyId, "A2-WH-B", "A2 Ordinary Warehouse B");
                seedLocations(companyId, secondWarehouseId, "A2-WH-B");
            }

            Long salesOwnerId;
            if (kind == FixtureKind.ORDINARY) {
                upsertUser(companyId, ownerId, "a2a-purchaser", "A2 A 采购员", PURCHASER, passwordPrefix, null);
                upsertUser(companyId, ownerId, "a2a-warehouse-admin", "A2 A 仓库管理员", WAREHOUSE_ADMIN, passwordPrefix, null);
                upsertUser(companyId, ownerId, "a2a-worker-wh-a", "A2 A 仓库 A 作业员", WAREHOUSE_STAFF, passwordPrefix, firstWarehouseId);
                upsertUser(companyId, ownerId, "a2a-worker-wh-b", "A2 A 仓库 B 作业员", WAREHOUSE_STAFF, passwordPrefix, secondWarehouseId);
                salesOwnerId = upsertUser(companyId, ownerId, "a2a-sales", "A2 A 销售员", SALESPERSON, passwordPrefix, null);
                upsertUser(companyId, ownerId, "a2a-manager", "A2 A 总经理", GENERAL_MANAGER, passwordPrefix, null);
            } else {
                upsertUser(companyId, ownerId, "a2b-purchaser", "A2 B 采购员", PURCHASER, passwordPrefix, null);
                upsertUser(companyId, ownerId, "a2b-worker", "A2 B 仓库作业员", WAREHOUSE_STAFF, passwordPrefix, firstWarehouseId);
                upsertUser(companyId, ownerId, "a2b-reviewer", "A2 B 审批与质检复核员", QUALITY_REVIEWER, passwordPrefix, null);
                salesOwnerId = ownerId;
            }

            seedPolicies(companyId, kind);
            seedBusinessMasterData(companyId, salesOwnerId);
            assertConfirmedRoleBoundaries(companyId, kind);
        });
    }

    private void seedBusinessPermissions(long companyId) {
        int sortOrder = 10;
        for (PermissionSeed permission : BUSINESS_PERMISSION_CATALOGUE) {
            jdbc.update("""
                insert into sys_permission (
                    company_id, permission_code, permission_name, permission_type,
                    resource_path, http_method, menu_url, data_scope, risk_level,
                    custom_assignable, description, status, sort_order, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, 'ALL', ?, ?, ?, 'ACTIVE', ?, current_timestamp, current_timestamp)
                on conflict (company_id, permission_code) do update set
                    permission_name = excluded.permission_name,
                    permission_type = excluded.permission_type,
                    resource_path = excluded.resource_path,
                    http_method = excluded.http_method,
                    menu_url = excluded.menu_url,
                    risk_level = excluded.risk_level,
                    custom_assignable = excluded.custom_assignable,
                    description = excluded.description,
                    status = excluded.status,
                    sort_order = excluded.sort_order,
                    updated_at = current_timestamp
                """, companyId, permission.code(), permission.code(), permission.type(),
                permission.resourcePath(), permission.httpMethod(), permission.menuUrl(),
                permission.riskLevel(), !"CRITICAL".equals(permission.riskLevel()),
                "A2 UAT manual business catalogue", sortOrder++);
        }
    }

    private long upsertRole(long companyId, RoleSeed role) {
        return jdbc.queryForObject("""
            insert into sys_role (
                company_id, role_code, role_name, description, role_type,
                system_category, import_allowed, approval_template_code,
                review_status, status, sort_order,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', 'ACTIVE', ?, current_timestamp, current_timestamp)
            on conflict (company_id, role_code) do update set
                role_name = excluded.role_name,
                description = excluded.description,
                role_type = excluded.role_type,
                system_category = excluded.system_category,
                import_allowed = excluded.import_allowed,
                approval_template_code = excluded.approval_template_code,
                review_status = 'APPROVED',
                status = 'ACTIVE',
                updated_at = current_timestamp
            returning id
            """, Long.class, companyId, role.code(), role.name(),
            "A2 UAT manually initialized role", role.custom() ? "CUSTOM" : "SYSTEM",
            role.custom() ? null : "BUSINESS_TEMPLATE", !role.custom(),
            role.custom() ? "ROLE_PACKAGE_SIMPLE_APPROVAL" : null,
            role.custom() ? 90 : 50);
    }

    private void convergeRolePermissions(long companyId, long actorId, long roleId, Set<String> permissionCodes) {
        List<Long> permissionIds = jdbc.queryForList("""
            select id from sys_permission
            where company_id = ? and status = 'ACTIVE' and permission_code = any (?::text[])
            order by id
            """, Long.class, companyId, permissionCodes.toArray(String[]::new));
        assertThat(permissionIds).as("all role permissions exist for role id %s", roleId)
            .hasSize(permissionCodes.size());

        jdbc.update("""
            delete from sys_role_permission
            where company_id = ? and role_id = ? and not (permission_id = any (?::bigint[]))
            """, companyId, roleId, permissionIds.toArray(Long[]::new));
        for (Long permissionId : permissionIds) {
            jdbc.update("""
                insert into sys_role_permission (company_id, role_id, permission_id, granted_by, granted_at)
                values (?, ?, ?, ?, current_timestamp)
                on conflict (company_id, role_id, permission_id) do nothing
                """, companyId, roleId, permissionId, actorId);
        }
    }

    private long upsertWarehouse(long companyId, String code, String name) {
        return jdbc.queryForObject("""
            insert into warehouses (
                company_id, code, name, address, contact, is_active, isactive,
                created_at, updated_at
            ) values (?, ?, ?, 'A2 isolated UAT only', 'UAT', true, true, current_timestamp, current_timestamp)
            on conflict (company_id, code) do update set
                name = excluded.name, address = excluded.address, contact = excluded.contact,
                is_active = true, isactive = true, updated_at = current_timestamp
            returning id
            """, Long.class, companyId, code, name);
    }

    private void seedLocations(long companyId, long warehouseId, String warehouseCode) {
        List<LocationSeed> locations = List.of(
            new LocationSeed("ZONE_A", "A-01", "001", 1, 1),
            new LocationSeed("ZONE_B", "B-01", "001", 2, 1),
            new LocationSeed("ZONE_Q", "Q-01", "001", 3, 1),
            new LocationSeed("ZONE_R", "R-01", "001", 4, 1)
        );
        for (LocationSeed location : locations) {
            String locationCode = String.join("-", warehouseCode, location.zone(), location.shelf(), location.position());
            jdbc.update("""
                insert into locations (
                    company_id, warehouse_id, warehousecode, zone, shelfnumber,
                    positionnumber, locationcode, enabled, status, pos_x, pos_y,
                    remark, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, true, 'EMPTY', ?, ?, 'A2 isolated UAT location', current_timestamp, current_timestamp)
                on conflict (company_id, locationcode) do update set
                    warehouse_id = excluded.warehouse_id, warehousecode = excluded.warehousecode,
                    zone = excluded.zone, shelfnumber = excluded.shelfnumber,
                    positionnumber = excluded.positionnumber, enabled = true,
                    pos_x = excluded.pos_x, pos_y = excluded.pos_y, updated_at = current_timestamp
                """, companyId, warehouseId, warehouseCode, location.zone(), location.shelf(),
                location.position(), locationCode, location.posX(), location.posY());
        }
    }

    private long upsertUser(
            long companyId,
            long actorId,
            String username,
            String displayName,
            RoleSeed role,
            String passwordPrefix,
            Long warehouseId) {
        long roleId = requireRoleId(companyId, role.code());
        List<Long> existing = jdbc.queryForList(
            "select id from users where company_id = ? and username = ?", Long.class, companyId, username);
        long userId;
        if (existing.isEmpty()) {
            // These accounts exist only in the guarded A2 database. Keeping the
            // first-login reset disabled lets browser/API UAT test business
            // permissions without weakening the production account defaults.
            userId = jdbc.queryForObject("""
                insert into users (
                    company_id, username, password, default_role_id, displayname,
                    enabled, remark, must_change_password, security_version, is_deleted,
                    created_at, updated_at
                ) values (?, ?, ?, ?, ?, true, 'A2 isolated UAT account', false, 1, false, current_timestamp, current_timestamp)
                returning id
                """, Long.class, companyId, username,
                passwordEncoder.encode(passwordPrefix + username), roleId, displayName);
        } else {
            userId = existing.get(0);
            jdbc.update("""
                update users set password = ?, default_role_id = ?, displayname = ?, enabled = true,
                    must_change_password = false, is_deleted = false, updated_at = current_timestamp
                where company_id = ? and id = ?
                """, passwordEncoder.encode(passwordPrefix + username), roleId, displayName, companyId, userId);
        }

        jdbc.update("delete from sys_user_role where company_id = ? and user_id = ? and role_id <> ?",
            companyId, userId, roleId);
        jdbc.update("""
            insert into sys_user_role (company_id, user_id, role_id, assigned_by, assigned_at)
            values (?, ?, ?, ?, current_timestamp)
            on conflict (company_id, user_id, role_id) do nothing
            """, companyId, userId, roleId, actorId);

        if (warehouseId == null) {
            jdbc.update("delete from sys_user_warehouse where company_id = ? and user_id = ?", companyId, userId);
        } else {
            jdbc.update("delete from sys_user_warehouse where company_id = ? and user_id = ? and warehouse_id <> ?",
                companyId, userId, warehouseId);
            jdbc.update("""
                insert into sys_user_warehouse (company_id, user_id, warehouse_id, assigned_by, assigned_at)
                values (?, ?, ?, ?, current_timestamp)
                on conflict (company_id, user_id, warehouse_id) do nothing
                """, companyId, userId, warehouseId, actorId);
        }
        return userId;
    }

    private void seedPolicies(long companyId, FixtureKind kind) {
        boolean governed = kind == FixtureKind.OPTIONAL_GOVERNANCE;
        upsertConfig(companyId, "purchase.approval.mode", governed ? "SIMPLE" : "NONE", "STRING");
        upsertConfig(companyId, "purchase.short_receipt_closure.mode",
            governed ? "APPROVAL_REQUIRED" : "DIRECT_WITH_REASON", "STRING");
        upsertConfig(companyId, "receiving.inspection.default.required", Boolean.toString(governed), "BOOLEAN");
        upsertConfig(companyId, "receiving.inspection.review.mode", governed ? "REVIEW_REQUIRED" : "DIRECT", "STRING");
        upsertConfig(companyId, "inventory.quality.release.mode",
            governed ? "APPROVAL_REQUIRED" : "DIRECT_WITH_REASON", "STRING");
        upsertConfig(companyId, "sales.order.approval.mode", "DIRECT", "STRING");
        upsertConfig(companyId, "sales.approval.amount_threshold", "50000.00", "DECIMAL");
        upsertConfig(companyId, "sales.logistics.detailed.enabled", Boolean.toString(governed), "BOOLEAN");
        upsertConfig(companyId, "inventory.other_inbound.enabled", Boolean.toString(governed), "BOOLEAN");
        upsertConfig(companyId, "inventory.other_inbound.allowed_types", governed
            ? "AUTHORIZED_SPECIAL,CUSTOMER_SUPPLIED,FREE_SAMPLE,PRODUCTION_COMPLETION,SUBCONTRACT_RETURN"
            : "FREE_SAMPLE", "STRING");
        upsertConfig(companyId, "inventory.disposition.review.mode", "DIRECT_WITH_REASON", "STRING");
        upsertConfig(companyId, "inventory.disposition.auto_expiry.enabled", "true", "BOOLEAN");
        upsertConfig(companyId, "inventory.disposition.business_timezone", "Europe/London", "STRING");
        upsertConfig(companyId, "sales.rma.approval.mode", "DIRECT", "STRING");
        upsertConfig(companyId, "sales.rma.standard_return_days", "5", "INTEGER");
        upsertConfig(companyId, "sales.rma.refund_only.enabled", "false", "BOOLEAN");
        upsertConfig(companyId, "sales.rma.advance_resolution.enabled", "false", "BOOLEAN");
        upsertConfig(companyId, "sales.rma.quarantine_staging.enabled", "false", "BOOLEAN");
        upsertConfig(companyId, "transfer.approval.mode", "DIRECT", "STRING");
        upsertConfig(companyId, "transfer.receiving_inspection.enabled", "false", "BOOLEAN");
    }

    private void upsertConfig(long companyId, String key, String value, String type) {
        jdbc.update("""
            insert into system_config (
                company_id, config_key, config_value, description, config_type, created_at, updated_at
            ) values (?, ?, ?, 'A2 isolated UAT confirmed policy', ?, current_timestamp, current_timestamp)
            on conflict (company_id, config_key) do update set
                config_value = excluded.config_value,
                description = excluded.description,
                config_type = excluded.config_type,
                updated_at = current_timestamp
            """, companyId, key, value, type);
    }

    private void seedBusinessMasterData(long companyId, long salesOwnerId) {
        jdbc.update("""
            insert into suppliers (
                company_id, code, name, contact, address, email, phone, is_active,
                is_deleted, remark, created_at, updated_at
            ) values (?, 'A2-SUPPLIER', 'A2 UAT Supplier', 'Procurement fixture', 'A2 isolated UAT',
                'supplier@a2-uat.invalid', null, true, false, 'Not visible to warehouse roles', current_timestamp, current_timestamp)
            on conflict (company_id, code) do update set
                name = excluded.name, contact = excluded.contact, address = excluded.address,
                email = excluded.email, is_active = true, is_deleted = false, remark = excluded.remark,
                updated_at = current_timestamp
            """, companyId);
        jdbc.update("""
            insert into customers (
                company_id, code, name, customer_type, source, contact, email,
                normalized_email, address, credit_limit, is_active, owner_id, is_deleted,
                created_at, updated_at
            ) values (?, 'A2-CUSTOMER', 'A2 UAT Customer', 'CLIENT', 'MANUAL', 'Sales fixture',
                'customer@a2-uat.invalid', 'customer@a2-uat.invalid', 'A2 isolated UAT', 100000.00,
                true, ?, false, current_timestamp, current_timestamp)
            on conflict (company_id, code) do update set
                name = excluded.name, contact = excluded.contact, email = excluded.email,
                normalized_email = excluded.normalized_email, address = excluded.address,
                credit_limit = excluded.credit_limit, is_active = true, owner_id = excluded.owner_id,
                is_deleted = false, updated_at = current_timestamp
            """, companyId, salesOwnerId);

        long categoryId = jdbc.queryForObject("""
            insert into categories (
                company_id, category_code, category_name, sort_order, enabled,
                description, version, created_at, updated_at
            ) values (?, 'A2-UAT', 'A2 UAT Products', 900, true, 'A2 isolated UAT category', 0, current_timestamp, current_timestamp)
            on conflict (company_id, category_code) do update set
                category_name = excluded.category_name, enabled = true,
                description = excluded.description, updated_at = current_timestamp
            returning id
            """, Long.class, companyId);
        long productId = jdbc.queryForObject("""
            insert into products (
                company_id, product_code, product_name, category_id, brand,
                description, enabled, version, created_at, updated_at
            ) values (?, 'A2-UAT-PRODUCT', 'A2 UAT Product Family', ?, 'A2',
                'Four controlled SKU behaviours for A2', true, 0, current_timestamp, current_timestamp)
            on conflict (company_id, product_code) do update set
                product_name = excluded.product_name, category_id = excluded.category_id,
                brand = excluded.brand, description = excluded.description,
                enabled = true, updated_at = current_timestamp
            returning id
            """, Long.class, companyId, categoryId);

        upsertSku(companyId, productId, "A2-NORMAL-LABEL", "A2 Normal Printed Label", "A2BAR001", false, "EXEMPT", "PRINTED_LABEL");
        upsertSku(companyId, productId, "A2-NORMAL-VISUAL", "A2 Normal Location Visual", "A2BAR002", false, "EXEMPT", "LOCATION_VISUAL");
        upsertSku(companyId, productId, "A2-EXPIRY", "A2 Expiry Managed", "A2BAR003", true, "EXEMPT", "PRINTED_LABEL");
        upsertSku(companyId, productId, "A2-INSPECTION", "A2 Inspection Required", "A2BAR004", false, "REQUIRED", "PRINTED_LABEL");
    }

    private void upsertSku(
            long companyId,
            long productId,
            String skuCode,
            String name,
            String barcode,
            boolean expiryManaged,
            String inspectionRequirement,
            String trackingMode) {
        jdbc.update("""
            insert into product_skus (
                company_id, sku_code, product_id, sku_name, specs, barcode, name,
                specification, unitprice, minstock, leadtime, safety_stock,
                description, enabled, supplier, pack_unit, conversion_rate,
                min_sales_price, near_expiry_days, expiry_management_enabled,
                inspection_requirement, batch_tracking_mode, per_pack_qty,
                is_deleted, version, created_at, updated_at
            ) values (?, ?, ?, ?, 'A2 controlled fixture', ?, ?, 'Each', 10.00, 5, 7, 5,
                'A2 isolated UAT SKU', true, 'A2 UAT Supplier', 'Box', 12,
                8.00, 30, ?, ?, ?, 12, false, 0, current_timestamp, current_timestamp)
            on conflict (company_id, sku_code) do update set
                product_id = excluded.product_id, sku_name = excluded.sku_name,
                specs = excluded.specs, barcode = excluded.barcode, name = excluded.name,
                specification = excluded.specification, unitprice = excluded.unitprice,
                minstock = excluded.minstock, leadtime = excluded.leadtime,
                safety_stock = excluded.safety_stock, description = excluded.description,
                enabled = true, supplier = excluded.supplier, pack_unit = excluded.pack_unit,
                conversion_rate = excluded.conversion_rate, min_sales_price = excluded.min_sales_price,
                near_expiry_days = excluded.near_expiry_days,
                expiry_management_enabled = excluded.expiry_management_enabled,
                inspection_requirement = excluded.inspection_requirement,
                batch_tracking_mode = excluded.batch_tracking_mode,
                per_pack_qty = excluded.per_pack_qty, is_deleted = false,
                updated_at = current_timestamp
            """, companyId, skuCode, productId, name, barcode, name,
            expiryManaged, inspectionRequirement, trackingMode);
    }

    private void assertConfirmedRoleBoundaries(long companyId, FixtureKind kind) {
        Set<String> warehouseAdminPermissions = kind == FixtureKind.ORDINARY
            ? permissionCodes(companyId, "WAREHOUSE_ADMIN") : Set.of();
        Set<String> warehouseStaffPermissions = permissionCodes(companyId, "WAREHOUSE_STAFF");
        assertThat(warehouseAdminPermissions).doesNotContain("supplier:view", "menu:suppliers", "purchase:view", "menu:purchase");
        assertThat(warehouseStaffPermissions).doesNotContain("supplier:view", "menu:suppliers", "purchase:view", "menu:purchase");
        assertThat(warehouseStaffPermissions).contains("receiving:view", "receiving:execute", "menu:warehouse-mobile");

        if (kind == FixtureKind.OPTIONAL_GOVERNANCE) {
            assertThat(permissionCodes(companyId, "PURCHASER"))
                .contains("purchase:request_close_remaining")
                .doesNotContain("purchase:approve", "purchase:approve_close_remaining");
            assertThat(permissionCodes(companyId, "QUALITY_REVIEWER"))
                .contains("purchase:approve", "purchase:approve_close_remaining",
                    "receiving_inspection:review", "inventory_quality:review");
        }
    }

    private FixtureSnapshot snapshot(Tenant tenant) {
        return withTenantTransaction(tenant, () -> new FixtureSnapshot(
            count("users", tenant.getId()),
            count("sys_role", tenant.getId()),
            count("warehouses", tenant.getId()),
            count("locations", tenant.getId()),
            count("categories", tenant.getId()),
            count("products", tenant.getId()),
            count("product_skus", tenant.getId()),
            count("suppliers", tenant.getId()),
            count("customers", tenant.getId())
        ));
    }

    private void assertTenantIsolation(Tenant tenantA, Tenant tenantB) {
        withTenantTransaction(tenantA, () -> {
            assertThat(jdbc.queryForObject(
                "select count(*) from product_skus where company_id = ? and sku_code = 'A2-NORMAL-LABEL'",
                Integer.class, tenantA.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "select count(*) from product_skus where company_id = ? and sku_code = 'A2-NORMAL-LABEL'",
                Integer.class, tenantB.getId())).isZero();
            assertThat(jdbc.queryForObject(
                "select count(*) from warehouses where company_id = ? and code = 'A2-WH-A'",
                Integer.class, tenantB.getId())).isZero();
        });
        withTenantTransaction(tenantB, () -> {
            assertThat(jdbc.queryForObject(
                "select count(*) from product_skus where company_id = ? and sku_code = 'A2-NORMAL-LABEL'",
                Integer.class, tenantB.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "select count(*) from product_skus where company_id = ? and sku_code = 'A2-NORMAL-LABEL'",
                Integer.class, tenantA.getId())).isZero();
        });
    }

    private Set<String> permissionCodes(long companyId, String roleCode) {
        return new LinkedHashSet<>(jdbc.queryForList("""
            select permission.permission_code
            from sys_role role
            join sys_role_permission assignment
              on assignment.company_id = role.company_id and assignment.role_id = role.id
            join sys_permission permission
              on permission.company_id = assignment.company_id and permission.id = assignment.permission_id
            where role.company_id = ? and role.role_code = ?
            order by permission.permission_code
            """, String.class, companyId, roleCode));
    }

    private long requireTenantAdminUser(long companyId) {
        List<Long> ids = jdbc.queryForList("""
            select account.id
            from users account
            join sys_user_role assignment
              on assignment.company_id = account.company_id and assignment.user_id = account.id
            join sys_role role
              on role.company_id = assignment.company_id and role.id = assignment.role_id
            where account.company_id = ? and role.role_code = 'TENANT_ADMIN'
              and account.enabled = true and account.is_deleted = false
            order by account.id
            """, Long.class, companyId);
        assertThat(ids).as("one active A2 tenant administrator").hasSize(1);
        return ids.get(0);
    }

    private long requireRoleId(long companyId, String roleCode) {
        return jdbc.queryForObject(
            "select id from sys_role where company_id = ? and role_code = ?",
            Long.class, companyId, roleCode);
    }

    private int count(String table, long companyId) {
        Set<String> allowed = Set.of("users", "sys_role", "warehouses", "locations", "categories",
            "products", "product_skus", "suppliers", "customers");
        if (!allowed.contains(table)) {
            throw new IllegalArgumentException("Unexpected A2 fixture table: " + table);
        }
        return jdbc.queryForObject("select count(*) from " + table + " where company_id = ?", Integer.class, companyId);
    }

    private Tenant requireTenant(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
            .orElseThrow(() -> new IllegalStateException("Run A2UatTenantProvisioningTest first for " + slug));
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getTimezone()).isEqualTo("Europe/London");
        return tenant;
    }

    private void withTenantTransaction(Tenant tenant, Runnable action) {
        withTenantTransaction(tenant, () -> {
            action.run();
            return null;
        });
    }

    private <T> T withTenantTransaction(Tenant tenant, java.util.function.Supplier<T> action) {
        TenantContext context = new TenantContext(
            tenant.getId(), tenant.getSlug(), tenant.getSlug() + ".bcwms.com",
            RequestSurface.TENANT, tenant.getStatus());
        return TenantContextHolder.runWithTenant(context, () -> {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            return transaction.execute(status -> {
                jdbc.queryForObject("select set_config('app.company_id', ?, true)", String.class,
                    tenant.getId().toString());
                return action.get();
            });
        });
    }

    private static PermissionSeed menu(String code, String url) {
        return new PermissionSeed(code, "MENU", null, null, url, "NORMAL");
    }

    private static PermissionSeed api(String code, String path, String method, String risk) {
        return new PermissionSeed(code, "API", path, method, null, risk);
    }

    private static RoleSeed role(String code, String name, boolean custom, String... permissions) {
        return new RoleSeed(code, name, custom, new LinkedHashSet<>(List.of(permissions)));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for explicit A2 fixtures");
        }
        return value;
    }

    private enum FixtureKind { ORDINARY, OPTIONAL_GOVERNANCE }

    private record PermissionSeed(
        String code,
        String type,
        String resourcePath,
        String httpMethod,
        String menuUrl,
        String riskLevel
    ) { }

    private record RoleSeed(String code, String name, boolean custom, Set<String> permissions) { }

    private record LocationSeed(String zone, String shelf, String position, int posX, int posY) { }

    private record FixtureSnapshot(
        int users,
        int roles,
        int warehouses,
        int locations,
        int categories,
        int products,
        int skus,
        int suppliers,
        int customers
    ) { }
}
