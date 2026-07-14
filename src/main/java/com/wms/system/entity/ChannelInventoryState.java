package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 渠道库存推送状态（P1 批次4，休眠功能的状态表）
 *
 * 每条 PRODUCT 类型的 SKU 映射对应一行：记录最近一次成功推送到渠道的
 * 可售数量。差量扫描只推送与上次不同的值，避免对渠道 API 的无谓调用。
 *
 * @author WMS Team
 * @since 2026-07-12
 * @version P1-B4 (Inventory Writeback, dormant)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "channel_inventory_state", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_state_mapping", columnNames = {"mapping_id"})
})
public class ChannelInventoryState extends BaseEntity {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String channel;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_id", nullable = false)
    private ChannelSkuMapping mapping;

    /**
     * 最近一次成功推送的可售数量（外部单位口径 = floor(内部ATP / 换算比)）
     */
    @Column(name = "last_pushed_available")
    private Integer lastPushedAvailable;

    @Column(name = "last_pushed_at")
    private LocalDateTime lastPushedAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_OK;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
}
