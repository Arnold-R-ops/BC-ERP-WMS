package com.wms.system.service;

import com.wms.system.entity.ChannelRawEvent;
import com.wms.system.repository.ChannelRawEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 渠道报文留底服务（P1 批次1）
 *
 * 全部方法使用 REQUIRES_NEW 独立事务：报文的落库与状态更新
 * 不随业务处理事务回滚——即使订单转换失败，"我们收到过什么"
 * 这个事实必须留存，这是可诊断、可重放的前提。
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
            .status(ChannelRawEvent.STATUS_RECEIVED)
            .build();
        return repository.save(event);
    }

    /**
     * 标记处理成功（独立事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(Long eventId) {
        repository.findById(eventId).ifPresent(e -> {
            e.setStatus(ChannelRawEvent.STATUS_PROCESSED);
            e.setProcessedAt(LocalDateTime.now());
            e.setErrorMessage(null);
            repository.save(e);
        });
    }

    /**
     * 标记处理失败并留下原因（独立事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
     * 标记主动跳过（如去重命中已同步订单；独立事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSkipped(Long eventId, String reason) {
        repository.findById(eventId).ifPresent(e -> {
            e.setStatus(ChannelRawEvent.STATUS_SKIPPED);
            e.setProcessedAt(LocalDateTime.now());
            e.setErrorMessage(reason);
            repository.save(e);
        });
    }

    /**
     * 标记待人工复核（P1-B3：渠道侧取消/修改无法自动处理时；独立事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        ChannelRawEvent event = ChannelRawEvent.builder()
            .channel(channel)
            .storeIdentifier(storeIdentifier)
            .source(ChannelRawEvent.SOURCE_WEBHOOK)
            .eventType(eventType)
            .externalId(externalId)
            .payload(payload)
            .webhookEventId(webhookEventId)
            .status(ChannelRawEvent.STATUS_RECEIVED)
            .build();
        return repository.save(event);
    }

    /**
     * Webhook 事件 id 是否已接收过（重发去重）
     */
    @Transactional(readOnly = true)
    public boolean webhookAlreadyReceived(String webhookEventId) {
        return webhookEventId != null && repository.existsByWebhookEventId(webhookEventId);
    }
}
