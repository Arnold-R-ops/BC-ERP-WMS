package com.wms.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.SalesCommandEvent;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SalesCommandEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Supplier;

/** Commits a sales mutation and its durable replay result in one transaction. */
@Service
@RequiredArgsConstructor
public class SalesCommandExecutor {
    private final SalesCommandEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public <T> T execute(
        String idempotencyKey,
        String operation,
        Long actorId,
        String actorName,
        Supplier<T> action
    ) {
        T response = action.get();
        eventRepository.saveAndFlush(SalesCommandEvent.builder()
            .idempotencyKey(idempotencyKey)
            .operation(operation)
            .responseBody(write(response))
            .actorId(actorId)
            .actorName(actorName)
            .build());
        return response;
    }

    @Transactional(readOnly = true)
    public boolean hasResult(String idempotencyKey, String operation) {
        return eventRepository.findByIdempotencyKey(idempotencyKey)
            .filter(event -> operation.equals(event.getOperation()))
            .isPresent();
    }

    @Transactional(readOnly = true)
    public <T> T replay(String idempotencyKey, String operation, Class<T> responseType) {
        SalesCommandEvent event = eventRepository.findByIdempotencyKey(idempotencyKey)
            .filter(candidate -> operation.equals(candidate.getOperation()))
            .orElseThrow(() -> new BusinessException(ErrorKeys.IDEMPOTENCY_CONFLICT,
                Map.of("operation", operation, "reason", "SALES_COMMAND_RESULT_NOT_FOUND")));
        try {
            return objectMapper.readValue(event.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorKeys.INTERNAL_SERVER_ERROR,
                Map.of("message", "Unable to deserialize sales command result"));
        }
    }

    private String write(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorKeys.INTERNAL_SERVER_ERROR,
                Map.of("message", "Unable to serialize sales command result"));
        }
    }
}
