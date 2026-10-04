package com.wms.system.commercial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

/** Tests new-table cleanup operations with receipt reversals; every change is rolled back. */
@EnabledIfSystemProperty(named="upgrade.dbtest",matches="true")
class CommercialPurgeCompatibilityTest {
    @Test void newTablesSupportTenantCleanupWithoutMutatingReceiptEvidence() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55519/salesbinder_upgrade_test","postgres","");
            var statement=connection.createStatement()) {
            try(var result=statement.executeQuery("select current_database() || ':' || inet_server_port()")) {
                result.next();assertThat(result.getString(1)).isEqualTo("salesbinder_upgrade_test:55519");
            }
            statement.setQueryTimeout(20);
            connection.setAutoCommit(false);
            try {
                assertThat(count(statement,"select count(*) from commercial_receipt where company_id=1 and reverses_id is not null")).isPositive();
                long otherRows=count(statement,"select count(*) from customers where company_id=2");
                statement.execute("select set_config('app.company_id','1',true)");
                statement.execute("select set_config('app.tenant_purge','on',true)");
                long reversals=count(statement,"select count(*) from commercial_receipt where company_id=1 and reverses_id is not null");
                statement.executeUpdate("update commercial_receipt set reverses_id=null where company_id=1");
                assertThat(count(statement,"select count(*) from commercial_receipt where company_id=1 and reverses_id is not null")).isEqualTo(reversals);
                for(String table:new String[]{"commercial_document_line","commercial_receipt","commercial_document","commercial_profile","warehouse_stock_threshold"}) {
                    statement.executeUpdate("delete from "+table+" where company_id=1");
                    assertThat(count(statement,"select count(*) from "+table+" where company_id=1")).as(table).isZero();
                }
                assertThat(count(statement,"select count(*) from customers where company_id=2")).isEqualTo(otherRows);
            } finally {connection.rollback();}
        }
    }
    private long count(Statement statement,String sql) throws SQLException {
        try(var result=statement.executeQuery(sql)){result.next();return result.getLong(1);}
    }
}
