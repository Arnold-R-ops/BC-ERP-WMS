package com.wms.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.receiving.ReceiptEventResponse;
import com.wms.system.dto.receiving.SubmitReceivingReceiptRequest;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.service.idempotency.IdempotencyClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Non-transactional idempotency facade around the atomic receipt executor. */
@Service
@RequiredArgsConstructor
public class ReceivingReceiptService {

    private static final String OPERATION = "RECEIVING_TASK_RECEIPT";

    private final IdempotencyService idempotencyService;
    private final ReceivingReceiptExecutor executor;
    private final ObjectMapper objectMapper;

    public ReceiptEventResponse receive(
        Long taskId,
        String idempotencyKey,
        SubmitReceivingReceiptRequest request,
        Long actorId,
        String actorName
    ) {
        IdempotencyClaim claim = idempotencyService.claim(
            idempotencyKey, OPERATION, canonicalIntent(taskId, request));
        return switch (claim.decision()) {
            case REPLAY_SUCCEEDED -> executor.replay(idempotencyKey);
            case IN_PROGRESS -> throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", OPERATION)
            );
            case REPLAY_FAILED -> throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", OPERATION, "reason", "PREVIOUS_ATTEMPT_FAILED")
            );
            case ACQUIRED -> executeAndComplete(
                claim, taskId, idempotencyKey, request, actorId, actorName);
        };
    }

    private ReceiptEventResponse executeAndComplete(
        IdempotencyClaim claim,
        Long taskId,
        String idempotencyKey,
        SubmitReceivingReceiptRequest request,
        Long actorId,
        String actorName
    ) {
        try {
            ReceiptEventResponse response = executor.execute(
                taskId, idempotencyKey, request, actorId, actorName);
            idempotencyService.markSucceeded(
                claim.request(), 200, "receiptEventId=" + response.id());
            return response;
        } catch (BusinessException exception) {
            idempotencyService.markFailed(
                claim.request(), 409, exception.getErrorKey());
            throw exception;
        }
    }

    private String canonicalIntent(Long taskId, SubmitReceivingReceiptRequest request) {
        List<CanonicalLine> lines = request.lines().stream()
            .map(line -> new CanonicalLine(
                line.receivingTaskItemId(), line.locationId(), line.quantity(),
                line.productionDate() == null ? null : line.productionDate().toString(),
                line.expiryDate() == null ? null : line.expiryDate().toString(),
                normalize(line.externalBatchCode())
            ))
            .sorted(Comparator.comparing(CanonicalLine::taskItemId)
                .thenComparing(CanonicalLine::locationId)
                .thenComparing(line -> String.valueOf(line.productionDate()))
                .thenComparing(line -> String.valueOf(line.expiryDate()))
                .thenComparing(line -> String.valueOf(line.externalBatchCode()))
                .thenComparing(CanonicalLine::quantity))
            .toList();
        try {
            return objectMapper.writeValueAsString(
                new CanonicalRequest(taskId, normalize(request.remark()), lines));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                ErrorKeys.INTERNAL_SERVER_ERROR,
                Map.of("message", "Unable to serialize receiving command")
            );
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record CanonicalRequest(Long taskId, String remark, List<CanonicalLine> lines) {
    }

    private record CanonicalLine(
        Long taskItemId,
        Long locationId,
        Integer quantity,
        String productionDate,
        String expiryDate,
        String externalBatchCode
    ) {
    }
}
