package com.wms.platformverification;

import org.springframework.jdbc.datasource.AbstractDataSource;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Test-only, exact frozen targets below (legacy helper/property names retained). No connection is opened by parsing/validating configuration. */
public final class PlatformR2Isolation {
    public static final int PORT = 55449;
    public static final String MAIN = "p3_m1_platform_release_r11";
    public static final String UPGRADE = "p3_m1_platform_upgrade_r11";
    public static final String APP = "p3_r11_app";
    public static final String MIGRATOR = "p3_r11_migrator";
    public static final String DIRECTORY = "D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260919/r11/pgdata";
    public static final List<String> VERSIONS = List.of("4.71", "4.72", "4.85", "4.86", "4.87", "4.88", "4.89", "4.90");
    private PlatformR2Isolation() { }
    public static String url(String database) {
        if (!Set.of(MAIN, UPGRADE).contains(database)) throw refused();
        return "jdbc:postgresql://127.0.0.1:" + PORT + "/" + database;
    }
    public record Settings(String database, String role, String password, String clusterId) {
        public Settings {
            url(database);
            if (!Set.of(APP, MIGRATOR).contains(role) || password == null || password.length() < 16
                || clusterId == null || !clusterId.matches("[0-9]{10,30}")) throw refused();
        }
        @Override public String toString() { return "PlatformR2Settings[credentials redacted]"; }
    }
    public static Settings settings(Properties properties, boolean migration, String database) {
        String permission = migration ? "p3.r2.migration-authorized" : "p3.r2.database-authorized";
        if (!"true".equals(properties.getProperty(permission))) throw refused();
        if (!migration && !MAIN.equals(database)) throw refused();
        if (!url(database).equals(properties.getProperty("p3.test.jdbcUrl"))) throw refused();
        String role = migration ? MIGRATOR : APP;
        for (String key : List.of("spring.datasource.url", "spring.flyway.url")) {
            String value = properties.getProperty(key);
            if (value != null && !value.equals(url(database))) throw refused();
        }
        if (properties.containsKey("spring.datasource.jndi-name")) throw refused();
        if (properties.containsKey("spring.datasource.username") && !role.equals(properties.getProperty("spring.datasource.username"))) throw refused();
        return new Settings(database, role, properties.getProperty(migration ? "p3.r2.migrator-password" : "p3.r2.app-password"),
            properties.getProperty("p3.r2.cluster-id"));
    }
    public static DataSource runtimeDataSource() {
        Settings settings = settings(configuration(), false, MAIN);
        return guarded(settings, () -> DriverManager.getConnection(url(settings.database()), settings.role(), settings.password()));
    }
    public static Properties configuration() {
        // Passwords must not enter command lines or Surefire's Java-system-property report.
        if (System.getProperties().containsKey("p3.r2.app-password") || System.getProperties().containsKey("p3.r2.migrator-password")) throw refused();
        Properties values = new Properties(); values.putAll(System.getProperties());
        String app = System.getenv("P3_R2_APP_PASSWORD"), migrator = System.getenv("P3_R2_MIGRATOR_PASSWORD");
        if (app != null) values.setProperty("p3.r2.app-password", app);
        if (migrator != null) values.setProperty("p3.r2.migrator-password", migrator);
        return values;
    }
    @FunctionalInterface public interface Connector { Connection open() throws SQLException; }
    public static DataSource migrationDataSource(Settings settings, Connector connector) {
        if (!MIGRATOR.equals(settings.role())) throw refused();
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = connector.open();
                try { verifyIdentity(connection, settings, true); return connection; }
                catch (Exception failure) { connection.close(); throw new SQLException("R2 migrator connection identity rejected"); }
            }
            @Override public Connection getConnection(String user, String password) { throw refused(); }
        };
    }
    public static DataSource guarded(Settings settings, Connector connector) {
        if (!APP.equals(settings.role()) || !MAIN.equals(settings.database())) throw refused();
        final String sourceFingerprint = migrationFingerprint();
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = connector.open();
                try {
                    verifyIdentity(connection, settings, false);
                    verifyVersions(connection, VERSIONS);
                    String historyFingerprint = historyFingerprint(connection);
                    try (var statement = connection.createStatement(); var row = statement.executeQuery("select migration_sha256,history_sha256 from p3_r11_control.ready where singleton")) {
                        if (!row.next() || !sourceFingerprint.equals(row.getString(1)) || !historyFingerprint.equals(row.getString(2)) || row.next()) throw refused();
                    }
                    return connection;
                } catch (Exception failure) {
                    connection.close();
                    throw new SQLException("R2 database identity or readiness rejected; no business connection released");
                }
            }
            @Override public Connection getConnection(String user, String password) { throw refused(); }
        };
    }
    public static void verifyIdentity(Connection connection, Settings settings, boolean migration) throws SQLException {
        String physical = migration ? "current_setting('data_directory'), (pg_control_system()).system_identifier::text"
            : "p3_r11_control.data_directory(), p3_r11_control.cluster_id()";
        String sql = "select current_database(), host(inet_server_addr()), inet_server_port(), current_user, rolsuper, rolbypassrls, "
            + physical + " from pg_roles where rolname=current_user";
        try (var statement = connection.createStatement(); var row = statement.executeQuery(sql)) {
            if (!row.next() || !settings.database().equals(row.getString(1)) || !"127.0.0.1".equals(row.getString(2))
                || PORT != row.getInt(3) || !settings.role().equals(row.getString(4)) || row.getBoolean(5) || row.getBoolean(6)
                || !DIRECTORY.equalsIgnoreCase(row.getString(7).replace('\\', '/')) || !settings.clusterId().equals(row.getString(8))) throw refused();
        }
    }
    public static void verifyVersions(Connection connection, List<String> expected) throws SQLException {
        List<String> actual = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select version, success from flyway_schema_history order by installed_rank")) {
            while (rows.next()) { if (!rows.getBoolean(2)) throw refused(); actual.add(rows.getString(1)); }
        }
        if (!actual.equals(expected)) throw refused();
    }
    public static String migrationFingerprint() {
        try {
            var resource = PlatformR2Isolation.class.getClassLoader().getResource("db/migration/V4_90__Platform_Browser_Session_Scope.sql");
            if (resource == null || !"file".equals(resource.getProtocol())) throw refused();
            Path directory = Path.of(resource.toURI()).getParent();
            MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.list(directory)) {
                var files = stream.filter(p -> p.toString().endsWith(".sql")).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
                if (files.size() != 94) throw refused();
                for (Path file : files) {
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                    aggregate.update((file.getFileName() + "\0" + digest + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(aggregate.digest());
        } catch (Exception exception) { throw refused(); }
    }
    public static String historyFingerprint(Connection connection) throws SQLException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select version,type,script,checksum,success from flyway_schema_history order by installed_rank")) {
                while (rows.next()) {
                    for (int i=1;i<=5;i++) digest.update((String.valueOf(rows.getString(i))+"\0").getBytes(StandardCharsets.UTF_8));
                    digest.update((byte)'\n');
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) { throw refused(); }
    }
    static IllegalStateException refused() { return new IllegalStateException("R2 isolation precondition failed; STOP without fallback"); }
}
