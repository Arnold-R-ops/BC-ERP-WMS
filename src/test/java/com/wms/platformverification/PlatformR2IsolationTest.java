package com.wms.platformverification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No Spring application, real driver, socket, database or service is used in these tests. */
class PlatformR2IsolationTest {
    Properties valid() {
        var p = new Properties();
        p.setProperty("p3.r2.database-authorized", "true");
        p.setProperty("p3.test.jdbcUrl", PlatformR2Isolation.url(PlatformR2Isolation.MAIN));
        p.setProperty("p3.r2.app-password", "synthetic-unit-fixture-only");
        p.setProperty("p3.r2.cluster-id", "1234567890123456789");
        return p;
    }
    @Test void noPermissionNoDefaultConnection() {
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.settings(new Properties(), false, PlatformR2Isolation.MAIN));
    }
    @ParameterizedTest @ValueSource(strings={"", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r10", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r9", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r8", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r7", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r6", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r5", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r4", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r3", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r2", "jdbc:postgresql://localhost:55449/p3_m1_platform_release_r11", "jdbc:postgresql://127.0.0.1:5432/wms_db_test",
        "jdbc:postgresql://127.0.0.1:55439/p3_m1_platform_browser_scope", "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r11?user=postgres",
        "jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_upgrade_r11", "jdbc:postgresql://192.0.2.1:55449/p3_m1_platform_release_r11"})
    void wrongDestinationsRejectedBeforeAnyConnector(String url) throws Exception {
        var connector = mock(PlatformR2Isolation.Connector.class);
        var p = valid(); p.setProperty("p3.test.jdbcUrl", url);
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.guarded(PlatformR2Isolation.settings(p, false, PlatformR2Isolation.MAIN), connector));
        verifyNoInteractions(connector);
    }
    @ParameterizedTest @ValueSource(strings={"p3.r2.app-password", "p3.r2.cluster-id", "p3.test.jdbcUrl", "p3.r2.database-authorized"})
    void missingRequiredInputFailsClosed(String key) {
        var p = valid(); p.remove(key);
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.settings(p, false, PlatformR2Isolation.MAIN));
    }
    @ParameterizedTest @ValueSource(strings={"spring.datasource.url", "spring.flyway.url", "spring.datasource.jndi-name", "spring.datasource.username"})
    void conflictingDefaultConfigurationIsRejected(String key) {
        var p = valid(); p.setProperty(key, "unapproved-default");
        assertThrows(IllegalStateException.class, () -> PlatformR2SpringInitializer.fixedProperties(p));
    }
    @Test void exactSettingsDisableAutomaticMigrationsAndDdl() {
        var values = PlatformR2SpringInitializer.fixedProperties(valid());
        assertEquals("false", values.get("spring.flyway.enabled"));
        assertEquals("never", values.get("spring.sql.init.mode"));
        assertEquals("validate", values.get("spring.jpa.hibernate.ddl-auto"));
        assertEquals("NONE", values.get("spring.test.mockmvc.print"));
        assertEquals(PlatformR2Isolation.url(PlatformR2Isolation.MAIN), values.get("spring.datasource.url"));
    }
    @Test void runtimeCannotUseMigratorOrUpgradeTarget() {
        var settings = PlatformR2Isolation.settings(valid(), false, PlatformR2Isolation.MAIN);
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.guarded(new PlatformR2Isolation.Settings(settings.database(), PlatformR2Isolation.MIGRATOR, settings.password(), settings.clusterId()), () -> null));
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.settings(valid(), false, PlatformR2Isolation.UPGRADE));
        assertFalse(settings.toString().contains(settings.password()));
    }
    @Test void runtimePermissionDoesNotAuthorizeMigration() {
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.settings(valid(), true, PlatformR2Isolation.MAIN));
    }
    ResultSet validIdentity() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.next()).thenReturn(true, false);
        when(row.getString(1)).thenReturn(PlatformR2Isolation.MAIN); when(row.getString(2)).thenReturn("127.0.0.1");
        when(row.getInt(3)).thenReturn(55449); when(row.getString(4)).thenReturn(PlatformR2Isolation.APP);
        when(row.getString(7)).thenReturn(PlatformR2Isolation.DIRECTORY); when(row.getString(8)).thenReturn("1234567890123456789");
        return row;
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8})
    void everyActualIdentityMismatchClosesConnection(int column) throws Exception {
        var connection = mock(Connection.class); var statement = mock(Statement.class); var row = validIdentity();
        when(connection.createStatement()).thenReturn(statement); when(statement.executeQuery(anyString())).thenReturn(row);
        if(column==3) when(row.getInt(3)).thenReturn(5432);
        else if(column==5 || column==6) when(row.getBoolean(column)).thenReturn(true);
        else when(row.getString(column)).thenReturn("wrong");
        var source = PlatformR2Isolation.guarded(PlatformR2Isolation.settings(valid(), false, PlatformR2Isolation.MAIN), () -> connection);
        assertThrows(SQLException.class, source::getConnection); verify(connection).close();
        verify(statement, times(1)).executeQuery(anyString());
    }
    @Test void migrationSequenceCannotOmitOrReorderARequiredVersion() throws Exception {
        var connection = mock(Connection.class); var statement = mock(Statement.class); var rows = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement); when(statement.executeQuery(anyString())).thenReturn(rows);
        when(rows.next()).thenReturn(true,true,false); when(rows.getString(1)).thenReturn("4.90","4.89"); when(rows.getBoolean(2)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.verifyVersions(connection, PlatformR2Isolation.VERSIONS));
    }
    @Test void unsupportedMigrationCommandsNeverReachConnectionCreation() {
        for(String command : List.of("clean", "repair", "baseline", "create", "start", "anything")) assertThrows(IllegalStateException.class, () -> PlatformR2DatabasePreparer.step(command));
    }
    @Test void approvedMigrationStepsHaveExactBeforeAndAfterStates() {
        var initial = PlatformR2DatabasePreparer.step("prepare-release");
        assertTrue(initial.before().isEmpty()); assertEquals(PlatformR2Isolation.VERSIONS, initial.after());
        assertEquals(6, PlatformR2DatabasePreparer.step("upgrade-to-88").after().size());
        assertEquals(List.of("4.89"), PlatformR2DatabasePreparer.step("upgrade-to-89").after().subList(6,7));
        assertEquals(List.of("4.90"), PlatformR2DatabasePreparer.step("upgrade-to-90").after().subList(7,8));
    }
    @Test void connectionCredentialOverrideIsRejectedWithoutOpeningDriver() throws Exception {
        var connector = mock(PlatformR2Isolation.Connector.class);
        var source = PlatformR2Isolation.guarded(PlatformR2Isolation.settings(valid(), false, PlatformR2Isolation.MAIN), connector);
        assertThrows(IllegalStateException.class, () -> source.getConnection("postgres", "ignored")); verifyNoInteractions(connector);
    }
    @Test void realCompiledMigrationResourcesHaveStableFingerprint() {
        assertTrue(PlatformR2Isolation.migrationFingerprint().matches("[a-f0-9]{64}"));
        assertEquals(PlatformR2Isolation.migrationFingerprint(), PlatformR2Isolation.migrationFingerprint());
    }
    Connection preparedConnection(String fingerprint) throws Exception {
        var connection = mock(Connection.class); var statement = mock(Statement.class);
        var versions = mock(ResultSet.class); var ready = mock(ResultSet.class); var identity = validIdentity(); var history = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(contains("pg_roles"))).thenReturn(identity);
        when(statement.executeQuery(contains("select version, success"))).thenReturn(versions);
        when(statement.executeQuery(contains("select version,type"))).thenReturn(history);
        when(history.next()).thenReturn(false);
        when(versions.next()).thenReturn(true,true,true,true,true,true,true,true,false);
        when(versions.getBoolean(2)).thenReturn(true);
        when(versions.getString(1)).thenReturn("4.71","4.72","4.85","4.86","4.87","4.88","4.89","4.90");
        when(statement.executeQuery(contains("p3_r11_control.ready"))).thenReturn(ready);
        when(ready.next()).thenReturn(true,false); when(ready.getString(1)).thenReturn(fingerprint);
        when(ready.getString(2)).thenReturn(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[0])));
        return connection;
    }
    @Test void matchingMockIdentityHistoryAndSourceDigestReleasesOnlyVerifiedConnection() throws Exception {
        var connection = preparedConnection(PlatformR2Isolation.migrationFingerprint());
        var source = PlatformR2Isolation.guarded(PlatformR2Isolation.settings(valid(),false,PlatformR2Isolation.MAIN), () -> connection);
        assertSame(connection,source.getConnection()); verify(connection,never()).close();
    }
    @Test void changedSourceDigestClosesConnectionBeforeBusinessUse() throws Exception {
        var connection = preparedConnection("0".repeat(64));
        var source = PlatformR2Isolation.guarded(PlatformR2Isolation.settings(valid(),false,PlatformR2Isolation.MAIN), () -> connection);
        assertThrows(SQLException.class,source::getConnection); verify(connection).close();
    }
    @Test void sourceCannotSelectMigratorConnectorUsingRuntimeRole() throws Exception {
        var connector = mock(PlatformR2Isolation.Connector.class);
        assertThrows(IllegalStateException.class, () -> PlatformR2Isolation.migrationDataSource(PlatformR2Isolation.settings(valid(),false,PlatformR2Isolation.MAIN),connector));
        verifyNoInteractions(connector);
    }
    @Test void changedHistoryChecksumClosesConnectionBeforeBusinessUse() throws Exception {
        var connection=preparedConnection(PlatformR2Isolation.migrationFingerprint());
        var rows=mock(ResultSet.class); when(rows.next()).thenReturn(true,false); when(rows.getString(anyInt())).thenReturn("tampered history");
        var statement=connection.createStatement(); when(statement.executeQuery(contains("select version,type"))).thenReturn(rows);
        var source=PlatformR2Isolation.guarded(PlatformR2Isolation.settings(valid(),false,PlatformR2Isolation.MAIN),()->connection);
        assertThrows(SQLException.class,source::getConnection); verify(connection).close();
    }
}
