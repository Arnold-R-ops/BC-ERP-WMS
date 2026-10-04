package com.wms.platformverification;

import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;

/** No cluster startup or database creation. Each mutating command requires separate explicit authorization. */
public final class PlatformR2DatabasePreparer {
    private PlatformR2DatabasePreparer() { }
    record Step(String database, List<String> before, List<String> after) { }
    static Step step(String command) {
        var all = PlatformR2Isolation.VERSIONS;
        return switch (command) {
            case "prepare-release" -> new Step(PlatformR2Isolation.MAIN, List.of(), all);
            case "upgrade-to-88" -> new Step(PlatformR2Isolation.UPGRADE, List.of(), all.subList(0, 6));
            case "upgrade-to-89" -> new Step(PlatformR2Isolation.UPGRADE, all.subList(0, 6), all.subList(0, 7));
            case "upgrade-to-90" -> new Step(PlatformR2Isolation.UPGRADE, all.subList(0, 7), all);
            default -> throw PlatformR2Isolation.refused();
        };
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw PlatformR2Isolation.refused();
        if (args[0].equals("plan")) {
            System.out.println("PLAN_ONLY; no connections; 127.0.0.1:55449; " + PlatformR2Isolation.MAIN + " and " + PlatformR2Isolation.UPGRADE + "; B4_71,V4_72,V4_85..90; separate execution approval required");
            return;
        }
        Step step = step(args[0]);
        var settings = PlatformR2Isolation.settings(PlatformR2Isolation.configuration(), true, step.database());
        String fingerprint = PlatformR2Isolation.migrationFingerprint();
        try (Connection connection = DriverManager.getConnection(PlatformR2Isolation.url(settings.database()), settings.role(), settings.password())) {
            PlatformR2Isolation.verifyIdentity(connection, settings, true);
            // No SQL writes, baseline or migration before validating the exact starting state.
            if (step.before().isEmpty()) {
                try (var statement = connection.createStatement(); var row = statement.executeQuery("select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S')")) {
                    row.next(); if (row.getLong(1) != 0) throw PlatformR2Isolation.refused();
                }
            } else PlatformR2Isolation.verifyVersions(connection, step.before());
            var migratorSource = PlatformR2Isolation.migrationDataSource(settings,
                () -> DriverManager.getConnection(PlatformR2Isolation.url(settings.database()), settings.role(), settings.password()));
            Flyway flyway = Flyway.configure().dataSource(migratorSource)
                .locations("classpath:db/migration").target(step.after().get(step.after().size() - 1))
                .baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false).ignoreMigrationPatterns("*:pending").load();
            // Pending increments are allowed, checksum/name/history failures are not repaired or masked.
            flyway.validate();
            PlatformR2Isolation.verifyIdentity(connection, settings, true);
            var migrated = flyway.migrate();
            if (migrated.migrationsExecuted != step.after().size() - step.before().size()) throw PlatformR2Isolation.refused();
            PlatformR2Isolation.verifyVersions(connection, step.after());
            flyway.validate();
            if (flyway.migrate().migrationsExecuted != 0) throw PlatformR2Isolation.refused();
            if (step.after().equals(PlatformR2Isolation.VERSIONS)) finishFixtures(connection, fingerprint);
            System.out.println("Dedicated migration step verified: " + args[0] + "; production compatibility not asserted");
        }
    }
    private static void finishFixtures(Connection connection, String fingerprint) throws SQLException {
        String historyFingerprint = PlatformR2Isolation.historyFingerprint(connection);
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("revoke create on schema public from public");
            statement.execute("create schema p3_r11_control");
            statement.execute("revoke all on schema p3_r11_control from public");
            statement.execute("create function p3_r11_control.data_directory() returns text language sql security definer set search_path=pg_catalog as 'select current_setting(''data_directory'')'");
            statement.execute("create function p3_r11_control.cluster_id() returns text language sql security definer set search_path=pg_catalog as 'select (pg_control_system()).system_identifier::text'");
            statement.execute("revoke all on all functions in schema p3_r11_control from public");
            statement.execute("create table p3_r11_control.ready(singleton boolean primary key check(singleton),migration_sha256 varchar(64) not null,history_sha256 varchar(64) not null)");
            statement.execute("create table p3_m1_probe(company_id bigint not null, value text)");
            statement.execute("alter table p3_m1_probe enable row level security");
            statement.execute("alter table p3_m1_probe force row level security");
            statement.execute("create policy tenant_probe on p3_m1_probe using(company_id=bcwms_current_company_id()) with check(company_id=bcwms_current_company_id())");
            statement.execute("grant usage on schema public,p3_r11_control to p3_r11_app");
            statement.execute("grant select,insert,update,delete on all tables in schema public to p3_r11_app");
            statement.execute("revoke insert,update,delete on flyway_schema_history from p3_r11_app");
            statement.execute("grant usage,select on all sequences in schema public to p3_r11_app");
            statement.execute("grant execute on all functions in schema public,p3_r11_control to p3_r11_app");
            statement.execute("grant select on p3_r11_control.ready to p3_r11_app");
            try (var insert = connection.prepareStatement("insert into p3_r11_control.ready values(true,?,?)")) {
                insert.setString(1, fingerprint); insert.setString(2,historyFingerprint); insert.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) { connection.rollback(); throw exception; }
        finally { connection.setAutoCommit(true); }
    }
}
