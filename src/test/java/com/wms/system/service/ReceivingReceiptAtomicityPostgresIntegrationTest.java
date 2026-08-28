package com.wms.system.service;

import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.entity.Category;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.PurchaseAsn;
import com.wms.system.entity.PurchaseAsnItem;
import com.wms.system.entity.PurchaseOrder;
import com.wms.system.entity.PurchaseOrderAllocation;
import com.wms.system.entity.PurchaseOrderItem;
import com.wms.system.entity.ReceivingPlan;
import com.wms.system.entity.ReceivingTask;
import com.wms.system.entity.ReceivingTaskItem;
import com.wms.system.entity.Supplier;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.PurchaseAsnStatus;
import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;
import com.wms.system.entity.enums.PurchaseOrderStatus;
import com.wms.system.entity.enums.ReceivingPlanStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.entity.enums.Zone;
import com.wms.system.repository.CategoryRepository;
import com.wms.system.repository.LocationRepository;
import com.wms.system.repository.ProductRepository;
import com.wms.system.repository.ProductSkuRepository;
import com.wms.system.repository.PurchaseAsnRepository;
import com.wms.system.repository.PurchaseOrderAllocationRepository;
import com.wms.system.repository.PurchaseOrderRepository;
import com.wms.system.repository.ReceivingPlanRepository;
import com.wms.system.repository.ReceivingTaskRepository;
import com.wms.system.repository.SupplierRepository;
import com.wms.system.repository.WarehouseRepository;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL proof that the unified receipt executor commits all lines or none.
 *
 * <p>The test deliberately has no surrounding test transaction. A targeted
 * PostgreSQL trigger rejects the stock-ledger INSERT for the second SKU after
 * the first line has already created and posted its batch. Assertions then read
 * committed state from fresh transactions, matching the production boundary.</p>
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=update"
})
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class ReceivingReceiptAtomicityPostgresIntegrationTest {

    private static final long ACTOR_ID = 78_001L;
    private static final int SHIPPED_QUANTITY = 10;
    private static final int RECEIPT_QUANTITY = 3;
    private static final List<String> COMPANY_TABLES_IN_DELETE_ORDER = List.of(
        "domain_outbox",
        "stock_transactions",
        "receipt_event_item",
        "receipt_event",
        "receiving_task_item",
        "receiving_task",
        "purchase_asn_item",
        "purchase_asn",
        "receiving_plan",
        "purchase_order_allocation",
        "inventory_batch",
        "purchase_order_item",
        "purchase_order",
        "suppliers",
        "locations",
        "product_skus",
        "products",
        "categories",
        "warehouses"
    );

    private final String runTag = UUID.randomUUID().toString()
        .replace("-", "")
        .substring(0, 10)
        .toUpperCase();
    private final long companyId = 9_800_000L
        + ThreadLocalRandom.current().nextLong(100_000L);
    private final String tenantSlug = "receipt-tx-" + runTag.toLowerCase();
    private final String idempotencyKey = "IT-RCV-TX-" + runTag;
    private final String failureFunction = "it_rcv_fail_" + runTag.toLowerCase();
    private final String failureTrigger = "it_rcv_trg_" + runTag.toLowerCase();
    private boolean failureObjectsInstalled;

    @Autowired private ReceivingReceiptExecutor receiptExecutor;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSkuRepository productSkuRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private PurchaseOrderAllocationRepository allocationRepository;
    @Autowired private ReceivingPlanRepository receivingPlanRepository;
    @Autowired private PurchaseAsnRepository purchaseAsnRepository;
    @Autowired private ReceivingTaskRepository receivingTaskRepository;

    @AfterEach
    void removeFailureObjectsAndExactCompanyData() {
        try {
            dropFailureInjectionObjects();
            purgeExactTestCompany();
        } finally {
            TenantContextHolder.clear();
        }
        assertThat(failureInjectionObjectCount()).isZero();
        assertThat(companyOwnedRowCount()).isZero();
    }

    @Test
    void secondLineLedgerFailureRollsBackEntireUnifiedPurchaseReceipt() {
        ReceiptFixture fixture = seedTwoLinePurchaseReceipt();
        installSecondSkuLedgerFailure(fixture.secondSkuId());

        SubmitReceivingReceiptRequest request = new SubmitReceivingReceiptRequest(
            "atomic PostgreSQL integration proof",
            List.of(
                new SubmitReceivingReceiptRequest.Line(
                    fixture.firstTaskItemId(), fixture.locationId(),
                    RECEIPT_QUANTITY, null, null, null),
                new SubmitReceivingReceiptRequest.Line(
                    fixture.secondTaskItemId(), fixture.locationId(),
                    RECEIPT_QUANTITY, null, null, null)
            )
        );

        assertThatThrownBy(() -> inCompany(() -> receiptExecutor.execute(
            fixture.taskId(), idempotencyKey, request, ACTOR_ID, "integration-receiver"
        )))
            .isInstanceOf(RuntimeException.class)
            .hasStackTraceContaining("forced second receipt line failure " + runTag);

        assertNoReceiptArtifactsCommitted(fixture);
        assertPurchaseWorkflowIsUnchanged(fixture);
    }

    private ReceiptFixture seedTwoLinePurchaseReceipt() {
        return inCompany(() -> inTransaction(() -> {
            Category category = categoryRepository.save(Category.builder()
                .categoryCode("IT-CAT-" + runTag)
                .categoryName("Receipt atomicity " + runTag)
                .enabled(true)
                .build());
            ProductSku firstSku = saveSku(category, "A");
            ProductSku secondSku = saveSku(category, "B");

            Warehouse warehouse = warehouseRepository.save(Warehouse.builder()
                .code("IT" + runTag)
                .name("Receipt atomicity warehouse " + runTag)
                .isActive(true)
                .build());
            Location location = locationRepository.save(Location.builder()
                .warehouse(warehouse)
                .zone(Zone.ZONE_A)
                .shelfNumber("S" + runTag.substring(0, 5))
                .positionNumber(runTag.substring(5))
                .enabled(true)
                .build());
            Supplier supplier = supplierRepository.save(Supplier.builder()
                .code("IT-SUP-" + runTag)
                .name("Receipt atomicity supplier " + runTag)
                .isActive(true)
                .isDeleted(false)
                .build());

            PurchaseOrder order = PurchaseOrder.builder()
                .poNumber("ITPO" + runTag)
                .supplier(supplier.getName())
                .supplierReference(supplier)
                .status(PurchaseOrderStatus.IN_TRANSIT)
                .totalQuantity(SHIPPED_QUANTITY * 2)
                .totalCost(BigDecimal.valueOf(SHIPPED_QUANTITY * 2L))
                .expectedDate(LocalDate.now().plusDays(2))
                .operatorId(ACTOR_ID)
                .operatorName("integration-buyer")
                .build();
            PurchaseOrderItem firstOrderItem = orderItem(firstSku);
            PurchaseOrderItem secondOrderItem = orderItem(secondSku);
            order.addItem(firstOrderItem);
            order.addItem(secondOrderItem);
            order = purchaseOrderRepository.saveAndFlush(order);
            // These entities initialise @Version to zero, so Spring Data uses
            // merge even on the first save. Continue with the managed children
            // returned by merge rather than the transient builder instances.
            firstOrderItem = order.getItems().stream()
                .filter(item -> item.getProductSku().getId().equals(firstSku.getId()))
                .findFirst()
                .orElseThrow();
            secondOrderItem = order.getItems().stream()
                .filter(item -> item.getProductSku().getId().equals(secondSku.getId()))
                .findFirst()
                .orElseThrow();

            PurchaseOrderAllocation firstAllocation = allocationRepository.save(
                allocation(firstOrderItem, warehouse));
            PurchaseOrderAllocation secondAllocation = allocationRepository.save(
                allocation(secondOrderItem, warehouse));

            ReceivingPlan plan = receivingPlanRepository.save(ReceivingPlan.builder()
                .planNumber("ITPLAN-" + runTag)
                .purchaseOrder(order)
                .warehouse(warehouse)
                .status(ReceivingPlanStatus.IN_TRANSIT)
                .createdBy(ACTOR_ID)
                .createdByName("integration-buyer")
                .build());

            PurchaseAsn asn = PurchaseAsn.builder()
                .asnNumber("ITASN-" + runTag)
                .purchaseOrder(order)
                .warehouse(warehouse)
                .status(PurchaseAsnStatus.CONFIRMED)
                .createdBy(ACTOR_ID)
                .createdByName("integration-buyer")
                .confirmedBy(ACTOR_ID)
                .confirmedByName("integration-buyer")
                .build();
            PurchaseAsnItem firstAsnItem = asnItem(
                firstOrderItem, firstAllocation, firstSku);
            PurchaseAsnItem secondAsnItem = asnItem(
                secondOrderItem, secondAllocation, secondSku);
            asn.addItem(firstAsnItem);
            asn.addItem(secondAsnItem);
            asn = purchaseAsnRepository.saveAndFlush(asn);
            firstAsnItem = asn.getItems().stream()
                .filter(item -> item.getProductSku().getId().equals(firstSku.getId()))
                .findFirst()
                .orElseThrow();
            secondAsnItem = asn.getItems().stream()
                .filter(item -> item.getProductSku().getId().equals(secondSku.getId()))
                .findFirst()
                .orElseThrow();

            ReceivingTask task = ReceivingTask.builder()
                .taskNumber("ITTASK-" + runTag)
                .receivingPlan(plan)
                .sourceType(ReceivingTaskSourceType.PURCHASE_ASN)
                .purchaseAsn(asn)
                .warehouse(warehouse)
                .status(ReceivingTaskStatus.PENDING)
                .createdBy(ACTOR_ID)
                .createdByName("integration-buyer")
                .build();
            ReceivingTaskItem firstTaskItem = taskItem(
                firstAsnItem, firstOrderItem, firstSku);
            ReceivingTaskItem secondTaskItem = taskItem(
                secondAsnItem, secondOrderItem, secondSku);
            task.addItem(firstTaskItem);
            task.addItem(secondTaskItem);
            task = receivingTaskRepository.saveAndFlush(task);
            firstTaskItem = task.getItems().stream()
                .filter(item -> item.getProductSku().getId().equals(firstSku.getId()))
                .findFirst()
                .orElseThrow();
            secondTaskItem = task.getItems().stream()
                .filter(item -> item.getProductSku().getId().equals(secondSku.getId()))
                .findFirst()
                .orElseThrow();

            return new ReceiptFixture(
                order.getId(), order.getVersion(),
                firstOrderItem.getId(), secondOrderItem.getId(),
                firstAllocation.getId(), secondAllocation.getId(),
                firstAllocation.getVersion(), secondAllocation.getVersion(),
                plan.getId(), plan.getVersion(),
                asn.getId(), asn.getVersion(),
                firstAsnItem.getId(), secondAsnItem.getId(),
                task.getId(), task.getVersion(),
                firstTaskItem.getId(), secondTaskItem.getId(),
                firstSku.getId(), secondSku.getId(),
                location.getId()
            );
        }));
    }

    private ProductSku saveSku(Category category, String suffix) {
        Product product = productRepository.save(Product.builder()
            .productCode("IT-PROD-" + runTag + suffix)
            .productName("Receipt product " + runTag + suffix)
            .category(category)
            .enabled(true)
            .build());
        return productSkuRepository.save(ProductSku.builder()
            .skuCode("IT-SKU-" + runTag + suffix)
            .product(product)
            .skuName("Receipt SKU " + suffix)
            .barcode("ITBC" + runTag + suffix)
            .name("Receipt SKU " + runTag + suffix)
            .unitPrice(BigDecimal.ONE)
            .enabled(true)
            .expiryManagementEnabled(false)
            .build());
    }

    private PurchaseOrderItem orderItem(ProductSku sku) {
        return PurchaseOrderItem.builder()
            .productSku(sku)
            .orderedQuantity(SHIPPED_QUANTITY)
            .receivedQuantity(0)
            .unitCost(BigDecimal.ONE)
            .build();
    }

    private PurchaseOrderAllocation allocation(
        PurchaseOrderItem item,
        Warehouse warehouse
    ) {
        return PurchaseOrderAllocation.builder()
            .purchaseOrderItem(item)
            .warehouse(warehouse)
            .plannedQuantity(SHIPPED_QUANTITY)
            .shippedQuantity(SHIPPED_QUANTITY)
            .receivedQuantity(0)
            .closedQuantity(0)
            .status(PurchaseOrderAllocationStatus.ACTIVE)
            .build();
    }

    private PurchaseAsnItem asnItem(
        PurchaseOrderItem orderItem,
        PurchaseOrderAllocation allocation,
        ProductSku sku
    ) {
        return PurchaseAsnItem.builder()
            .purchaseOrderItem(orderItem)
            .purchaseOrderAllocation(allocation)
            .productSku(sku)
            .shippedQuantity(SHIPPED_QUANTITY)
            .receivedQuantity(0)
            .closedQuantity(0)
            .build();
    }

    private ReceivingTaskItem taskItem(
        PurchaseAsnItem asnItem,
        PurchaseOrderItem orderItem,
        ProductSku sku
    ) {
        return ReceivingTaskItem.builder()
            .purchaseAsnItem(asnItem)
            .purchaseOrderItem(orderItem)
            .productSku(sku)
            .expectedQuantity(SHIPPED_QUANTITY)
            .receivedQuantity(0)
            .closedQuantity(0)
            .build();
    }

    private void installSecondSkuLedgerFailure(Long secondSkuId) {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT version() LIKE 'PostgreSQL%'", Boolean.class
        )).isTrue();
        jdbcTemplate.execute(
            "CREATE FUNCTION " + failureFunction + "() "
                + "RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN "
                + "IF NEW.company_id = " + companyId
                + " AND NEW.idempotency_key = '" + idempotencyKey + "'"
                + " AND NEW.product_sku_id = " + secondSkuId + " THEN "
                + "RAISE EXCEPTION 'forced second receipt line failure " + runTag + "'; "
                + "END IF; "
                + "RETURN NEW; "
                + "END; $$"
        );
        jdbcTemplate.execute(
            "CREATE TRIGGER " + failureTrigger
                + " BEFORE INSERT ON stock_transactions"
                + " FOR EACH ROW EXECUTE FUNCTION " + failureFunction + "()"
        );
        failureObjectsInstalled = true;
    }

    private void assertNoReceiptArtifactsCommitted(ReceiptFixture fixture) {
        inCompany(() -> inTransaction(() -> {
            assertThat(count("receipt_event")).isZero();
            assertThat(count("receipt_event_item")).isZero();
            assertThat(count("inventory_batch")).isZero();
            assertThat(count("stock_transactions")).isZero();
            assertThat(count("domain_outbox")).isZero();
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM receipt_event WHERE company_id = ? AND receiving_task_id = ?",
                Long.class, companyId, fixture.taskId()
            )).isZero();
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_transactions WHERE company_id = ? AND idempotency_key = ?",
                Long.class, companyId, idempotencyKey
            )).isZero();
            return null;
        }));
    }

    private void assertPurchaseWorkflowIsUnchanged(ReceiptFixture fixture) {
        inCompany(() -> inTransaction(() -> {
            assertThat(stringValue(
                "SELECT status FROM receiving_task WHERE id = ?", fixture.taskId()))
                .isEqualTo(ReceivingTaskStatus.PENDING.name());
            assertThat(longValue(
                "SELECT version FROM receiving_task WHERE id = ?", fixture.taskId()))
                .isEqualTo(fixture.taskVersion());
            assertThat(intValues(
                "SELECT received_quantity FROM receiving_task_item WHERE receiving_task_id = ? ORDER BY id",
                fixture.taskId())).containsExactly(0, 0);

            assertThat(stringValue(
                "SELECT status FROM purchase_asn WHERE id = ?", fixture.asnId()))
                .isEqualTo(PurchaseAsnStatus.CONFIRMED.name());
            assertThat(longValue(
                "SELECT version FROM purchase_asn WHERE id = ?", fixture.asnId()))
                .isEqualTo(fixture.asnVersion());
            assertThat(intValues(
                "SELECT received_quantity FROM purchase_asn_item WHERE purchase_asn_id = ? ORDER BY id",
                fixture.asnId())).containsExactly(0, 0);

            assertThat(intValues(
                "SELECT received_quantity FROM purchase_order_allocation WHERE id IN (?, ?) ORDER BY id",
                fixture.firstAllocationId(), fixture.secondAllocationId()))
                .containsExactly(0, 0);
            assertThat(stringValues(
                "SELECT status FROM purchase_order_allocation WHERE id IN (?, ?) ORDER BY id",
                fixture.firstAllocationId(), fixture.secondAllocationId()))
                .containsExactly(
                    PurchaseOrderAllocationStatus.ACTIVE.name(),
                    PurchaseOrderAllocationStatus.ACTIVE.name());
            assertThat(longValues(
                "SELECT version FROM purchase_order_allocation WHERE id IN (?, ?) ORDER BY id",
                fixture.firstAllocationId(), fixture.secondAllocationId()))
                .containsExactly(
                    fixture.firstAllocationVersion(), fixture.secondAllocationVersion());

            assertThat(stringValue(
                "SELECT status FROM purchase_order WHERE id = ?", fixture.orderId()))
                .isEqualTo(PurchaseOrderStatus.IN_TRANSIT.name());
            assertThat(longValue(
                "SELECT version FROM purchase_order WHERE id = ?", fixture.orderId()))
                .isEqualTo(fixture.orderVersion());
            assertThat(intValues(
                "SELECT received_quantity FROM purchase_order_item WHERE purchase_order_id = ? ORDER BY id",
                fixture.orderId())).containsExactly(0, 0);

            assertThat(stringValue(
                "SELECT status FROM receiving_plan WHERE id = ?", fixture.planId()))
                .isEqualTo(ReceivingPlanStatus.IN_TRANSIT.name());
            assertThat(longValue(
                "SELECT version FROM receiving_plan WHERE id = ?", fixture.planId()))
                .isEqualTo(fixture.planVersion());
            return null;
        }));
    }

    private long count(String table) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE company_id = ?",
            Long.class,
            companyId
        );
        return count == null ? 0 : count;
    }

    private String stringValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private long longValue(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private List<Integer> intValues(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, Integer.class, args);
    }

    private List<Long> longValues(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, Long.class, args);
    }

    private List<String> stringValues(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, String.class, args);
    }

    private void purgeExactTestCompany() {
        inCompany(() -> inTransaction(() -> {
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.tenant_purge', 'on', true)", String.class);
            for (String table : COMPANY_TABLES_IN_DELETE_ORDER) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE company_id = ?", companyId);
            }
            return null;
        }));
    }

    private long companyOwnedRowCount() {
        return inCompany(() -> inTransaction(() -> {
            long total = 0;
            for (String table : COMPANY_TABLES_IN_DELETE_ORDER) {
                total += count(table);
            }
            return total;
        }));
    }

    private void dropFailureInjectionObjects() {
        if (!failureObjectsInstalled) {
            return;
        }
        jdbcTemplate.execute(
            "DROP TRIGGER IF EXISTS " + failureTrigger + " ON stock_transactions");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + failureFunction + "()");
        failureObjectsInstalled = false;
    }

    private long failureInjectionObjectCount() {
        Long triggerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_trigger WHERE NOT tgisinternal AND tgname = ?",
            Long.class,
            failureTrigger
        );
        Long functionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_proc WHERE proname = ?",
            Long.class,
            failureFunction
        );
        return (triggerCount == null ? 0 : triggerCount)
            + (functionCount == null ? 0 : functionCount);
    }

    private <T> T inCompany(java.util.function.Supplier<T> action) {
        TenantContext context = new TenantContext(
            companyId,
            tenantSlug,
            tenantSlug + ".example.test",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        );
        return TenantContextHolder.runWithTenant(context, action);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private record ReceiptFixture(
        Long orderId,
        Long orderVersion,
        Long firstOrderItemId,
        Long secondOrderItemId,
        Long firstAllocationId,
        Long secondAllocationId,
        Long firstAllocationVersion,
        Long secondAllocationVersion,
        Long planId,
        Long planVersion,
        Long asnId,
        Long asnVersion,
        Long firstAsnItemId,
        Long secondAsnItemId,
        Long taskId,
        Long taskVersion,
        Long firstTaskItemId,
        Long secondTaskItemId,
        Long firstSkuId,
        Long secondSkuId,
        Long locationId
    ) {
    }
}
