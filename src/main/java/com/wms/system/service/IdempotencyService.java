package com.wms.system.service;

import com.wms.system.entity.IdempotencyRequest;
import com.wms.system.entity.enums.IdempotencyStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.IdempotencyRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRequestRepository repository;

    @Transactional(rollbackFor = Exception.class)
    public IdempotencyRequest start(String key, String operation, String requestBody) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String requestHash = sha256(requestBody == null ? "" : requestBody);
        Optional<IdempotencyRequest> existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            IdempotencyRequest record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED, Map.of(
                    "operation", operation,
                    "reason", "Idempotency key is already used by a different request"
                ));
            }
            return record;
        }

        IdempotencyRequest record = IdempotencyRequest.builder()
            .idempotencyKey(key)
            .requestHash(requestHash)
            .operation(operation)
            .status(IdempotencyStatus.PROCESSING)
            .lockedUntil(LocalDateTime.now().plusMinutes(5))
            .build();
        return repository.save(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSucceeded(IdempotencyRequest record, int status, String responseBody) {
        if (record == null) {
            return;
        }
        record.setStatus(IdempotencyStatus.SUCCEEDED);
        record.setResponseStatus(status);
        record.setResponseBody(responseBody);
        repository.save(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(IdempotencyRequest record, int status, String responseBody) {
        if (record == null) {
            return;
        }
        record.setStatus(IdempotencyStatus.FAILED);
        record.setResponseStatus(status);
        record.setResponseBody(responseBody);
        repository.save(record);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException(ErrorKeys.INTERNAL_SERVER_ERROR, Map.of("message", "Unable to hash idempotency request"));
        }
    }
}
