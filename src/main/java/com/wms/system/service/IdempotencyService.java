package com.wms.system.service;

import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.IdempotencyRequestRepository;
import com.wms.system.service.idempotency.IdempotencyClaim;
import com.wms.system.service.idempotency.IdempotencyClaimDecision;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);

    private final IdempotencyRequestRepository repository;

    /**
     * Backwards-compatible entry point used by the Shopify webhook flow.
     *
     * <p>A blank key continues to disable idempotency. Successful replays are
     * returned to the existing caller. Active processing is surfaced as a
     * {@link BusinessException}. A failed terminal record is re-opened for the
     * legacy caller because Shopify uses that path for retryable failures.</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public IdempotencyRequest start(String key, String operation, String requestBody) {
        if (key == null || key.isBlank()) {
            return null;
        }
        IdempotencyClaim claim = claimInternal(key, operation, requestBody);
        return switch (claim.decision()) {
            case ACQUIRED, REPLAY_SUCCEEDED -> claim.request();
            case IN_PROGRESS -> throw replayException(
                operation,
                "IDEMPOTENCY_REQUEST_PROCESSING",
                claim.request()
            );
            case REPLAY_FAILED -> reacquireFailedForLegacyRetry(claim.request());
        };
    }

    /**
     * Claims a required idempotency key and exposes all replay decisions to
     * newer command handlers.
     */
    @Transactional(rollbackFor = Exception.class)
    public IdempotencyClaim claim(String key, String operation, String requestBody) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(
                ErrorKeys.IDEMPOTENCY_KEY_REQUIRED,
                Map.of("parameter", "Idempotency-Key")
            );
        }
        return claimInternal(key, operation, requestBody);
    }

    private IdempotencyRequest reacquireFailedForLegacyRetry(IdempotencyRequest record) {
        record.setStatus(IdempotencyStatus.PROCESSING);
        record.setLockedUntil(LocalDateTime.now().plus(PROCESSING_LEASE));
        record.setLeaseToken(UUID.randomUUID().toString());
        record.setResponseStatus(null);
        record.setResponseBody(null);
        return repository.saveAndFlush(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSucceeded(IdempotencyRequest record, int status, String responseBody) {
        complete(record, IdempotencyStatus.SUCCEEDED, status, responseBody);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(IdempotencyRequest record, int status, String responseBody) {
        complete(record, IdempotencyStatus.FAILED, status, responseBody);
    }

    private IdempotencyClaim claimInternal(String key, String operation, String requestBody) {
        validateLengths(key, operation);
        String normalizedOperation = requireOperation(operation);
        String requestHash = requestHash(normalizedOperation, requestBody);
        Long companyId = CompanyScope.currentCompanyId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockedUntil = now.plus(PROCESSING_LEASE);
        String leaseToken = UUID.randomUUID().toString();

        boolean inserted = repository.insertReservation(
            companyId,
            key,
            normalizedOperation,
            requestHash,
            lockedUntil,
            leaseToken
        ) == 1;

        IdempotencyRequest record = repository.findByCompanyAndKeyForUpdate(companyId, key)
            .orElseThrow(() -> new IllegalStateException(
                "Idempotency reservation is missing after insert attempt"));
        validateIntent(record, normalizedOperation, requestHash);

        if (inserted) {
            return new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, record);
        }
        if (record.getStatus() == IdempotencyStatus.SUCCEEDED) {
            return new IdempotencyClaim(IdempotencyClaimDecision.REPLAY_SUCCEEDED, record);
        }
        if (record.getStatus() == IdempotencyStatus.FAILED) {
            return new IdempotencyClaim(IdempotencyClaimDecision.REPLAY_FAILED, record);
        }
        if (record.getStatus() != IdempotencyStatus.PROCESSING) {
            throw stateConflict(normalizedOperation, "IDEMPOTENCY_STATUS_INVALID", record);
        }
        if (record.getLockedUntil() != null && record.getLockedUntil().isAfter(now)) {
            return new IdempotencyClaim(IdempotencyClaimDecision.IN_PROGRESS, record);
        }

        record.setLockedUntil(lockedUntil);
        record.setLeaseToken(leaseToken);
        record.setResponseStatus(null);
        record.setResponseBody(null);
        IdempotencyRequest renewed = repository.saveAndFlush(record);
        return new IdempotencyClaim(IdempotencyClaimDecision.ACQUIRED, renewed);
    }

    private void complete(
        IdempotencyRequest claimed,
        IdempotencyStatus terminalStatus,
        int responseStatus,
        String responseBody
    ) {
        if (claimed == null) {
            return;
        }
        if (claimed.getId() == null) {
            throw stateConflict(
                claimed.getOperation(),
                "IDEMPOTENCY_CLAIM_NOT_PERSISTED",
                claimed
            );
        }

        Long companyId = CompanyScope.currentCompanyId();
        IdempotencyRequest current = repository
            .findByCompanyAndIdForUpdate(companyId, claimed.getId())
            .orElseThrow(() -> stateConflict(
                claimed.getOperation(),
                "IDEMPOTENCY_CLAIM_NOT_FOUND",
                claimed
            ));
        validateClaimIdentity(claimed, current);

        if (current.getStatus() == terminalStatus) {
            copyTerminalState(current, claimed);
            return;
        }
        if (current.getStatus() != IdempotencyStatus.PROCESSING) {
            throw stateConflict(
                current.getOperation(),
                "IDEMPOTENCY_TERMINAL_STATE_CONFLICT",
                current
            );
        }
        if (claimed.getLeaseToken() == null
            || !Objects.equals(claimed.getLeaseToken(), current.getLeaseToken())) {
            throw stateConflict(
                current.getOperation(),
                "IDEMPOTENCY_LEASE_LOST",
                current
            );
        }

        current.setStatus(terminalStatus);
        current.setResponseStatus(responseStatus);
        current.setResponseBody(responseBody);
        current.setLockedUntil(null);
        current.setLeaseToken(null);
        IdempotencyRequest saved = repository.saveAndFlush(current);
        copyTerminalState(saved, claimed);
    }

    private void validateIntent(
        IdempotencyRequest record,
        String operation,
        String requestHash
    ) {
        if (!operation.equals(record.getOperation())
            || !requestHash.equals(record.getRequestHash())) {
            throw stateConflict(operation, "IDEMPOTENCY_KEY_REUSED_FOR_DIFFERENT_INTENT", record);
        }
    }

    private void validateClaimIdentity(
        IdempotencyRequest claimed,
        IdempotencyRequest current
    ) {
        if (!Objects.equals(claimed.getCompanyId(), current.getCompanyId())
            || !Objects.equals(claimed.getIdempotencyKey(), current.getIdempotencyKey())
            || !Objects.equals(claimed.getOperation(), current.getOperation())
            || !Objects.equals(claimed.getRequestHash(), current.getRequestHash())) {
            throw stateConflict(
                current.getOperation(),
                "IDEMPOTENCY_CLAIM_IDENTITY_MISMATCH",
                current
            );
        }
    }

    private void copyTerminalState(IdempotencyRequest source, IdempotencyRequest target) {
        target.setStatus(source.getStatus());
        target.setResponseStatus(source.getResponseStatus());
        target.setResponseBody(source.getResponseBody());
        target.setLockedUntil(source.getLockedUntil());
        target.setLeaseToken(source.getLeaseToken());
    }

    private String requireOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            throw new BusinessException(
                ErrorKeys.PARAMETER_REQUIRED,
                Map.of("parameter", "operation")
            );
        }
        return operation.trim();
    }

    private void validateLengths(String key, String operation) {
        if (key.length() > 120) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "Idempotency-Key", "maxLength", 120)
            );
        }
        if (operation != null && operation.trim().length() > 80) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "operation", "maxLength", 80)
            );
        }
    }

    /** Hashes the operation and exact request body as one command intent. */
    static String requestHash(String operation, String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            String body = requestBody == null ? "" : requestBody;
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(
                ErrorKeys.INTERNAL_SERVER_ERROR,
                Map.of("message", "Unable to hash idempotency request")
            );
        }
    }

    private BusinessException replayException(
        String operation,
        String reason,
        IdempotencyRequest record
    ) {
        return stateConflict(operation, reason, record);
    }

    private BusinessException stateConflict(
        String operation,
        String reason,
        IdempotencyRequest record
    ) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("operation", operation == null ? "IDEMPOTENCY" : operation);
        params.put("reason", reason);
        if (record != null && record.getStatus() != null) {
            params.put("idempotencyStatus", record.getStatus().name());
        }
        if (record != null && record.getLockedUntil() != null) {
            params.put("lockedUntil", record.getLockedUntil());
        }
        if (record != null && record.getResponseStatus() != null) {
            params.put("responseStatus", record.getResponseStatus());
        }
        String errorKey = "IDEMPOTENCY_REQUEST_PROCESSING".equals(reason)
            ? ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS
            : ErrorKeys.IDEMPOTENCY_CONFLICT;
        return new BusinessException(errorKey, params);
    }
}
