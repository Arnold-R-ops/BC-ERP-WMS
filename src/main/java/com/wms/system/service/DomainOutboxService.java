package com.wms.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.entity.DomainOutbox;
import com.wms.system.entity.enums.OutboxStatus;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.DomainOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DomainOutboxService {

    private final DomainOutboxRepository domainOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public DomainOutbox append(String eventType, String aggregateType, Object aggregateId, Object payload) {
        try {
            DomainOutbox event = DomainOutbox.builder()
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(String.valueOf(aggregateId))
                .payloadJson(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.PENDING)
                .build();
            return domainOutboxRepository.save(event);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                ErrorKeys.VALIDATION_FAILED,
                Map.of("message", "Unable to serialize outbox payload", "eventType", eventType)
            );
        }
    }

    @Transactional(readOnly = true)
    public List<DomainOutbox> listPending() {
        return domainOutboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    }

    @Transactional(rollbackFor = Exception.class)
    public DomainOutbox markPublished(Long id) {
        DomainOutbox event = domainOutboxRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND, Map.of("resourceType", "DomainOutbox", "resourceId", id)));
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        return domainOutboxRepository.save(event);
    }
}
