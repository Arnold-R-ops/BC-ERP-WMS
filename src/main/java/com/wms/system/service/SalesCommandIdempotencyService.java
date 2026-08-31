package com.wms.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

/** Required company-scoped idempotency facade for sales write commands. */
@Service
@RequiredArgsConstructor
public class SalesCommandIdempotencyService {
    private final IdempotencyService idempotencyService;
    private final SalesCommandExecutor executor;
    private final ObjectMapper objectMapper;

    public <T> T execute(
        String idempotencyKey,
        String operation,
        Object intent,
        Long actorId,
        String actorName,
        Class<T> responseType,
        Supplier<T> action
    ) {
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, operation,
            canonicalIntent(actorId, intent));
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return executor.replay(idempotencyKey, operation, responseType);
        }
        if (claim.decision() == IdempotencyClaimDecision.IN_PROGRESS) {
            throw new BusinessException(ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", operation));
        }
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_FAILED) {
            throw replayFailure(claim.request(), operation);
        }

        // Covers a process crash after the business transaction committed but
        // before the generic idempotency row was marked successful.
        if (executor.hasResult(idempotencyKey, operation)) {
            idempotencyService.markSucceeded(claim.request(), 200, "SALES_COMMAND_REPLAY");
            return executor.replay(idempotencyKey, operation, responseType);
        }

        try {
            T response = executor.execute(idempotencyKey, operation, actorId, actorName, action);
            idempotencyService.markSucceeded(claim.request(), 200, "SALES_COMMAND_SUCCEEDED");
            return response;
        } catch (BusinessException exception) {
            idempotencyService.markFailed(claim.request(), 409, exception.getErrorKey());
            throw exception;
        } catch (RuntimeException exception) {
            idempotencyService.markFailed(claim.request(), 500, ErrorKeys.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private String canonicalIntent(Long actorId, Object intent) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "actorId", actorId,
                "intent", intent == null ? Map.of() : intent
            ));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorKeys.INTERNAL_SERVER_ERROR,
                Map.of("message", "Unable to serialize sales command intent"));
        }
    }

    private BusinessException replayFailure(IdempotencyRequest request, String operation) {
        String errorKey = request.getResponseBody();
        return new BusinessException(errorKey == null || errorKey.isBlank()
            ? ErrorKeys.IDEMPOTENCY_CONFLICT : errorKey,
            Map.of("operation", operation, "reason", "PREVIOUS_ATTEMPT_FAILED"));
    }
}
