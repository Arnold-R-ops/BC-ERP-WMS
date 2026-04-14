package com.wms.system.entity.enums;

/**
 * 入库单状态枚举
 *
 * 状态流转：
 * User (Apply) → PENDING_APPROVAL
 * GM (Approve) → APPROVED_PLAN
 * Purchaser (Confirm) → AWAITING_RECEIVAL (生成批次码)
 * Warehouse (Receive) → COMPLETED
 *
 * 拒绝路径：
 * PENDING_APPROVAL/APPROVED_PLAN → REJECTED
 */
public enum InboundOrderStatus {
    /**
     * 待总经理审批
     */
    PENDING_APPROVAL("待总经理审批"),

    /**
     * 总经理已批，待采购员确认
     */
    APPROVED_PLAN("总经理已批，待采购员确认"),

    /**
     * 已确认，待仓库收货
     */
    AWAITING_RECEIVAL("已确认，待仓库收货"),

    /**
     * 完成
     */
    COMPLETED("完成"),

    /**
     * 拒绝
     */
    REJECTED("拒绝"),

    /**
     * 已取消：入库单取消（业务失败，AI 学习）
     *
     * 说明：
     * - 真实的业务失败（如供应商延期、计划变更）
     * - 必须关联 reason_code 或填写 remarks
     * - 数据保留供 AI 学习
     */
    CANCELLED("已取消"),

    /**
     * 已作废：系统作废（数据噪音，AI 过滤）
     *
     * 说明：
     * - 数据噪音（如员工录入错误、测试单）
     * - 财务审计中保留流水号
     * - AI 提取和业务统计中彻底过滤
     * - 不参与常规状态流转
     */
    VOIDED("已作废");

    private final String description;

    InboundOrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否可以由总经理审批
     */
    public boolean canApproveByGM() {
        return this == PENDING_APPROVAL;
    }

    /**
     * 判断是否可以由采购员确认
     */
    public boolean canConfirmByPurchaser() {
        return this == APPROVED_PLAN;
    }

    /**
     * 判断是否可以收货
     */
    public boolean canReceive() {
        return this == AWAITING_RECEIVAL;
    }

    /**
     * 判断是否可以拒绝
     */
    public boolean canReject() {
        return this == PENDING_APPROVAL || this == APPROVED_PLAN;
    }

    /**
     * 判断是否为终态
     */
    public boolean isFinalState() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED || this == VOIDED;
    }

    /**
     * 判断是否可以物理删除（仅草稿期）
     *
     * @return true 如果状态为 PENDING_APPROVAL
     */
    public boolean canPhysicallyDelete() {
        return this == PENDING_APPROVAL;
    }

    /**
     * 判断是否可以取消（业务取消）
     *
     * @return true 如果状态为 PENDING_APPROVAL 或 APPROVED_PLAN
     */
    public boolean canCancel() {
        return this == PENDING_APPROVAL || this == APPROVED_PLAN;
    }

    /**
     * 判断是否可以作废（系统作废）
     *
     * @return true 如果状态为 PENDING_APPROVAL 或 APPROVED_PLAN
     */
    public boolean canVoid() {
        return this == PENDING_APPROVAL || this == APPROVED_PLAN;
    }
}
