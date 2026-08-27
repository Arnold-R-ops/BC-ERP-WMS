package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.v45.EmergencyStockCorrectionImpactResponse;
import com.wms.system.dto.v45.EmergencyStockCorrectionResponse;
import com.wms.system.entity.Category;
import com.wms.system.entity.EmergencyStockCorrection;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.CategoryRepository;
import com.wms.system.repository.EmergencyStockCorrectionRepository;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import com.wms.system.service.inventory.InventoryPostingCommand;
import com.wms.system.service.inventory.InventoryPostingService;
import com.wms.system.service.inventory.PostingContext;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL transaction facts for governed emergency inventory correction.
 *
 * <p>No test method owns a surrounding rollback transaction. Every service
 * proxy therefore opens and commits (or rolls back) the same boundaries used
 * in production. PostgreSQL triggers inject failures at the actual ledger and
 * Outbox INSERT statements; the assertions then read committed database state
 * from a fresh transaction.</p>
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=update"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class EmergencyStockCorrectionTransactionPostgresIntegrationTest {

    private static final long OPERATOR_ID = 73_001L;
    private static final int INITIAL_QUANTITY = 100;
    private static final List<String> COMPANY_TABLES_IN_DELETE_ORDER = List.of(
        "domain_outbox",
        "stock_transactions",
        "idempotency_request",
        "emergency_stock_correction",
        "inventory_batch",
        "locations",
        "product_skus",
        "products",
        "categories",
        "warehouses"
    );

    private final String runTag = UUID.randomUUID().toString()
        .replace("-", "")
        .substring(0, 12)
        .toUpperCase();
    private final long companyId = 9_300_000L
        + ThreadLocalRandom.current().nextLong(500_000L);
    private final String tenantSlug = "correction-tx-" + runTag.toLowerCase();
    private final String stockFailureMarker = "IT_TX_FAIL_" + runTag;
    private final String outboxFailureMarker = "IT_OUTBOX_FAIL_" + runTag;
    private final String stockFailureFunction = "it_corr_stock_fail_" + runTag.toLowerCase();
    private final String stockFailureTrigger = "it_corr_stock_trg_" + runTag.toLowerCase();
    private final String outboxFailureFunction = "it_corr_outbox_fail_" + runTag.toLowerCase();
    private final String outboxFailureTrigger = "it_corr_outbox_trg_" + runTag.toLowerCase();

    @Autowired private EmergencyStockCorrectionService correctionService;
    @Autowired private EmergencyStockCorrectionApplicationExecutor applicationExecutor;
    @Autowired private InventoryPostingService inventoryPostingService;
    @Autowired private IdempotencyService idempotencyService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository productSkuRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private InventoryBatchRepository inventoryBatchRepository;
    @Autowired private EmergencyStockCorrectionRepository correctionRepository;

    @BeforeAll
    void installTargetedPostgresFailureTriggers() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT version() LIKE 'PostgreSQL%'",
            Boolean.class
        )).isTrue();

        jdbcTemplate.execute(
            "CREATE FUNCTION " + stockFailureFunction + "() "
                + "RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN "
                + "IF starts_with(NEW.posting_id, '" + stockFailureMarker + "') THEN "
                + "RAISE EXCEPTION 'forced stock transaction failure " + runTag + "'; "
                + "END IF; "
                + "RETURN NEW; "
                + "END; $$"
        );
        jdbcTemplate.execute(
            "CREATE TRIGGER " + stockFailureTrigger
                + " BEFORE INSERT ON stock_transactions"
                + " FOR EACH ROW EXECUTE FUNCTION " + stockFailureFunction + "()"
        );

        jdbcTemplate.execute(
            "CREATE FUNCTION " + outboxFailureFunction + "() "
                + "RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN "
                + "IF position('" + outboxFailureMarker + "' in NEW.payload_json) > 0 THEN "
                + "RAISE EXCEPTION 'forced outbox failure " + runTag + "'; "
                + "END IF; "
                + "RETURN NEW; "
                + "END; $$"
        );
        jdbcTemplate.execute(
            "CREATE TRIGGER " + outboxFailureTrigger
                + " BEFORE INSERT ON domain_outbox"
                + " FOR EACH ROW EXECUTE FUNCTION " + outboxFailureFunction + "()"
        );
    }

    @AfterAll
    void removeTargetedPostgresFailureTriggersAndVerifyNoResidue() {
        try {
            purgeExactTestCompany();
            assertThat(companyOwnedRowCount()).isZero();
        } finally {
            dropFailureInjectionObjects();
            TenantContextHolder.clear();
        }
        assertThat(failureInjectionObjectCount()).isZero();
    }

    @AfterEach
    void purgeExactCompanyDataAndClearTenantContext() {
        try {
            purgeExactTestCompany();
            assertThat(companyOwnedRowCount()).isZero();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void expiredClaimRecoversCommittedPostingWithoutDuplicatingLedgerOrOutbox() throws Exception {
        CorrectionFixture fixture = seedCorrection(5);
        String idempotencyKey = randomBusinessKey("RECOVER");
        String commandBody = objectMapper.writeValueAsString(
            Map.of("correctionId", fixture.correctionId())
        );
        EmergencyStockCorrectionImpactResponse impact = inCompany(
            () -> correctionService.impact(fixture.correctionId())
        );

        IdempotencyClaim initialClaim = inCompany(() -> idempotencyService.claim(
            idempotencyKey,
            EmergencyStockCorrectionService.APPLY_OPERATION,
            commandBody
        ));
        EmergencyStockCorrection appliedBeforeTerminalMark = inCompany(
            () -> applicationExecutor.applyAtomic(
                fixture.correctionId(),
                OPERATOR_ID,
                "integration-maker",
                idempotencyKey,
                impact.getPreviewToken()
            )
        );

        assertThat(initialClaim.decision()).isEqualTo(IdempotencyClaimDecision.ACQUIRED);
        assertThat(appliedBeforeTerminalMark.getStatus())
            .isEqualTo(EmergencyCorrectionStatus.APPLIED);
        assertThat(batchQuantity(fixture.batchId())).isEqualTo(105);
        assertThat(stockTransactionCount(appliedBeforeTerminalMark.getPostingId())).isOne();
        assertThat(inventoryOutboxCount(fixture.batchId())).isOne();
        assertThat(idempotencyStatus(idempotencyKey))
            .isEqualTo(IdempotencyStatus.PROCESSING.name());

        int expired = inCompany(() -> inTransaction(() -> jdbcTemplate.update(
            """
                UPDATE idempotency_request
                SET locked_until = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE company_id = ? AND idempotency_key = ?
                """,
            companyId,
            idempotencyKey
        )));
        EmergencyStockCorrectionResponse recovered = inCompany(() -> correctionService.apply(
            fixture.correctionId(),
            OPERATOR_ID,
            "integration-maker",
            idempotencyKey,
            impact.getPreviewToken()
        ));

        assertThat(expired).isOne();
        assertThat(recovered.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPLIED);
        assertThat(recovered.getAppliedQuantityBefore()).isEqualTo(100);
        assertThat(recovered.getAppliedQuantityAfter()).isEqualTo(105);
        assertThat(batchQuantity(fixture.batchId())).isEqualTo(105);
        assertThat(stockTransactionCount(recovered.getPostingId())).isOne();
        assertThat(inventoryOutboxCount(fixture.batchId())).isOne();
        assertThat(idempotencyStatus(idempotencyKey))
            .isEqualTo(IdempotencyStatus.SUCCEEDED.name());
    }

    @Test
    void stockTransactionInsertFailureRollsBackBatchLedgerAndOutbox() {
        BatchFixture fixture = seedBatch();
        String postingId = stockFailureMarker + "_" + UUID.randomUUID();

        assertThatThrownBy(() -> inCompany(() -> inventoryPostingService.post(
            postingCommand(fixture, postingId)
        )))
            .isInstanceOf(RuntimeException.class)
            .hasStackTraceContaining("forced stock transaction failure " + runTag);

        assertRollbackFacts(fixture, postingId);
    }

    @Test
    void outboxInsertFailureRollsBackBatchLedgerAndOutbox() {
        BatchFixture fixture = seedBatch();
        String postingId = outboxFailureMarker + "_" + UUID.randomUUID();

        assertThatThrownBy(() -> inCompany(() -> inventoryPostingService.post(
            postingCommand(fixture, postingId)
        )))
            .isInstanceOf(RuntimeException.class)
            .hasStackTraceContaining("forced outbox failure " + runTag);

        assertRollbackFacts(fixture, postingId);
    }

    @Test
    void zeroDeltaCommitsCorrectionAuditWithoutLedgerOrOutbox() {
        CorrectionFixture fixture = seedCorrection(0);
        String idempotencyKey = randomBusinessKey("ZERO");
        EmergencyStockCorrectionImpactResponse impact = inCompany(
            () -> correctionService.impact(fixture.correctionId())
        );

        EmergencyStockCorrectionResponse response = inCompany(() -> correctionService.apply(
            fixture.correctionId(),
            OPERATOR_ID,
            "integration-maker",
            idempotencyKey,
            impact.getPreviewToken()
        ));

        EmergencyStockCorrection persisted = correction(fixture.correctionId());
        assertThat(response.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPLIED);
        assertThat(persisted.getStatus()).isEqualTo(EmergencyCorrectionStatus.APPLIED);
        assertThat(persisted.getAppliedBy()).isEqualTo(OPERATOR_ID);
        assertThat(persisted.getAppliedAt()).isNotNull();
        assertThat(persisted.getAppliedQuantityBefore()).isEqualTo(INITIAL_QUANTITY);
        assertThat(persisted.getAppliedQuantityAfter()).isEqualTo(INITIAL_QUANTITY);
        assertThat(persisted.getPostingId()).isNotBlank();
        assertThat(batchQuantity(fixture.batchId())).isEqualTo(INITIAL_QUANTITY);
        assertThat(stockTransactionCount(persisted.getPostingId())).isZero();
        assertThat(inventoryOutboxCount(fixture.batchId())).isZero();
        assertThat(idempotencyStatus(idempotencyKey))
            .isEqualTo(IdempotencyStatus.SUCCEEDED.name());
    }

    private InventoryPostingCommand postingCommand(BatchFixture fixture, String postingId) {
        return new InventoryPostingCommand(
            fixture.batchId(),
            fixture.productSkuId(),
            fixture.locationId(),
            5,
            new PostingContext(
                companyId,
                fixture.warehouseId(),
                SourceType.INVENTORY_GAIN,
                "IT-SOURCE-" + runTag,
                "INTEGRATION_FAILURE",
                "PostgreSQL transaction rollback proof",
                OPERATOR_ID,
                "integration-maker",
                postingId,
                randomBusinessKey("POST")
            )
        );
    }

    private void assertRollbackFacts(BatchFixture fixture, String postingId) {
        assertThat(batchQuantity(fixture.batchId())).isEqualTo(INITIAL_QUANTITY);
        assertThat(stockTransactionCount(postingId)).isZero();
        assertThat(inventoryOutboxCount(fixture.batchId())).isZero();
    }

    private CorrectionFixture seedCorrection(int adjustment) {
        BatchFixture batch = seedBatch();
        String correctionNo = "ITC" + runTag + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 12)
            .toUpperCase();

        EmergencyStockCorrection correction = inCompany(() -> inTransaction(() ->
            correctionRepository.saveAndFlush(EmergencyStockCorrection.builder()
                .correctionNo(correctionNo)
                .productSkuId(batch.productSkuId())
                .warehouseId(batch.warehouseId())
                .locationId(batch.locationId())
                .inventoryBatchId(batch.batchId())
                .batchCode(batch.batchCode())
                .productionDate(LocalDate.now().minusDays(10))
                .expiryDate(LocalDate.now().plusYears(1))
                .systemQty(INITIAL_QUANTITY)
                .countedQty(INITIAL_QUANTITY + adjustment)
                .adjustmentQty(adjustment)
                .reasonCode("INTEGRATION_TEST")
                .reasonDetail("PostgreSQL transaction integration proof")
                .createdBy(OPERATOR_ID)
                .status(EmergencyCorrectionStatus.DRAFT)
                .build()
            )
        ));
        return new CorrectionFixture(correction.getId(), batch.batchId());
    }

    private BatchFixture seedBatch() {
        return inCompany(() -> inTransaction(() -> {
            String tag = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();
            Category category = categoryRepository.save(Category.builder()
                .categoryCode("IT-CAT-" + tag)
                .categoryName("Integration category " + tag)
                .enabled(true)
                .build());
            Product product = productRepository.save(Product.builder()
                .productCode("IT-PROD-" + tag)
                .productName("Integration product " + tag)
                .category(category)
                .enabled(true)
                .build());
            ProductSku productSku = productSkuRepository.save(ProductSku.builder()
                .skuCode("IT-SKU-" + tag)
                .product(product)
                .skuName("Integration SKU " + tag)
                .barcode("ITBC" + tag)
                .name("Integration SKU " + tag)
                .unitPrice(BigDecimal.ONE)
                .enabled(true)
                .build());
            Warehouse warehouse = warehouseRepository.save(Warehouse.builder()
                .code("IT" + tag)
                .name("Integration warehouse " + tag)
                .isActive(true)
                .build());
            Location location = locationRepository.save(Location.builder()
                .warehouse(warehouse)
                .zone(Zone.ZONE_A)
                .shelfNumber("S" + tag.substring(0, 6))
                .positionNumber(tag.substring(6))
                .enabled(true)
                .build());
            InventoryBatch batch = inventoryBatchRepository.saveAndFlush(
                InventoryBatch.builder()
                    .batchCode("IT-BATCH-" + tag)
                    .productSku(productSku)
                    .location(location)
                    .locationCode(location.getLocationCode())
                    .quantity(INITIAL_QUANTITY)
                    .reservedQuantity(0)
                    .initialQuantity(INITIAL_QUANTITY)
                    .productionDate(LocalDate.now().minusDays(10))
                    .expiryDate(LocalDate.now().plusYears(1))
                    .active(true)
                    .build()
            );
            return new BatchFixture(
                batch.getId(),
                batch.getBatchCode(),
                productSku.getId(),
                location.getId(),
                warehouse.getId()
            );
        }));
    }

    private EmergencyStockCorrection correction(Long correctionId) {
        return inCompany(() -> inTransaction(() ->
            correctionRepository.findById(correctionId).orElseThrow()
        ));
    }

    private int batchQuantity(Long batchId) {
        return inCompany(() -> inTransaction(() ->
            inventoryBatchRepository.findById(batchId).orElseThrow().getQuantity()
        ));
    }

    private long stockTransactionCount(String postingId) {
        return inCompany(() -> inTransaction(() -> jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM stock_transactions
                WHERE company_id = ? AND posting_id = ?
                """,
            Long.class,
            companyId,
            postingId
        )));
    }

    private long inventoryOutboxCount(Long batchId) {
        return inCompany(() -> inTransaction(() -> jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM domain_outbox
                WHERE company_id = ?
                  AND event_type = 'INVENTORY_POSTED'
                  AND aggregate_type = 'InventoryBatch'
                  AND aggregate_id = ?
                """,
            Long.class,
            companyId,
            batchId.toString()
        )));
    }

    private String idempotencyStatus(String idempotencyKey) {
        return inCompany(() -> inTransaction(() -> jdbcTemplate.queryForObject(
            """
                SELECT status
                FROM idempotency_request
                WHERE company_id = ? AND idempotency_key = ?
                """,
            String.class,
            companyId,
            idempotencyKey
        )));
    }

    private String randomBusinessKey(String scenario) {
        return "IT-CORR-" + scenario + "-" + UUID.randomUUID();
    }

    private void purgeExactTestCompany() {
        inCompany(() -> inTransaction(() -> {
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_purge', 'on', true)",
                String.class
            );
            for (String table : COMPANY_TABLES_IN_DELETE_ORDER) {
                jdbcTemplate.update(
                    "DELETE FROM " + table + " WHERE company_id = ?",
                    companyId
                );
            }
            return null;
        }));
    }

    private long companyOwnedRowCount() {
        return inCompany(() -> inTransaction(() -> {
            long total = 0;
            for (String table : COMPANY_TABLES_IN_DELETE_ORDER) {
                Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE company_id = ?",
                    Long.class,
                    companyId
                );
                total += count == null ? 0 : count;
            }
            return total;
        }));
    }

    private void dropFailureInjectionObjects() {
        jdbcTemplate.execute(
            "DROP TRIGGER IF EXISTS " + stockFailureTrigger + " ON stock_transactions"
        );
        jdbcTemplate.execute(
            "DROP FUNCTION IF EXISTS " + stockFailureFunction + "()"
        );
        jdbcTemplate.execute(
            "DROP TRIGGER IF EXISTS " + outboxFailureTrigger + " ON domain_outbox"
        );
        jdbcTemplate.execute(
            "DROP FUNCTION IF EXISTS " + outboxFailureFunction + "()"
        );
    }

    private long failureInjectionObjectCount() {
        Long triggerCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM pg_trigger
                WHERE NOT tgisinternal AND tgname IN (?, ?)
                """,
            Long.class,
            stockFailureTrigger,
            outboxFailureTrigger
        );
        Long functionCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM pg_proc
                WHERE proname IN (?, ?)
                """,
            Long.class,
            stockFailureFunction,
            outboxFailureFunction
        );
        return (triggerCount == null ? 0 : triggerCount)
            + (functionCount == null ? 0 : functionCount);
    }

    private <T> T inCompany(Supplier<T> action) {
        TenantContext context = new TenantContext(
            companyId,
            tenantSlug,
            tenantSlug + ".example.test",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        );
        return TenantContextHolder.runWithTenant(context, action);
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private record BatchFixture(
        Long batchId,
        String batchCode,
        Long productSkuId,
        Long locationId,
        Long warehouseId
    ) { }

    private record CorrectionFixture(Long correctionId, Long batchId) { }
}
