package com.wms.system.entity.enums;

/**
 * 盘点任务状态枚举
 *
 * V3.8 架构：智能盘点系统
 *
 * 状态流转：
 * CREATED → COUNTING → REVIEWING → COMPLETED
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
public enum StocktakeStatus {
    /**
     * 已创建
     *
     * 说明：
     * - 盘点任务已创建，快照已生成
     * - 等待开始盘点
     */
    CREATED("已创建"),

    /**
     * 盘点中
     *
     * 说明：
     * - 仓库员正在进行盘点
     * - 可以录入实盘数量
     */
    COUNTING("盘点中"),

    /**
     * 审核中
     *
     * 说明：
     * - 盘点完成，等待审核
     * - 审核差异是否合理
     */
    REVIEWING("审核中"),

    /**
     * 已完成
     *
     * 说明：
     * - 审核通过，已平账
     * - 库存已调整
     */
    COMPLETED("已完成");

    private final String description;

    StocktakeStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否可以录入盘点数量
     *
     * @return true 如果状态为 COUNTING
     */
    public boolean canCount() {
        return this == COUNTING;
    }

    /**
     * 判断是否可以审核
     *
     * @return true 如果状态为 REVIEWING
     */
    public boolean canReview() {
        return this == REVIEWING;
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
     * 判断是否可以开始盘点
     *
     * @return true 如果状态为 CREATED
     */
    public boolean canStartCounting() {
        return this == CREATED;
    }
}
