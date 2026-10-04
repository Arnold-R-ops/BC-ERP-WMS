package com.wms.system.uat;

import com.wms.system.dto.sales.*;
import com.wms.system.dto.stocktake.*;
import com.wms.system.entity.OutboundTask;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.OutboundTaskRepository;
import com.wms.system.service.*;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.TenantStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Destructive scenarios are allowed ONLY in a separate copy, never in phone UAT. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "A2_FIX_ACCEPTANCE", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class A2FixAcceptanceTest {
    @DynamicPropertySource
    static void isolatedCopy(DynamicPropertyRegistry registry) {
        String url = System.getenv("A2_FIX_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:55484/wms_a2_fix_20260908".equals(url)) {
            throw new IllegalStateException("Refusing any database other than the dedicated A2 fix copy");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "wms_p004c_app");
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("wms.legacy-admin-bootstrap-enabled", () -> false);
        registry.add("wms.tenancy.enabled", () -> false);
        registry.add("wms.tenancy.require-safe-database-role", () -> true);
        registry.add("spring.task.scheduling.enabled", () -> false);
        registry.add("logging.level.com.wms.system", () -> "WARN");
    }

    @Autowired SalesSubmissionService sales;
    @Autowired SalesCommandIdempotencyService salesCommands;
    @Autowired OutboundService outbound;
    @Autowired OutboundTaskRepository tasks;
    @Autowired StocktakeService stocktake;
    @Autowired StocktakeReviewCommandService reviews;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    private static final TenantContext COMPANY = new TenantContext(1L, "a2-ordinary",
        "a2-ordinary.localhost", RequestSurface.TENANT, TenantStatus.ACTIVE);

    @Test @Order(1)
    void cancelAndReplayReleaseReservationsOnceWithDatabaseAuditConstraintIntact() {
        tenant(() -> {
            assertThat(read(() -> jdbc.queryForObject("select current_database()", String.class)))
                .isEqualTo("wms_a2_fix_20260908");
            assertThat(read(() -> jdbc.queryForObject(
                "select rolsuper or rolbypassrls from pg_roles where rolname=current_user", Boolean.class))).isFalse();
            assertThat(read(() -> jdbc.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname='ck_outbound_task_archive_fields'",
                String.class))).contains("archived_at", "archived_by", "archive_reason");
            int stockBefore = totalStock();
            long ledgerBefore = ledgerCount();
            long id = newOrder(Map.of(1L, 2));
            assertThat(openReserved(id)).isEqualTo(2);
            String key = key();
            SalesOrderResponse first = cancel(id, key);
            SalesOrderResponse replay = cancel(id, key);
            assertThat(first.getCommercialStatus()).isEqualTo("CANCELLED");
            assertThat(replay.getId()).isEqualTo(id);
            assertThat(openReserved(id)).isZero();
            List<OutboundTask> cancelledTasks = read(() -> tasks.findBySalesOrderId(id));
            assertThat(cancelledTasks).isNotEmpty();
            for (OutboundTask task : cancelledTasks) {
                assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.VOIDED);
                assertThat(task.getArchivedBy()).isEqualTo(8L);
                assertThat(task.getArchivedAt()).isNotNull();
                assertThat(task.getArchiveReason()).isEqualTo("A2 fix cancellation");
                assertThat(task.getActualQty()).isZero();
            }
            assertThat(totalStock()).isEqualTo(stockBefore);
            assertThat(ledgerCount()).isEqualTo(ledgerBefore);
            assertNoReservedDrift();
        });
    }

    @Test @Order(2)
    void partlyShippedClosePreservesShippedFactAndReleasesOnlyRemainder() {
        tenant(() -> {
            long id = newOrder(Map.of(1L, 1, 3L, 2));
            List<OutboundTask> assigned = read(() -> tasks.findBySalesOrderId(id));
            assertThat(assigned).hasSizeGreaterThanOrEqualTo(2);
            OutboundTask shipped = assigned.get(0);
            dispatch(shipped);
            int afterDispatch = totalStock();
            long posted = ledgerCount();
            String key = key();
            Supplier<SalesOrderResponse> close = () -> salesCommands.execute(key, "SALES_ORDER_CLOSE",
                Map.of("id", id, "reason", "A2 fix close balance"), 8L, "a2a-manager", SalesOrderResponse.class,
                () -> sales.closeSalesOrder(id, "A2 fix close balance", 8L, "a2a-manager"));
            assertThat(close.get().getCommercialStatus()).isEqualTo("CLOSED");
            assertThat(close.get().getCommercialStatus()).isEqualTo("CLOSED");
            assertThat(openReserved(id)).isZero();
            List<OutboundTask> completed = read(() -> tasks.findBySalesOrderId(id));
            assertThat(completed).hasSize(assigned.size());
            for (OutboundTask task : completed) {
                if (task.getId().equals(shipped.getId())) {
                    assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.COMPLETED);
                    assertThat(task.getActualQty()).isEqualTo(shipped.getPlanQty());
                    assertThat(task.getArchivedAt()).isNull();
                } else {
                    assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.VOIDED);
                    assertThat(task.getArchivedBy()).isEqualTo(8L);
                }
            }
            assertThat(totalStock()).isEqualTo(afterDispatch);
            assertThat(ledgerCount()).isEqualTo(posted);
            assertNoReservedDrift();
        });
    }

    @Test @Order(3)
    void failureAfterFlushedCancellationRollsBackReservationAndTaskTogether() {
        tenant(() -> {
            long id = newOrder(Map.of(1L, 2));
            int stockBefore = totalStock();
            assertThatThrownBy(() -> write(() -> {
                sales.cancelSalesOrder(id, "A2 forced rollback", 8L, "a2a-manager");
                entityManager.flush(); // Exercise real PG constraint checks before the injected failure.
                throw new IllegalStateException("A2_INJECTED_AFTER_FLUSH");
            })).isInstanceOf(IllegalStateException.class).hasMessage("A2_INJECTED_AFTER_FLUSH");
            assertThat(openReserved(id)).isEqualTo(2);
            assertThat(read(() -> tasks.findBySalesOrderId(id))).isNotEmpty().allSatisfy(task -> {
                assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.PENDING);
                assertThat(task.getArchivedAt()).isNull();
            });
            assertThat(totalStock()).isEqualTo(stockBefore);
            cancel(id, key()); // Retry as a new, valid command.
            assertThat(openReserved(id)).isZero();
            assertNoReservedDrift();
        });
    }

    @Test @Order(4)
    void concurrentCancelAndDispatchHaveOneWinnerWithoutStockOrReservationDrift() throws Exception {
        long id = TenantContextHolder.runWithTenant(COMPANY, () -> newOrder(Map.of(1L, 1)));
        OutboundTask task = TenantContextHolder.runWithTenant(COMPANY, () -> read(() -> tasks.findBySalesOrderId(id).get(0)));
        int before = TenantContextHolder.runWithTenant(COMPANY, this::totalStock);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> cancelled = pool.submit(() -> race(ready, start, () -> sales.cancelSalesOrder(
                id, "Concurrent cancellation", 8L, "a2a-manager")));
            Future<Boolean> dispatched = pool.submit(() -> race(ready, start, () -> {
                dispatch(task); return null;
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            boolean cancelWon = cancelled.get(30, TimeUnit.SECONDS);
            boolean dispatchWon = dispatched.get(30, TimeUnit.SECONDS);
            assertThat(cancelWon ^ dispatchWon).isTrue();
            tenant(() -> {
                assertThat(openReserved(id)).isZero();
                assertThat(totalStock()).isEqualTo(before - (dispatchWon ? 1 : 0));
                assertNoReservedDrift();
            });
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test @Order(5)
    void rejectedStocktakeCanCorrectMultipleLinesWithoutAutomaticResubmission() {
        tenant(() -> {
            int before = totalStock();
            long ledger = ledgerCount();
            long id = createCountTask();
            List<Map<String, Object>> lines = snapshots(id);
            assertThat(lines).hasSizeGreaterThanOrEqualTo(2);
            for (int i = 0; i < lines.size(); i++) {
                saveCount(id, lines.get(i), number(lines.get(i), "snapshot_qty") + (i < 2 ? 1 : 0));
            }
            assertThat(stocktake.getStocktakeTask(id).getStatus()).isEqualTo("COUNTING");
            assertThat(stocktake.finishCounting(id).getStatus()).isEqualTo("REVIEWING");
            assertThat(stocktake.finishCounting(id).getStatus()).isEqualTo("REVIEWING");
            ReviewStocktakeRequest reject = ReviewStocktakeRequest.builder().approved(false).comment("Recheck two lines").build();
            reviews.review(id, reject, 8L, "a2a-manager", key());
            for (int i = 0; i < 2; i++) {
                saveCount(id, lines.get(i), number(lines.get(i), "snapshot_qty"));
                assertThat(stocktake.getStocktakeTask(id).getStatus()).isEqualTo("COUNTING");
            }
            stocktake.finishCounting(id);
            SubmitCountRequest late = new SubmitCountRequest();
            late.setCountedQty(0);
            assertThatThrownBy(() -> stocktake.submitCount(id, ((Number) lines.get(0).get("id")).longValue(),
                late, 5L, "a2a-worker-wh-a")).isInstanceOf(BusinessException.class);
            ReviewStocktakeRequest approve = ReviewStocktakeRequest.builder().approved(true).comment("Corrected").build();
            String key = key();
            assertThat(reviews.review(id, approve, 8L, "a2a-manager", key).getStatus()).isEqualTo("COMPLETED");
            assertThat(reviews.review(id, approve, 8L, "a2a-manager", key).getStatus()).isEqualTo("COMPLETED");
            assertThat(totalStock()).isEqualTo(before);
            assertThat(ledgerCount()).isEqualTo(ledger);
        });
    }

    @Test @Order(6)
    void nonzeroReviewReplayPostsEachDifferenceOnce() {
        tenant(() -> {
            int before = totalStock();
            long ledger = ledgerCount();
            long id = createCountTask();
            List<Map<String, Object>> lines = snapshots(id);
            assertThatThrownBy(() -> stocktake.finishCounting(id)).isInstanceOf(BusinessException.class);
            for (int i = 0; i < lines.size(); i++) {
                saveCount(id, lines.get(i), number(lines.get(i), "snapshot_qty") + (i < 2 ? 1 : 0));
            }
            stocktake.finishCounting(id);
            ReviewStocktakeRequest approve = ReviewStocktakeRequest.builder().approved(true).comment("Two simulated gains").build();
            String key = key();
            reviews.review(id, approve, 8L, "a2a-manager", key);
            reviews.review(id, approve, 8L, "a2a-manager", key);
            assertThat(totalStock()).isEqualTo(before + 2);
            assertThat(ledgerCount()).isEqualTo(ledger + 2);
            assertNoReservedDrift();
        });
    }

    private long newOrder(Map<Long, Integer> quantities) {
        CreateSalesOrderRequest request = new CreateSalesOrderRequest();
        request.setCustomerId(1L);
        request.setSaveAsDraft(false);
        request.setItems(quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry ->
            CreateSalesOrderRequest.SalesOrderItemData.builder().productSkuId(entry.getKey())
                .quantity(entry.getValue()).unitPrice(new BigDecimal("20.00")).build()).toList());
        SalesOrderResponse result = sales.createSalesOrder(request, 7L, "a2a-sales");
        assertThat(result.getCommercialStatus()).isEqualTo("APPROVED");
        return result.getId();
    }
    private SalesOrderResponse cancel(long id, String key) {
        return salesCommands.execute(key, "SALES_ORDER_CANCEL", Map.of("id", id, "reason", "A2 fix cancellation"),
            8L, "a2a-manager", SalesOrderResponse.class,
            () -> sales.cancelSalesOrder(id, "A2 fix cancellation", 8L, "a2a-manager"));
    }
    private void dispatch(OutboundTask task) {
        String batch = read(() -> jdbc.queryForObject("select batch_code from inventory_batch where id=?",
            String.class, task.getAssignedBatchId()));
        outbound.confirmDispatch(task.getId(), task.getPlanQty(), task.getLocationId(), null, batch, 5L, "a2a-worker-wh-a");
    }
    private boolean race(CountDownLatch ready, CountDownLatch start, Supplier<?> action) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
        return TenantContextHolder.runWithTenant(COMPANY, () -> {
            try {
                action.get();
                return true;
            } catch (BusinessException | OptimisticLockingFailureException expectedLoser) {
                // Both are explicit conflict outcomes at the HTTP boundary.
                // The caller still asserts exactly one winner and committed
                // stock/reservation invariants; other runtime failures propagate.
                return false;
            }
        });
    }
    private long createCountTask() {
        long id = stocktake.createCycleTask(CreateStocktakeTaskRequest.builder().warehouseId(1L).cycleType("ADHOC").build(),
            8L, "a2a-manager").getId();
        stocktake.startCounting(id);
        return id;
    }
    private List<Map<String, Object>> snapshots(long id) {
        return read(() -> jdbc.queryForList("select id,snapshot_qty from stocktake_items where task_id=? order by id", id));
    }
    private void saveCount(long task, Map<String, Object> line, int quantity) {
        SubmitCountRequest count = new SubmitCountRequest();
        count.setCountedQty(quantity);
        stocktake.submitCount(task, ((Number) line.get("id")).longValue(), count, 5L, "a2a-worker-wh-a");
    }
    private int number(Map<String, Object> line, String field) { return ((Number) line.get(field)).intValue(); }
    private int totalStock() {
        return read(() -> jdbc.queryForObject("select coalesce(sum(quantity),0)::integer from inventory_batch", Integer.class));
    }
    private long ledgerCount() { return read(() -> jdbc.queryForObject("select count(*) from stock_transactions", Long.class)); }
    private int openReserved(long order) {
        return read(() -> jdbc.queryForObject("select coalesce(sum(reserved_qty-consumed_qty-released_qty),0)::integer "
            + "from inventory_reservations where sales_order_id=?", Integer.class, order));
    }
    private void assertNoReservedDrift() {
        assertThat(read(() -> jdbc.queryForObject("""
            select count(*) from inventory_batch b
            where reserved_quantity < 0 or quantity < reserved_quantity or reserved_quantity <>
              coalesce((select sum(r.reserved_qty-r.consumed_qty-r.released_qty)
                from inventory_reservations r where r.inventory_batch_id=b.id),0)
            """, Long.class))).isZero();
    }
    private <T> T read(Supplier<T> action) { return transact(action, true); }
    private <T> T write(Supplier<T> action) { return transact(action, false); }
    private <T> T transact(Supplier<T> action, boolean readOnly) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(readOnly);
        return tx.execute(status -> {
            jdbc.queryForObject("select set_config('app.company_id','1',true)", String.class);
            return action.get();
        });
    }
    private void tenant(Runnable action) { TenantContextHolder.runWithTenant(COMPANY, action); }
    private String key() { return "a2-fix-" + UUID.randomUUID(); }
}
