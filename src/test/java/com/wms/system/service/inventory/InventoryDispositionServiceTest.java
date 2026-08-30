package com.wms.system.service.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inventorydisposition.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.*;
import com.wms.system.service.*;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryDispositionServiceTest {
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private OutboundTaskRepository outboundTaskRepository;
    @Mock private InventoryDispositionOrderRepository orderRepository;
    @Mock private InventoryDispositionLineRepository lineRepository;
    @Mock private InventoryDispositionExecutionRepository executionRepository;
    @Mock private InventoryDispositionExecutionLineRepository executionLineRepository;
    @Mock private InventoryDispositionReservationImpactRepository impactRepository;
    @Mock private InventoryQualityStateService qualityStateService;
    @Mock private InventoryDispositionPolicyService policyService;
    @Mock private InventoryPostingService postingService;
    @Mock private BackorderService backorderService;
    @Mock private LocationOccupancyService locationOccupancyService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private DomainOutboxService outboxService;

    private InventoryDispositionService service;

    @BeforeEach
    void setUp() {
        service = new InventoryDispositionService(
            batchRepository, reservationRepository, outboundTaskRepository,
            orderRepository, lineRepository, executionRepository, executionLineRepository,
            impactRepository, qualityStateService, policyService, postingService,
            backorderService, locationOccupancyService, idempotencyService,
            outboxService, new ObjectMapper()
        );
    }

    @Test
    void damagePreviewClassifiesStartedPickingAsQualityExceptionAndBackorder() {
        InventoryBatch source = batch(1L, 100, 30, InventoryQualityStatus.AVAILABLE);
        InventoryReservation reservation = reservation(10L, 1L, 30);
        OutboundTask task = task(20L, 10L, 1L, 30, OutboundTaskStatus.PICKING);
        when(batchRepository.findAllById(List.of(1L))).thenReturn(List.of(source));
        when(reservationRepository.findByInventoryBatchId(1L)).thenReturn(List.of(reservation));
        when(outboundTaskRepository.findByReservationId(10L)).thenReturn(Optional.of(task));
        when(batchRepository.findSaleableByProductSkuIdOrderByExpiryDateAsc(3L))
            .thenReturn(List.of(source));

        var preview = service.previewDamage(damageRequest(100), null);

        assertThat(preview.confirmationRequired()).isTrue();
        assertThat(preview.totalBackorderedQuantity()).isEqualTo(30);
        assertThat(preview.totalQualityExceptionQuantity()).isEqualTo(30);
        assertThat(preview.lines()).singleElement().satisfies(line -> {
            assertThat(line.qualityExceptionQuantity()).isEqualTo(30);
            assertThat(line.affectedSalesOrders()).singleElement()
                .satisfies(impact -> assertThat(impact.impact()).isEqualTo("QUALITY_EXCEPTION"));
        });
    }

    @Test
    void damageReportReleasesUnstartedReservationVoidsTaskAndCreatesBackorder() {
        InventoryBatch source = batch(1L, 100, 30, InventoryQualityStatus.AVAILABLE);
        InventoryReservation reservation = reservation(10L, 1L, 30);
        OutboundTask task = task(20L, 10L, 1L, 30, OutboundTaskStatus.PENDING);
        IdempotencyRequest claimRecord = claim(
            "damage-key", InventoryDispositionService.DAMAGE_OPERATION);
        stubClaim(claimRecord);
        when(batchRepository.findAllByIdForPosting(List.of(1L))).thenReturn(List.of(source));
        when(reservationRepository.findOpenByBatchIdsForUpdate(List.of(1L)))
            .thenReturn(List.of(reservation));
        when(outboundTaskRepository.findByReservationIdForUpdate(10L))
            .thenReturn(Optional.of(task));
        when(batchRepository.findSaleableForQualityReallocationForUpdate(3L, Set.of(1L)))
            .thenReturn(List.of());
        stubOrderAndLinePersistence();

        var result = service.reportDamage(
            damageRequest(100), "damage-key", 7L, "operator", null);

        assertThat(result.status()).isEqualTo(InventoryDispositionStatus.COMPLETED);
        assertThat(source.getReservedQuantity()).isZero();
        assertThat(reservation.getReleasedQty()).isEqualTo(30);
        assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.VOIDED);
        verify(backorderService).addQualityShortage(
            51L, 30, "Inventory quality exception: " + result.dispositionNumber());
        verify(qualityStateService).transition(argThat(command ->
            command.inventoryBatchId().equals(1L)
                && command.targetStatus() == InventoryQualityStatus.DAMAGED));
        verify(idempotencyService).markSucceeded(claimRecord, 201, "500");
    }

    @Test
    void directDispositionCanBeSavedAsDraftAndSubmittedByItsMaker() {
        InventoryBatch damaged = batch(1L, 20, 0, InventoryQualityStatus.DAMAGED);
        IdempotencyRequest createClaim = claim(
            "draft-key", InventoryDispositionService.CREATE_OPERATION);
        when(idempotencyService.claim(eq("draft-key"),
            eq(InventoryDispositionService.CREATE_OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, createClaim));
        when(batchRepository.findAllByIdForPosting(List.of(1L))).thenReturn(List.of(damaged));
        when(lineRepository.sumOutstandingByBatch(eq(1L),
            eq(InventoryDispositionType.SCRAP_DESTROY), anySet())).thenReturn(0L);
        when(policyService.reviewMode()).thenReturn(InventoryDispositionReviewMode.DIRECT_WITH_REASON);
        InventoryDispositionOrder[] saved = stubOrderAndLinePersistence();

        var draft = service.createScrapOrder(new CreateInventoryScrapOrderRequest(
            List.of(new InventoryDispositionQuantityRequest(1L, 10)),
            "DAMAGED_SCRAP", "case damaged", null, null, true
        ), "draft-key", 7L, "maker", null);

        assertThat(draft.status()).isEqualTo(InventoryDispositionStatus.DRAFT);
        IdempotencyRequest submitClaim = claim(
            "submit-key", InventoryDispositionService.SUBMIT_OPERATION);
        when(idempotencyService.claim(eq("submit-key"),
            eq(InventoryDispositionService.SUBMIT_OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, submitClaim));
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(saved[0]));

        var submitted = service.submit(500L, "submit-key", 7L);

        assertThat(submitted.status()).isEqualTo(InventoryDispositionStatus.READY_TO_EXECUTE);
        verify(idempotencyService).markSucceeded(submitClaim, 200, "500");
    }

    @Test
    void physicalDispositionPostsEverySelectedLineAndCompletesAtomically() {
        InventoryBatch damaged = batch(1L, 10, 0, InventoryQualityStatus.DAMAGED);
        InventoryDispositionOrder order = InventoryDispositionOrder.builder()
            .id(500L).dispositionNumber("IDS-500")
            .dispositionType(InventoryDispositionType.SCRAP_DESTROY)
            .status(InventoryDispositionStatus.READY_TO_EXECUTE)
            .reviewModeSnapshot(InventoryDispositionReviewMode.DIRECT_WITH_REASON)
            .reasonCode("DAMAGED_SCRAP").requestedBy(7L).requestedByName("maker")
            .idempotencyKey("create-key").requestFingerprint("a".repeat(64))
            .build();
        InventoryDispositionLine line = InventoryDispositionLine.builder()
            .id(501L).order(order).sourceBatchId(1L).resultBatchId(1L)
            .productSkuId(3L).productSkuCode("SKU-3").productName("Product 3")
            .sourceBatchCode("B-1").resultBatchCode("B-1")
            .warehouseId(101L).warehouseName("Warehouse 1")
            .locationId(11L).locationCode("A-01")
            .quantity(10).executedQuantity(0)
            .fromQualityStatus(InventoryQualityStatus.AVAILABLE)
            .toQualityStatus(InventoryQualityStatus.DAMAGED)
            .build();
        order.getLines().add(line);
        IdempotencyRequest claimRecord = claim(
            "execute-key", InventoryDispositionService.EXECUTE_OPERATION);
        stubClaim(claimRecord);
        when(orderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(lineRepository.findAllByIdForUpdate(List.of(501L))).thenReturn(List.of(line));
        when(batchRepository.findAllByIdForPosting(List.of(1L))).thenReturn(List.of(damaged));
        when(executionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryDispositionExecution execution = invocation.getArgument(0);
            execution.setId(700L);
            return execution;
        });
        when(postingService.post(any())).thenReturn(new InventoryPostingResult(
            1L, "B-1", 3L, 11L, 101L, -10, 10, 0,
            true, 900L, "DSP:IDS-500-EX-1:501", "execute-line-key"));
        when(executionLineRepository.save(any())).thenAnswer(invocation -> {
            InventoryDispositionExecutionLine executionLine = invocation.getArgument(0);
            executionLine.setId(701L);
            return executionLine;
        });

        var result = service.execute(500L, new ExecuteInventoryDispositionRequest(
            List.of(new InventoryDispositionExecutionQuantityRequest(501L, 10)),
            "destroyed under supervision"
        ), "execute-key", 8L, "warehouse-operator");

        assertThat(result.status()).isEqualTo(InventoryDispositionStatus.COMPLETED);
        assertThat(result.executions()).singleElement().satisfies(execution -> {
            assertThat(execution.executedBy()).isEqualTo(8L);
            assertThat(execution.lines()).singleElement()
                .satisfies(executionLine -> assertThat(executionLine.quantityAfter()).isZero());
        });
        ArgumentCaptor<InventoryPostingCommand> posting =
            ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(postingService).post(posting.capture());
        assertThat(posting.getValue().delta()).isEqualTo(-10);
        assertThat(posting.getValue().context().sourceType()).isEqualTo(SourceType.SCRAP_OUT);
        assertThat(posting.getValue().context().operatorId()).isEqualTo(8L);
        verify(locationOccupancyService).refreshLocationStatus(11L);
        verify(idempotencyService).markSucceeded(claimRecord, 200, "700");
    }

    private InventoryDispositionOrder[] stubOrderAndLinePersistence() {
        InventoryDispositionOrder[] saved = new InventoryDispositionOrder[1];
        when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryDispositionOrder order = invocation.getArgument(0);
            if (order.getId() == null) order.setId(500L);
            saved[0] = order;
            return order;
        });
        when(lineRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryDispositionLine line = invocation.getArgument(0);
            if (line.getId() == null) line.setId(501L);
            return line;
        });
        lenient().when(lineRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findById(500L)).thenAnswer(invocation -> Optional.of(saved[0]));
        return saved;
    }

    private void stubClaim(IdempotencyRequest request) {
        when(idempotencyService.claim(eq(request.getIdempotencyKey()),
            eq(request.getOperation()), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, request));
    }

    private IdempotencyRequest claim(String key, String operation) {
        return IdempotencyRequest.builder()
            .id(90L).idempotencyKey(key).operation(operation)
            .requestHash("hash").status(IdempotencyStatus.PROCESSING).build();
    }

    private ReportInventoryDamageRequest damageRequest(int quantity) {
        return new ReportInventoryDamageRequest(
            List.of(new InventoryDispositionQuantityRequest(1L, quantity)),
            "PHYSICAL_DAMAGE", "crushed packaging", null, null, true);
    }

    private InventoryReservation reservation(Long id, Long batchId, int quantity) {
        return InventoryReservation.builder()
            .id(id).salesOrderId(50L).salesOrderItemId(51L)
            .inventoryBatchId(batchId).productSkuId(3L).locationId(11L)
            .reservedQty(quantity).build();
    }

    private OutboundTask task(
        Long id, Long reservationId, Long batchId, int quantity, OutboundTaskStatus status
    ) {
        return OutboundTask.builder()
            .id(id).salesOrderId(50L).salesOrderItemId(51L)
            .assignedBatchId(batchId).reservationId(reservationId).locationId(11L)
            .planQty(quantity).actualQty(0).status(status).build();
    }

    private InventoryBatch batch(
        Long id, int quantity, int reserved, InventoryQualityStatus qualityStatus
    ) {
        Warehouse warehouse = Warehouse.builder()
            .id(100L + id).code("WH" + id).name("Warehouse " + id).build();
        Location location = Location.builder()
            .id(10L + id).warehouse(warehouse).warehouseCode(warehouse.getCode())
            .locationCode("A-0" + id).build();
        ProductSku sku = ProductSku.builder()
            .id(3L).skuCode("SKU-3").name("Product 3").build();
        return InventoryBatch.builder()
            .id(id).batchCode("B-" + id).locationCode(location.getLocationCode())
            .productSku(sku).location(location).quantity(quantity)
            .reservedQuantity(reserved).initialQuantity(quantity)
            .qualityStatus(qualityStatus).expiryDate(LocalDate.now().plusDays(30))
            .active(true).build();
    }
}
