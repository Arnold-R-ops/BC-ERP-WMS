package com.wms.system.repository;

import com.wms.system.entity.Warehouse;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryTestSupportConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BusinessRepositoryTenantIsolationTest {

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void enableWarehouseRls() {
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION bcwms_current_company_id()
            RETURNS BIGINT LANGUAGE SQL STABLE AS $$
                SELECT NULLIF(current_setting('app.company_id', true), '')::BIGINT
            $$
            """);
        jdbcTemplate.execute("ALTER TABLE warehouses ENABLE ROW LEVEL SECURITY");
        jdbcTemplate.execute("ALTER TABLE warehouses FORCE ROW LEVEL SECURITY");
        jdbcTemplate.execute("DROP POLICY IF EXISTS company_isolation ON warehouses");
        jdbcTemplate.execute("""
            CREATE POLICY company_isolation ON warehouses
            FOR ALL
            USING (company_id = bcwms_current_company_id())
            WITH CHECK (company_id = bcwms_current_company_id())
            """);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        inCompany(10L, "alpha", () -> {
            inTransaction(() -> {
                warehouseRepository.deleteAll();
                warehouseRepository.flush();
                return null;
            });
            return null;
        });
        inCompany(20L, "beta", () -> {
            inTransaction(() -> {
                warehouseRepository.deleteAll();
                warehouseRepository.flush();
                return null;
            });
            return null;
        });
    }

    @Test
    void sameBusinessKeyAndFindByIdAreIsolatedAcrossTwoCompanies() {
        Long alphaId = inCompany(10L, "alpha", () -> saveWarehouse("MAIN", "Alpha warehouse"));
        Long betaId = inCompany(20L, "beta", () -> saveWarehouse("MAIN", "Beta warehouse"));

        inCompany(10L, "alpha", () -> {
            List<Warehouse> rows = inTransaction(() -> warehouseRepository.findAll());
            assertThat(rows).extracting(Warehouse::getName).containsExactly("Alpha warehouse");
            assertThat(inTransaction(() -> warehouseRepository.findById(betaId))).isEmpty();
            assertThat(inTransaction(() -> warehouseRepository.findByCode("MAIN")))
                .get().extracting(Warehouse::getId).isEqualTo(alphaId);
            return null;
        });

        inCompany(20L, "beta", () -> {
            List<Warehouse> rows = inTransaction(() -> warehouseRepository.findAll());
            assertThat(rows).extracting(Warehouse::getName).containsExactly("Beta warehouse");
            assertThat(inTransaction(() -> warehouseRepository.findById(alphaId))).isEmpty();
            return null;
        });
    }

    @Test
    void appSurfaceCannotWriteTenantOwnedEntity() {
        TenantContext app = new TenantContext(
            null, null, "app.bcwms.com", RequestSurface.APP, null);
        TenantContextHolder.runWithTenant(app, () ->
            assertThatThrownBy(() -> inTransaction(() -> saveWarehouse("APP", "Forbidden")))
                .hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void rawSqlIsAlsoIsolatedAndTransactionSettingDoesNotLeak() {
        inCompany(10L, "alpha", () -> saveWarehouse("RAW-A", "Alpha raw"));
        inCompany(20L, "beta", () -> saveWarehouse("RAW-B", "Beta raw"));

        inCompany(10L, "alpha", () -> {
            List<Long> visible = inTransaction(() -> jdbcTemplate.queryForList(
                "SELECT company_id FROM warehouses ORDER BY id", Long.class));
            assertThat(visible).containsExactly(10L);
            assertThat(inTransaction(() -> jdbcTemplate.queryForObject(
                "SELECT current_setting('app.company_id', true)", String.class)))
                .isEqualTo("10");
            assertThatThrownBy(() -> inTransaction(() -> jdbcTemplate.update(
                "INSERT INTO warehouses (company_id, code, name, isActive, created_at, updated_at) "
                    + "VALUES (20, 'CROSS', 'Forbidden', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            return null;
        });

        inCompany(20L, "beta", () -> {
            List<Long> visible = inTransaction(() -> jdbcTemplate.queryForList(
                "SELECT company_id FROM warehouses ORDER BY id", Long.class));
            assertThat(visible).containsExactly(20L);
            assertThat(inTransaction(() -> jdbcTemplate.queryForObject(
                "SELECT current_setting('app.company_id', true)", String.class)))
                .isEqualTo("20");
            return null;
        });
    }

    @Test
    void crossCompanyUpdateAndDeleteAreBlockedWithoutRevealingTheTarget() {
        Long alphaId = inCompany(10L, "alpha", () -> saveWarehouse("SAFE-A", "Alpha safe"));
        Long betaId = inCompany(20L, "beta", () -> saveWarehouse("SAFE-B", "Beta safe"));

        inCompany(10L, "alpha", () -> {
            assertThat(inTransaction(() -> jdbcTemplate.update(
                "UPDATE warehouses SET name = 'Intruded' WHERE id = ?", betaId))).isZero();
            assertThat(inTransaction(() -> jdbcTemplate.update(
                "DELETE FROM warehouses WHERE id = ?", betaId))).isZero();
            return null;
        });

        inCompany(20L, "beta", () -> {
            assertThat(inTransaction(() -> warehouseRepository.findById(betaId)))
                .get().extracting(Warehouse::getName).isEqualTo("Beta safe");
            assertThat(inTransaction(() -> warehouseRepository.findById(alphaId))).isEmpty();
            return null;
        });
    }

    private Long saveWarehouse(String code, String name) {
        return inTransaction(() -> warehouseRepository.saveAndFlush(
            Warehouse.builder().code(code).name(name).isActive(true).build()).getId());
    }

    private <T> T inCompany(Long companyId, String slug, java.util.function.Supplier<T> action) {
        TenantContext context = new TenantContext(
            companyId,
            slug,
            slug + ".bcwms.com",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        );
        return TenantContextHolder.runWithTenant(context, action);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }
}
