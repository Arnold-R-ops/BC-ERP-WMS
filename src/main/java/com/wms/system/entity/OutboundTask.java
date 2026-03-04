package com.wms.system.entity;

import com.wms.system.entity.enums.OutboundTaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 出库任务实体
 *
 * V3.7 架构：出库任务管理
 *
 * 说明：
 * - 存储出库任务信息
 * - 由智能分配算法生成
 * - 仓库员确认拣货后扣减库存
 *
 * 核心字段：
 * - salesOrderId: 销售订单ID
 * - salesOrderItemId: 销售订单明细ID
 * - assignedBatchId: 分配的批次ID
 * - locationId: 库位ID
 * - planQty: 计划出库数量
 * - actualQty: 实际出库数量
 * - status: 任务状态
 *
 * 业务流程：
 * 1. 智能分配算法生成出库任务（status = PENDING）
 * 2. 仓库员确认拣货（status = PICKING）
 * 3. 仓库员完成拣货（status = COMPLETED，扣减库存）
 *
 * 状态流转：
 * PENDING → PICKING → COMPLETED
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "outbound_tasks",
    indexes = {
        @Index(name = "idx_outbound_task_order", columnList = "sales_order_id"),
        @Index(name = "idx_outbound_task_batch", columnList = "assigned_batch_id"),
        @Index(name = "idx_outbound_task_status", columnList = "status"),
        @Index(name = "idx_outbound_task_item", columnList = "sales_order_item_id")
    }
)
public class OutboundTask extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 销售订单ID
     */
    @NotNull(message = "销售订单ID不能为空")
    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    /**
     * 销售订单实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", insertable = false, updatable = false)
    private SalesOrder salesOrder;

    /**
     * 销售订单明细ID
     */
    @NotNull(message = "销售订单明细ID不能为空")
    @Column(name = "sales_order_item_id", nullable = false)
    private Long salesOrderItemId;

    /**
     * 销售订单明细实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_item_id", insertable = false, updatable = false)
    private SalesOrderItem salesOrderItem;

    /**
     * 分配的批次ID
     */
    @NotNull(message = "批次ID不能为空")
    @Column(name = "assigned_batch_id", nullable = false)
    private Long assignedBatchId;

    /**
     * 批次实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_batch_id", insertable = false, updatable = false)
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
     * 计划出库数量
     *
     * 说明：
     * - 智能分配算法计算的出库数量
     * - 仓库员拣货时的参考数量
     */
    @NotNull(message = "计划出库数量不能为空")
    @Min(value = 1, message = "计划出库数量必须大于 0")
    @Column(name = "plan_qty", nullable = false)
    private Integer planQty;

    /**
     * 实际出库数量
     *
     * 说明：
     * - 仓库员确认拣货后填写的实际数量
     * - 通常等于 planQty，但可能因为实际情况有差异
     * - 默认值：0（未拣货）
     */
    @Min(value = 0, message = "实际出库数量不能为负数")
    @Column(name = "actual_qty", nullable = false)
    @Builder.Default
    private Integer actualQty = 0;

    /**
     * 任务状态
     *
     * 状态说明：
     * - PENDING: 待拣货
     * - PICKING: 拣货中
     * - COMPLETED: 已完成
     */
    @NotNull(message = "任务状态不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboundTaskStatus status;

    // ========== 仓库员信息 ==========

    /**
     * 拣货人ID
     */
    @Column(name = "picked_by")
    private Long pickedBy;

    /**
     * 拣货时间
     */
    @Column(name = "picked_at")
    private LocalDateTime pickedAt;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    @Column(length = 500)
    private String remark;

    // ========== 便利方法 ==========

    /**
     * 判断是否可以确认拣货
     *
     * @return true 如果状态为 PENDING 或 PICKING
     */
    public boolean canConfirmPicking() {
        return status != null && status.canConfirmPicking();
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
     * 判断是否正在处理中
     *
     * @return true 如果状态为 PENDING 或 PICKING
     */
    public boolean isInProgress() {
        return status != null && status.isInProgress();
    }
}
