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
                // Build local evidence so this test never depends on the workflow test running first.
                long documentId=count(statement,"insert into commercial_document(company_id,kind,status,request_key,request_hash,customer_id,sales_order_id,created_by,customer_name,company_name,document_date,due_date,total) "
                    +"select 1,'INVOICE','VOID',gen_random_uuid()::text,'purge-fixture',customer_id,id,1,'Purge customer','Purge company',current_date,current_date,10 from sales_orders where company_id=1 order by id limit 1 returning id");
                statement.executeUpdate("insert into commercial_document_line(company_id,document_id,position,product_sku_id,sku_code,description,unit,quantity,unit_price,amount) "
                    +"select 1,"+documentId+",1,id,sku_code,'Purge item','unit',1,10,10 from product_skus where company_id=1 order by id limit 1");
                long receiptId=count(statement,"insert into commercial_receipt(company_id,document_id,kind,request_key,request_hash,amount,payment_date,method,created_by) values(1,"+documentId+",'RECEIPT',gen_random_uuid()::text,'purge-fixture',10,current_date,'CASH',1) returning id");
                statement.executeUpdate("insert into commercial_receipt(company_id,document_id,kind,reverses_id,request_key,request_hash,amount,payment_date,method,note,created_by) values(1,"+documentId+",'REVERSAL',"+receiptId+",gen_random_uuid()::text,'purge-fixture',10,current_date,'CASH','Fixture correction',1)");
                statement.executeUpdate("insert into commercial_profile(company_id,name) values(1,'Purge company') on conflict(company_id) do nothing");
                statement.executeUpdate("insert into warehouse_stock_threshold(company_id,product_sku_id,warehouse_id,min_stock) select 1,p.id,w.id,1 from product_skus p cross join warehouses w where p.company_id=1 and w.company_id=1 order by p.id,w.id limit 1 on conflict(company_id,product_sku_id,warehouse_id) do nothing");
                assertThat(count(statement,"select count(*) from commercial_receipt where company_id=1 and reverses_id is not null")).isPositive();
                long otherRows=count(statement,"select count(*) from customers where company_id=2");
                statement.execute("select set_config('app.company_id','1',true)");
                statement.execute("select set_config('app.tenant_purge','on',true)");
                long reversals=count(statement,"select count(*) from commercial_receipt where company_id=1 and reverses_id is not null");
                statement.executeUpdate("update commercial_receipt set reverses_id=null where company_id=1");
                assertThat(count(statement,"select count(*) from commercial_receipt where company_id=1 and reverses_id is not null")).isEqualTo(reversals);
                for(String table:new String[]{"commercial_document_line","commercial_receipt","commercial_document","commercial_profile","warehouse_stock_threshold"}) {
                    assertThat(count(statement,"select count(*) from "+table+" where company_id=1")).as(table+" fixture").isPositive();
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
