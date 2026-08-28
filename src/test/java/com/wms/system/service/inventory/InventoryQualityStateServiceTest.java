package com.wms.system.service.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryStatusEvent;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.entity.enums.InventoryQualityStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryStatusEventRepository;
import com.wms.system.service.DomainOutboxService;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryQualityStateServiceTest {

    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryStatusEventRepository eventRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private DomainOutboxService outboxService;

    private InventoryQualityStateService service;
    private IdempotencyRequest claimRecord;

    @BeforeEach
    void setUp() {
        service = new InventoryQualityStateService(
            batchRepository,
            eventRepository,
            idempotencyService,
            outboxService,
            new ObjectMapper()
        );
        claimRecord = IdempotencyRequest.builder()
            .id(50L)
            .idempotencyKey("quality-key")
            .operation(InventoryQualityStateService.OPERATION)
            .requestHash("hash")
            .status(IdempotencyStatus.PROCESSING)
            .build();
    }

    @Test
    void transitionPersistsEventOutboxAndSupportsSafeReplay() {
        InventoryBatch batch = batch(0);
        when(idempotencyService.claim(eq("quality-key"), eq(InventoryQualityStateService.OPERATION), anyString()))
            .thenReturn(
                new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, claimRecord),
                new IdempotencyClaim(IdempotencyClaimDecision.REPLAY_SUCCEEDED, claimRecord)
            );
        when(batchRepository.findByIdForPosting(7L)).thenReturn(Optional.of(batch));
        when(eventRepository.saveAndFlush(any(InventoryStatusEvent.class))).thenAnswer(invocation -> {
            InventoryStatusEvent event = invocation.getArgument(0);
            java.lang.reflect.Field id = InventoryStatusEvent.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(event, 99L);
            return event;
        });
        doAnswer(invocation -> {
            claimRecord.setResponseBody(invocation.getArgument(2));
            claimRecord.setStatus(IdempotencyStatus.SUCCEEDED);
            return null;
        }).when(idempotencyService).markSucceeded(eq(claimRecord), eq(200), anyString());

        InventoryQualityTransitionCommand command = command(InventoryQualityStatus.QUARANTINED);
        InventoryQualityTransitionResult first = service.transition(command);

        ArgumentCaptor<InventoryStatusEvent> eventCaptor = ArgumentCaptor.forClass(InventoryStatusEvent.class);
        verify(eventRepository).saveAndFlush(eventCaptor.capture());
        InventoryStatusEvent event = eventCaptor.getValue();
        when(eventRepository.findByIdempotencyKey("quality-key")).thenReturn(Optional.of(event));

        InventoryQualityTransitionResult replay = service.transition(command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.eventId()).isEqualTo(99L);
        assertThat(batch.getQualityStatus()).isEqualTo(InventoryQualityStatus.QUARANTINED);
        assertThat(event.getFromStatus()).isEqualTo(InventoryQualityStatus.AVAILABLE);
        assertThat(event.getToStatus()).isEqualTo(InventoryQualityStatus.QUARANTINED);
        assertThat(event.getSourceType()).isEqualTo("INSPECTION");
        assertThat(event.getRequestFingerprint()).hasSize(64);
        verify(outboxService).append(
            eq(InventoryQualityStateService.OUTBOX_EVENT),
            eq("InventoryBatch"),
            eq(7L),
            anyMap()
        );
        verify(batchRepository, times(1)).findByIdForPosting(7L);
    }

    @Test
    void reservedAvailableStockCannotBecomeNonSaleable() {
        InventoryBatch batch = batch(3);
        when(idempotencyService.claim(anyString(), anyString(), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, claimRecord));
        when(batchRepository.findByIdForPosting(7L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.transition(command(InventoryQualityStatus.RECALL_HOLD)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_QUALITY_RESERVED_CONFLICT));

        assertThat(batch.getQualityStatus()).isEqualTo(InventoryQualityStatus.AVAILABLE);
        verify(eventRepository, never()).saveAndFlush(any());
        verify(outboxService, never()).append(anyString(), anyString(), any(), any());
    }

    @Test
    void noOpTransitionIsRejectedWithoutCreatingEvidence() {
        InventoryBatch batch = batch(0);
        when(idempotencyService.claim(anyString(), anyString(), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, claimRecord));
        when(batchRepository.findByIdForPosting(7L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.transition(command(InventoryQualityStatus.AVAILABLE)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_QUALITY_TRANSITION_INVALID));

        verify(eventRepository, never()).saveAndFlush(any());
        verify(outboxService, never()).append(anyString(), anyString(), any(), any());
    }

    @Test
    void dateExpiredStockCannotBeRestoredToAvailable() {
        InventoryBatch batch = batch(0);
        batch.setQualityStatus(InventoryQualityStatus.QUARANTINED);
        batch.setExpiryDate(LocalDate.now().minusDays(1));
        when(idempotencyService.claim(anyString(), anyString(), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, claimRecord));
        when(batchRepository.findByIdForPosting(7L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.transition(command(InventoryQualityStatus.AVAILABLE)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_QUALITY_TRANSITION_INVALID));

        assertThat(batch.getQualityStatus()).isEqualTo(InventoryQualityStatus.QUARANTINED);
        verify(eventRepository, never()).saveAndFlush(any());
        verify(outboxService, never()).append(anyString(), anyString(), any(), any());
    }

    private InventoryBatch batch(int reserved) {
        return InventoryBatch.builder()
            .id(7L)
            .batchCode("QUALITY-7")
            .quantity(10)
            .reservedQuantity(reserved)
            .initialQuantity(10)
            .active(true)
            .qualityStatus(InventoryQualityStatus.AVAILABLE)
            .expiryDate(LocalDate.now().plusDays(30))
            .build();
    }

    private InventoryQualityTransitionCommand command(InventoryQualityStatus target) {
        return new InventoryQualityTransitionCommand(
            7L,
            target,
            "INSPECTION",
            "INS-100",
            "FAILED_CHECK",
            "Seal damaged",
            21L,
            "Warehouse operator",
            "quality-key"
        );
    }
}
