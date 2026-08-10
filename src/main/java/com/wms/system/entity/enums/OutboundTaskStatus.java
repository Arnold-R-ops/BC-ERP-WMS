package com.wms.system.entity.enums;

/**
 * 出库任务状态枚举
 *
 * 状态流转：
 * PENDING → PICKING → COMPLETED
 * PENDING → VOIDED（仅受控历史测试数据归档）
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
public enum OutboundTaskStatus {
    /**
     * 待拣货：任务已创建，等待仓库员拣货
     *
     * 说明：
     * - 库存已预占（plan_qty）
     * - 等待仓库员确认拣货
     */
    PENDING("待拣货"),

    /**
     * 拣货中：仓库员正在拣货
     *
     * 说明：
     * - 仓库员已开始拣货操作
     * - 等待确认实际拣货数量
     */
    PICKING("拣货中"),

    /**
     * 已完成：拣货完成，库存已扣减
     *
     * 说明：
     * - actual_qty 已记录
     * - 库存已扣减
     * - 已生成库存流水记录
     */
    COMPLETED("已完成"),

    /**
     * 已作废：仅用于保留历史测试任务证据并从正常作业队列排除。
     * 不代表完成拣货，也不得产生库存流水。
     */
    VOIDED("已作废");

    private final String description;

    OutboundTaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否可以确认拣货
     *
     * @return true 如果状态为 PENDING 或 PICKING
     */
    public boolean canConfirmPicking() {
        return this == PENDING || this == PICKING;
    }

    /**
     * 判断是否已完成
     *
     * @return true 如果状态为 COMPLETED
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 判断是否正在处理中
     *
     * @return true 如果状态为 PENDING 或 PICKING
     */
    public boolean isInProgress() {
        return this == PENDING || this == PICKING;
    }
}
