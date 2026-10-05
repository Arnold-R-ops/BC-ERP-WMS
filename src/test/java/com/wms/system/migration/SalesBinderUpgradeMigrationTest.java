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
            .locations("classpath:db/migration").target("4.75").cleanDisabled(false).load();
        original.clean();
        original.migrate();
        String legacyItems;
        String legacyOrders;
        String inventoryBefore;
        try (var connection = DriverManager.getConnection(URL, "postgres", "")) {
            seedLegacyInbound(connection);
            legacyItems = legacySnapshot(connection, "inbound_order_items");
            legacyOrders = legacySnapshot(connection, "inbound_orders");
            inventoryBefore = inventorySnapshot(connection);
        }

        var receivingUpgrade = Flyway.configure().dataSource(URL, "postgres", "")
            .locations("classpath:db/migration").target("4.84").load();
        receivingUpgrade.migrate();
        receivingUpgrade.validate();
        try (var connection = DriverManager.getConnection(URL, "postgres", "")) {
            assertThat(legacySnapshot(connection, "inbound_order_items")).isEqualTo(legacyItems);
            assertThat(legacySnapshot(connection, "inbound_orders")).isEqualTo(legacyOrders);
            assertThat(inventorySnapshot(connection)).isEqualTo(inventoryBefore);
            assertLegacyHistory(connection);
        }
        assertNewInvalidDatesAreRejected();

        try (var connection = DriverManager.getConnection(URL, "postgres", ""); var sql = connection.createStatement()) {
            for (int company = 1; company <= 2; company++) {
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
        var upgrade = Flyway.configure().dataSource(URL, "postgres", "")
            .locations("classpath:db/migration").target("4.94").load();
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

    private void seedLegacyInbound(Connection connection) throws SQLException {
        // Distinct creation times preserve each same-SKU legacy line through migration deduplication.
        try (var sql = connection.createStatement(); var item = connection.prepareStatement("""
            insert into inbound_order_items(id,company_id,inbound_order_id,product_sku_id,target_warehouse_id,
                plan_qty,actual_qty,production_date,expiry_date,external_batch_code,remark,created_at,updated_at)
            values (?,?,?,?,?,10,10,?,?,?,?,'2026-07-26 12:00:00'::timestamp + ? * interval '1 second',
                '2026-07-26 12:00:00'::timestamp + ? * interval '1 second')
            """)) {
            for (int company = 1; company <= 2; company++) {
                sql.execute("insert into tenants(id,tenant_code,display_name,slug,status) values (" + company
                    + ",'upgrade-" + company + "','Upgrade Test " + company + "','upgrade-" + company + "','ACTIVE')");
                sql.execute("select set_config('app.company_id','" + company + "',false)");
                sql.execute("insert into suppliers(id,company_id,code,name,is_active,created_at,updated_at) values ("
                    + company + "," + company + ",'LEG-SUP-" + company + "','Legacy supplier',true,now(),now())");
                sql.execute("insert into warehouses(id,company_id,code,name) values ("
                    + company + "," + company + ",'LEG-WH-" + company + "','Legacy warehouse')");
                sql.execute("insert into categories(id,company_id,category_code,category_name) values ("
                    + company + "," + company + ",'LEG-CAT-" + company + "','Legacy category')");
                sql.execute("insert into products(id,company_id,product_code,product_name,category_id) values ("
                    + company + "," + company + ",'LEG-PROD-" + company + "','Legacy product'," + company + ")");
                sql.execute("insert into product_skus(id,company_id,product_id,sku_code,sku_name,barcode,name,enabled,"
                    + "leadtime,minstock,unitprice,created_at,updated_at) values (" + company + "," + company + ","
                    + company + ",'LEG-SKU-" + company + "','Legacy SKU','LEG-BAR-" + company
                    + "','Legacy SKU',true,0,0,1,now(),now())");
                sql.execute("insert into inbound_orders(id,company_id,supplier_id,applicant_id,applicant_name,order_no,"
                    + "status,total_plan_qty,total_actual_qty,created_at,updated_at) values (" + company + ","
                    + company + "," + company + ",1,'Legacy applicant','LEG-IN-" + company
                    + "','COMPLETED',40,40,'2026-07-26 12:00:00','2026-07-26 12:00:00')");
                for (int fixture = 1; fixture <= 4; fixture++) {
                    item.setInt(1, company * 10 + fixture);
                    item.setInt(2, company);
                    item.setInt(3, company);
                    item.setInt(4, company);
                    item.setInt(5, company);
                    item.setDate(6, fixture == 3 ? null : Date.valueOf(fixture == 1 ? "2027-07-26" : "2026-07-26"));
                    item.setDate(7, fixture == 4 ? null : Date.valueOf(fixture == 1 ? "2026-07-26" : "2027-07-26"));
                    item.setString(8, "LEG-BATCH-" + company + "-" + fixture);
                    item.setString(9, fixture == 1 ? "旧备注".repeat(166) + "尾注" : "Original legacy remark " + fixture);
                    item.setInt(10, fixture);
                    item.setInt(11, fixture);
                    item.executeUpdate();
                }
            }
        }
    }

    private void assertLegacyHistory(Connection connection) throws SQLException {
        for (int company = 1; company <= 2; company++) {
            assertThat(scalar(connection, "select count(*) from other_inbound_application where company_id=" + company
                + " and legacy_inbound_order_id=" + company + " and status='COMPLETED'")).isEqualTo("1");
            assertThat(scalar(connection, "select count(*) from other_inbound_application_item where company_id=" + company))
                .isEqualTo("4");
            try (var sql = connection.prepareStatement("""
                select history.production_date, history.expiry_date, history.remark,
                    legacy.production_date, legacy.expiry_date, legacy.remark, legacy.id
                from other_inbound_application_item history
                join other_inbound_application application
                    on application.company_id=history.company_id and application.id=history.application_id
                join inbound_order_items legacy on legacy.company_id=history.company_id
                    and legacy.inbound_order_id=application.legacy_inbound_order_id
                    and legacy.external_batch_code=history.external_batch_code
                where history.company_id=? order by legacy.id
                """)) {
                sql.setInt(1, company);
                try (var rows = sql.executeQuery()) {
                    for (int fixture = 1; fixture <= 4; fixture++) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt(7)).isEqualTo(company * 10 + fixture);
                        if (fixture == 1) {
                            assertThat(rows.getDate(1)).isNull();
                            assertThat(rows.getDate(2)).isNull();
                            assertThat(rows.getString(3)).hasSize(500)
                                .contains("原入库明细 " + (company * 10 + fixture), "2027-07-26", "2026-07-26", "旧备注");
                            assertThat(rows.getDate(4)).isEqualTo(Date.valueOf("2027-07-26"));
                            assertThat(rows.getDate(5)).isEqualTo(Date.valueOf("2026-07-26"));
                            assertThat(rows.getString(6)).isEqualTo("旧备注".repeat(166) + "尾注");
                        } else {
                            assertThat(rows.getDate(1)).isEqualTo(rows.getDate(4));
                            assertThat(rows.getDate(2)).isEqualTo(rows.getDate(5));
                            assertThat(rows.getString(3)).isEqualTo(rows.getString(6));
                        }
                    }
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    private void assertNewInvalidDatesAreRejected() throws SQLException {
        // A separate, rolled-back transaction verifies new-business validation remains intact.
        for (int company = 1; company <= 2; company++) {
            try (var connection = DriverManager.getConnection(URL, "postgres", "")) {
                connection.setAutoCommit(false);
                try (var sql = connection.createStatement()) {
                    sql.execute("select set_config('app.company_id','" + company + "',true)");
                    String applicationId = scalar(connection, "insert into other_inbound_application(company_id,application_number,"
                        + "business_type,source_reference,business_reason,warehouse_id,status,created_by,created_by_name) values ("
                        + company + ",'NEW-DATE-CHECK-" + company + "','AUTHORIZED_SPECIAL','Migration regression',"
                        + "'Verify current date constraint'," + company + ",'DRAFT',1,'Test') returning id");
                    String invalidInsert = "insert into other_inbound_application_item(company_id,application_id,product_sku_id,"
                        + "planned_quantity,production_date,expiry_date) values (" + company + "," + applicationId + ","
                        + company + ",1,'2027-07-26','2026-07-26')";
                    assertThatThrownBy(() -> sql.executeUpdate(invalidInsert))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"))
                        .hasMessageContaining("ck_other_inbound_item_dates");
                } finally {
                    connection.rollback();
                }
            }
        }
    }

    private String legacySnapshot(Connection connection, String table) throws SQLException {
        return scalar(connection, "select jsonb_agg(to_jsonb(legacy) order by legacy.id)::text from " + table
            + " legacy where company_id in (1,2)");
    }

    private String inventorySnapshot(Connection connection) throws SQLException {
        String[] tables = {"inventory", "inventory_batch", "inventory_reservations", "stock_transactions",
            "receiving_task", "receipt_event"};
        StringBuilder snapshot = new StringBuilder();
        for (String table : tables) {
            String count = scalar(connection, "select count(*) from " + table + " where company_id in (1,2)");
            assertThat(count).as("No inventory replay in %s", table).isEqualTo("0");
            snapshot.append(table).append('=').append(count).append(';');
        }
        return snapshot.toString();
    }

    private String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}

