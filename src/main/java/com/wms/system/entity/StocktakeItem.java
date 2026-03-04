package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 盘点明细实体
 *
 * V3.8 架构：智能盘点系统
 *
 * 说明：
 * - 存储盘点明细信息
 * - 快照数量（snapshot_qty）不可变
 * - 实盘数量（counted_qty）由仓库员录入
 * - 差异数量（difference_qty）自动计算
 *
 * 核心字段：
 * - taskId: 盘点任务ID
 * - productId: 产品ID
 * - batchId: 批次ID
 * - locationId: 库位ID
 * - snapshotQty: 快照数量（账面数 - 不可变）
 * - countedQty: 实盘数量
 * - differenceQty: 差异数量
 *
 * 盲盘功能：
 * - 前端录入时不显示 snapshotQty
 * - 防止仓库员作弊
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "stocktake_items",
    indexes = {
        @Index(name = "idx_stocktake_item_task", columnList = "task_id"),
        @Index(name = "idx_stocktake_item_product", columnList = "product_id"),
        @Index(name = "idx_stocktake_item_batch", columnList = "batch_id"),
        @Index(name = "idx_stocktake_item_location", columnList = "location_id"),
        @Index(name = "idx_stocktake_item_counted", columnList = "is_counted")
    }
)
public class StocktakeItem extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 盘点任务ID
     */
    @NotNull(message = "盘点任务ID不能为空")
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /**
     * 盘点任务实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    private StocktakeTask task;

    /**
     * 产品ID
     */
    @NotNull(message = "产品ID不能为空")
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * 产品实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    /**
     * 批次ID
     */
    @NotNull(message = "批次ID不能为空")
    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /**
     * 批次实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    private InventoryBatch batch;

    /**
     * 库位ID
     */
    @NotNull(message = "库位ID不能为空")
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    /**
     * 库位实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", insertable = false, updatable = false)
    private Location location;

    /**
     * 快照数量（账面数 - 不可变）
     *
     * 说明：
     * - 创建盘点任务时锁定的库存数量
     * - 不可修改，用于对比实盘数量
     * - 前端录入时不显示此字段（盲盘）
     */
    @NotNull(message = "快照数量不能为空")
    @Min(value = 0, message = "快照数量不能为负数")
    @Column(name = "snapshot_qty", nullable = false)
    private Integer snapshotQty;

    /**
     * 实盘数量
     *
     * 说明：
     * - 仓库员实际盘点的数量
     * - 可以为 null（未盘点）
     */
    @Min(value = 0, message = "实盘数量不能为负数")
    @Column(name = "counted_qty")
    private Integer countedQty;

    /**
     * 差异数量
     *
     * 计算公式：difference_qty = counted_qty - snapshot_qty
     *
     * 说明：
     * - 正数：盘盈（实际多于账面）
     * - 负数：盘亏（实际少于账面）
     * - 零：账实相符
     */
    @Column(name = "difference_qty", nullable = false)
    @Builder.Default
    private Integer differenceQty = 0;

    /**
     * 是否已盘点
     */
    @Column(name = "is_counted", nullable = false)
    @Builder.Default
    private Boolean isCounted = false;

    // ========== 盘点人信息 ==========

    /**
     * 盘点人ID
     */
    @Column(name = "counted_by")
    private Long countedBy;

    /**
     * 盘点人姓名
     */
    @Size(max = 100, message = "盘点人姓名长度不能超过 100 个字符")
    @Column(name = "counted_by_name", length = 100)
    private String countedByName;

    /**
     * 盘点时间
     */
    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    @Column(length = 500)
    private String remark;

    // ========== 便利方法 ==========

    /**
     * 计算差异数量
     *
     * @return 差异数量（counted_qty - snapshot_qty）
     */
    public Integer calculateDifference() {
        if (countedQty == null) {
            return 0;
        }
        return countedQty - snapshotQty;
    }

    /**
     * 判断是否有差异
     *
     * @return true 如果差异数量不为 0
     */
    public boolean hasDifference() {
        return differenceQty != null && differenceQty != 0;
    }

    /**
     * 判断是否盘盈
     *
     * @return true 如果差异数量 > 0
     */
    public boolean isSurplus() {
        return differenceQty != null && differenceQty > 0;
    }

    /**
     * 判断是否盘亏
     *
     * @return true 如果差异数量 < 0
     */
    public boolean isShortage() {
        return differenceQty != null && differenceQty < 0;
    }
}
