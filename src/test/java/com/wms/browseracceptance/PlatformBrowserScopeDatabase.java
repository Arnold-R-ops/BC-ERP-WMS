package com.wms.browseracceptance;

import org.flywaydb.core.Flyway;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

/** Explicitly authorized V4_90 rehearsal only. Never connects to the live browser acceptance DB. */
public final class PlatformBrowserScopeDatabase {
    private static final Path ROOT = Path.of("D:/ERP_WMS/BC warehouse/2G");
    private static final String BASE = "jdbc:postgresql://127.0.0.1:55439/";
    private static final String SOURCE = "p3_m1_platform_session";
    private static final String TARGET = "p3_m1_platform_browser_scope";
    private static final List<String> BEFORE = List.of("4.71", "4.72", "4.85", "4.86", "4.87", "4.88", "4.89");
    private static final List<String> AFTER = List.of("4.71", "4.72", "4.85", "4.86", "4.87", "4.88", "4.89", "4.90");

    public static void main(String[] args) throws Exception {
        if (!Path.of("").toAbsolutePath().normalize().equals(ROOT)) throw new IllegalStateException("Wrong workspace");
        if (args.length != 1) throw new IllegalArgumentException("Use prepare or verify; no connection overrides accepted");
        if ("verify".equals(args[0])) {
            verifyCurrent();
            return;
        }
        if (!"prepare".equals(args[0])) throw new IllegalArgumentException("Unknown operation");

        // Read-only source checks. The only acceptable validation finding is this authorized increment.
        checkDatabase(SOURCE, BEFORE);
        requireOnlyV490Pending(flyway(SOURCE));
        try (Connection owner = DriverManager.getConnection(BASE + SOURCE, "p3_m1_owner", "")) {
            try (var statement = owner.createStatement()) {
                try (var row = statement.executeQuery("select current_database(),host(inet_server_addr()),inet_server_port(),current_user,current_setting('data_directory')")) {
                    if (!row.next() || !SOURCE.equals(row.getString(1)) || !"127.0.0.1".equals(row.getString(2))
                        || row.getInt(3) != 55439 || !"p3_m1_owner".equals(row.getString(4))
                        || !Path.of(row.getString(5)).normalize().equals(ROOT.resolve("target/p3-m1-pg-20260909")))
                        throw new IllegalStateException("Wrong cluster identity; STOP");
                }
                try (var row = statement.executeQuery("select count(*) from pg_database where datname='p3_m1_platform_browser_scope'")) {
                    row.next();
                    if (row.getInt(1) != 0) throw new IllegalStateException("Target already exists; never drop or overwrite it");
                }
                // Fixed identifiers only; no ALTER of the template, session termination, or service restart.
                statement.executeUpdate("create database p3_m1_platform_browser_scope with template p3_m1_platform_session owner p3_m1_app");
            }
        }
        checkDatabase(TARGET, BEFORE);
        var target = flyway(TARGET);
        requireOnlyV490Pending(target);
        var migrated = target.migrate();
        if (migrated.migrationsExecuted != 1) throw new IllegalStateException("Unexpected migration count; STOP");
        verifyCurrent();
        System.out.println("ONLY_V4_90_MIGRATED_IN_NEW_SCOPE_TEST_DB; source and live browser DB unchanged");
    }

    private static Flyway flyway(String database) {
        if (!SOURCE.equals(database) && !TARGET.equals(database)) throw new IllegalStateException("Unapproved database");
        return Flyway.configure().dataSource(BASE + database, "p3_m1_app", "")
            .locations("filesystem:" + ROOT.resolve("src/main/resources/db/migration").toString().replace('\\', '/'))
            .cleanDisabled(true).load();
    }

    private static void requireOnlyV490Pending(Flyway flyway) {
        var result = flyway.validateWithResult();
        if (result.validationSuccessful || result.invalidMigrations.size() != 1)
            throw new IllegalStateException("Expected exactly one V4_90 validation finding; STOP");
        var pending = result.invalidMigrations.get(0);
        if (!"4.90".equals(pending.version)
            || !"RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(pending.errorDetails.errorCode.toString()))
            throw new IllegalStateException("Unexpected version/checksum/validation failure; STOP");
        var migrations = flyway.info().pending();
        if (migrations.length != 1 || !"4.90".equals(migrations[0].getVersion().toString())
            || !"V4_90__Platform_Browser_Session_Scope.sql".equals(migrations[0].getScript()))
            throw new IllegalStateException("Unexpected pending migrations; STOP");
        System.out.println("APPLIED_CHECKSUMS_VALID; sole approved pending migration=4.90");
    }

    private static void checkDatabase(String database, List<String> expected) throws Exception {
        if (!SOURCE.equals(database) && !TARGET.equals(database)) throw new IllegalStateException("Unapproved database");
        try (Connection connection = DriverManager.getConnection(BASE + database, "p3_m1_app", "")) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement()) {
                try (var row = statement.executeQuery("select current_database(),host(inet_server_addr()),inet_server_port(),current_user,rolsuper,rolbypassrls from pg_roles where rolname=current_user")) {
                    if (!row.next() || !database.equals(row.getString(1)) || !"127.0.0.1".equals(row.getString(2))
                        || row.getInt(3) != 55439 || !"p3_m1_app".equals(row.getString(4)) || row.getBoolean(5) || row.getBoolean(6))
                        throw new IllegalStateException("Wrong database/runtime role identity; STOP");
                }
                List<String> actual = new ArrayList<>();
                try (var rows = statement.executeQuery("select version,success from flyway_schema_history order by installed_rank")) {
                    while (rows.next()) {
                        if (!rows.getBoolean(2)) throw new IllegalStateException("Failed migration; STOP");
                        actual.add(rows.getString(1));
                    }
                }
                if (!actual.equals(expected)) throw new IllegalStateException("Migration sequence mismatch; STOP");
            }
        }
    }

    private static void verifyCurrent() throws Exception {
        checkDatabase(TARGET, AFTER);
        flyway(TARGET).validate();
        checkDatabase(SOURCE, BEFORE);
        requireOnlyV490Pending(flyway(SOURCE));
        System.out.println("SCOPE_DB_IDENTITY_AND_V4_90_CHECKSUMS_VERIFIED; source remains V4_89");
    }
}
