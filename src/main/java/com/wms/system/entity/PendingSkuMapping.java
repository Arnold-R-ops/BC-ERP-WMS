package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待映射 SKU 队列（P1 批次2，四层机制的"人工兜底层"）
 *
 * 未知渠道 SKU 不再静默丢单，而是进入此队列等待人工映射；
 * occurrence_count 记录该 SKU 卡单次数，给运营排优先级。
 * 无 SKU 行使用合成键 "NOSKU::" + 行标题。
 *
 * 人工处理（resolve）后自动写入 channel_sku_mapping，被卡订单
 * 在下一轮同步中经 FAILED 报文重试机制自动放行（见批次1）。
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "pending_sku_mapping", uniqueConstraints = {
    @UniqueConstraint(name = "uk_pending_sku", columnNames = {"company_id", "channel", "external_sku"})
})
public class PendingSkuMapping extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_IGNORED = "IGNORED";

    public static final String RESOLUTION_MAPPED = "MAPPED";
    public static final String RESOLUTION_VIRTUAL = "VIRTUAL";
    public static final String RESOLUTION_IGNORED = "IGNORED";

    /**
     * 无 SKU 行的合成键前缀
     */
    public static final String NO_SKU_PREFIX = "NOSKU::";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(name = "store_identifier")
    private String storeIdentifier;

    @Column(name = "external_sku", nullable = false, length = 200)
    private String externalSku;

    /**
     * 渠道侧商品标题（人工识别的主要线索）
     */
    @Column(name = "external_title", length = 500)
    private String externalTitle;

    @Column(name = "sample_unit_price", precision = 12, scale = 2)
    private BigDecimal sampleUnitPrice;

    @Column(name = "sample_external_order_no", length = 100)
    private String sampleExternalOrderNo;

    /**
     * 卡单次数（运营优先级信号）
     */
    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private Integer occurrenceCount = 1;

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(length = 20)
    private String resolution;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
