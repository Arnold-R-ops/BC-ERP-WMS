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
    COMPLETED("已完成"),

    /**
     * 已取消：盘点任务取消（业务失败，AI 学习）
     *
     * 说明：
     * - 真实的业务失败（如盘点中断、数据异常）
     * - 必须关联 reason_code 或填写 remarks
     * - 数据保留供 AI 学习
     */
    CANCELLED("已取消"),

    /**
     * 已作废：系统作废（数据噪音，AI 过滤）
     *
     * 说明：
     * - 数据噪音（如员工录入错误、测试任务）
     * - 财务审计中保留流水号
     * - AI 提取和业务统计中彻底过滤
     * - 不参与常规状态流转
     */
    VOIDED("已作废");

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

    /**
     * 判断是否为终态
     *
     * @return true 如果状态为 COMPLETED, CANCELLED, 或 VOIDED
     */
    public boolean isFinalState() {
        return this == COMPLETED || this == CANCELLED || this == VOIDED;
    }

    /**
     * 判断是否可以物理删除（仅草稿期）
     *
     * @return true 如果状态为 CREATED
     */
    public boolean canPhysicallyDelete() {
        return this == CREATED;
    }

    /**
     * 判断是否可以取消（业务取消）
     *
     * @return true 如果状态为 CREATED 或 COUNTING
     */
    public boolean canCancel() {
        return this == CREATED || this == COUNTING;
    }

    /**
     * 判断是否可以作废（系统作废）
     *
     * @return true 如果状态为 CREATED 或 COUNTING
     */
    public boolean canVoid() {
        return this == CREATED || this == COUNTING;
    }
}
