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
    REJECTED("拒绝");

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
        return this == COMPLETED || this == REJECTED;
    }
}
