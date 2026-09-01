package com.wms.system.service.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.transfer.DispatchTransferRequest;
import com.wms.system.dto.transfer.ReceiveTransferRequest;
import com.wms.system.dto.transfer.CloseTransferShortageRequest;
import com.wms.system.dto.transfer.TransferDecisionRequest;
import com.wms.system.dto.transfer.TransferPolicyResponse;
import com.wms.system.dto.transfer.TransferReasonRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.*;
import com.wms.system.service.BackorderService;
import com.wms.system.service.DomainOutboxService;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.LocationOccupancyService;
import com.wms.system.service.idempotency.*;
import com.wms.system.service.inspection.ReceivingInspectionTaskFactory;
import com.wms.system.service.inventory.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private TransferOrderRepository orderRepository;
    @Mock private TransferOrderLineRepository lineRepository;
    @Mock private TransferAllocationRepository allocationRepository;
    @Mock private TransferWarehouseTaskRepository taskRepository;
    @Mock private TransferDispatchRepository dispatchRepository;
    @Mock private TransferDispatchLineRepository dispatchLineRepository;
    @Mock private TransferReceiptRepository receiptRepository;
    @Mock private TransferReceiptLineRepository receiptLineRepository;
    @Mock private TransferEventRepository eventRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private BackorderLineRepository backorderRepository;
    @Mock private LocationOccupancyService locationOccupancyService;
    @Mock private TransferPolicyService policyService;
    @Mock private InventoryPostingService postingService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private DomainOutboxService outboxService;
    @Mock private BackorderService backorderService;
    @Mock private ReceivingInspectionTaskFactory inspectionTaskFactory;

    private TransferService service;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private ProductSku sku;
    private Location sourceLocation;
    private Location destinationLocation;

    @BeforeEach
    void setUp() {
        service = new TransferService(
            orderRepository, lineRepository, allocationRepository, taskRepository,
            dispatchRepository, dispatchLineRepository, receiptRepository, receiptLineRepository,
            eventRepository, warehouseRepository, productSkuRepository, batchRepository,
            locationRepository, backorderRepository, locationOccupancyService, policyService, postingService,
            idempotencyService, outboxService, backorderService, inspectionTaskFactory,
            new ObjectMapper()
        );
        sourceWarehouse = warehouse(10L, "SRC", "Source");
        destinationWarehouse = warehouse(20L, "DST", "Destination");
        sku = ProductSku.builder().id(30L).skuCode("SKU-30").name("Widget")
            .barcode("BAR-30").build();
        sourceLocation = location(40L, sourceWarehouse, "SRC-ZONE_A-A-01-001");
        destinationLocation = location(50L, destinationWarehouse, "DST-ZONE_A-A-01-001");

        lenient().when(idempotencyService.claim(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> acquired(
                invocation.getArgument(0), invocation.getArgument(1), "request-hash"));
        lenient().when(orderRepository.saveAndFlush(any(TransferOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskRepository.saveAndFlush(any(TransferWarehouseTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        AtomicLong dispatchIds = new AtomicLong(100L);
        lenient().when(dispatchRepository.saveAndFlush(any(TransferDispatch.class)))
            .thenAnswer(invocation -> {
                TransferDispatch dispatch = invocation.getArgument(0);
                if (dispatch.getId() == null) dispatch.setId(dispatchIds.incrementAndGet());
                return dispatch;
            });
        AtomicLong receiptIds = new AtomicLong(200L);
        lenient().when(receiptRepository.saveAndFlush(any(TransferReceipt.class)))
            .thenAnswer(invocation -> {
                TransferReceipt receipt = invocation.getArgument(0);
                if (receipt.getId() == null) receipt.setId(receiptIds.incrementAndGet());
                return receipt;
            });
        AtomicLong batchIds = new AtomicLong(300L);
        lenient().when(batchRepository.saveAndFlush(any(InventoryBatch.class)))
            .thenAnswer(invocation -> {
                InventoryBatch batch = invocation.getArgument(0);
                if (batch.getId() == null) batch.setId(batchIds.incrementAndGet());
                return batch;
            });
    }

    @Test
    void directSubmissionApprovesCreatesBothWarehouseTasksAndReservesAvailableStock() {
        TransferOrder order = order(1L, TransferBusinessStatus.DRAFT, 10);
        InventoryBatch batch = sourceBatch(60L, 7, 0);
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(policyService.get()).thenReturn(new TransferPolicyResponse(TransferApprovalMode.DIRECT, false));
        when(batchRepository.findTransferCandidatesForUpdate(10L, 30L)).thenReturn(List.of(batch));

        var response = service.submit(1L, new TransferDecisionRequest(0L, "ready"),
            "submit-key", 71L, "buyer");

        assertThat(response.businessStatus()).isEqualTo(TransferBusinessStatus.APPROVED);
        assertThat(response.outboundStatus()).isEqualTo(TransferOutboundStatus.PENDING);
        assertThat(response.allocatedQuantity()).isEqualTo(7);
        assertThat(batch.getReservedQuantity()).isEqualTo(7);
        assertThat(order.getTasks()).extracting(TransferWarehouseTask::getDirection)
            .containsExactly(TransferTaskDirection.OUTBOUND, TransferTaskDirection.INBOUND);
        assertThat(order.getTasks()).extracting(TransferWarehouseTask::getStatus)
            .containsExactly(TransferTaskStatus.PENDING, TransferTaskStatus.EXPECTED);
        ArgumentCaptor<TransferAllocation> allocation = ArgumentCaptor.forClass(TransferAllocation.class);
        verify(allocationRepository).save(allocation.capture());
        assertThat(allocation.getValue().getAllocatedQuantity()).isEqualTo(7);
        assertThat(allocation.getValue().getStatus()).isEqualTo(TransferAllocationStatus.ACTIVE);
        verify(idempotencyService).markSucceeded(any(IdempotencyRequest.class), eq(200), eq("1"));
    }

    @Test
    void approvalPolicyKeepsSubmissionPendingWithoutReservingStock() {
        TransferOrder order = order(2L, TransferBusinessStatus.DRAFT, 10);
        when(orderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(order));
        when(policyService.get()).thenReturn(new TransferPolicyResponse(TransferApprovalMode.ALWAYS, true));

        var response = service.submit(2L, new TransferDecisionRequest(0L, "review please"),
            "submit-review-key", 71L, "buyer");

        assertThat(response.businessStatus()).isEqualTo(TransferBusinessStatus.PENDING_APPROVAL);
        assertThat(response.receivingInspectionSnapshot()).isTrue();
        assertThat(order.getTasks()).isEmpty();
        verifyNoInteractions(postingService);
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void submissionRejectsABackorderAlreadyPromisedByAnotherActiveTransfer() {
        TransferOrder order = order(12L, TransferBusinessStatus.DRAFT, 4);
        order.setEstimatedArrivalAt(LocalDateTime.of(2026, 9, 3, 10, 0));
        order.getLines().get(0).setBackorderLineId(880L);
        BackorderLine backorder = BackorderLine.builder().id(880L).salesOrderId(700L)
            .salesOrderItemId(701L).productSkuId(30L).requestedQty(4).remainingQty(4)
            .status(BackorderStatus.OPEN).build();
        when(orderRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(order));
        when(backorderRepository.findByIdForUpdate(880L)).thenReturn(Optional.of(backorder));
        when(lineRepository.existsByBackorderLineIdAndTransferOrderIdNotAndTransferOrderBusinessStatusIn(
            eq(880L), eq(12L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(12L,
            new TransferDecisionRequest(0L, "ready"), "duplicate-link", 71L, "buyer"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getParam("reason"))
                    .isEqualTo("BACKORDER_ALREADY_LINKED_TO_ACTIVE_TRANSFER"));

        verifyNoInteractions(policyService, postingService);
    }

    @Test
    void submissionAllowsBackorderReuseAfterThePreviousTransferReachedATerminalState() {
        TransferOrder order = order(13L, TransferBusinessStatus.DRAFT, 4);
        order.setEstimatedArrivalAt(LocalDateTime.of(2026, 9, 3, 10, 0));
        order.getLines().get(0).setBackorderLineId(881L);
        BackorderLine backorder = BackorderLine.builder().id(881L).salesOrderId(710L)
            .salesOrderItemId(711L).productSkuId(30L).requestedQty(4).remainingQty(4)
            .status(BackorderStatus.OPEN).build();
        when(orderRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(order));
        when(backorderRepository.findByIdForUpdate(881L)).thenReturn(Optional.of(backorder));
        when(lineRepository.existsByBackorderLineIdAndTransferOrderIdNotAndTransferOrderBusinessStatusIn(
            eq(881L), eq(13L), anyCollection())).thenReturn(false);
        when(policyService.get()).thenReturn(new TransferPolicyResponse(TransferApprovalMode.DIRECT, false));
        when(batchRepository.findTransferCandidatesForUpdate(10L, 30L)).thenReturn(List.of());

        var response = service.submit(13L, new TransferDecisionRequest(0L, "replacement"),
            "replacement-link", 71L, "buyer");

        assertThat(response.businessStatus()).isEqualTo(TransferBusinessStatus.APPROVED);
        verify(backorderService).markWaitingInbound(881L, LocalDate.of(2026, 9, 3));
        verify(lineRepository).existsByBackorderLineIdAndTransferOrderIdNotAndTransferOrderBusinessStatusIn(
            eq(881L), eq(13L), argThat(statuses ->
                statuses.size() == 2
                    && statuses.contains(TransferBusinessStatus.PENDING_APPROVAL)
                    && statuses.contains(TransferBusinessStatus.APPROVED)));
    }

    @Test
    void rejectedTransferClearsThePreviousReviewBeforeResubmission() {
        TransferOrder order = order(15L, TransferBusinessStatus.REJECTED, 2);
        order.setReviewedBy(90L);
        order.setReviewedByName("old reviewer");
        order.setReviewedAt(LocalDateTime.of(2026, 8, 31, 9, 0));
        order.setReviewComment("old rejection");
        when(orderRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(order));
        when(policyService.get()).thenReturn(new TransferPolicyResponse(TransferApprovalMode.ALWAYS, false));

        var response = service.submit(15L, new TransferDecisionRequest(0L, "corrected"),
            "resubmit-key", 71L, "buyer");

        assertThat(response.businessStatus()).isEqualTo(TransferBusinessStatus.PENDING_APPROVAL);
        assertThat(response.reviewedBy()).isNull();
        assertThat(response.reviewedByName()).isNull();
        assertThat(response.reviewedAt()).isNull();
        assertThat(response.reviewComment()).isNull();
    }

    @Test
    void voidChecksOnlyTasksBelongingToTheTransfer() {
        TransferOrder order = order(14L, TransferBusinessStatus.DRAFT, 1);
        when(orderRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(order));
        when(taskRepository.existsByTransferOrderId(14L)).thenReturn(false);
        when(allocationRepository.findByTransferLineTransferOrderIdOrderById(14L)).thenReturn(List.of());
        when(dispatchRepository.findByTransferOrderIdOrderById(14L)).thenReturn(List.of());

        var response = service.voidOrder(14L, new TransferReasonRequest(0L, "entered by mistake"),
            "void-key", 71L, "buyer");

        assertThat(response.businessStatus()).isEqualTo(TransferBusinessStatus.VOIDED);
        verify(taskRepository).existsByTransferOrderId(14L);
        verify(taskRepository, never()).findAll();
    }

    @Test
    void closeRemainderRecordsCompleteTerminationAuditAndCannotBeRepeated() {
        TransferOrder order = order(16L, TransferBusinessStatus.APPROVED, 10);
        order.getLines().get(0).setDispatchedQuantity(4);
        when(orderRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(order));

        var response = service.closeRemainder(16L,
            new TransferReasonRequest(0L, "supplier short shipped"),
            "close-remainder-key", 76L, "supervisor");

        assertThat(response.outboundStatus()).isEqualTo(TransferOutboundStatus.CLOSED);
        assertThat(response.closedQuantity()).isEqualTo(6);
        assertThat(order.getTerminatedBy()).isEqualTo(76L);
        assertThat(order.getTerminatedByName()).isEqualTo("supervisor");
        assertThat(order.getTerminatedAt()).isNotNull();
        assertThat(order.getTerminationReason()).isEqualTo("supplier short shipped");

        assertThatThrownBy(() -> service.closeRemainder(16L,
            new TransferReasonRequest(0L, "repeat"), "close-remainder-repeat", 76L, "supervisor"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getParam("reason"))
                    .isEqualTo("TRANSFER_UNSHIPPED_REMAINDER_ALREADY_CLOSED"));
    }

    @Test
    void shortageCannotCloseUntilOutboundIsFullyDispatchedOrItsRemainderClosed() {
        TransferOrder order = order(17L, TransferBusinessStatus.APPROVED, 10);
        order.getLines().get(0).setDispatchedQuantity(4);
        when(orderRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.closeShortage(17L,
            new CloseTransferShortageRequest(0L, "missing parcel", true),
            "shortage-before-outbound-final", 76L, "supervisor"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getParam("reason")).isEqualTo("TRANSFER_OUTBOUND_NOT_FINAL"));

        verifyNoInteractions(dispatchLineRepository);
    }

    @Test
    void reallocateReservesOnlyTheStillOpenQuantity() {
        TransferOrder order = order(3L, TransferBusinessStatus.APPROVED, 10);
        order.getLines().get(0).setAllocatedQuantity(4);
        InventoryBatch newlyAvailable = sourceBatch(61L, 20, 2);
        when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
        when(batchRepository.findTransferCandidatesForUpdate(10L, 30L)).thenReturn(List.of(newlyAvailable));

        var response = service.reallocate(3L, new TransferDecisionRequest(0L, "stock arrived"),
            "reallocate-key", 72L, "planner");

        assertThat(response.allocatedQuantity()).isEqualTo(10);
        assertThat(newlyAvailable.getReservedQuantity()).isEqualTo(8);
        ArgumentCaptor<TransferAllocation> allocation = ArgumentCaptor.forClass(TransferAllocation.class);
        verify(allocationRepository).save(allocation.capture());
        assertThat(allocation.getValue().getAllocatedQuantity()).isEqualTo(6);
    }

    @Test
    void fullReservationIsReleasedBeforeTransferOutPosting() {
        TransferOrder order = order(4L, TransferBusinessStatus.APPROVED, 10);
        TransferOrderLine line = order.getLines().get(0);
        line.setAllocatedQuantity(10);
        InventoryBatch batch = sourceBatch(62L, 10, 10);
        TransferAllocation allocation = TransferAllocation.builder().id(70L).transferLine(line)
            .sourceBatch(batch).allocatedQuantity(10).status(TransferAllocationStatus.ACTIVE).build();
        TransferWarehouseTask outbound = task(80L, order, sourceWarehouse,
            TransferTaskDirection.OUTBOUND, TransferTaskStatus.PENDING);
        TransferWarehouseTask inbound = task(81L, order, destinationWarehouse,
            TransferTaskDirection.INBOUND, TransferTaskStatus.EXPECTED);
        order.addTask(outbound);
        order.addTask(inbound);
        when(taskRepository.findTransferOrderIdById(80L)).thenReturn(Optional.of(4L));
        when(taskRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(outbound));
        when(orderRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(order));
        when(allocationRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(allocation));
        when(batchRepository.findByIdForPosting(62L)).thenReturn(Optional.of(batch));
        when(postingService.post(any())).thenAnswer(invocation -> {
            assertThat(batch.getReservedQuantity())
                .as("the unified ledger rejects a deduction that still consumes reserved stock")
                .isZero();
            InventoryPostingCommand command = invocation.getArgument(0);
            return postingResult(command, 901L);
        });

        service.dispatch(80L, new DispatchTransferRequest(
            0L, "HAND-1", null, "loaded", List.of(new DispatchTransferRequest.Line(70L, 10))),
            "dispatch-key", 73L, "dispatcher");

        InOrder orderOfCalls = inOrder(batchRepository, postingService);
        orderOfCalls.verify(batchRepository).saveAndFlush(batch);
        orderOfCalls.verify(postingService).post(any(InventoryPostingCommand.class));
        assertThat(batch.getReservedQuantity()).isZero();
        assertThat(allocation.getConsumedQuantity()).isEqualTo(10);
        assertThat(allocation.getStatus()).isEqualTo(TransferAllocationStatus.CONSUMED);
        assertThat(line.getAllocatedQuantity()).isZero();
        assertThat(line.getDispatchedQuantity()).isEqualTo(10);
        assertThat(order.getOutboundStatus()).isEqualTo(TransferOutboundStatus.DISPATCHED);
        assertThat(order.getInboundStatus()).isEqualTo(TransferInboundStatus.IN_TRANSIT);
        ArgumentCaptor<InventoryPostingCommand> posting = ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(postingService).post(posting.capture());
        assertThat(posting.getValue().delta()).isEqualTo(-10);
        assertThat(posting.getValue().context().sourceType()).isEqualTo(SourceType.TRANSFER_OUT);
        verify(locationOccupancyService).refreshLocationStatus(40L);
    }

    @Test
    void receiptCreatesANewDestinationBatchBeforeTransferInPosting() {
        ReceiptFixture fixture = receiptFixture(5L, 10, 0);
        when(locationRepository.findByIdForInventoryPosting(50L)).thenReturn(Optional.of(destinationLocation));
        when(postingService.post(any())).thenAnswer(invocation ->
            postingResult(invocation.getArgument(0), 902L));

        service.receive(91L, new ReceiveTransferRequest(0L, "received",
                List.of(new ReceiveTransferRequest.Line(
                    110L, 50L, TransferReceiptDisposition.AVAILABLE, 4))),
            "receipt-key", 74L, "receiver");

        ArgumentCaptor<InventoryBatch> createdBatch = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(batchRepository).saveAndFlush(createdBatch.capture());
        InventoryBatch destinationBatch = createdBatch.getValue();
        assertThat(destinationBatch).isNotSameAs(fixture.sourceBatch());
        assertThat(destinationBatch.getQuantity()).isZero();
        assertThat(destinationBatch.getInitialQuantity()).isEqualTo(4);
        assertThat(destinationBatch.getLocation()).isSameAs(destinationLocation);
        assertThat(destinationBatch.getExternalBatchCode()).isEqualTo("LOT-62");
        assertThat(destinationBatch.getProductionDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(destinationBatch.getExpiryDate()).isEqualTo(LocalDate.of(2027, 1, 2));
        ArgumentCaptor<InventoryPostingCommand> posting = ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(postingService).post(posting.capture());
        assertThat(posting.getValue().inventoryBatchId()).isEqualTo(destinationBatch.getId());
        assertThat(posting.getValue().delta()).isEqualTo(4);
        assertThat(posting.getValue().context().sourceType()).isEqualTo(SourceType.TRANSFER_IN);
        verify(locationOccupancyService).validateCanStore(
            same(destinationLocation), same(sku), eq(destinationBatch.getBatchCode()));
        verify(locationOccupancyService).refreshLocationStatus(50L);
        assertThat(fixture.line().getReceivedQuantity()).isEqualTo(4);
        assertThat(fixture.dispatchLine().getReceivedQuantity()).isEqualTo(4);
        assertThat(fixture.order().getInboundStatus()).isEqualTo(TransferInboundStatus.PARTIAL_RECEIPT);
    }

    @Test
    void receiptRejectsAggregateOverReceiptBeforeCreatingInventory() {
        receiptFixture(6L, 5, 4);

        assertThatThrownBy(() -> service.receive(91L,
            new ReceiveTransferRequest(0L, "too much", List.of(
                new ReceiveTransferRequest.Line(110L, 50L, TransferReceiptDisposition.AVAILABLE, 1),
                new ReceiveTransferRequest.Line(110L, 50L, TransferReceiptDisposition.DAMAGED, 1))),
            "over-receipt-key", 74L, "receiver"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getParam("reason"))
                    .isEqualTo("TRANSFER_RECEIPT_EXCEEDS_DISPATCH"));

        verify(receiptRepository, never()).saveAndFlush(any());
        verify(postingService, never()).post(any());
        verify(locationRepository, never()).findByIdForInventoryPosting(anyLong());
    }

    @Test
    void successfulIdempotencyReplayReturnsTheExistingTaskWithoutDispatchingAgain() {
        TransferOrder order = order(7L, TransferBusinessStatus.APPROVED, 10);
        TransferWarehouseTask outbound = task(80L, order, sourceWarehouse,
            TransferTaskDirection.OUTBOUND, TransferTaskStatus.PENDING);
        order.addTask(outbound);
        IdempotencyRequest request = IdempotencyRequest.builder()
            .idempotencyKey("dispatch-replay").operation("TRANSFER_DISPATCH:80")
            .requestHash("same-hash").status(IdempotencyStatus.SUCCEEDED)
            .responseStatus(201).responseBody("80").build();
        when(idempotencyService.claim(eq("dispatch-replay"), eq("TRANSFER_DISPATCH:80"), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.REPLAY_SUCCEEDED, request));
        when(taskRepository.findById(80L)).thenReturn(Optional.of(outbound));

        var response = service.dispatch(80L,
            new DispatchTransferRequest(0L, null, null, null,
                List.of(new DispatchTransferRequest.Line(70L, 1))),
            "dispatch-replay", 73L, "dispatcher");

        assertThat(response.id()).isEqualTo(80L);
        verify(taskRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(postingService);
        verify(dispatchRepository, never()).saveAndFlush(any());
        verify(idempotencyService, never()).markSucceeded(any(), anyInt(), anyString());
    }

    @Test
    void shortageClosureRequiresExplicitConfirmationEvenOutsideControllerValidation() {
        assertThatThrownBy(() -> service.closeShortage(8L,
            new CloseTransferShortageRequest(0L, "missing parcel", false),
            "shortage-key", 75L, "supervisor"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getParam("reason"))
                    .isEqualTo("TRANSFER_SHORTAGE_CLOSE_NOT_CONFIRMED"));

        verifyNoInteractions(orderRepository, dispatchLineRepository);
        verify(idempotencyService, never()).claim(anyString(), anyString(), anyString());
    }

    @Test
    void outboundTaskDoesNotExposeDestinationLocationCandidates() {
        TransferOrder order = order(9L, TransferBusinessStatus.APPROVED, 1);
        TransferWarehouseTask outbound = task(92L, order, sourceWarehouse,
            TransferTaskDirection.OUTBOUND, TransferTaskStatus.PENDING);
        when(taskRepository.findById(92L)).thenReturn(Optional.of(outbound));

        assertThatThrownBy(() -> service.locations(92L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getParam("reason")).isEqualTo("TRANSFER_TASK_NOT_RECEIVABLE"));

        verifyNoInteractions(locationRepository);
    }

    @Test
    void inspectionEnabledReceiptCreatesInspectionAndDoesNotReleaseBackorderEarly() {
        ReceiptFixture fixture = receiptFixture(10L, 3, 0);
        fixture.order().setReceivingInspectionSnapshot(true);
        fixture.line().setBackorderLineId(880L);
        when(locationRepository.findByIdForInventoryPosting(50L)).thenReturn(Optional.of(destinationLocation));
        when(postingService.post(any())).thenAnswer(invocation ->
            postingResult(invocation.getArgument(0), 903L));

        service.receive(91L, new ReceiveTransferRequest(0L, "inspect first",
                List.of(new ReceiveTransferRequest.Line(
                    110L, 50L, TransferReceiptDisposition.AVAILABLE, 3))),
            "inspection-receipt-key", 74L, "receiver");

        ArgumentCaptor<TransferReceipt> receipt = ArgumentCaptor.forClass(TransferReceipt.class);
        verify(inspectionTaskFactory).createForTransferReceipt(receipt.capture());
        assertThat(receipt.getValue().getLines()).hasSize(1);
        assertThat(receipt.getValue().getLines().get(0).getDestinationBatch().getQualityStatus())
            .isEqualTo(InventoryQualityStatus.PENDING_INSPECTION);
        verifyNoInteractions(backorderService);
    }

    @Test
    void inspectionDisabledReceiptReleasesOnlyTheLinkedDestinationBackorder() {
        ReceiptFixture fixture = receiptFixture(11L, 2, 0);
        fixture.line().setBackorderLineId(881L);
        when(locationRepository.findByIdForInventoryPosting(50L)).thenReturn(Optional.of(destinationLocation));
        when(postingService.post(any())).thenAnswer(invocation ->
            postingResult(invocation.getArgument(0), 904L));

        service.receive(91L, new ReceiveTransferRequest(0L, "ready",
                List.of(new ReceiveTransferRequest.Line(
                    110L, 50L, TransferReceiptDisposition.AVAILABLE, 2))),
            "direct-receipt-key", 74L, "receiver");

        verify(backorderService).wakeBackorderAtWarehouse(881L, 20L);
        verifyNoInteractions(inspectionTaskFactory);
    }

    private ReceiptFixture receiptFixture(Long orderId, int dispatchedQuantity, int receivedQuantity) {
        TransferOrder order = order(orderId, TransferBusinessStatus.APPROVED, dispatchedQuantity);
        TransferOrderLine line = order.getLines().get(0);
        line.setDispatchedQuantity(dispatchedQuantity);
        line.setReceivedQuantity(receivedQuantity);
        InventoryBatch sourceBatch = sourceBatch(62L, 0, 0);
        TransferWarehouseTask inbound = task(91L, order, destinationWarehouse,
            TransferTaskDirection.INBOUND, TransferTaskStatus.PENDING);
        order.addTask(inbound);
        TransferDispatch dispatch = TransferDispatch.builder().id(100L).transferOrder(order)
            .task(inbound).dispatchNumber("TRD-100").build();
        TransferDispatchLine dispatchLine = TransferDispatchLine.builder().id(110L)
            .dispatch(dispatch).transferLine(line).sourceBatch(sourceBatch)
            .sourceBatchCode(sourceBatch.getBatchCode()).externalBatchCode(sourceBatch.getExternalBatchCode())
            .productionDate(sourceBatch.getProductionDate()).expiryDate(sourceBatch.getExpiryDate())
            .quantity(dispatchedQuantity).receivedQuantity(receivedQuantity).build();
        dispatch.addLine(dispatchLine);
        when(taskRepository.findTransferOrderIdById(91L)).thenReturn(Optional.of(orderId));
        when(taskRepository.findByIdForUpdate(91L)).thenReturn(Optional.of(inbound));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(dispatchLineRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(dispatchLine));
        return new ReceiptFixture(order, line, sourceBatch, dispatchLine);
    }

    private TransferOrder order(Long id, TransferBusinessStatus status, int requestedQuantity) {
        TransferOrder order = TransferOrder.builder().id(id).version(0L).transferNumber("TR-" + id)
            .sourceWarehouse(sourceWarehouse).destinationWarehouse(destinationWarehouse)
            .businessStatus(status).outboundStatus(status == TransferBusinessStatus.DRAFT
                ? TransferOutboundStatus.NOT_RELEASED : TransferOutboundStatus.PENDING)
            .inboundStatus(TransferInboundStatus.NOT_IN_TRANSIT)
            .approvalModeSnapshot(TransferApprovalMode.DIRECT).receivingInspectionSnapshot(false)
            .reason("rebalance").requestedBy(71L).requestedByName("buyer").build();
        order.addLine(TransferOrderLine.builder().id(id * 10).version(0L).productSku(sku)
            .requestedQuantity(requestedQuantity).build());
        return order;
    }

    private InventoryBatch sourceBatch(Long id, int quantity, int reservedQuantity) {
        return InventoryBatch.builder().id(id).batchCode("BATCH-" + id)
            .externalBatchCode("LOT-" + id).productSku(sku).location(sourceLocation)
            .locationCode(sourceLocation.getLocationCode()).quantity(quantity)
            .reservedQuantity(reservedQuantity).initialQuantity(Math.max(quantity, 1))
            .productionDate(LocalDate.of(2026, 1, 2)).expiryDate(LocalDate.of(2027, 1, 2))
            .qualityStatus(InventoryQualityStatus.AVAILABLE).active(true).build();
    }

    private TransferWarehouseTask task(Long id, TransferOrder order, Warehouse warehouse,
                                       TransferTaskDirection direction, TransferTaskStatus status) {
        return TransferWarehouseTask.builder().id(id).version(0L).transferOrder(order)
            .warehouse(warehouse).taskNumber("TASK-" + id).direction(direction).status(status).build();
    }

    private Warehouse warehouse(Long id, String code, String name) {
        return Warehouse.builder().id(id).code(code).name(name).isActive(true).build();
    }

    private Location location(Long id, Warehouse warehouse, String code) {
        return Location.builder().id(id).warehouse(warehouse).warehouseCode(warehouse.getCode())
            .zone(Zone.ZONE_A).shelfNumber("A-01").positionNumber("001")
            .locationCode(code).enabled(true).build();
    }

    private IdempotencyClaim acquired(String key, String operation, String hash) {
        return new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED,
            IdempotencyRequest.builder().idempotencyKey(key).operation(operation).requestHash(hash)
                .status(IdempotencyStatus.PROCESSING).build());
    }

    private InventoryPostingResult postingResult(InventoryPostingCommand command, Long transactionId) {
        int before = command.delta() < 0 ? -command.delta() : 0;
        return new InventoryPostingResult(command.inventoryBatchId(), "POSTED", command.productSkuId(),
            command.locationId(), command.context().warehouseId(), command.delta(), before,
            before + command.delta(), true, transactionId, command.context().postingId(),
            command.context().idempotencyKey());
    }

    private record ReceiptFixture(TransferOrder order, TransferOrderLine line,
                                  InventoryBatch sourceBatch, TransferDispatchLine dispatchLine) { }
}
