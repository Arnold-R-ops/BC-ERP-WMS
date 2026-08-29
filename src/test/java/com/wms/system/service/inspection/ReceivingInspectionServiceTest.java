package com.wms.system.service.inspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.receivinginspection.SubmitReceivingInspectionRequest;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.*;
import com.wms.system.repository.*;
import com.wms.system.service.*;
import com.wms.system.service.idempotency.*;
import com.wms.system.service.inventory.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceivingInspectionServiceTest {
    @Mock ReceivingInspectionTaskRepository taskRepository;
    @Mock ReceivingInspectionTaskLineRepository taskLineRepository;
    @Mock ReceivingInspectionResultRepository resultRepository;
    @Mock ReceivingInspectionResultLineRepository resultLineRepository;
    @Mock InventoryBatchRepository batchRepository;
    @Mock InventoryQualityStateService stateService;
    @Mock InventoryQualityActionService actionService;
    @Mock ReceivingInspectionPolicyService policyService;
    @Mock IdempotencyService idempotencyService;
    @Mock BackorderService backorderService;
    ReceivingInspectionService service;

    @BeforeEach
    void setUp() {
        service = new ReceivingInspectionService(
            taskRepository, taskLineRepository, resultRepository, resultLineRepository,
            batchRepository, stateService, actionService, policyService,
            idempotencyService, backorderService, new ObjectMapper());
    }

    @Test
    void partialDecisionSplitsPendingStockWithoutChangingOnHand() {
        Fixture fixture = fixture(ReceivingInspectionReviewMode.DIRECT);
        IdempotencyRequest request = IdempotencyRequest.builder().id(99L)
            .idempotencyKey("inspect-key").operation("op")
            .status(IdempotencyStatus.PROCESSING).requestHash("hash").build();
        when(idempotencyService.claim(eq("inspect-key"),
            eq(ReceivingInspectionService.SUBMIT_OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, request));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fixture.task));
        when(taskLineRepository.findAllByIdForUpdate(Set.of(20L)))
            .thenReturn(List.of(fixture.line));
        when(batchRepository.findByIdForPosting(30L))
            .thenReturn(Optional.of(fixture.batch));
        when(batchRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InventoryBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) batch.setId(31L);
            return batch;
        });
        final ReceivingInspectionResult[] saved = new ReceivingInspectionResult[1];
        when(resultRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ReceivingInspectionResult result = invocation.getArgument(0);
            if (result.getId() == null) result.setId(40L);
            saved[0] = result;
            return result;
        });
        when(resultRepository.findById(40L)).thenAnswer(invocation -> Optional.of(saved[0]));
        when(taskLineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit(10L, new SubmitReceivingInspectionRequest(
            ReceivingInspectionMethod.FULL, null, 1, "INSPECTION_PARTIAL", "one defect",
            List.of(new SubmitReceivingInspectionRequest.Line(20L, 6, 4))),
            "inspect-key", 7L, "inspector");

        assertThat(fixture.batch.getQuantity()).isEqualTo(6);
        assertThat(fixture.line.getPassedQuantity()).isEqualTo(6);
        assertThat(fixture.line.getQuarantinedQuantity()).isEqualTo(4);
        assertThat(fixture.task.getStatus()).isEqualTo(ReceivingInspectionTaskStatus.COMPLETED);
        assertThat(response.lines()).singleElement().satisfies(line -> {
            assertThat(line.passedQuantity()).isEqualTo(6);
            assertThat(line.quarantinedQuantity()).isEqualTo(4);
        });
        verify(stateService).transition(argThat(command ->
            command.inventoryBatchId().equals(31L)
                && command.targetStatus() == InventoryQualityStatus.QUARANTINED));
        verify(stateService).transition(argThat(command ->
            command.inventoryBatchId().equals(30L)
                && command.targetStatus() == InventoryQualityStatus.AVAILABLE));
        verify(backorderService).wakeProduct(50L);
    }

    @Test
    void approvalReleasesPendingReviewStockAndCompletesTask() {
        Fixture fixture = fixture(ReceivingInspectionReviewMode.REVIEW_REQUIRED);
        fixture.line.setPendingQuantity(0);
        fixture.line.setPendingReviewQuantity(10);
        ReceivingInspectionResult result = ReceivingInspectionResult.builder()
            .id(40L).resultNumber("IQR-40").inspectionTask(fixture.task)
            .status(ReceivingInspectionResultStatus.PENDING_REVIEW)
            .inspectionMethod(ReceivingInspectionMethod.FULL).defectQuantity(0)
            .reasonCode("INSPECTION_PASSED").submittedBy(7L)
            .submittedByName("inspector").build();
        ReceivingInspectionResultLine resultLine = ReceivingInspectionResultLine.builder()
            .id(41L).inspectionTaskLine(fixture.line).sourceBatch(fixture.batch)
            .passedQuantity(10).quarantinedQuantity(0).build();
        result.addLine(resultLine);
        IdempotencyRequest request = IdempotencyRequest.builder().id(100L)
            .idempotencyKey("review-key").operation("review")
            .status(IdempotencyStatus.PROCESSING).requestHash("hash").build();
        when(idempotencyService.claim(eq("review-key"), anyString(), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, request));
        when(resultRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(result));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(fixture.task));
        when(batchRepository.findByIdForPosting(30L)).thenReturn(Optional.of(fixture.batch));
        when(taskLineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resultLineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resultRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approve(40L, "approved after review", "review-key", 8L, "reviewer");

        assertThat(response.status()).isEqualTo(ReceivingInspectionResultStatus.APPROVED);
        assertThat(fixture.line.getPendingReviewQuantity()).isZero();
        assertThat(fixture.line.getPassedQuantity()).isEqualTo(10);
        assertThat(fixture.task.getStatus()).isEqualTo(ReceivingInspectionTaskStatus.COMPLETED);
        verify(stateService).transition(argThat(command ->
            command.inventoryBatchId().equals(30L)
                && command.targetStatus() == InventoryQualityStatus.AVAILABLE));
        verify(backorderService).wakeProduct(50L);
    }

    private Fixture fixture(ReceivingInspectionReviewMode mode) {
        Warehouse warehouse = Warehouse.builder().id(1L).code("W1").name("Warehouse").build();
        Location location = Location.builder().id(2L).locationCode("L1").warehouse(warehouse).build();
        ProductSku sku = ProductSku.builder().id(50L).skuCode("SKU-50").name("Product").build();
        InventoryBatch batch = InventoryBatch.builder().id(30L).batchCode("B-30")
            .productSku(sku).location(location).locationCode("L1").quantity(10)
            .reservedQuantity(0).initialQuantity(10)
            .qualityStatus(InventoryQualityStatus.PENDING_INSPECTION).active(true).build();
        ReceivingInspectionTask task = ReceivingInspectionTask.builder().id(10L)
            .taskNumber("IQT-10").sourceType(ReceivingInspectionSourceType.RECEIPT)
            .warehouse(warehouse).sourceReference("PO-1")
            .status(ReceivingInspectionTaskStatus.PENDING).reviewModeSnapshot(mode)
            .createdBy(1L).createdByName("receiver").build();
        ReceivingInspectionTaskLine line = ReceivingInspectionTaskLine.builder().id(20L)
            .inspectionTask(task).productSku(sku).sourceBatch(batch).location(location)
            .skuCode("SKU-50").productName("Product").batchCode("B-30")
            .locationCode("L1").plannedQuantity(10).pendingQuantity(10).build();
        task.getLines().add(line);
        return new Fixture(task, line, batch);
    }

    private record Fixture(
        ReceivingInspectionTask task,
        ReceivingInspectionTaskLine line,
        InventoryBatch batch
    ) {
    }
}
