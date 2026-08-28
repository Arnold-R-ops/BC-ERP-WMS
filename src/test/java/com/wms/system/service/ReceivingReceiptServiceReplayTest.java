package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivingReceiptServiceReplayTest {

    @Test
    void successfulIdempotencyReplayReturnsStoredReceiptWithoutPostingAgain() {
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        ReceivingReceiptExecutor executor = mock(ReceivingReceiptExecutor.class);
        ReceivingReceiptService service = new ReceivingReceiptService(
            idempotencyService,
            executor,
            new ObjectMapper().registerModule(new JavaTimeModule())
        );
        IdempotencyRequest record = IdempotencyRequest.builder()
            .id(91L)
            .idempotencyKey("other-inbound-receipt-1")
            .operation("RECEIVING_TASK_RECEIPT")
            .requestHash("hash")
            .status(IdempotencyStatus.SUCCEEDED)
            .build();
        record.setCompanyId(1L);
        when(idempotencyService.claim(
            eq("other-inbound-receipt-1"),
            eq("RECEIVING_TASK_RECEIPT"),
            anyString()
        )).thenReturn(new IdempotencyClaim(
            IdempotencyClaimDecision.REPLAY_SUCCEEDED, record));

        ReceiptEventResponse stored = new ReceiptEventResponse(
            31L,
            "RCE-31",
            7L,
            "RCV-7",
            LocalDateTime.of(2026, 8, 28, 9, 0),
            "server-receiver",
            "received by camera scan",
            List.of()
        );
        when(executor.replay("other-inbound-receipt-1")).thenReturn(stored);
        SubmitReceivingReceiptRequest request = new SubmitReceivingReceiptRequest(
            "received by camera scan",
            List.of(new SubmitReceivingReceiptRequest.Line(
                17L, 21L, 4, null, null, "EXT-LOT-1"))
        );

        ReceiptEventResponse replayed = service.receive(
            7L, "other-inbound-receipt-1", request, 503L, "server-receiver");

        assertThat(replayed).isSameAs(stored);
        verify(executor).replay("other-inbound-receipt-1");
        verify(executor, never()).execute(
            anyLong(), anyString(), any(), anyLong(), anyString());
        verify(idempotencyService, never()).markSucceeded(any(), anyInt(), anyString());
        verify(idempotencyService, never()).markFailed(any(), anyInt(), anyString());
    }
}
