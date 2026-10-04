package com.wms.system.service;

import com.wms.system.entity.ChannelRawEvent;
import com.wms.system.repository.ChannelRawEventRepository;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Map;

/**
 * 渠道报文留底服务（P1 批次1）
 *
 * 报文接收使用 REQUIRES_NEW 独立留底；处理结果加入调用方事务。
 * P3-M1：不得在业务事务尚未提交时，独立提交 PROCESSED/去重标记；
 * 否则业务回滚后会被错误地视为已处理。没有外层事务时状态更新仍独立提交。
 *
 * @author WMS Team
 * @since 2026-07-10
 * @version P1-B1 (Channel Integration Foundation)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelRawEventService {

    private final ChannelRawEventRepository repository;

    /**
     * 查询同渠道同类型同外部标识的最近一条留底（去重判断）
     */
    @Transactional(readOnly = true)
    public Optional<ChannelRawEvent> findLatest(String channel, String eventType, String externalId) {
        return repository.findFirstByChannelAndEventTypeAndExternalIdOrderByIdDesc(channel, eventType, externalId);
    }

    @Transactional(readOnly = true)
    public Optional<ChannelRawEvent> findLatest(
        String channel,
        String storeIdentifier,
        String source,
        String eventType,
        String externalId
    ) {
        return repository.findFirstByChannelAndStoreIdentifierAndSourceAndEventTypeAndExternalIdOrderByIdDesc(
            channel,
            storeIdentifier,
            source,
            eventType,
            externalId
        );
    }

    @Transactional(readOnly = true)
    public ChannelRawEvent requireById(Long eventId) {
        return repository.findById(eventId)
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "ChannelRawEvent", "resourceId", eventId)
            ));
    }

    /**
     * 落库一条新收到的报文（独立事务，立即持久化）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChannelRawEvent record(String channel, String storeIdentifier, String source,
                                  String eventType, String externalId, String payload) {
        ChannelRawEvent event = ChannelRawEvent.builder()
            .channel(channel)
            .storeIdentifier(storeIdentifier)
            .source(source)
            .eventType(eventType)
            .externalId(externalId)
            .payload(payload)
            .sourceInboxId(com.wms.system.tenant.security.TenantWebhookReceiptScope.currentId())
            .status(ChannelRawEvent.STATUS_RECEIVED)
            .build();
        return repository.save(event);
    }

    /**
     * 标记处理成功（加入业务事务，无外层事务时独立提交）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markProcessed(Long eventId) {
        repository.findById(eventId).ifPresent(e -> {
            e.setStatus(ChannelRawEvent.STATUS_PROCESSED);
            e.setProcessedAt(LocalDateTime.now());
            e.setErrorMessage(null);
            repository.save(e);
        });
    }

    /**
     * 标记处理失败并留下原因（加入调用方事务）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markFailed(Long eventId, String errorMessage) {
        repository.findById(eventId).ifPresent(e -> {
            e.setStatus(ChannelRawEvent.STATUS_FAILED);
            e.setProcessedAt(LocalDateTime.now());
            e.setErrorMessage(errorMessage == null ? null
                : errorMessage.substring(0, Math.min(errorMessage.length(), 2000)));
            repository.save(e);
        });
    }

    /**
     * 标记主动跳过（如去重命中已同步订单；加入调用方事务）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markSkipped(Long eventId, String reason) {
        repository.findById(eventId).ifPresent(e -> {
            e.setStatus(ChannelRawEvent.STATUS_SKIPPED);
            e.setProcessedAt(LocalDateTime.now());
            e.setErrorMessage(reason);
            repository.save(e);
        });
    }

    /**
     * 标记待人工复核（P1-B3：渠道侧取消/修改无法自动处理时；加入调用方事务）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markManualReview(Long eventId, String reason) {
        repository.findById(eventId).ifPresent(e -> {
            e.setStatus(ChannelRawEvent.STATUS_MANUAL_REVIEW);
            e.setProcessedAt(LocalDateTime.now());
            e.setErrorMessage(reason);
            repository.save(e);
        });
    }

    /**
     * Webhook 报文落库（P1-B3，带 webhook 事件 id；独立事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChannelRawEvent recordWebhook(String channel, String storeIdentifier, String eventType,
                                         String externalId, String payload, String webhookEventId) {
        if(webhookEventId!=null) {
            var existing=repository.findByWebhookEventId(webhookEventId);
            if(existing.isPresent()) {
                var event=existing.get();
                if(event.getSourceInboxId()==null && com.wms.system.tenant.security.TenantWebhookReceiptScope.currentId()!=null) {
                    event.setSourceInboxId(com.wms.system.tenant.security.TenantWebhookReceiptScope.currentId());
                    repository.save(event);
                }
                return event;
            }
        }
        ChannelRawEvent event = ChannelRawEvent.builder()
            .channel(channel)
            .storeIdentifier(storeIdentifier)
            .source(ChannelRawEvent.SOURCE_WEBHOOK)
            .eventType(eventType)
            .externalId(externalId)
            .payload(payload)
            .webhookEventId(webhookEventId)
            .sourceInboxId(com.wms.system.tenant.security.TenantWebhookReceiptScope.currentId())
            .status(ChannelRawEvent.STATUS_RECEIVED)
            .build();
        return repository.save(event);
    }

    /**
     * Webhook 事件 id 是否已接收过（重发去重）
     */
    @Transactional(readOnly = true)
    public boolean webhookAlreadyReceived(String webhookEventId) {
        return webhookEventId != null && repository.existsByWebhookEventIdAndStatusIn(webhookEventId,
            java.util.List.of(ChannelRawEvent.STATUS_PROCESSED,ChannelRawEvent.STATUS_SKIPPED,ChannelRawEvent.STATUS_MANUAL_REVIEW));
    }
}
