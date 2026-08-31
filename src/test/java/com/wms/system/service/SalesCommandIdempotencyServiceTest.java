package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesCommandIdempotencyServiceTest {
    private static final String KEY = "sales-command-1";
    private static final String OPERATION = "SALES_ORDER_CREATE";

    @Mock private IdempotencyService idempotencyService;
    @Mock private SalesCommandExecutor executor;

    private SalesCommandIdempotencyService service;
    private IdempotencyRequest record;

    @BeforeEach
    void setUp() {
        service = new SalesCommandIdempotencyService(idempotencyService, executor, new ObjectMapper());
        record = IdempotencyRequest.builder()
            .id(1L)
            .idempotencyKey(KEY)
            .operation(OPERATION)
            .status(IdempotencyStatus.PROCESSING)
            .build();
    }

    @Test
    void successfulReplayNeverRunsBusinessActionAgain() {
        when(idempotencyService.claim(eq(KEY), eq(OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.REPLAY_SUCCEEDED, record));
        when(executor.replay(KEY, OPERATION, String.class)).thenReturn("persisted");
        AtomicInteger calls = new AtomicInteger();

        String result = service.execute(KEY, OPERATION, Map.of("id", 1), 7L, "actor",
            String.class, () -> { calls.incrementAndGet(); return "new"; });

        assertThat(result).isEqualTo("persisted");
        assertThat(calls).hasValue(0);
        verify(executor, never()).execute(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void acquiredCommandCommitsDurableResultBeforeMarkingGenericClaimSuccessful() {
        when(idempotencyService.claim(eq(KEY), eq(OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, record));
        when(executor.hasResult(KEY, OPERATION)).thenReturn(false);
        when(executor.execute(eq(KEY), eq(OPERATION), eq(7L), eq("actor"), any()))
            .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());

        String result = service.execute(KEY, OPERATION, Map.of("id", 1), 7L, "actor",
            String.class, () -> "created");

        assertThat(result).isEqualTo("created");
        verify(idempotencyService).markSucceeded(record, 200, "SALES_COMMAND_SUCCEEDED");
    }

    @Test
    void unexpectedFailureIsTerminalInsteadOfLeavingAProcessingLease() {
        when(idempotencyService.claim(eq(KEY), eq(OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, record));
        when(executor.hasResult(KEY, OPERATION)).thenReturn(false);
        when(executor.execute(eq(KEY), eq(OPERATION), eq(7L), eq("actor"), any()))
            .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.execute(KEY, OPERATION, Map.of("id", 1), 7L, "actor",
            String.class, () -> "created"))
            .isInstanceOf(IllegalStateException.class);

        verify(idempotencyService).markFailed(record, 500, ErrorKeys.INTERNAL_SERVER_ERROR);
    }

    @Test
    void businessFailureIsReplayableWithItsDomainErrorKey() {
        when(idempotencyService.claim(eq(KEY), eq(OPERATION), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, record));
        when(executor.hasResult(KEY, OPERATION)).thenReturn(false);
        when(executor.execute(eq(KEY), eq(OPERATION), eq(7L), eq("actor"), any()))
            .thenThrow(new BusinessException(ErrorKeys.SALES_ORDER_INVALID_STATUS, Map.of()));

        assertThatThrownBy(() -> service.execute(KEY, OPERATION, Map.of("id", 1), 7L, "actor",
            String.class, () -> "created"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SALES_ORDER_INVALID_STATUS);

        verify(idempotencyService).markFailed(record, 409, ErrorKeys.SALES_ORDER_INVALID_STATUS);
    }
}
