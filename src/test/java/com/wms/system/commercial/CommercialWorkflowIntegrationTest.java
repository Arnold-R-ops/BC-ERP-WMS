package com.wms.system.commercial;

import com.wms.system.commercial.CommercialDtos.*;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.security.SecurityUser;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named="upgrade.dbtest",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommercialWorkflowIntegrationTest {
    private static final String URL="jdbc:postgresql://127.0.0.1:55519/salesbinder_upgrade_test";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->URL);p.add("spring.datasource.username",()->"wms_upgrade_app");p.add("spring.datasource.password",()->"");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate");p.add("spring.jpa.properties.hibernate.hbm2ddl.auto",()->"validate");p.add("spring.flyway.enabled",()->false);
        p.add("wms.legacy-admin-bootstrap-enabled",()->false);p.add("wms.tenancy.enabled",()->false);p.add("wms.tenancy.require-safe-database-role",()->true);
        p.add("spring.task.scheduling.enabled",()->false);p.add("logging.level.com.wms.system",()->"WARN");
    }
    @Autowired CommercialService service;
    @Autowired CommercialDocumentRepository documents;
    static Long quoteId;
    static Long invoiceId;
    static long orderCountBefore;

    @BeforeAll static void seed() throws Exception {
        try(Connection c=DriverManager.getConnection(URL,"postgres","");Statement s=c.createStatement()) {
            assertThat(scalar(c,"select current_database() || ':' || inet_server_port()" )).isEqualTo("salesbinder_upgrade_test:55519");
            s.execute("grant usage on schema public to wms_upgrade_app; grant select,insert,update,delete on all tables in schema public to wms_upgrade_app; grant usage,select on all sequences in schema public to wms_upgrade_app");
            s.execute("insert into categories(id,company_id,category_code,category_name) values(901,1,'QUOTE-TEST','报价测试') on conflict do nothing");
            s.execute("insert into products(id,company_id,product_code,product_name,category_id) values(901,1,'QUOTE-TEST','测试茶叶',901) on conflict do nothing");
            s.execute("insert into product_skus(id,company_id,barcode,enabled,leadtime,minstock,name,unitprice,product_id,sku_name,sku_code,base_unit,created_at,updated_at) values(901,1,'QUOTE-TEST',true,7,0,'测试茶叶 100g',12.50,901,'100g','QUOTE-SKU','袋',now(),now()) on conflict do nothing");
            s.execute("insert into customers(id,company_id,code,name,address,shipping_address,shipping_same_as_billing,owner_id,is_active,is_deleted,created_at,updated_at) values(901,1,'QUOTE-C','报价客户有限公司','账单地址一号','仓库二号',false,202,true,false,now(),now()) on conflict do nothing");
            s.execute("insert into system_config(company_id,config_key,config_value,config_type,created_at,updated_at) values(1,'sales.order.approval.mode','ALWAYS','STRING',now(),now()) on conflict(company_id,config_key) do update set config_value='ALWAYS'");
            s.execute("insert into sales_orders(id,company_id,customer_id,applicant_id,applicant_name,order_no,commercial_status,total_amount,created_at,updated_at) values(901,1,901,202,'Test','COMMERCIAL-TEST','APPROVED',50,now(),now()) on conflict do nothing");
            s.execute("insert into sales_order_items(company_id,sales_order_id,product_sku_id,quantity,requested_qty,unit_price,subtotal,reject_near_expiry,created_at,updated_at) select 1,901,901,4,4,12.50,50,false,now(),now() where not exists(select 1 from sales_order_items where sales_order_id=901)");
            s.execute("select setval('sales_orders_id_seq',(select max(id) from sales_orders))");
            orderCountBefore=Long.parseLong(scalar(c,"select count(*) from sales_orders"));
        }
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();TenantContextHolder.clear();}
    @Test @Order(6) void blankShippingAddressKeepsCustomerDefault() {
        QuoteInput source=input(UUID.randomUUID());
        var created=service.createQuote(new QuoteInput(source.requestKey(),null,source.customerId(),source.validUntil(),"   ",source.terms(),source.lines()),202L);
        assertThat(created.shippingAddress()).isEqualTo("仓库二号");
        var changed=service.updateQuote(created.id(),new QuoteInput(UUID.randomUUID(),created.version(),source.customerId(),source.validUntil(),"",source.terms(),source.lines()));
        assertThat(changed.shippingAddress()).isEqualTo("仓库二号");
    }
    @Test @Order(1) void quoteDoesNotReserveAndPreservesOriginalParties() throws Exception {
        service.saveProfile(new ProfileInput("测试商贸有限公司","公司地址","020 1234","test@example.com","报价有效期内按本单价格。"));
        long stock=quantity("select count(*) from stock_transactions");
        QuoteInput input=input(UUID.randomUUID());
        DocumentView d=service.createQuote(input,202L); quoteId=d.id();
        assertThat(d.total()).isEqualByComparingTo("25.00");assertThat(d.shippingAddress()).isEqualTo("仓库二号");
        assertThat(d.status()).isEqualTo("OPEN");assertThat(d.salesOrderId()).isNull();assertThat(d.lines().get(0).unit()).isEqualTo("袋");
        assertThat(service.createQuote(input,202L).id()).isEqualTo(d.id());
        assertThat(quantity("select count(*) from sales_orders")).isEqualTo(orderCountBefore);
        assertThat(quantity("select count(*) from stock_transactions")).isEqualTo(stock);
        execute("update customers set address='新版客户地址' where id=901");
        service.saveProfile(new ProfileInput("新版公司名","新地址",null,null,null));
        assertThat(service.export(d.id()).billingAddress()).isEqualTo("账单地址一号");
        assertThat(service.export(d.id()).company().name()).isEqualTo("测试商贸有限公司");
        assertThatThrownBy(()->service.updateQuote(d.id(),new QuoteInput(UUID.randomUUID(),99L,901L,LocalDate.now().plusDays(10),null,null,input.lines())))
            .isInstanceOf(BusinessException.class).hasMessage("COMMERCIAL_STALE_VERSION");
    }
    @Test @Order(2) void concurrentConversionCreatesOneOrderAndReusesApprovalPolicy() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var futures=pool.invokeAll(List.<Callable<DocumentView>>of(()->service.convert(quoteId,202L,"Test"),()->service.convert(quoteId,202L,"Test")));
            DocumentView a=futures.get(0).get(30,TimeUnit.SECONDS),b=futures.get(1).get(30,TimeUnit.SECONDS);
            assertThat(a.salesOrderId()).isEqualTo(b.salesOrderId()).isNotNull();assertThat(a.status()).isEqualTo("CONVERTED");
            assertThat(quantity("select count(*) from sales_orders")).isEqualTo(orderCountBefore+1);
            assertThat(query("select commercial_status from sales_orders where id="+a.salesOrderId())).isEqualTo("PENDING_APPROVAL");
            assertThat(query("select ship_address1 from sales_orders where id="+a.salesOrderId())).isEqualTo("仓库二号");
            assertThatThrownBy(()->service.createInvoice(new InvoiceInput(UUID.randomUUID(),a.salesOrderId(),LocalDate.now()),202L)).hasMessage("COMMERCIAL_ORDER_NOT_CONFIRMED");
        } finally {pool.shutdownNow();}
    }
    @Test @Order(3) void invoiceAndReceiptsDoNotMoveStockAndRetryCannotDoubleCharge() throws Exception {
        long stock=quantity("select count(*) from stock_transactions");
        var d=service.createInvoice(new InvoiceInput(UUID.randomUUID(),901L,LocalDate.now().plusDays(30)),202L);invoiceId=d.id();
        assertThat(d.total()).isEqualByComparingTo("50.00");
        assertThat(service.createInvoice(new InvoiceInput(UUID.randomUUID(),901L,LocalDate.now()),202L).id()).isEqualTo(invoiceId);
        var payment=payment("10.00");
        assertThat(service.recordReceipt(invoiceId,payment,202L).received()).isEqualByComparingTo("10.00");
        assertThat(service.recordReceipt(invoiceId,payment,202L).receipts()).hasSize(1);
        assertThatThrownBy(()->service.recordReceipt(invoiceId,payment("40.01"),202L)).hasMessage("COMMERCIAL_OVERPAYMENT");
        assertThatThrownBy(()->service.recordReceipt(invoiceId,new ReceiptInput(payment.requestKey(),new BigDecimal("11.00"),payment.paymentDate(),payment.method(),null,null),202L)).hasMessage("COMMERCIAL_REQUEST_CONFLICT");
        assertThat(quantity("select count(*) from stock_transactions")).isEqualTo(stock);
    }
    @Test @Order(4) void concurrentReceiptsCannotExceedBalanceAndCorrectionsKeepEvidence() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var results=pool.invokeAll(List.<Callable<Boolean>>of(()->receiveTwentyFive(),()->receiveTwentyFive()));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(true,false);
        } finally {pool.shutdownNow();}
        var d=service.get(invoiceId);assertThat(d.received()).isEqualByComparingTo("35.00");
        assertThatThrownBy(()->service.voidInvoice(invoiceId,"test")).hasMessage("COMMERCIAL_PAID_INVOICE_VOID");
        for(var r:d.receipts()) {service.reverseReceipt(invoiceId,r.id(),"录入错误",202L);service.reverseReceipt(invoiceId,r.id(),"重试",202L);}
        assertThat(service.get(invoiceId).received()).isZero();assertThat(service.get(invoiceId).receipts()).hasSize(4);
        assertThatThrownBy(()->execute("update commercial_receipt set amount=1 where document_id="+invoiceId)).isInstanceOf(SQLException.class).hasMessageContaining("immutable");
        assertThat(service.voidInvoice(invoiceId,"账单录入错误").status()).isEqualTo("VOID");
        assertThat(service.createInvoice(new InvoiceInput(UUID.randomUUID(),901L,LocalDate.now()),202L).id()).isNotEqualTo(invoiceId);
    }
    @Test @Order(5) void tenantAndCustomerOwnershipAreEnforced() throws Exception {
        TenantContextHolder.set(new TenantContext(2L,"upgrade-2","upgrade-2.test",RequestSurface.TENANT,TenantStatus.ACTIVE));
        assertThat(service.list("QUOTE",null,0,20).getTotalElements()).isZero();
        assertThatThrownBy(()->service.get(quoteId)).hasMessage("COMMERCIAL_DOCUMENT_NOT_FOUND");
        TenantContextHolder.clear();
        var user=User.builder().id(201L).username("another-salesperson").enabled(true).build();user.setCompanyId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new SecurityUser(user),"",List.of(new SimpleGrantedAuthority("SALESPERSON"))));
        assertThat(service.list("QUOTE",null,0,20).getTotalElements()).isZero();
        assertThatThrownBy(()->service.get(quoteId)).isInstanceOf(BusinessException.class);
        user.setId(202L);
        assertThat(service.get(quoteId).customerName()).contains("*");
        try(Connection c=DriverManager.getConnection(URL,"wms_upgrade_app","");Statement s=c.createStatement()) {
            s.execute("select set_config('app.company_id','2',false)");
            assertThat(scalar(c,"select count(*) from commercial_document")).isEqualTo("0");
            assertThat(scalar(c,"select count(*) from sys_permission where permission_code like 'quotation:%'")).isEqualTo("3");
        }
    }
    private boolean receiveTwentyFive(){try{service.recordReceipt(invoiceId,payment("25.00"),202L);return true;}catch(BusinessException ex){assertThat(ex.getErrorKey()).isEqualTo("COMMERCIAL_OVERPAYMENT");return false;}}
    private static QuoteInput input(UUID key){return new QuoteInput(key,null,901L,LocalDate.now().plusDays(30),null,"测试条款",List.of(new LineInput(901L,2,new BigDecimal("12.50"))));}
    private static ReceiptInput payment(String amount){return new ReceiptInput(UUID.randomUUID(),new BigDecimal(amount),LocalDate.now(),PaymentMethod.BANK_TRANSFER,"BANK-TEST",null);}
    private static long quantity(String sql)throws Exception{return Long.parseLong(query(sql));}
    private static String query(String sql)throws Exception{try(Connection c=DriverManager.getConnection(URL,"postgres","")){return scalar(c,sql);}}
    private static String scalar(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private static void execute(String sql)throws SQLException{try(Connection c=DriverManager.getConnection(URL,"postgres","");Statement s=c.createStatement()){s.execute(sql);}}
}
