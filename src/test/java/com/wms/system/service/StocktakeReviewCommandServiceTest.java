package com.wms.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.stocktake.ReviewStocktakeRequest;
import com.wms.system.dto.stocktake.StocktakeTaskResponse;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StocktakeReviewCommandServiceTest {

    @Mock private StocktakeReviewExecutor executor;
    @Mock private IdempotencyService idempotencyService;

    private StocktakeReviewCommandService service;

    @BeforeEach
    void setUp() {
        service = new StocktakeReviewCommandService(
            executor,
            idempotencyService,
            new ObjectMapper()
        );
    }

    @Test
    void successfulRetryReplaysWithoutExecutingStocktakeTwice() throws Exception {
        ReviewStocktakeRequest request = ReviewStocktakeRequest.builder()
            .approved(true)
            .comment("Approved")
            .build();
        StocktakeTaskResponse response = StocktakeTaskResponse.builder()
            .id(1L)
            .taskNo("TK-1")
            .status("COMPLETED")
            .build();
        IdempotencyRequest record = record("review-key-1");
        IdempotencyClaim acquired = new IdempotencyClaim(
            IdempotencyClaimDecision.ACQUIRED, record);
        IdempotencyClaim replay = new IdempotencyClaim(
            IdempotencyClaimDecision.REPLAY_SUCCEEDED, record);
        when(idempotencyService.claim(
            eq("review-key-1"),
            eq(StocktakeReviewCommandService.REVIEW_OPERATION),
            anyString()
        )).thenReturn(acquired, replay);
        when(executor.executeAtomic(
            same(record), eq(1L), same(request), eq(7L), eq("Reviewer"), eq("review-key-1")
        )).thenReturn(response);

        StocktakeTaskResponse first = service.review(
            1L, request, 7L, "Reviewer", " review-key-1 ");
        record.setStatus(IdempotencyStatus.SUCCEEDED);
        record.setResponseBody(new ObjectMapper().writeValueAsString(response));
        StocktakeTaskResponse second = service.review(
            1L, request, 7L, "Reviewer", "review-key-1");

        assertThat(first).isSameAs(response);
        assertThat(second).usingRecursiveComparison().isEqualTo(response);
        verify(executor, times(1)).executeAtomic(
            same(record), eq(1L), same(request), eq(7L), eq("Reviewer"), eq("review-key-1"));
        ArgumentCaptor<String> intent = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService, times(2)).claim(
            eq("review-key-1"),
            eq(StocktakeReviewCommandService.REVIEW_OPERATION),
            intent.capture()
        );
        assertThat(intent.getAllValues()).allSatisfy(value -> {
            assertThat(value).contains("\"taskId\":1");
            assertThat(value).contains("\"approved\":true");
            assertThat(value).contains("\"reviewedBy\":7");
        });
    }

    @Test
    void deterministicFailureIsRememberedForReplay() {
        ReviewStocktakeRequest request = ReviewStocktakeRequest.builder()
            .approved(true)
            .build();
        IdempotencyRequest record = record("review-key-2");
        when(idempotencyService.claim(anyString(), anyString(), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, record));
        BusinessException failure = new BusinessException(
            ErrorKeys.STOCKTAKE_TASK_CANNOT_REVIEW);
        when(executor.executeAtomic(any(), anyLong(), any(), anyLong(), anyString(), anyString()))
            .thenThrow(failure);

        assertThatThrownBy(() -> service.review(
            1L, request, 7L, "Reviewer", "review-key-2"))
            .isSameAs(failure);

        verify(idempotencyService).markFailed(
            same(record), eq(409), eq(ErrorKeys.STOCKTAKE_TASK_CANNOT_REVIEW));
    }

    @Test
    void inProgressClaimDoesNotEnterInventoryTransaction() {
        ReviewStocktakeRequest request = ReviewStocktakeRequest.builder()
            .approved(false)
            .build();
        IdempotencyRequest record = record("review-key-3");
        when(idempotencyService.claim(anyString(), anyString(), anyString()))
            .thenReturn(new IdempotencyClaim(IdempotencyClaimDecision.IN_PROGRESS, record));

        assertThatThrownBy(() -> service.review(
            1L, request, 7L, "Reviewer", "review-key-3"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS);

        verifyNoInteractions(executor);
    }

    private IdempotencyRequest record(String key) {
        return IdempotencyRequest.builder()
            .id(10L)
            .idempotencyKey(key)
            .operation(StocktakeReviewCommandService.REVIEW_OPERATION)
            .requestHash("hash")
            .status(IdempotencyStatus.PROCESSING)
            .leaseToken("lease")
            .build();
    }
}
