package com.wms.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.stocktake.ReviewStocktakeRequest;
import com.wms.system.dto.stocktake.StocktakeTaskResponse;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Required command-level idempotency boundary for stocktake review. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeReviewCommandService {

    static final String REVIEW_OPERATION = "STOCKTAKE_REVIEW";

    private final StocktakeReviewExecutor executor;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Intentionally has no surrounding transaction. The claim commits first;
     * the executor then commits inventory, task state and SUCCEEDED together.
     */
    public StocktakeTaskResponse review(
        Long taskId,
        ReviewStocktakeRequest request,
        Long reviewedBy,
        String reviewedByName,
        String idempotencyKey
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        IdempotencyClaim claim = idempotencyService.claim(
            normalizedKey,
            REVIEW_OPERATION,
            serializeIntent(taskId, request, reviewedBy)
        );

        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return replaySucceeded(claim.request(), taskId);
        }
        if (claim.decision() == IdempotencyClaimDecision.IN_PROGRESS) {
            throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", REVIEW_OPERATION)
            );
        }
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_FAILED) {
            throw replayFailure(claim.request());
        }

        try {
            return executor.executeAtomic(
                claim.request(),
                taskId,
                request,
                reviewedBy,
                reviewedByName,
                normalizedKey
            );
        } catch (BusinessException exception) {
            rememberFailure(claim.request(), 409, exception.getErrorKey());
            throw exception;
        } catch (RuntimeException exception) {
            rememberFailure(claim.request(), 500, ErrorKeys.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private StocktakeTaskResponse replaySucceeded(IdempotencyRequest request, Long taskId) {
        try {
            StocktakeTaskResponse response = objectMapper.readValue(
                request.getResponseBody(),
                StocktakeTaskResponse.class
            );
            if (!taskId.equals(response.getId())) {
                throw new BusinessException(
                    ErrorKeys.IDEMPOTENCY_CONFLICT,
                    Map.of("operation", REVIEW_OPERATION, "reason", "RESULT_TASK_MISMATCH")
                );
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", REVIEW_OPERATION, "reason", "RESULT_DESERIALIZATION_FAILED")
            );
        }
    }

    private BusinessException replayFailure(IdempotencyRequest request) {
        String errorKey = request.getResponseBody();
        return new BusinessException(
            errorKey == null || errorKey.isBlank() ? ErrorKeys.IDEMPOTENCY_CONFLICT : errorKey,
            Map.of("operation", REVIEW_OPERATION, "reason", "PREVIOUS_ATTEMPT_FAILED")
        );
    }

    private void rememberFailure(IdempotencyRequest request, int status, String responseBody) {
        try {
            idempotencyService.markFailed(request, status, responseBody);
        } catch (RuntimeException completionFailure) {
            log.warn(
                "Unable to persist failed stocktake review idempotency result for key {}",
                request.getIdempotencyKey(),
                completionFailure
            );
        }
    }

    private String serializeIntent(
        Long taskId,
        ReviewStocktakeRequest request,
        Long reviewedBy
    ) {
        LinkedHashMap<String, Object> intent = new LinkedHashMap<>();
        intent.put("taskId", taskId);
        intent.put("approved", request.getApproved());
        intent.put("comment", request.getComment());
        intent.put("reviewedBy", reviewedBy);
        try {
            return objectMapper.writeValueAsString(intent);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                ErrorKeys.INTERNAL_SERVER_ERROR,
                Map.of("message", "Unable to serialize stocktake review intent")
            );
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_KEY_REQUIRED);
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "Idempotency-Key", "maxLength", 120)
            );
        }
        return normalized;
    }
}
