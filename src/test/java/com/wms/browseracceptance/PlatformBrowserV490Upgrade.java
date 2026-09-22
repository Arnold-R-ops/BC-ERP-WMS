package com.wms.browseracceptance;

import org.flywaydb.core.Flyway;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

/** Explicit, test-only upgrade for the existing synthetic browser DB; never used at app startup. */
public final class PlatformBrowserV490Upgrade {
    private static final List<String> BEFORE=List.of("4.71","4.72","4.85","4.86","4.87","4.88","4.89");

    public static void main(String[] args) throws Exception {
        if (!Path.of("").toAbsolutePath().normalize().equals(PlatformBrowserHarness.ROOT))
            throw new IllegalStateException("Wrong isolated working directory");
        if (args.length!=1 || !List.of("preflight","migrate","verify").contains(args[0]))
            throw new IllegalArgumentException("Use preflight, migrate or verify; no connection overrides accepted");
        if ("verify".equals(args[0])) {
            PlatformBrowserHarness.validate(PlatformBrowserHarness.URL);
            System.out.println("BROWSER_DB_V4_90_VERIFIED; automatic runtime migrations remain disabled");
            return;
        }
        // Validates the already-tested migration checksum as well as the untouched V4_89 template.
        PlatformBrowserScopeDatabase.main(new String[]{"verify"});
        before();
        if ("preflight".equals(args[0])) {
            System.out.println("BROWSER_V4_89_IDENTITY_VALID; ONLY_TESTED_V4_90_PENDING");
            return;
        }
        try(var connection=DriverManager.getConnection(PlatformBrowserHarness.URL,"p3_m1_app","")) {
            connection.setReadOnly(true);
            try(var statement=connection.createStatement(); var rows=statement.executeQuery(
                "select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid()")) {
                rows.next();
                if(rows.getInt(1)!=0) throw new IllegalStateException("Stop dedicated browser backend first; other DB connections remain");
            }
        }
        var result=flyway().migrate();
        if(result.migrationsExecuted!=1) throw new IllegalStateException("Unexpected migration count; STOP");
        PlatformBrowserHarness.validate(PlatformBrowserHarness.URL);
        System.out.println("BROWSER_DB_ONLY_V4_90_APPLIED_AND_VERIFIED; no account data updated");
    }

    private static Flyway flyway() {
        return Flyway.configure().dataSource(PlatformBrowserHarness.URL,"p3_m1_app","")
            .locations("filesystem:"+PlatformBrowserHarness.ROOT.resolve("src/main/resources/db/migration").toString().replace('\\','/'))
            .cleanDisabled(true).load();
    }

    private static void before() throws Exception {
        try(var connection=DriverManager.getConnection(PlatformBrowserHarness.URL,"p3_m1_app","")) {
            connection.setReadOnly(true);
            try(var statement=connection.createStatement()) {
                try(var rows=statement.executeQuery("select current_database(),host(inet_server_addr()),inet_server_port(),current_user,rolsuper,rolbypassrls from pg_roles where rolname=current_user")) {
                    if(!rows.next() || !"p3_m1_platform_browser".equals(rows.getString(1)) || !"127.0.0.1".equals(rows.getString(2))
                        || rows.getInt(3)!=55439 || !"p3_m1_app".equals(rows.getString(4)) || rows.getBoolean(5) || rows.getBoolean(6))
                        throw new IllegalStateException("Database identity/runtime role mismatch; STOP");
                }
                var versions=new ArrayList<String>();
                try(var rows=statement.executeQuery("select version,success from flyway_schema_history order by installed_rank")) {
                    while(rows.next()) {
                        if(!rows.getBoolean(2)) throw new IllegalStateException("Failed migration; STOP");
                        versions.add(rows.getString(1));
                    }
                }
                if(!BEFORE.equals(versions)) throw new IllegalStateException("Unexpected pre-upgrade migration sequence; STOP");
            }
        }
        var validation=flyway().validateWithResult();
        if(validation.validationSuccessful || validation.invalidMigrations.size()!=1)
            throw new IllegalStateException("Expected only tested V4_90 pending; STOP");
        var invalid=validation.invalidMigrations.get(0);
        if(!"4.90".equals(invalid.version) || !"RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(invalid.errorDetails.errorCode.toString()))
            throw new IllegalStateException("Unexpected migration/checksum finding; STOP");
        var pending=flyway().info().pending();
        if(pending.length!=1 || !"4.90".equals(pending[0].getVersion().toString())
            || !"V4_90__Platform_Browser_Session_Scope.sql".equals(pending[0].getScript()))
            throw new IllegalStateException("Unexpected pending increment; STOP");
    }
}
