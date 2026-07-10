package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 渠道原始报文留底（P1 批次1）
 *
 * 设计原则：任何从销售渠道拉取/接收的报文，先落库再处理。
 * - 处理失败可诊断、可重放（payload 是渠道原样 JSON）
 * - 每日对账的原料（external_id 与本地订单比对）
 * - 渠道通用：channel 字段区分 SHOPIFY / AMAZON / ...
 *
 * 状态机：RECEIVED → PROCESSED / FAILED / SKIPPED
 * - SKIPPED：主动跳过（如去重命中、无需处理的类型）
 *
 * @author WMS Team
 * @since 2026-07-10
 * @version P1-B1 (Channel Integration Foundation)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "channel_raw_events", indexes = {
    @Index(name = "idx_raw_events_channel_status", columnList = "channel, status"),
    @Index(name = "idx_raw_events_external", columnList = "channel, event_type, external_id")
})
public class ChannelRawEvent extends BaseEntity {

    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    public static final String SOURCE_POLL = "POLL";
    public static final String SOURCE_WEBHOOK = "WEBHOOK";
    public static final String SOURCE_RECONCILE = "RECONCILE";

    public static final String TYPE_ORDER = "ORDER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 销售渠道：SHOPIFY / AMAZON / ...
     */
    @Column(nullable = false, length = 50)
    private String channel;

    /**
     * 店铺标识（如 xxx.myshopify.com），多店时区分来源
     */
    @Column(name = "store_identifier")
    private String storeIdentifier;

    /**
     * 报文来源：POLL（轮询拉取）/ WEBHOOK（推送）/ RECONCILE（对账补拉）
     */
    @Column(nullable = false, length = 20)
    private String source;

    /**
     * 报文类型：ORDER 等
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * 渠道侧标识（如 Shopify 订单 id），用于去重与关联
     */
    @Column(name = "external_id", length = 100)
    private String externalId;

    /**
     * 渠道原样 JSON 报文（JSONB）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    /**
     * 处理状态：RECEIVED / PROCESSED / FAILED / SKIPPED
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_RECEIVED;

    /**
     * 失败原因（status=FAILED 时）
     */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /**
     * 处理完成时间
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
