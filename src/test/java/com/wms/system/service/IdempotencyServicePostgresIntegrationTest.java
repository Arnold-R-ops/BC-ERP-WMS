package com.wms.system.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=update"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class IdempotencyServicePostgresIntegrationTest {

    private static final long ALPHA_COMPANY_ID = 91_001L;
    private static final long BETA_COMPANY_ID = 91_002L;
    private static final String OPERATION = "INVENTORY_POST_TEST";
    private static final String BODY = "{\"quantity\":10}";

    @Autowired
    private IdempotencyService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Set<String> testKeys = ConcurrentHashMap.newKeySet();

    @BeforeAll
    void enableDatabaseCompanyIsolation() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT current_setting('server_version')", String.class)).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT version() LIKE 'PostgreSQL%'", Boolean.class)).isTrue();

        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION bcwms_current_company_id()
            RETURNS BIGINT LANGUAGE SQL STABLE AS $$
                SELECT NULLIF(current_setting('app.company_id', true), '')::BIGINT
            $$
            """);
        jdbcTemplate.execute("ALTER TABLE idempotency_request ENABLE ROW LEVEL SECURITY");
        jdbcTemplate.execute("ALTER TABLE idempotency_request FORCE ROW LEVEL SECURITY");
        jdbcTemplate.execute("DROP POLICY IF EXISTS company_isolation ON idempotency_request");
        jdbcTemplate.execute("""
            CREATE POLICY company_isolation ON idempotency_request
            FOR ALL
            USING (company_id = bcwms_current_company_id())
            WITH CHECK (company_id = bcwms_current_company_id())
            """);
    }

    @AfterEach
    void deleteOnlyRowsCreatedByThisTest() {
        TenantContextHolder.clear();
        try {
            for (String key : testKeys) {
                deleteKey(ALPHA_COMPANY_ID, key);
                deleteKey(BETA_COMPANY_ID, key);
            }
            testKeys.clear();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @Timeout(20)
    void concurrentSameTenantClaimsHaveOneOwnerAndOneInProgressReplay() throws Exception {
        String key = randomKey("concurrent");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Supplier<IdempotencyClaimDecision> claim = () -> inCompany(
                ALPHA_COMPANY_ID,
                "alpha",
                () -> {
                    ready.countDown();
                    await(start);
                    return service.claim(key, OPERATION, BODY).decision();
                }
            );
            Future<IdempotencyClaimDecision> first = executor.submit(claim::get);
            Future<IdempotencyClaimDecision> second = executor.submit(claim::get);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(
                IdempotencyClaimDecision.ACQUIRED,
                IdempotencyClaimDecision.IN_PROGRESS
            );
            assertThat(countRows(ALPHA_COMPANY_ID, "alpha", key)).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredLeaseCanBeReacquiredWithANewOwnerToken() {
        String key = randomKey("expired");

        IdempotencyClaim first = inCompany(
            ALPHA_COMPANY_ID,
            "alpha",
            () -> service.claim(key, OPERATION, BODY)
        );
        String firstLeaseToken = first.request().getLeaseToken();

        int updated = inCompany(ALPHA_COMPANY_ID, "alpha", () -> inTransaction(() ->
            jdbcTemplate.update("""
                UPDATE idempotency_request
                SET locked_until = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE company_id = ? AND idempotency_key = ?
                """, ALPHA_COMPANY_ID, key)
        ));
        IdempotencyClaim renewed = inCompany(
            ALPHA_COMPANY_ID,
            "alpha",
            () -> service.claim(key, OPERATION, BODY)
        );

        assertThat(updated).isOne();
        assertThat(renewed.decision()).isEqualTo(IdempotencyClaimDecision.ACQUIRED);
        assertThat(firstLeaseToken).isNotBlank();
        assertThat(renewed.request().getLeaseToken())
            .isNotBlank()
            .isNotEqualTo(firstLeaseToken);
    }

    @Test
    void successfulSameIntentIsReplayedFromPostgres() {
        String key = randomKey("success-replay");

        IdempotencyClaim claimed = inCompany(
            ALPHA_COMPANY_ID,
            "alpha",
            () -> service.claim(key, OPERATION, BODY)
        );
        inCompany(ALPHA_COMPANY_ID, "alpha", () -> {
            service.markSucceeded(claimed.request(), 201, "{\"result\":\"posted\"}");
            return null;
        });
        IdempotencyClaim replay = inCompany(
            ALPHA_COMPANY_ID,
            "alpha",
            () -> service.claim(key, OPERATION, BODY)
        );

        assertThat(replay.decision()).isEqualTo(IdempotencyClaimDecision.REPLAY_SUCCEEDED);
        assertThat(replay.request().getId()).isEqualTo(claimed.request().getId());
        assertThat(replay.request().getResponseStatus()).isEqualTo(201);
        assertThat(replay.request().getResponseBody()).isEqualTo("{\"result\":\"posted\"}");
        assertThat(replay.request().getLeaseToken()).isNull();
        assertThat(replay.request().getLockedUntil()).isNull();
    }

    @Test
    void sameKeyWithDifferentIntentConflicts() {
        String key = randomKey("intent-conflict");
        inCompany(
            ALPHA_COMPANY_ID,
            "alpha",
            () -> service.claim(key, OPERATION, BODY)
        );

        assertThatThrownBy(() -> inCompany(
            ALPHA_COMPANY_ID,
            "alpha",
            () -> service.claim(key, OPERATION, "{\"quantity\":11}")))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.IDEMPOTENCY_CONFLICT);
                assertThat(exception.getParam("reason"))
                    .isEqualTo("IDEMPOTENCY_KEY_REUSED_FOR_DIFFERENT_INTENT");
            });
    }

    @Test
    void sameKeyIsIndependentlyAcquiredInTwoTenants() {
        String key = randomKey("tenant-isolation");

        IdempotencyClaim alpha = inCompany(
            ALPHA_COMPANY_ID,
            "alpha",
            () -> service.claim(key, OPERATION, BODY)
        );
        IdempotencyClaim beta = inCompany(
            BETA_COMPANY_ID,
            "beta",
            () -> service.claim(key, OPERATION, BODY)
        );

        assertThat(alpha.decision()).isEqualTo(IdempotencyClaimDecision.ACQUIRED);
        assertThat(beta.decision()).isEqualTo(IdempotencyClaimDecision.ACQUIRED);
        assertThat(alpha.request().getId()).isNotEqualTo(beta.request().getId());
        assertThat(alpha.request().getCompanyId()).isEqualTo(ALPHA_COMPANY_ID);
        assertThat(beta.request().getCompanyId()).isEqualTo(BETA_COMPANY_ID);
        assertThat(countRows(ALPHA_COMPANY_ID, "alpha", key)).isOne();
        assertThat(countRows(BETA_COMPANY_ID, "beta", key)).isOne();
    }

    private String randomKey(String scenario) {
        String key = "idem-pg-" + scenario + "-" + UUID.randomUUID();
        testKeys.add(key);
        return key;
    }

    private long countRows(long companyId, String slug, String key) {
        return inCompany(companyId, slug, () -> inTransaction(() ->
            jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM idempotency_request
                WHERE idempotency_key = ?
                """, Long.class, key)
        ));
    }

    private void deleteKey(long companyId, String key) {
        inCompany(companyId, "cleanup-" + companyId, () -> inTransaction(() ->
            jdbcTemplate.update("""
                DELETE FROM idempotency_request
                WHERE company_id = ? AND idempotency_key = ?
                """, companyId, key)
        ));
    }

    private <T> T inCompany(
        long companyId,
        String slug,
        Supplier<T> action
    ) {
        TenantContext context = new TenantContext(
            companyId,
            slug,
            slug + ".example.test",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        );
        return TenantContextHolder.runWithTenant(context, action);
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent claim start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent claim was interrupted", exception);
        }
    }
}
