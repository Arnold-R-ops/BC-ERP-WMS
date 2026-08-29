package com.wms.system.service.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inventoryquality.InventoryQualityHoldRequest;
import com.wms.system.dto.inventoryquality.InventoryQualityQuantityRequest;
import com.wms.system.dto.inventoryquality.InventoryQualityReleaseRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryQualityActionServiceTest {
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private OutboundTaskRepository outboundTaskRepository;
    @Mock private InventoryQualityActionRepository actionRepository;
    @Mock private InventoryQualityActionLineRepository lineRepository;
    @Mock private InventoryQualityReservationMoveRepository moveRepository;
    @Mock private InventoryQualityStateService stateService;
    @Mock private InventoryQualityPolicyService policyService;
    @Mock private IdempotencyService idempotencyService;

    private InventoryQualityActionService service;

    @BeforeEach
    void setUp() {
        service = new InventoryQualityActionService(
            batchRepository, reservationRepository, outboundTaskRepository,
            actionRepository, lineRepository, moveRepository, stateService,
            policyService, idempotencyService, new ObjectMapper()
        );
    }

    @Test
    void previewRequiresExplicitConfirmationWhenReservedStockMustMove() {
        InventoryBatch source = batch(1L, 100, 30);
        InventoryBatch alternate = batch(2L, 20, 0);
        InventoryReservation reservation = InventoryReservation.builder()
            .id(10L).salesOrderId(50L).salesOrderItemId(51L)
            .inventoryBatchId(1L).productSkuId(3L).locationId(11L).reservedQty(30).build();
        OutboundTask task = OutboundTask.builder()
            .id(20L).salesOrderId(50L).salesOrderItemId(51L).assignedBatchId(1L)
            .reservationId(10L).locationId(11L).planQty(30).actualQty(0)
            .status(OutboundTaskStatus.PENDING).build();
        when(batchRepository.findAllById(any())).thenReturn(List.of(source));
        when(reservationRepository.findByInventoryBatchId(1L)).thenReturn(List.of(reservation));
        when(outboundTaskRepository.findByReservationId(10L)).thenReturn(Optional.of(task));
        when(batchRepository.findSaleableByProductSkuIdOrderByExpiryDateAsc(3L))
            .thenReturn(List.of(source, alternate));

        var preview = service.previewHold(holdRequest(80), null);

        assertThat(preview.executable()).isTrue();
        assertThat(preview.confirmationRequired()).isTrue();
        assertThat(preview.totalReservationQuantityToReallocate()).isEqualTo(10);
        assertThat(preview.totalShortageQuantity()).isZero();
        assertThat(preview.lines().get(0).affectedSalesOrders()).singleElement()
            .satisfies(order -> {
                assertThat(order.salesOrderId()).isEqualTo(50L);
                assertThat(order.quantity()).isEqualTo(10);
            });
    }

    @Test
    void previewBlocksWhenTheOnlyAffectedOutboundTaskAlreadyStarted() {
        InventoryBatch source = batch(1L, 100, 30);
        InventoryBatch alternate = batch(2L, 20, 0);
        InventoryReservation reservation = InventoryReservation.builder()
            .id(10L).salesOrderId(50L).salesOrderItemId(51L)
            .inventoryBatchId(1L).productSkuId(3L).locationId(11L).reservedQty(30).build();
        OutboundTask task = OutboundTask.builder()
            .id(20L).salesOrderId(50L).salesOrderItemId(51L).assignedBatchId(1L)
            .reservationId(10L).locationId(11L).planQty(30).actualQty(0)
            .status(OutboundTaskStatus.PICKING).build();
        when(batchRepository.findAllById(any())).thenReturn(List.of(source));
        when(reservationRepository.findByInventoryBatchId(1L)).thenReturn(List.of(reservation));
        when(outboundTaskRepository.findByReservationId(10L)).thenReturn(Optional.of(task));
        when(batchRepository.findSaleableByProductSkuIdOrderByExpiryDateAsc(3L))
            .thenReturn(List.of(source, alternate));

        var preview = service.previewHold(holdRequest(80), null);

        assertThat(preview.executable()).isFalse();
        assertThat(preview.totalShortageQuantity()).isEqualTo(10);
    }

    @Test
    void partialHoldSplitsTraceableSegmentWithoutChangingTotalOnHand() {
        InventoryBatch source = batch(1L, 100, 0);
        IdempotencyRequest claimRecord = IdempotencyRequest.builder()
            .id(90L).idempotencyKey("hold-key").operation(InventoryQualityActionService.HOLD_OPERATION)
            .requestHash("hash").status(IdempotencyStatus.PROCESSING).build();
        when(idempotencyService.claim(eq("hold-key"), eq(InventoryQualityActionService.HOLD_OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, claimRecord));
        when(batchRepository.findAllByIdForPosting(List.of(1L))).thenReturn(List.of(source));
        when(reservationRepository.findOpenByBatchIdsForUpdate(List.of(1L))).thenReturn(List.of());
        when(batchRepository.findSaleableForQualityReallocationForUpdate(3L, Set.of(1L)))
            .thenReturn(List.of());
        when(lineRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryQualityActionLine line = invocation.getArgument(0);
            if (line.getId() == null) line.setId(501L);
            return line;
        });
        when(lineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(batchRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(200L);
            return batch;
        });
        // Return the managed action from the final lookup.
        final InventoryQualityAction[] savedAction = new InventoryQualityAction[1];
        when(actionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryQualityAction action = invocation.getArgument(0);
            if (action.getId() == null) action.setId(500L);
            savedAction[0] = action;
            return action;
        });
        when(actionRepository.findById(500L)).thenAnswer(invocation -> Optional.of(savedAction[0]));

        var result = service.hold(holdRequest(20), "hold-key", 7L, "operator", null);

        ArgumentCaptor<InventoryBatch> childCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(batchRepository, atLeastOnce()).saveAndFlush(childCaptor.capture());
        InventoryBatch child = childCaptor.getAllValues().stream()
            .filter(batch -> batch.getId().equals(200L)).findFirst().orElseThrow();
        assertThat(source.getQuantity()).isEqualTo(80);
        assertThat(child.getQuantity()).isEqualTo(20);
        assertThat(child.getQualityParentBatchId()).isEqualTo(1L);
        assertThat(source.getQuantity() + child.getQuantity()).isEqualTo(100);
        assertThat(result.status()).isEqualTo(InventoryQualityActionStatus.COMPLETED);
        verify(stateService).transition(argThat(command ->
            command.inventoryBatchId().equals(200L)
                && command.targetStatus() == InventoryQualityStatus.QUARANTINED));
        verify(idempotencyService).markSucceeded(claimRecord, 201, "500");
    }

    @Test
    void rejectsFreeTextThatIsNotAStandardReasonCode() {
        InventoryQualityHoldRequest request = new InventoryQualityHoldRequest(
            InventoryQualityHoldType.QUARANTINE,
            List.of(new InventoryQualityQuantityRequest(1L, 1)),
            "typed by client", null, null, null, false
        );

        assertThatThrownBy(() -> service.previewHold(request, null))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo(ErrorKeys.VALIDATION_FAILED);
                assertThat(exception.getParam("reason")).isEqualTo("UNSUPPORTED_STANDARD_REASON");
            });
        verifyNoInteractions(batchRepository);
    }

    @Test
    void directReleaseTransitionsHeldStockImmediately() {
        InventoryBatch held = batch(1L, 20, 0);
        held.setQualityStatus(InventoryQualityStatus.QUARANTINED);
        IdempotencyRequest claimRecord = releaseClaim("release-key");
        final InventoryQualityAction[] savedAction = stubReleasePersistence(claimRecord, held);
        when(policyService.releaseMode()).thenReturn(InventoryQualityReleaseMode.DIRECT_WITH_REASON);

        var result = service.release(releaseRequest(20), "release-key", 7L, "requester");

        assertThat(result.status()).isEqualTo(InventoryQualityActionStatus.COMPLETED);
        verify(stateService).transition(argThat(command ->
            command.inventoryBatchId().equals(1L)
                && command.targetStatus() == InventoryQualityStatus.AVAILABLE));
        verify(idempotencyService).markSucceeded(claimRecord, 201, savedAction[0].getId().toString());
    }

    @Test
    void approvalReleaseWaitsForAnotherUserAndThenTransitionsAtomically() {
        InventoryBatch held = batch(1L, 20, 0);
        held.setQualityStatus(InventoryQualityStatus.RECALL_HOLD);
        IdempotencyRequest claimRecord = releaseClaim("approval-key");
        final InventoryQualityAction[] savedAction = stubReleasePersistence(claimRecord, held);
        when(policyService.releaseMode()).thenReturn(InventoryQualityReleaseMode.APPROVAL_REQUIRED);

        var pending = service.release(releaseRequest(20), "approval-key", 7L, "requester");

        assertThat(pending.status()).isEqualTo(InventoryQualityActionStatus.PENDING_REVIEW);
        verifyNoInteractions(stateService);
        when(actionRepository.findByIdForUpdate(500L)).thenAnswer(invocation -> Optional.of(savedAction[0]));

        var approved = service.approve(500L, "verified", 8L, "reviewer");

        assertThat(approved.status()).isEqualTo(InventoryQualityActionStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo(8L);
        verify(stateService).transition(argThat(command ->
            command.inventoryBatchId().equals(1L)
                && command.targetStatus() == InventoryQualityStatus.AVAILABLE));
    }

    private InventoryQualityHoldRequest holdRequest(int quantity) {
        return new InventoryQualityHoldRequest(
            InventoryQualityHoldType.QUARANTINE,
            List.of(new InventoryQualityQuantityRequest(1L, quantity)),
            "QUALITY_RISK", "seal issue", null, null, true
        );
    }

    private InventoryQualityReleaseRequest releaseRequest(int quantity) {
        return new InventoryQualityReleaseRequest(
            List.of(new InventoryQualityQuantityRequest(1L, quantity)),
            "RISK_CLEARED", "verified", null
        );
    }

    private IdempotencyRequest releaseClaim(String key) {
        return IdempotencyRequest.builder()
            .id(91L).idempotencyKey(key).operation(InventoryQualityActionService.RELEASE_OPERATION)
            .requestHash("hash").status(IdempotencyStatus.PROCESSING).build();
    }

    private InventoryQualityAction[] stubReleasePersistence(
        IdempotencyRequest claimRecord,
        InventoryBatch held
    ) {
        when(idempotencyService.claim(eq(claimRecord.getIdempotencyKey()),
            eq(InventoryQualityActionService.RELEASE_OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, claimRecord));
        when(batchRepository.findAllByIdForPosting(List.of(1L))).thenReturn(List.of(held));
        when(lineRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryQualityActionLine line = invocation.getArgument(0);
            if (line.getId() == null) line.setId(501L);
            return line;
        });
        when(lineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        final InventoryQualityAction[] savedAction = new InventoryQualityAction[1];
        when(actionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryQualityAction action = invocation.getArgument(0);
            if (action.getId() == null) action.setId(500L);
            savedAction[0] = action;
            return action;
        });
        when(actionRepository.findById(500L)).thenAnswer(invocation -> Optional.of(savedAction[0]));
        return savedAction;
    }

    private InventoryBatch batch(Long id, int quantity, int reserved) {
        Warehouse warehouse = Warehouse.builder().id(id + 100).code("W" + id).name("Warehouse " + id).build();
        Location location = Location.builder().id(id + 10).locationCode("L" + id).warehouse(warehouse).build();
        ProductSku sku = ProductSku.builder().id(3L).skuCode("SKU-3").name("Product 3").build();
        return InventoryBatch.builder()
            .id(id).batchCode("BATCH-" + id).locationCode(location.getLocationCode())
            .productSku(sku).location(location).quantity(quantity).reservedQuantity(reserved)
            .initialQuantity(quantity).qualityStatus(InventoryQualityStatus.AVAILABLE).active(true)
            .build();
    }
}
