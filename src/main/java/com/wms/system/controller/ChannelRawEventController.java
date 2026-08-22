package com.wms.system.controller;

import com.wms.system.entity.ChannelRawEvent;
import com.wms.system.repository.ChannelRawEventRepository;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 渠道报文留底查询（P2 前端交接：人工复核工作台的数据源）
 *
 * 用途：
 * - 待人工复核列表（status=MANUAL_REVIEW：渠道取消未拣完、渠道改单等）
 * - 失败件诊断（status=FAILED，含失败原因；FAILED 订单件由同步自动重试）
 * - 报文详情回放（含原始 JSON）
 *
 * 列表接口不返回 payload（体积大），详情接口才带。
 *
 * @author WMS Team
 * @since 2026-07-12
 * @version P2-FE (Frontend Handover)
 */
@Slf4j
@RestController
@RequestMapping("/api/integration/raw-events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('system:admin', 'TENANT_ADMIN')")
public class ChannelRawEventController {

    private final ChannelRawEventRepository repository;

    /**
     * 分页查询报文留底（不含 payload）
     */
    @GetMapping
    public ResponseEntity<Page<RawEventSummary>> list(
        @RequestParam(value = "channel", defaultValue = "SHOPIFY") String channel,
        @RequestParam(value = "status", defaultValue = "MANUAL_REVIEW") String status,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<ChannelRawEvent> events = repository.findByChannelAndStatusOrderByIdDesc(
            channel, status, PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(events.map(RawEventSummary::from));
    }

    /**
     * 报文详情（含原始 JSON payload）
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChannelRawEvent> get(@PathVariable("id") Long id) {
        ChannelRawEvent event = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.RESOURCE_NOT_FOUND,
                Map.of("resourceType", "ChannelRawEvent", "resourceId", String.valueOf(id))));
        return ResponseEntity.ok(event);
    }

    /**
     * 列表行摘要（省去大体积 payload）
     */
    public record RawEventSummary(Long id, String channel, String storeIdentifier, String source,
                                  String eventType, String externalId, String status,
                                  String errorMessage, java.time.LocalDateTime createdAt,
                                  java.time.LocalDateTime processedAt) {
        static RawEventSummary from(ChannelRawEvent e) {
            return new RawEventSummary(e.getId(), e.getChannel(), e.getStoreIdentifier(), e.getSource(),
                e.getEventType(), e.getExternalId(), e.getStatus(),
                e.getErrorMessage(), e.getCreatedAt(), e.getProcessedAt());
        }
    }
}
