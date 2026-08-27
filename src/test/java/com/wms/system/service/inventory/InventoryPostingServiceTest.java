package com.wms.system.service.inventory;

import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.Location;
import com.wms.system.entity.ProductSku;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import com.wms.system.entity.enums.Zone;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.StockTransactionRepository;
import com.wms.system.service.DomainOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryPostingService")
class InventoryPostingServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long WAREHOUSE_ID = 11L;
    private static final long LOCATION_ID = 21L;
    private static final long PRODUCT_SKU_ID = 31L;
    private static final long BATCH_ID = 41L;

    @Mock
    private InventoryBatchRepository inventoryBatchRepository;

    @Mock
    private StockTransactionRepository stockTransactionRepository;

    @Mock
    private DomainOutboxService domainOutboxService;

    @InjectMocks
    private InventoryPostingService inventoryPostingService;

    private InventoryBatch batch;

    @BeforeEach
    void setUp() {
        Warehouse warehouse = Warehouse.builder()
            .id(WAREHOUSE_ID)
            .code("WH01")
            .name("Main warehouse")
            .isActive(true)
            .build();
        warehouse.setCompanyId(COMPANY_ID);

        Location location = Location.builder()
            .id(LOCATION_ID)
            .warehouse(warehouse)
            .warehouseCode("WH01")
            .zone(Zone.ZONE_A)
            .shelfNumber("A01")
            .positionNumber("001")
            .locationCode("WH01-ZONE_A-A01-001")
            .enabled(true)
            .build();
        location.setCompanyId(COMPANY_ID);

        ProductSku productSku = ProductSku.builder()
            .id(PRODUCT_SKU_ID)
            .skuCode("SKU-POSTING-001")
            .skuName("Posting SKU")
            .name("Posting SKU")
            .barcode("POSTING-BARCODE-001")
            .enabled(true)
            .isDeleted(false)
            .build();
        productSku.setCompanyId(COMPANY_ID);

        batch = InventoryBatch.builder()
            .id(BATCH_ID)
            .batchCode("BATCH-POSTING-001")
            .productSku(productSku)
            .location(location)
            .locationCode(location.getLocationCode())
            .quantity(10)
            .reservedQuantity(3)
            .initialQuantity(10)
            .expiryDate(LocalDate.now().plusYears(1))
            .active(true)
            .version(0)
            .build();
        batch.setCompanyId(COMPANY_ID);
    }

    @Test
    @DisplayName("positive delta updates the locked batch and writes complete trace metadata")
    void postPositiveDeltaWritesLedgerAndOutbox() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));
        when(stockTransactionRepository.save(any(StockTransaction.class))).thenAnswer(invocation -> {
            StockTransaction transaction = invocation.getArgument(0);
            transaction.setId(901L);
            return transaction;
        });

        InventoryPostingResult result = inventoryPostingService.post(command(5));

        assertThat(result.changed()).isTrue();
        assertThat(result.quantityBefore()).isEqualTo(10);
        assertThat(result.quantityAfter()).isEqualTo(15);
        assertThat(result.stockTransactionId()).isEqualTo(901L);
        assertThat(batch.getQuantity()).isEqualTo(15);
        verify(inventoryBatchRepository).save(batch);

        ArgumentCaptor<StockTransaction> transactionCaptor =
            ArgumentCaptor.forClass(StockTransaction.class);
        verify(stockTransactionRepository).save(transactionCaptor.capture());
        StockTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(transaction.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(transaction.getInventoryBatchId()).isEqualTo(BATCH_ID);
        assertThat(transaction.getBatchCode()).isEqualTo("BATCH-POSTING-001");
        assertThat(transaction.getPostingId()).isEqualTo("POST-001");
        assertThat(transaction.getIdempotencyKey()).isEqualTo("IDEMPOTENCY-001");
        assertThat(transaction.getProductSku()).isSameAs(batch.getProductSku());
        assertThat(transaction.getLocation()).isSameAs(batch.getLocation());
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.IN);
        assertThat(transaction.getSourceType()).isEqualTo(SourceType.MANUAL_ADJUST);
        assertThat(transaction.getSourceOrderId()).isEqualTo("ADJ-001");
        assertThat(transaction.getReasonCode()).isEqualTo("COUNT_VARIANCE");
        assertThat(transaction.getOperatorId()).isEqualTo(51L);
        assertThat(transaction.getOperatorName()).isEqualTo("Inventory manager");
        assertThat(transaction.getQuantity()).isEqualTo(5);
        assertThat(transaction.getQuantityBefore()).isEqualTo(10);
        assertThat(transaction.getQuantityAfter()).isEqualTo(15);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(domainOutboxService).append(
            eq(InventoryPostingService.OUTBOX_EVENT_TYPE),
            eq(InventoryPostingService.OUTBOX_AGGREGATE_TYPE),
            eq(BATCH_ID),
            payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload)
            .containsEntry("companyId", COMPANY_ID)
            .containsEntry("warehouseId", WAREHOUSE_ID)
            .containsEntry("productSkuId", PRODUCT_SKU_ID)
            .containsEntry("inventoryBatchId", BATCH_ID)
            .containsEntry("batchCode", "BATCH-POSTING-001")
            .containsEntry("delta", 5)
            .containsEntry("quantityBefore", 10)
            .containsEntry("quantityAfter", 15)
            .containsEntry("postingId", "POST-001")
            .containsEntry("idempotencyKey", "IDEMPOTENCY-001");
    }

    @Test
    @DisplayName("negative delta may reduce quantity exactly to the reserved quantity")
    void postNegativeDeltaDownToReservedQuantity() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryPostingResult result = inventoryPostingService.post(command(-7));

        assertThat(result.quantityAfter()).isEqualTo(3);
        assertThat(batch.getQuantity()).isEqualTo(3);
        ArgumentCaptor<StockTransaction> captor = ArgumentCaptor.forClass(StockTransaction.class);
        verify(stockTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionType()).isEqualTo(TransactionType.OUT);
        assertThat(captor.getValue().getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("a non-reserved batch becomes inactive when posting consumes its final quantity")
    void postToZeroMarksBatchInactive() {
        batch.setReservedQuantity(0);
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryPostingResult result = inventoryPostingService.post(command(-10));

        assertThat(result.quantityAfter()).isZero();
        assertThat(batch.getQuantity()).isZero();
        assertThat(batch.getActive()).isFalse();
        verify(inventoryBatchRepository).save(batch);
    }

    @Test
    @DisplayName("delta zero returns an unchanged result without inventory, ledger or Outbox writes")
    void zeroDeltaDoesNotWriteAnything() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));

        InventoryPostingResult result = inventoryPostingService.post(command(0));

        assertThat(result.changed()).isFalse();
        assertThat(result.quantityBefore()).isEqualTo(10);
        assertThat(result.quantityAfter()).isEqualTo(10);
        assertThat(result.stockTransactionId()).isNull();
        assertThat(batch.getQuantity()).isEqualTo(10);
        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting rejects a resulting quantity below the current reservation")
    void postRejectsQuantityBelowReservedQuantity() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> inventoryPostingService.post(command(-8)))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_RESERVED_QUANTITY_CONFLICT);
                assertThat(error.getParam("reservedQuantity")).isEqualTo(3);
                assertThat(error.getParam("quantityAfter")).isEqualTo(2L);
            });

        assertThat(batch.getQuantity()).isEqualTo(10);
        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting rejects a negative resulting quantity")
    void postRejectsNegativeQuantity() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> inventoryPostingService.post(command(-11)))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.STOCK_INVALID_QUANTITY));

        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting reports a genuinely missing batch as not found")
    void postRejectsMissingBatch() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryPostingService.post(command(1)))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.BATCH_NOT_FOUND);
                assertThat(error.getParam("batchId")).isEqualTo(BATCH_ID);
            });

        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting rejects an inactive existing batch")
    void postRejectsInactiveBatch() {
        batch.setActive(false);
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> inventoryPostingService.post(command(1)))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.BATCH_INACTIVE));

        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("a governed positive correction reactivates an inactive zero-quantity batch")
    void governedPositiveCorrectionReactivatesInactiveEmptyBatch() {
        batch.setQuantity(0);
        batch.setReservedQuantity(0);
        batch.setActive(false);
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));
        when(stockTransactionRepository.save(any(StockTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryPostingResult result = inventoryPostingService.post(
            command(5, SourceType.INVENTORY_GAIN)
        );

        assertThat(result.quantityBefore()).isZero();
        assertThat(result.quantityAfter()).isEqualTo(5);
        assertThat(batch.getQuantity()).isEqualTo(5);
        assertThat(batch.getActive()).isTrue();
        verify(inventoryBatchRepository).save(batch);
        verify(stockTransactionRepository).save(any(StockTransaction.class));
        verify(domainOutboxService).append(
            eq(InventoryPostingService.OUTBOX_EVENT_TYPE),
            eq(InventoryPostingService.OUTBOX_AGGREGATE_TYPE),
            eq(BATCH_ID),
            any()
        );
    }

    @Test
    @DisplayName("an ordinary positive posting cannot reactivate an inactive empty batch")
    void ordinaryPostingCannotReactivateInactiveEmptyBatch() {
        batch.setQuantity(0);
        batch.setReservedQuantity(0);
        batch.setActive(false);
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> inventoryPostingService.post(command(1)))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.BATCH_INACTIVE));

        assertThat(batch.getActive()).isFalse();
        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting rejects an SKU that does not own the locked batch")
    void postRejectsSkuMismatch() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));
        InventoryPostingCommand mismatched = new InventoryPostingCommand(
            BATCH_ID,
            999L,
            LOCATION_ID,
            1,
            context()
        );

        assertThatThrownBy(() -> inventoryPostingService.post(mismatched))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_POSTING_CONTEXT_MISMATCH);
                assertThat(error.getParam("field")).isEqualTo("productSkuId");
                assertThat(error.getParam("expected")).isEqualTo(999L);
                assertThat(error.getParam("actual")).isEqualTo(PRODUCT_SKU_ID);
            });

        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting rejects a location that does not own the locked batch")
    void postRejectsLocationMismatch() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));
        InventoryPostingCommand mismatched = new InventoryPostingCommand(
            BATCH_ID,
            PRODUCT_SKU_ID,
            999L,
            1,
            context()
        );

        assertThatThrownBy(() -> inventoryPostingService.post(mismatched))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_POSTING_CONTEXT_MISMATCH);
                assertThat(error.getParam("field")).isEqualTo("locationId");
                assertThat(error.getParam("expected")).isEqualTo(999L);
                assertThat(error.getParam("actual")).isEqualTo(LOCATION_ID);
            });

        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting rejects a warehouse that does not own the batch location")
    void postRejectsWarehouseMismatch() {
        when(inventoryBatchRepository.findByIdForPosting(BATCH_ID)).thenReturn(Optional.of(batch));
        PostingContext wrongWarehouse = new PostingContext(
            COMPANY_ID,
            999L,
            SourceType.MANUAL_ADJUST,
            "ADJ-001",
            "COUNT_VARIANCE",
            "Cycle count variance",
            51L,
            "Inventory manager",
            "POST-001",
            "IDEMPOTENCY-001"
        );
        InventoryPostingCommand mismatched = new InventoryPostingCommand(
            BATCH_ID,
            PRODUCT_SKU_ID,
            LOCATION_ID,
            1,
            wrongWarehouse
        );

        assertThatThrownBy(() -> inventoryPostingService.post(mismatched))
            .isInstanceOfSatisfying(BusinessException.class, error -> {
                assertThat(error.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_POSTING_CONTEXT_MISMATCH);
                assertThat(error.getParam("field")).isEqualTo("warehouseId");
                assertThat(error.getParam("expected")).isEqualTo(999L);
                assertThat(error.getParam("actual")).isEqualTo(WAREHOUSE_ID);
            });

        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
        verifyNoInteractions(stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting rejects a command for another tenant before loading a batch")
    void postRejectsCrossTenantContext() {
        PostingContext otherCompany = new PostingContext(
            2L,
            WAREHOUSE_ID,
            SourceType.MANUAL_ADJUST,
            "ADJ-001",
            "COUNT_VARIANCE",
            "Cycle count variance",
            51L,
            "Inventory manager",
            "POST-001",
            "IDEMPOTENCY-001"
        );
        InventoryPostingCommand command = new InventoryPostingCommand(
            BATCH_ID,
            PRODUCT_SKU_ID,
            LOCATION_ID,
            1,
            otherCompany
        );

        assertThatThrownBy(() -> inventoryPostingService.post(command))
            .isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorKey()).isEqualTo(ErrorKeys.AUTH_ACCESS_DENIED));

        verifyNoInteractions(inventoryBatchRepository, stockTransactionRepository, domainOutboxService);
    }

    @Test
    @DisplayName("posting owns one rollback-on-exception transaction boundary")
    void postDeclaresTransactionBoundary() throws Exception {
        Method method = InventoryPostingService.class
            .getMethod("post", InventoryPostingCommand.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private InventoryPostingCommand command(int delta) {
        return command(delta, SourceType.MANUAL_ADJUST);
    }

    private InventoryPostingCommand command(int delta, SourceType sourceType) {
        return new InventoryPostingCommand(
            BATCH_ID,
            PRODUCT_SKU_ID,
            LOCATION_ID,
            delta,
            context(sourceType)
        );
    }

    private PostingContext context() {
        return context(SourceType.MANUAL_ADJUST);
    }

    private PostingContext context(SourceType sourceType) {
        return new PostingContext(
            COMPANY_ID,
            WAREHOUSE_ID,
            sourceType,
            " ADJ-001 ",
            " COUNT_VARIANCE ",
            " Cycle count variance ",
            51L,
            " Inventory manager ",
            " POST-001 ",
            " IDEMPOTENCY-001 "
        );
    }
}
