package com.wms.system.tenant.security;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Only the explicitly named disposable cluster is accepted, never the normal test/development DB. */
@EnabledIfSystemProperty(named = "p3.r2.database-authorized", matches = "true")
class TenantSecurityPostgresTest {
    static String url;
    static javax.sql.DataSource dataSource;
    static JdbcTemplate jdbc;
    static long tenantA, tenantB;
    static com.wms.platformverification.PlatformFixtureScope fixtures;

    @BeforeAll static void migrate() throws Exception {
        url = com.wms.platformverification.PlatformR2Isolation.url(com.wms.platformverification.PlatformR2Isolation.MAIN);
        dataSource = com.wms.platformverification.PlatformR2Isolation.runtimeDataSource();
        jdbc = new JdbcTemplate(dataSource);
        assertFalse(Boolean.TRUE.equals(jdbc.queryForObject(
            "select rolsuper or rolbypassrls from pg_roles where rolname=current_user", Boolean.class)));
        // Prepared schema/fixture is required; tests never migrate or create database objects.
        fixtures=com.wms.platformverification.PlatformFixtureScope.open(jdbc,new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),TenantSecurityPostgresTest.class.getName(),"suiteFixture");
        tenantA = tenant("p3-foundation-a"); tenantB = tenant("p3-foundation-b");
        fixtures.ownTenant(tenantA); fixtures.ownTenant(tenantB);
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("select relrowsecurity and relforcerowsecurity from pg_class where oid='p3_m1_probe'::regclass", Boolean.class)));
    }
    static long tenant(String slug) {
        return jdbc.queryForObject("insert into tenants(tenant_code,display_name,slug,status,timezone,locale,version) values(?,?,?,'ACTIVE','UTC','en-US',0) returning id",
            Long.class, "P3-" + java.util.UUID.randomUUID(), slug, "p3-" + java.util.UUID.randomUUID().toString().substring(0, 20));
    }
    @AfterAll static void retainOwnedTenants() { if(fixtures!=null) fixtures.close(); }
    @BeforeEach void active() {
        jdbc.update("update tenants set status='ACTIVE',session_version=1 where id in (?,?)", tenantA, tenantB);
    }
    @Test void committedSuspensionInvalidatesOldEpochAndNeverRevivesIt() {
        var service = new TenantSecurityStateService(jdbc);
        service.require(tenantA, TenantOperationPurpose.BUSINESS, 1L);
        jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?", tenantA);
        assertThrows(TenantAccessException.class, () -> service.require(tenantA, TenantOperationPurpose.BUSINESS, 1L));
        service.require(tenantA, TenantOperationPurpose.AUTHENTICATION, 2L);
        jdbc.update("update tenants set status='ACTIVE',session_version=3 where id=?", tenantA);
        assertThrows(TenantAccessException.class, () -> service.require(tenantA, TenantOperationPurpose.BUSINESS, 1L));
        assertThrows(TenantAccessException.class, () -> service.require(tenantA, TenantOperationPurpose.BUSINESS, 2L));
        service.require(tenantA, TenantOperationPurpose.BUSINESS, 3L);
        service.require(tenantB, TenantOperationPurpose.BUSINESS, 1L);
    }
    @Test void pauseCannotCommitAheadOfAlreadyAdmittedWriteTransaction() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection writer = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            assertEquals(1L, check(writer, tenantA, 1, "BUSINESS", true));
            CountDownLatch started = new CountDownLatch(1);
            Future<Integer> pause = pool.submit(() -> {
                started.countDown();
                return jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?", tenantA);
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> pause.get(150, TimeUnit.MILLISECONDS));
            writer.commit();
            assertEquals(1, pause.get(5, TimeUnit.SECONDS));
            assertThrows(SQLException.class, () -> check(writer, tenantA, 1, "BUSINESS", true));
            writer.rollback();
        } finally { pool.shutdownNow(); }
    }
    @Test void preloadedTaskCannotWriteAfterPauseAndRestoreEvenWithActiveStatus() throws Exception {
        jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?", tenantA);
        jdbc.update("update tenants set status='ACTIVE',session_version=3 where id=?", tenantA);
        try (Connection connection = dataSource.getConnection()) {
            assertThrows(SQLException.class, () -> check(connection, tenantA, 1, "BUSINESS", true));
            assertEquals(3, check(connection, tenantA, 3, "BUSINESS", true));
        }
    }
    @Test void suspendedPurposeMatrixAndRlsAreIndependent() throws Exception {
        jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?", tenantA);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            assertEquals(2, check(connection, tenantA, 2, "PLATFORM_READ", false));
            try (PreparedStatement setting = connection.prepareStatement("select set_config('app.company_id',?,true)")) {
                setting.setString(1, Long.toString(tenantA)); setting.execute();
            }
            try (PreparedStatement insert = connection.prepareStatement("insert into p3_m1_probe values(?, 'isolated')")) {
                insert.setLong(1, tenantB);
                assertThrows(SQLException.class, insert::executeUpdate);
            }
            connection.rollback();
            assertThrows(SQLException.class, () -> check(connection, tenantA, 2, "BUSINESS", true));
            connection.rollback();
        }
    }
    static long check(Connection connection, long tenant, long epoch, String purpose, boolean write) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select bcwms_check_tenant_activity(?,?,?,?)")) {
            statement.setLong(1, tenant); statement.setLong(2, epoch); statement.setString(3, purpose); statement.setBoolean(4, write);
            try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getLong(1); }
        }
    }
}
