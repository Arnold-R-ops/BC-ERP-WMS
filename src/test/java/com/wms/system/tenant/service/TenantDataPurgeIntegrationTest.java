package com.wms.system.tenant.service;

import com.wms.system.entity.Warehouse;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TenantDataPurgeIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantRepository tenants;
    @Autowired TenantPurgeJobRepository jobs;
    @Autowired WarehouseRepository warehouses;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TenantDataPurgeService purgeService;

    @BeforeEach void installPurgeFunction() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V4_55__Tenant_Expired_Data_Purge.sql"));
        int start = sql.indexOf("-- Tenant-side append-only evidence");
        jdbc.execute(sql.substring(start));
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void purgesOnlyTargetCompanyAndKeepsTombstoneAndPlatformAudit() {
        Tenant alpha = tenants.saveAndFlush(tenant("purge-alpha", TenantStatus.PURGE_PENDING));
        Tenant beta = tenants.saveAndFlush(tenant("keep-beta", TenantStatus.ACTIVE));
        inCompany(alpha, () -> warehouses.saveAndFlush(warehouse("A", "Alpha")));
        inCompany(beta, () -> warehouses.saveAndFlush(warehouse("B", "Beta")));
        jdbc.update("insert into platform_users (normalized_email,password_hash,display_name,enabled,mfa_enabled,mfa_failed_attempts,security_version,created_at,updated_at) "
            + "values (?,?,?,?,?,?,?,current_timestamp,current_timestamp)",
            "purge-auditor@example.test", "not-a-real-hash", "Purge auditor", true, false, 0, 1);
        Long platformUserId = jdbc.queryForObject(
            "select id from platform_users where normalized_email=?", Long.class,
            "purge-auditor@example.test");
        jdbc.update("insert into platform_audit_logs "
            + "(platform_user_id,target_tenant_id,action,result,created_at) values (?,?,?,?,current_timestamp)",
            platformUserId, alpha.getId(), "READ", "SUCCESS");

        purgeService.purgeDueCompany(alpha.getId());

        Tenant tombstone = tenants.findById(alpha.getId()).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(TenantStatus.PURGED);
        assertThat(tombstone.getSlug()).isEqualTo("purged-" + alpha.getId());
        assertThat(jdbc.queryForObject(
            "select count(*) from platform_audit_logs where target_tenant_id=?", Long.class, alpha.getId()))
            .isEqualTo(1L);
        assertThat(jobs.findByTenantId(alpha.getId())).get()
            .extracting(TenantPurgeJob::getStatus).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
            "select count(*) from tenant_purge_audit_logs where tenant_id=? and result='SUCCESS'",
            Long.class, alpha.getId())).isEqualTo(1L);
        inCompany(beta, () -> assertThat(warehouses.findAll())
            .extracting(Warehouse::getName).containsExactly("Beta"));
    }

    private Tenant tenant(String slug, TenantStatus status) {
        return Tenant.builder().tenantCode("T-" + slug).displayName(slug).slug(slug)
            .status(status).purgeDueAt(status == TenantStatus.PURGE_PENDING
                ? OffsetDateTime.now().minusMinutes(1) : null)
            .timezone("UTC").locale("en").createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now()).build();
    }
    private Warehouse warehouse(String code, String name) {
        return Warehouse.builder().code(code + "1").name(name).isActive(true).build();
    }
    private void inCompany(Tenant tenant, Runnable action) {
        TenantContext context = new TenantContext(tenant.getId(), tenant.getSlug(),
            tenant.getSlug() + ".bcwms.com", RequestSurface.TENANT, tenant.getStatus());
        TenantContextHolder.runWithTenant(context,
            () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run()));
    }
}
