package com.wms.system.service.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.InventoryBatch;
import com.wms.system.entity.InventoryStatusEvent;
import com.wms.system.entity.enums.InventoryQualityStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.InventoryBatchRepository;
import com.wms.system.repository.InventoryStatusEventRepository;
import com.wms.system.service.DomainOutboxService;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single internal transaction boundary for inventory quality-state changes.
 * Quantity is never changed here; future inspection/freeze/disposition
 * workflows call this service after enforcing their own workflow authority.
 */
@Service
@RequiredArgsConstructor
public class InventoryQualityStateService {

    static final String OPERATION = "INVENTORY_QUALITY_TRANSITION";
    static final String OUTBOX_EVENT = "INVENTORY_QUALITY_STATUS_CHANGED";

    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryStatusEventRepository eventRepository;
    private final IdempotencyService idempotencyService;
    private final DomainOutboxService domainOutboxService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public InventoryQualityTransitionResult transition(InventoryQualityTransitionCommand command) {
        ValidatedCommand validated = validate(command);
        String requestBody = serialize(validated);
        String fingerprint = sha256(requestBody);
        IdempotencyClaim claim = idempotencyService.claim(
            validated.idempotencyKey(), OPERATION, requestBody);

        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            return replay(claim.request(), validated, fingerprint);
        }
        if (claim.decision() == IdempotencyClaimDecision.IN_PROGRESS) {
            throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                Map.of("operation", OPERATION)
            );
        }
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_FAILED) {
            throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", OPERATION, "reason", "PREVIOUS_ATTEMPT_FAILED")
            );
        }

        InventoryBatch batch = inventoryBatchRepository
            .findByIdForPosting(validated.inventoryBatchId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.BATCH_NOT_FOUND,
                Map.of("batchId", validated.inventoryBatchId())
            ));

        InventoryQualityStatus fromStatus = batch.getQualityStatus();
        validateTransition(batch, fromStatus, validated.targetStatus());

        batch.setQualityStatus(validated.targetStatus());
        inventoryBatchRepository.saveAndFlush(batch);

        InventoryStatusEvent event = eventRepository.saveAndFlush(
            InventoryStatusEvent.builder()
                .inventoryBatchId(batch.getId())
                .batchCode(batch.getBatchCode())
                .fromStatus(fromStatus)
                .toStatus(validated.targetStatus())
                .quantitySnapshot(batch.getQuantity())
                .sourceType(validated.sourceType())
                .sourceId(validated.sourceId())
                .reasonCode(validated.reasonCode())
                .reasonDetail(validated.reasonDetail())
                .actorId(validated.actorId())
                .actorName(validated.actorName())
                .idempotencyKey(validated.idempotencyKey())
                .requestFingerprint(fingerprint)
                .build()
        );

        domainOutboxService.append(
            OUTBOX_EVENT,
            "InventoryBatch",
            batch.getId(),
            outboxPayload(event)
        );
        idempotencyService.markSucceeded(claim.request(), 200, event.getId().toString());

        return result(event, false);
    }

    private InventoryQualityTransitionResult replay(
        IdempotencyRequest request,
        ValidatedCommand validated,
        String fingerprint
    ) {
        InventoryStatusEvent event = eventRepository
            .findByIdempotencyKey(validated.idempotencyKey())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", OPERATION, "reason", "EVENT_NOT_FOUND")
            ));
        if (!fingerprint.equals(event.getRequestFingerprint())
            || !event.getId().toString().equals(request.getResponseBody())) {
            throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", OPERATION, "reason", "EVENT_FINGERPRINT_MISMATCH")
            );
        }
        return result(event, true);
    }

    private void validateTransition(
        InventoryBatch batch,
        InventoryQualityStatus fromStatus,
        InventoryQualityStatus toStatus
    ) {
        if (!batch.isActive() || batch.getQuantity() == null || batch.getQuantity() <= 0
            || fromStatus == null || fromStatus == toStatus
            || (toStatus == InventoryQualityStatus.AVAILABLE && batch.isExpired())) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_QUALITY_TRANSITION_INVALID,
                transitionParams(batch, fromStatus, toStatus)
            );
        }
        int reserved = batch.getReservedQuantity() == null ? 0 : batch.getReservedQuantity();
        if (fromStatus == InventoryQualityStatus.AVAILABLE
            && toStatus != InventoryQualityStatus.AVAILABLE
            && reserved > 0) {
            throw new BusinessException(
                ErrorKeys.INVENTORY_QUALITY_RESERVED_CONFLICT,
                Map.of(
                    "batchId", batch.getId(),
                    "reservedQuantity", reserved,
                    "targetStatus", toStatus.name()
                )
            );
        }
    }

    private Map<String, Object> transitionParams(
        InventoryBatch batch,
        InventoryQualityStatus fromStatus,
        InventoryQualityStatus toStatus
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("batchId", batch.getId());
        params.put("active", batch.isActive());
        params.put("quantity", batch.getQuantity());
        params.put("fromStatus", fromStatus == null ? "UNKNOWN" : fromStatus.name());
        params.put("toStatus", toStatus.name());
        params.put("expired", batch.isExpired());
        return params;
    }

    private InventoryQualityTransitionResult result(InventoryStatusEvent event, boolean replayed) {
        return new InventoryQualityTransitionResult(
            event.getId(),
            event.getInventoryBatchId(),
            event.getFromStatus(),
            event.getToStatus(),
            replayed
        );
    }

    private Map<String, Object> outboxPayload(InventoryStatusEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getId());
        payload.put("inventoryBatchId", event.getInventoryBatchId());
        payload.put("batchCode", event.getBatchCode());
        payload.put("fromStatus", event.getFromStatus().name());
        payload.put("toStatus", event.getToStatus().name());
        payload.put("quantitySnapshot", event.getQuantitySnapshot());
        payload.put("sourceType", event.getSourceType());
        payload.put("sourceId", event.getSourceId());
        payload.put("reasonCode", event.getReasonCode());
        payload.put("actorId", event.getActorId());
        return payload;
    }

    private ValidatedCommand validate(InventoryQualityTransitionCommand command) {
        if (command == null || command.inventoryBatchId() == null || command.inventoryBatchId() <= 0
            || command.targetStatus() == null || command.actorId() == null || command.actorId() < 0) {
            throw validationFailure("command");
        }
        return new ValidatedCommand(
            command.inventoryBatchId(),
            command.targetStatus(),
            requireText(command.sourceType(), "sourceType", 60),
            requireText(command.sourceId(), "sourceId", 120),
            requireText(command.reasonCode(), "reasonCode", 80),
            optionalText(command.reasonDetail(), "reasonDetail", 500),
            command.actorId(),
            requireText(command.actorName(), "actorName", 100),
            requireText(command.idempotencyKey(), "idempotencyKey", 120)
        );
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) {
            throw validationFailure(field);
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", field, "maxLength", maxLength)
            );
        }
        return normalized;
    }

    private BusinessException validationFailure(String field) {
        return new BusinessException(ErrorKeys.VALIDATION_FAILED, Map.of("field", field));
    }

    private String serialize(ValidatedCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException ex) {
            throw validationFailure("command");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record ValidatedCommand(
        Long inventoryBatchId,
        InventoryQualityStatus targetStatus,
        String sourceType,
        String sourceId,
        String reasonCode,
        String reasonDetail,
        Long actorId,
        String actorName,
        String idempotencyKey
    ) {
    }
}
