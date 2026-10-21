package com.wms.system.service;

import com.wms.system.dto.inventory.*;
import com.wms.system.dto.stocktake.*;
import com.wms.system.dto.transfer.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.*;
import com.wms.system.security.SecurityUser;
import com.wms.system.service.inventory.*;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL transactions; no mocked repositories and no enclosing rollback-only test transaction. */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update", "spring.jpa.properties.hibernate.hbm2ddl.auto=update",
    "wms.tenancy.require-safe-database-role=false", "wms.legacy-admin-bootstrap-enabled=false",
    "wms.platform-bootstrap.enabled=false", "wms.integration.shopify.scheduler-enabled=false",
    "wms.integration.shopify.reconcile-enabled=false", "wms.tenant-security.webhook-worker-enabled=false",
    "wms.tenancy.purge-enabled=false", "wms.inventory-disposition.expiry-cron=-"
})
@ActiveProfiles("test")
class InventoryPackagingPostgresIntegrationTest {
    @Autowired InventoryPackagingCommandService commands;
    @Autowired InventoryPackagingService packaging;
    @Autowired InventoryPostingService posting;
    @Autowired InventoryQueryService query;
    @Autowired StocktakeService stocktake;
    @Autowired StocktakeReviewCommandService review;
    @Autowired InventoryBatchRepository batches;
    @Autowired ProductSkuRepository skus;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired WarehouseRepository warehouses;
    @Autowired LocationRepository locations;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired OutboundService outbound;
    @Autowired SalesOrderRepository salesOrders;
    @Autowired SalesOrderItemRepository salesItems;
    @Autowired OutboundTaskRepository outboundTasks;
    @Autowired CustomerRepository customers;
    @Autowired com.wms.system.service.transfer.TransferService transfers;
    @Autowired ReceivingReceiptExecutor receipts;
    @Autowired OtherInboundApplicationRepository otherInbounds;
    @Autowired ReceivingTaskRepository receivingTasks;
    @Autowired UserRepository users;
    @Autowired SysRoleRepository roles;
    @Autowired SysUserRoleRepository userRoles;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    long company = 8_100_000L + ThreadLocalRandom.current().nextLong(100_000);
    final String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    final Authentication auth = new UsernamePasswordAuthenticationToken(
        new SecurityUser(User.builder().id(88001L).username("packaging-test").password("unused").build()), null,
        List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"), new SimpleGrantedAuthority("TENANT_ADMIN")));

    @BeforeEach void isolatedDatabaseOnly() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).startsWith("packaging_test_");
        jdbc.execute("create unique index if not exists uk_inventory_batch_company_batch_location on inventory_batch(company_id,batch_code,location_code)");
    }
    @AfterEach void clearContext() { TenantContextHolder.clear(); }

    @Test void verifiesLegacyStockThenMergesOverStandardAndReplaysWithoutDoublePosting() {
        Fixture f = seed(29, null);
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isNull();
        var verified = arrange(f, p(1, 9, 8), ArrangePackagingRequest.Mode.VERIFY);
        assertThat(verified.quantity()).isEqualTo(29);
        String key = key();
        var request = request(List.of(verified), List.of(result(f.a, p(1, 17))), ArrangePackagingRequest.Mode.ARRANGE);
        var first = in(() -> commands.arrange(request, auth, key));
        assertThat(in(() -> commands.arrange(request, auth, key))).isEqualTo(first);
        assertThat(first.get(0).packaging()).isEqualTo(p(1, 17));
        assertThat(ledgerCount()).isZero();
        var details = in(() -> query.getDetailsBySkuId(f.sku));
        assertThat(details.get(0).getPackaging()).isEqualTo(p(1, 17));
        assertThat(details.get(0).getPackageStatus()).contains("17");
        assertThat(in(() -> query.getSummary(org.springframework.data.domain.PageRequest.of(0, 20), tag))
            .getContent().get(0).getSealedBoxes()).isEqualTo(1);
        var different = request(List.of(verified), List.of(result(f.a, p(1, 12, 5))), ArrangePackagingRequest.Mode.ARRANGE);
        assertThatThrownBy(() -> in(() -> commands.arrange(different, auth, key))).hasMessage("IDEMPOTENCY_CONFLICT");
    }

    @Test void movesSealedAndOpenedGoodsBetweenLocationsWithoutChangingBatchOrTotal() {
        Fixture f = seed(41, p(2, 9, 8));
        var before = in(() -> packaging.get(f.batch, auth));
        var after = in(() -> commands.arrange(request(List.of(before),
            List.of(result(f.a, p(1, 9)), result(f.b, p(1, 8))), ArrangePackagingRequest.Mode.ARRANGE), auth, key()));
        assertThat(after.stream().mapToInt(PackagingView::quantity).sum()).isEqualTo(41);
        assertThat(after).allMatch(v -> v.batchCode().equals(before.batchCode()));
        assertThat(after.stream().filter(v -> v.locationId().equals(f.b)).findFirst().orElseThrow().packaging()).isEqualTo(p(1, 8));
        assertThat(jdbc.queryForObject("select sum(case when transactiontype='IN' then quantity else -quantity end) from stock_transactions where company_id=?", Integer.class, company)).isZero();
    }

    @Test void explicitOpeningPreservesQuantityAndNeverResealsOpenedContents() {
        Fixture f = seed(29, p(1, 17));
        var opened = arrange(f, p(0, 12, 17), ArrangePackagingRequest.Mode.ARRANGE);
        assertThat(opened.quantity()).isEqualTo(29);
        assertThat(opened.packaging().sealedBoxes()).isZero();
        assertThatThrownBy(() -> arrange(f, p(1, 17), ArrangePackagingRequest.Mode.ARRANGE)).hasMessage("PACKAGING_CANNOT_RESEAL");
        assertThat(ledgerCount()).isZero();
    }

    @Test void rejectsMismatchedTotalsAndLeavesAllBalancesUnchanged() {
        Fixture f = seed(17, p(0, 9, 8));
        assertThatThrownBy(() -> arrange(f, p(0, 16), ArrangePackagingRequest.Mode.ARRANGE)).hasMessage("PACKAGING_TOTAL_MISMATCH");
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(0, 9, 8));
        assertThat(ledgerCount()).isZero();
    }

    @Test void onlyOneConcurrentArrangementCanUseTheSameVersion() throws Exception {
        Fixture f = seed(17, p(0, 9, 8));
        var before = in(() -> packaging.get(f.batch, auth));
        var request = request(List.of(before), List.of(result(f.a, p(0, 17))), ArrangePackagingRequest.Mode.ARRANGE);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                start.await();
                try { in(() -> commands.arrange(request, auth, key())); return true; }
                catch (BusinessException e) { assertThat(e.getErrorKey()).isEqualTo("PACKAGING_STALE"); return false; }
            }));
            start.countDown();
            int successes = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(in(() -> packaging.get(f.batch, auth)).quantity()).isEqualTo(17);
        } finally { pool.shutdownNow(); }
    }

    @Test void stocktakeCanRecordARepackWithoutLossThenApplyAnActualShortageOnce() {
        Fixture f = seed(17, p(0, 9, 8));
        count(f, p(0, 17));
        assertThat(ledgerCount()).isZero();
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(0, 17));
        count(f, p(0, 16));
        assertThat(in(() -> packaging.get(f.batch, auth)).quantity()).isEqualTo(16);
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(0, 16));
        assertThat(ledgerCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity from stock_transactions where company_id=?", Integer.class, company)).isEqualTo(1);
    }

    @Test void countingBlocksRepackingAndDispatchAndCancellationDoesNotInventComposition() {
        Fixture f = seed(17, p(0, 9, 8));
        var task = in(() -> stocktake.createCycleTask(new CreateStocktakeTaskRequest(f.warehouse, "ADHOC"), 88001L, "test"));
        in(() -> stocktake.startCounting(task.getId()));
        assertThatThrownBy(() -> arrange(f, p(0, 17), ArrangePackagingRequest.Mode.ARRANGE)).hasMessage("PACKAGING_STOCKTAKE_LOCKED");
        assertThatThrownBy(() -> in(() -> posting.post(new InventoryPostingCommand(f.batch, f.sku, f.a, -17,
            new PostingContext(company, f.warehouse, SourceType.SALE_OUT, "TEST", "TEST", null, 88001L, "test", key(), key())))))
            .hasMessage("PACKAGING_STOCKTAKE_LOCKED");
        in(() -> stocktake.cancelStocktakeTask(task.getId(), "test cancellation", 88001L));
        assertThat(in(() -> packaging.get(f.batch, auth)).stocktakeTaskId()).isNull();
        assertThat(in(() -> packaging.get(f.batch, auth)).quantity()).isEqualTo(17);
    }

    @Test void tenantBoundaryDoesNotExposeAnotherCompanyPackaging() {
        Fixture f = seed(17, p(0, 17));
        var other = new TenantContext(company + 200_000, "other", "other.example.test", RequestSurface.TENANT, TenantStatus.ACTIVE);
        assertThatThrownBy(() -> TenantContextHolder.runWithTenant(other, () -> packaging.get(f.batch, auth)))
            .isInstanceOf(BusinessException.class).hasMessage("BATCH_NOT_FOUND");
    }

    @Test void pendingPickingTaskBlocksRepackingAndCountingEvenWithoutAReservation() {
        Fixture f = seed(17, p(0, 9, 8));
        outboundTask(f, 4);
        assertThat(in(() -> packaging.get(f.batch, auth)).reservedQuantity()).isZero();
        assertThatThrownBy(() -> arrange(f, p(0, 17), ArrangePackagingRequest.Mode.ARRANGE))
            .hasMessage("PACKAGING_RESERVED");
        var task = in(() -> stocktake.createCycleTask(new CreateStocktakeTaskRequest(f.warehouse, "ADHOC"), 88001L, "test"));
        assertThatThrownBy(() -> in(() -> stocktake.startCounting(task.getId()))).hasMessage("PACKAGING_RESERVED");
        assertThat(in(() -> packaging.get(f.batch, auth)).stocktakeTaskId()).isNull();
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(0, 9, 8));
        assertThat(ledgerCount()).isZero();
    }

    @Test void actualDispatchConsumesChosenOpenedBoxAndRejectsDuplicateDispatch() {
        Fixture f = seed(29, p(1, 9, 8));
        Long taskId = outboundTask(f, 4);
        var before = in(() -> packaging.get(f.batch, auth));
        in(() -> outbound.confirmDispatch(taskId, 4, f.a, null, before.batchCode(), 88001L, "test",
            new StockPackaging.Selection(before.version(), 0, List.of(4, 0), List.of())));
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(1, 5, 8));
        assertThat(ledgerCount()).isEqualTo(1);
        assertThatThrownBy(() -> in(() -> outbound.confirmDispatch(taskId, 4, 88001L, "test")))
            .hasMessage("OUTBOUND_TASK_ALREADY_COMPLETED");
        assertThat(in(() -> packaging.get(f.batch, auth)).quantity()).isEqualTo(25);
    }

    @Test void dispatchOpensOneSealedBoxAndKeepsOtherBoxUntouched() {
        Fixture f = seed(24, p(2));
        Long taskId = outboundTask(f, 3);
        var before = in(() -> packaging.get(f.batch, auth));
        in(() -> outbound.confirmDispatch(taskId, 3, f.a, null, before.batchCode(), 88001L, "test",
            new StockPackaging.Selection(before.version(), 0, List.of(), List.of(3))));
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(1, 9));
        assertThat(ledgerCount()).isEqualTo(1);
    }

    @Test void actualTransferPreservesCompositionThroughSplitDestinationReceipt() {
        Fixture f = seed(29, p(1, 9, 8));
        long[] destination = in(() -> new TransactionTemplate(transactions).execute(status -> {
            var wh = warehouses.save(Warehouse.builder().code("DST" + tag).name("Destination").isActive(true).build());
            var a = locations.save(Location.builder().warehouse(wh).zone(Zone.ZONE_A).shelfNumber("C").positionNumber("01").enabled(true).build());
            var b = locations.save(Location.builder().warehouse(wh).zone(Zone.ZONE_A).shelfNumber("D").positionNumber("02").enabled(true).build());
            return new long[] { wh.getId(), a.getId(), b.getId() };
        }));
        var draft = in(() -> transfers.create(new CreateTransferRequest(f.warehouse, destination[0], "physical packaging test",
            null, List.of(new CreateTransferRequest.Line(f.sku, 29, f.batch, null))), key(), 88001L, "test"));
        in(() -> transfers.submit(draft.id(), new TransferDecisionRequest(draft.version(), "test"), key(), 88001L, "test"));
        var out = in(() -> transfers.listTasks(TransferTaskDirection.OUTBOUND, null)).get(0);
        long allocation = out.lines().get(0).allocations().get(0).allocationId();
        String dispatchKey = key();
        var dispatch = new DispatchTransferRequest(out.version(), null, null, null,
            List.of(new DispatchTransferRequest.Line(allocation, 29)));
        in(() -> transfers.dispatch(out.id(), dispatch, dispatchKey, 88001L, "test"));
        in(() -> transfers.dispatch(out.id(), dispatch, dispatchKey, 88001L, "test"));
        assertThat(in(() -> packaging.get(f.batch, auth)).quantity()).isZero();
        var incoming = in(() -> transfers.listTasks(TransferTaskDirection.INBOUND, null)).get(0);
        var line = incoming.dispatchLines().get(0);
        assertThat(line.packaging()).isEqualTo(p(1, 9, 8));
        var receive = new ReceiveTransferRequest(incoming.version(), "two destination locations", List.of(
            new ReceiveTransferRequest.Line(line.dispatchLineId(), destination[1], TransferReceiptDisposition.AVAILABLE, 21,
                new StockPackaging.Selection(Math.toIntExact(line.packagingVersion()), 1, List.of(9, 0), List.of())),
            new ReceiveTransferRequest.Line(line.dispatchLineId(), destination[2], TransferReceiptDisposition.AVAILABLE, 8,
                new StockPackaging.Selection(Math.toIntExact(line.packagingVersion()), 0, List.of(0, 8), List.of()))));
        String receiptKey = key();
        in(() -> transfers.receive(incoming.id(), receive, receiptKey, 88001L, "test"));
        in(() -> transfers.receive(incoming.id(), receive, receiptKey, 88001L, "test"));
        var rows = in(() -> new TransactionTemplate(transactions).execute(status ->
            batches.findPositiveActiveBatchesByLocationId(destination[1]).stream().map(PackagingView::of).toList()));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).packaging()).isEqualTo(p(1, 9));
        assertThat(rows.get(0).batchCode()).startsWith("TRB");
        StockPackaging destinationPackaging = in(() -> new TransactionTemplate(transactions).execute(status ->
            batches.findPositiveActiveBatchesByLocationId(destination[2]).get(0).getPackaging()));
        assertThat(destinationPackaging).isEqualTo(p(0, 8));
        assertThat(ledgerCount()).isEqualTo(3);
    }

    @Test void moveBackToAFormerLocationReusesItsEmptyBalance() {
        Fixture f = seed(29, p(1, 9, 8));
        var before = in(() -> packaging.get(f.batch, auth));
        var moved = in(() -> commands.arrange(request(List.of(before), List.of(result(f.b, p(1, 9, 8))),
            ArrangePackagingRequest.Mode.ARRANGE), auth, key())).stream().filter(v -> v.quantity() > 0).findFirst().orElseThrow();
        var returned = in(() -> commands.arrange(request(List.of(moved), List.of(result(f.a, p(1, 9, 8))),
            ArrangePackagingRequest.Mode.ARRANGE), auth, key())).stream().filter(v -> v.quantity() > 0).findFirst().orElseThrow();
        assertThat(returned.batchId()).isEqualTo(f.batch);
        assertThat(returned.packaging()).isEqualTo(p(1, 9, 8));
        assertThat(ledgerCount()).isEqualTo(4);
    }

    @Test void reservedStockCannotBeRepacked() {
        Fixture f = seed(29, p(1, 9, 8));
        in(() -> new TransactionTemplate(transactions).execute(status -> {
            var batch = batches.findByIdForPosting(f.batch).orElseThrow();
            batch.setReservedQuantity(1); return batches.saveAndFlush(batch);
        }));
        assertThatThrownBy(() -> arrange(f, p(1, 17), ArrangePackagingRequest.Mode.ARRANGE)).hasMessage("PACKAGING_RESERVED");
        assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(1, 9, 8));
        assertThat(ledgerCount()).isZero();
    }

    @Test void destinationLedgerFailureRollsBackBothLocationsAndPhysicalComposition() {
        Fixture f = seed(29, p(1, 9, 8));
        String object = "packaging_fail_" + tag.toLowerCase(Locale.ROOT);
        jdbc.execute("create function " + object + "() returns trigger language plpgsql as $$ begin "
            + "if NEW.company_id=" + company + " and NEW.transactiontype='IN' then raise exception 'forced packaging destination failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger " + object + " before insert on stock_transactions for each row execute function " + object + "()");
        try {
            var before = in(() -> packaging.get(f.batch, auth));
            var request = request(List.of(before), List.of(result(f.a, p(1, 9)), result(f.b, p(0, 8))), ArrangePackagingRequest.Mode.ARRANGE);
            assertThatThrownBy(() -> in(() -> commands.arrange(request, auth, key())))
                .hasStackTraceContaining("forced packaging destination failure");
            assertThat(in(() -> packaging.get(f.batch, auth)).packaging()).isEqualTo(p(1, 9, 8));
            assertThat(in(() -> packaging.get(f.batch, auth)).quantity()).isEqualTo(29);
            assertThat(jdbc.queryForObject("select count(*) from inventory_batch where company_id=? and location_id=?", Integer.class, company, f.b)).isZero();
            assertThat(ledgerCount()).isZero();
        } finally {
            jdbc.execute("drop trigger " + object + " on stock_transactions");
            jdbc.execute("drop function " + object + "()");
        }
    }

    @Test void warehouseStaffCannotReadOrArrangeAnUnassignedWarehouse() {
        Fixture f = seed(29, p(1, 9, 8));
        var staff = new UsernamePasswordAuthenticationToken(
            new SecurityUser(User.builder().id(88002L).username("unassigned-staff").password("unused").build()), null,
            List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"), new SimpleGrantedAuthority("WAREHOUSE_STAFF")));
        assertThatThrownBy(() -> in(() -> packaging.get(f.batch, staff))).hasMessage("WAREHOUSE_SCOPE_DENIED");
        var before = in(() -> packaging.get(f.batch, auth));
        assertThatThrownBy(() -> in(() -> commands.arrange(
            request(List.of(before), List.of(result(f.a, p(1, 17))), ArrangePackagingRequest.Mode.ARRANGE), staff, key())))
            .hasMessage("WAREHOUSE_SCOPE_DENIED");
    }

    @Test void migrationAddsNullablePhysicalFieldsWithoutInventingLegacyComposition() throws Exception {
        String schema = "packaging_migration_" + tag.toLowerCase(Locale.ROOT);
        jdbc.execute("create schema " + schema);
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            String originalSchema = connection.getSchema();
            try {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.execute("create table inventory_batch(id bigint, company_id bigint, quantity integer)");
                statement.execute("insert into inventory_batch values (1, 1, 29)");
                statement.execute("create table stocktake_items(id bigint)");
                statement.execute("create table transfer_dispatch_line(id bigint)");
            }
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                new org.springframework.core.io.ClassPathResource("db/migration/V4_96__Actual_inventory_packaging.sql"));
            try (var statement = connection.createStatement(); var result = statement.executeQuery("select quantity, packaging from inventory_batch")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(29);
                assertThat(result.getObject(2)).isNull();
            }
            } finally { connection.setSchema(originalSchema); }
        } finally { jdbc.execute("drop schema " + schema + " cascade"); }
    }

    @Test void unifiedReceiptRecordsPhysicalBoxesInTwoLocationsAndReplaysOnce() {
        Fixture f = seed(0, null);
        var ids = receivingTask(f);
        var request = new com.wms.system.dto.receiving.SubmitReceivingReceiptRequest("physical receipt",
            List.of(new com.wms.system.dto.receiving.SubmitReceivingReceiptRequest.Line(ids[1], f.a, 21, null, null, "SUPPLIER-LOT", p(1, 9)),
                new com.wms.system.dto.receiving.SubmitReceivingReceiptRequest.Line(ids[1], f.b, 8, null, null, "SUPPLIER-LOT", p(0, 8))));
        String key = key();
        var received = in(() -> receipts.execute(ids[0], key, request, 88001L, "test"));
        assertThat(in(() -> receipts.execute(ids[0], key, request, 88001L, "test")).id()).isEqualTo(received.id());
        var a = in(() -> new TransactionTemplate(transactions).execute(status ->
            batches.findPositiveActiveBatchesByLocationId(f.a).stream().map(PackagingView::of).toList()));
        var b = in(() -> new TransactionTemplate(transactions).execute(status ->
            batches.findPositiveActiveBatchesByLocationId(f.b).stream().map(PackagingView::of).toList()));
        assertThat(a.get(0).packaging()).isEqualTo(p(1, 9));
        assertThat(b.get(0).packaging()).isEqualTo(p(0, 8));
        assertThat(a.get(0).batchCode()).isEqualTo(b.get(0).batchCode());
        assertThat(ledgerCount()).isEqualTo(2);
    }

    @Test void seedIsolatedBrowserAcceptanceFixture() throws Exception {
        company = 1L;
        Fixture f = seed(29, p(1, 9, 8));
        String username = "packaging-" + tag.toLowerCase(Locale.ROOT);
        in(() -> new TransactionTemplate(transactions).execute(status -> {
            var role = roles.findByRoleCode("TENANT_ADMIN").orElseGet(() -> roles.saveAndFlush(SysRole.builder()
                .roleCode("TENANT_ADMIN").roleName("Test administrator").roleType("SYSTEM").status("ACTIVE").build()));
            var user = users.saveAndFlush(User.builder().username(username).displayName("包装验收测试")
                .password(passwordEncoder.encode("Packaging-Test-Only-2026!")).enabled(true).defaultRoleId(role.getId()).build());
            userRoles.save(SysUserRole.builder().userId(user.getId()).roleId(role.getId()).build());
            return user.getId();
        }));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/packaging-browser-fixture.json"),
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "username", username, "batchId", f.batch, "productSkuId", f.sku, "warehouseId", f.warehouse,
                "locationA", f.a, "locationB", f.b, "skuCode", "SKU" + tag)));
    }

    private long[] receivingTask(Fixture f) {
        return in(() -> new TransactionTemplate(transactions).execute(status -> {
            var sku = skus.findById(f.sku).orElseThrow();
            var wh = warehouses.findById(f.warehouse).orElseThrow();
            var application = OtherInboundApplication.builder().applicationNumber("OI" + tag)
                .businessType(OtherInboundBusinessType.values()[0]).sourceReference("packaging test").businessReason("test")
                .warehouse(wh).status(OtherInboundStatus.CONFIRMED).createdBy(88001L).createdByName("test").build();
            application.addItem(OtherInboundApplicationItem.builder().productSku(sku).plannedQuantity(29).build());
            application = otherInbounds.saveAndFlush(application);
            var task = ReceivingTask.builder().taskNumber("RT" + tag).sourceType(ReceivingTaskSourceType.OTHER_INBOUND)
                .otherInboundApplication(application).warehouse(wh).status(ReceivingTaskStatus.PENDING)
                .createdBy(88001L).createdByName("test").build();
            task.addItem(ReceivingTaskItem.builder().otherInboundApplicationItem(application.getItems().get(0))
                .productSku(sku).expectedQuantity(29).build());
            task = receivingTasks.saveAndFlush(task);
            return new long[] {task.getId(), task.getItems().get(0).getId()};
        }));
    }

    private Long outboundTask(Fixture f, int quantity) {
        return in(() -> new TransactionTemplate(transactions).execute(status -> {
            var customer = customers.save(Customer.builder().code("C" + tag).name("Packaging test").isActive(true).build());
            var order = salesOrders.saveAndFlush(SalesOrder.builder().orderNo("SO" + tag).customerId(customer.getId())
                .applicantId(88001L).applicantName("test").commercialStatus(CommercialStatus.APPROVED).build());
            var item = salesItems.saveAndFlush(SalesOrderItem.builder().salesOrderId(order.getId()).productSkuId(f.sku)
                .quantity(quantity).requestedQty(quantity).unitPrice(BigDecimal.ONE).subtotal(BigDecimal.valueOf(quantity)).build());
            return outboundTasks.saveAndFlush(OutboundTask.builder().salesOrderId(order.getId()).salesOrderItemId(item.getId())
                .assignedBatchId(f.batch).locationId(f.a).planQty(quantity).status(OutboundTaskStatus.PENDING).build()).getId();
        }));
    }

    private void count(Fixture f, StockPackaging counted) {
        var task = in(() -> stocktake.createCycleTask(new CreateStocktakeTaskRequest(f.warehouse, "ADHOC"), 88001L, "test"));
        in(() -> stocktake.startCounting(task.getId()));
        var item = in(() -> stocktake.getStocktakeItems(task.getId())).get(0);
        in(() -> stocktake.submitCount(task.getId(), item.getId(), SubmitCountRequest.builder()
            .countedQty(counted.total()).packaging(counted).build(), 88001L, "test"));
        in(() -> stocktake.finishCounting(task.getId()));
        String key = key();
        var response = in(() -> review.review(task.getId(), new ReviewStocktakeRequest(true, "test"), 88001L, "test", key));
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(in(() -> review.review(task.getId(), new ReviewStocktakeRequest(true, "test"), 88001L, "test", key)).getId()).isEqualTo(task.getId());
    }

    private PackagingView arrange(Fixture f, StockPackaging p, ArrangePackagingRequest.Mode mode) {
        var view = in(() -> packaging.get(f.batch, auth));
        return in(() -> commands.arrange(request(List.of(view), List.of(result(f.a, p)), mode), auth, key())).get(0);
    }
    private ArrangePackagingRequest request(List<PackagingView> before, List<ArrangePackagingRequest.Result> results, ArrangePackagingRequest.Mode mode) {
        return new ArrangePackagingRequest(mode, before.stream().map(v -> new ArrangePackagingRequest.Source(v.batchId(), v.version())).toList(), results);
    }
    private ArrangePackagingRequest.Result result(Long location, StockPackaging p) { return new ArrangePackagingRequest.Result(location, p); }
    private StockPackaging p(int sealed, Integer... opened) { return new StockPackaging(12, sealed, List.of(opened)); }
    private String key() { return "pack-test-" + UUID.randomUUID(); }
    private int ledgerCount() { return jdbc.queryForObject("select count(*) from stock_transactions where company_id=?", Integer.class, company); }
    private <T> T in(Supplier<T> action) {
        return TenantContextHolder.runWithTenant(new TenantContext(company, "pack-" + tag, "pack.example.test", RequestSurface.TENANT, TenantStatus.ACTIVE), action);
    }
    private Fixture seed(int quantity, StockPackaging p) {
        return in(() -> new TransactionTemplate(transactions).execute(status -> {
            var category = categories.save(Category.builder().categoryCode("CAT" + tag).categoryName(tag).enabled(true).build());
            var product = products.save(Product.builder().productCode("PROD" + tag).productName(tag).category(category).enabled(true).build());
            var sku = skus.save(ProductSku.builder().skuCode("SKU" + tag).product(product).skuName(tag).name(tag)
                .barcode("BAR" + tag).unitPrice(BigDecimal.ONE).enabled(true).expiryManagementEnabled(false)
                .conversionRate(12).perPackQty(12).baseUnit("袋").packUnit("箱").build());
            var warehouse = warehouses.save(Warehouse.builder().code("WH" + tag).name(tag).isActive(true).build());
            var a = locations.save(Location.builder().warehouse(warehouse).zone(Zone.ZONE_A).shelfNumber("A").positionNumber("01").enabled(true).build());
            var b = locations.save(Location.builder().warehouse(warehouse).zone(Zone.ZONE_A).shelfNumber("B").positionNumber("02").enabled(true).build());
            var batch = batches.saveAndFlush(InventoryBatch.builder().batchCode("LOT" + tag).productSku(sku)
                .location(a).locationCode(a.getLocationCode()).quantity(quantity).initialQuantity(quantity).packaging(p)
                .entryDate(LocalDateTime.now()).expiryDate(LocalDate.now().plusYears(1)).active(true).build());
            return new Fixture(batch.getId(), sku.getId(), warehouse.getId(), a.getId(), b.getId());
        }));
    }
    record Fixture(Long batch, Long sku, Long warehouse, Long a, Long b) {}
}
