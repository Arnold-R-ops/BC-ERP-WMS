package com.wms.system.entity;

import com.wms.system.entity.enums.StocktakeCycleType;
import com.wms.system.entity.enums.StocktakeStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 盘点任务实体
 *
 * V3.8 架构：智能盘点系统
 *
 * 说明：
 * - 支持月度/季度/年度/临时盘点
 * - 基于快照的盲盘功能
 * - 智能选品策略
 *
 * 核心字段：
 * - taskNo: 盘点任务编号（如 TK-202601-M01）
 * - cycleType: 盘点周期类型
 * - status: 任务状态
 * - snapshotTime: 快照时间（锁定库存的时间点）
 *
 * 状态流转：
 * CREATED → COUNTING → REVIEWING → COMPLETED
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
    name = "stocktake_tasks",
    indexes = {
        @Index(name = "idx_stocktake_task_no", columnList = "task_no"),
        @Index(name = "idx_stocktake_warehouse", columnList = "warehouse_id"),
        @Index(name = "idx_stocktake_status", columnList = "status"),
        @Index(name = "idx_stocktake_cycle_type", columnList = "cycle_type"),
        @Index(name = "idx_stocktake_created_at", columnList = "created_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stocktake_tasks_company_task_no", columnNames = {"company_id", "task_no"})
    }
)
public class StocktakeTask extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 盘点任务编号
     *
     * 格式：TK + yyyyMM + 周期类型 + 序号
     * 示例：
     * - TK-202601-M01（2026年1月月度盘点第1次）
     * - TK-202601-Q01（2026年1月季度盘点第1次）
     */
    @NotBlank(message = "盘点任务编号不能为空")
    @Size(max = 30, message = "盘点任务编号长度不能超过 30 个字符")
    @Column(name = "task_no", nullable = false, length = 30)
    private String taskNo;

    /**
     * 仓库ID
     */
    @NotNull(message = "仓库ID不能为空")
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /**
     * 仓库实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    private Warehouse warehouse;

    /**
     * 盘点周期类型
     *
     * 类型说明：
     * - MONTHLY: 月度盘点（选取动销率高或高价值商品）
     * - QUARTERLY: 季度盘点（全量商品）
     * - ANNUAL: 年度盘点（全量商品）
     * - ADHOC: 临时盘点（按需盘点）
     */
    @NotNull(message = "盘点周期类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false, length = 20)
    private StocktakeCycleType cycleType;

    /**
     * 任务状态
     *
     * 状态说明：
     * - CREATED: 已创建
     * - COUNTING: 盘点中
     * - REVIEWING: 审核中
     * - COMPLETED: 已完成
     */
    @NotNull(message = "任务状态不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StocktakeStatus status;

    /**
     * 快照时间（锁定库存的时间点）
     *
     * 说明：
     * - 创建任务时记录当前时间
     * - 所有盘点明细的 snapshot_qty 基于此时间点的库存
     * - 用于追溯和审计
     */
    @NotNull(message = "快照时间不能为空")
    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    // ========== 统计信息 ==========

    /**
     * 盘点明细总数
     */
    @Min(value = 0, message = "盘点明细总数不能为负数")
    @Column(name = "total_items", nullable = false)
    @Builder.Default
    private Integer totalItems = 0;

    /**
     * 已盘点明细数
     */
    @Min(value = 0, message = "已盘点明细数不能为负数")
    @Column(name = "counted_items", nullable = false)
    @Builder.Default
    private Integer countedItems = 0;

    /**
     * 有差异的明细数
     */
    @Min(value = 0, message = "有差异的明细数不能为负数")
    @Column(name = "difference_items", nullable = false)
    @Builder.Default
    private Integer differenceItems = 0;

    // ========== 审核信息 ==========

    /**
     * 审核人ID
     */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /**
     * 审核人姓名
     */
    @Size(max = 100, message = "审核人姓名长度不能超过 100 个字符")
    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * 审核意见
     */
    @Size(max = 500, message = "审核意见长度不能超过 500 个字符")
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    // ========== 创建人信息 ==========

    /**
     * 创建人ID
     */
    @NotNull(message = "创建人ID不能为空")
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * 创建人姓名
     */
    @NotBlank(message = "创建人姓名不能为空")
    @Size(max = 100, message = "创建人姓名长度不能超过 100 个字符")
    @Column(name = "created_by_name", nullable = false, length = 100)
    private String createdByName;

    // ========== 便利方法 ==========

    /**
     * 判断是否可以录入盘点数量
     *
     * @return true 如果状态为 COUNTING
     */
    public boolean canCount() {
        return status != null && status.canCount();
    }

    /**
     * 判断是否可以审核
     *
     * @return true 如果状态为 REVIEWING
     */
    public boolean canReview() {
        return status != null && status.canReview();
    }

    /**
     * 判断是否已完成
     *
     * @return true 如果状态为 COMPLETED
     */
    public boolean isCompleted() {
        return status != null && status.isCompleted();
    }

    /**
     * 判断是否可以开始盘点
     *
     * @return true 如果状态为 CREATED
     */
    public boolean canStartCounting() {
        return status != null && status.canStartCounting();
    }

    /**
     * 计算盘点进度百分比
     *
     * @return 盘点进度（0-100）
     */
    public int getProgress() {
        if (totalItems == null || totalItems == 0) {
            return 0;
        }
        return (int) ((countedItems * 100.0) / totalItems);
    }
}
