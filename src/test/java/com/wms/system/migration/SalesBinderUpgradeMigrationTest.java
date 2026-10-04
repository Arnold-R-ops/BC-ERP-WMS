package com.wms.system.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "upgrade.dbtest", matches = "true")
class SalesBinderUpgradeMigrationTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55519/salesbinder_upgrade_test";

    @Test void migratesLegacyDataAndRebuildsConfirmedAmountsPerTenant() throws Exception {
        // This dedicated database is created by target/start_upgrade_db.py; never use an environment URL.
        try (var connection = DriverManager.getConnection(URL, "postgres", "")) {
            assertThat(scalar(connection, "select current_database()")).isEqualTo("salesbinder_upgrade_test");
            assertThat(scalar(connection, "select inet_server_port()")).isEqualTo("55519");
        }
        var original = Flyway.configure().dataSource(URL, "postgres", "")
            .locations("classpath:db/migration").target("4.84").cleanDisabled(false).load();
        original.clean();
        original.migrate();
        try (var connection = DriverManager.getConnection(URL, "postgres", ""); var sql = connection.createStatement()) {
            for (int company = 1; company <= 2; company++) {
                sql.execute("insert into tenants(id,tenant_code,display_name,slug,status) values (" + company
                    + ",'upgrade-" + company + "','Upgrade Test " + company + "','upgrade-" + company + "','ACTIVE')");
                sql.execute("select set_config('app.company_id','" + company + "',false)");
                sql.execute("insert into customers(id,company_id,code,name,is_active,is_deleted,created_at,updated_at) values ("
                    + company + "," + company + ",'TEST-" + company + "','测试客户 " + company + "',true,false,now(),now())");
                String[] states = {"DRAFT", "PENDING_APPROVAL", "APPROVED", "COMPLETED", "CLOSED", "REJECTED", "CANCELLED", "VOIDED"};
                for (int i = 0; i < states.length; i++) {
                    sql.execute("insert into sales_orders(company_id,customer_id,applicant_id,applicant_name,order_no,commercial_status,warehouse_status,total_amount,created_at,updated_at)"
                        + " values (" + company + "," + company + ",1,'Test','UPG-" + company + "-" + i + "','" + states[i] + "','"
                        + (i == 3 ? "SHIPPED" : "NOT_RELEASED") + "'," + ((i + 1) * 100 * company) + ",'2026-09-18 12:00:00',now())");
                }
            }
        }
        var upgrade = Flyway.configure().dataSource(URL, "postgres", "").locations("classpath:db/migration").load();
        upgrade.migrate();
        upgrade.validate();
        try (var connection = DriverManager.getConnection(URL, "postgres", "")) {
            assertThat(scalar(connection, "select total_order_count from sales_daily_summary where company_id=1")).isEqualTo("3");
            assertThat(scalar(connection, "select total_amount from sales_daily_summary where company_id=1")).isEqualTo("1200.00");
            assertThat(scalar(connection, "select total_amount from sales_daily_summary where company_id=2")).isEqualTo("2400.00");
            assertThat(scalar(connection, "select draft_count from sales_daily_summary where company_id=1")).isEqualTo("1");
            assertThat(scalar(connection, "select pending_approval_count from sales_daily_summary where company_id=1")).isEqualTo("1");
            assertThat(scalar(connection, "select approved_awaiting_shipment_count from sales_daily_summary where company_id=1")).isEqualTo("1");
            assertThat(scalar(connection, "select shipped_count from sales_daily_summary where company_id=1")).isEqualTo("1");
            assertThat(scalar(connection, "select closed_count from sales_daily_summary where company_id=1")).isEqualTo("1");
            assertThat(scalar(connection, "select total_amount from customer_fact_summary where company_id=1")).isEqualTo("1200.00");
            assertThat(scalar(connection, "select shipping_same_as_billing from customers where company_id=1")).isEqualTo("t");
            assertThat(scalar(connection, "select is_nullable from information_schema.columns where table_name='sales_order_shipments' and column_name='tracking_no'")).isEqualTo("YES");
            assertThat(scalar(connection, "select relrowsecurity and relforcerowsecurity from pg_class where relname='warehouse_stock_threshold'")).isEqualTo("t");
            assertThat(scalar(connection, "select column_default from information_schema.columns where table_name='product_skus' and column_name='base_unit'")).contains("件");
        }
    }

    private String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}

