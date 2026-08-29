package com.wms.system.service.inspection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.receivinginspection.*;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.ReceivingArrivalExceptionType;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.*;
import com.wms.system.service.IdempotencyService;
import com.wms.system.service.idempotency.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReceivingArrivalExceptionService {
    static final String OPERATION = "RECEIVING_ARRIVAL_EXCEPTION";

    private final ReceivingTaskRepository taskRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ReceivingArrivalExceptionRepository exceptionRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public ReceivingArrivalExceptionResponse record(
        Long receivingTaskId,
        RecordReceivingArrivalExceptionRequest request,
        String idempotencyKey,
        Long actorId,
        String actorName
    ) {
        String body = serialize(Map.of(
            "receivingTaskId", receivingTaskId,
            "request", request
        ));
        String fingerprint = sha256(body);
        IdempotencyClaim claim = idempotencyService.claim(idempotencyKey, OPERATION, body);
        if (claim.decision() == IdempotencyClaimDecision.REPLAY_SUCCEEDED) {
            ReceivingArrivalException replay = exceptionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT));
            if (!fingerprint.equals(replay.getRequestFingerprint())) {
                throw new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT);
            }
            return toResponse(replay);
        }
        if (claim.decision() != IdempotencyClaimDecision.ACQUIRED) {
            throw new BusinessException(
                claim.decision() == IdempotencyClaimDecision.IN_PROGRESS
                    ? ErrorKeys.IDEMPOTENCY_REQUEST_IN_PROGRESS
                    : ErrorKeys.IDEMPOTENCY_CONFLICT
            );
        }

        ReceivingTask task = taskRepository.findById(receivingTaskId)
            .orElseThrow(() -> new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "ARRIVAL_EXCEPTION", "reason", "TASK_NOT_FOUND")));
        ProductSku sku = request.productSkuId() == null ? null
            : productSkuRepository.findById(request.productSkuId())
                .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_SKU_NOT_FOUND));
        if (sku != null
            && request.exceptionType() != ReceivingArrivalExceptionType.WRONG_ITEM
            && task.getItems().stream().noneMatch(
                item -> item.getProductSku().getId().equals(sku.getId()))) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED,
                Map.of("field", "productSkuId", "reason", "SKU_NOT_IN_RECEIVING_TASK"));
        }

        ReceivingArrivalException exception = exceptionRepository.saveAndFlush(
            ReceivingArrivalException.builder()
                .exceptionNumber(ReceivingInspectionTaskFactory.number("RAE"))
                .receivingTask(task)
                .productSku(sku)
                .exceptionType(request.exceptionType())
                .disposition(request.disposition())
                .observedQuantity(request.observedQuantity())
                .reasonDetail(trim(request.reasonDetail()))
                .recordedBy(actorId)
                .recordedByName(actorName.trim())
                .recordedAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey.trim())
                .requestFingerprint(fingerprint)
                .build()
        );
        idempotencyService.markSucceeded(claim.request(), 201, exception.getId().toString());
        return toResponse(exception);
    }

    @Transactional(readOnly = true)
    public List<ReceivingArrivalExceptionResponse> list(Long receivingTaskId) {
        return exceptionRepository.findByReceivingTaskIdOrderByIdDesc(receivingTaskId)
            .stream().map(this::toResponse).toList();
    }

    private ReceivingArrivalExceptionResponse toResponse(ReceivingArrivalException value) {
        return new ReceivingArrivalExceptionResponse(
            value.getId(), value.getExceptionNumber(), value.getReceivingTask().getId(),
            value.getProductSku() == null ? null : value.getProductSku().getId(),
            value.getProductSku() == null ? null : value.getProductSku().getSkuCode(),
            value.getExceptionType(), value.getDisposition(), value.getObservedQuantity(),
            value.getReasonDetail(), value.getRecordedByName(), value.getRecordedAt()
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorKeys.VALIDATION_FAILED);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
