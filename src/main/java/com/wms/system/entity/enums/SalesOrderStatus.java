package com.wms.system.entity.enums;

/**
 * 销售订单状态枚举
 *
 * 状态流转：
 * DRAFT → PENDING_APPROVAL → APPROVED_AWAITING_SHIPMENT → SHIPPED
 *                          ↘ REJECTED
 *
 * 支持订单修改（仅 DRAFT 和 PENDING_APPROVAL 状态）
 * 支持订单取消（释放已预占库存）
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
public enum SalesOrderStatus {
    /**
     * 草稿：订单已创建，等待提交
     *
     * 允许操作：
     * - 修改订单明细
     * - 提交订单（进入 PENDING_APPROVAL 或 APPROVED_AWAITING_SHIPMENT）
     * - 取消订单
     */
    DRAFT("草稿"),

    /**
     * 待审批：订单已提交，等待经理审批
     *
     * 触发条件：
     * - 单价低于最低限价
     * - 订单总额超过审批阈值
     *
     * 允许操作：
     * - 经理审批通过（进入 APPROVED_AWAITING_SHIPMENT）
     * - 经理拒绝（进入 REJECTED）
     * - 修改订单明细
     * - 取消订单
     */
    PENDING_APPROVAL("待审批"),

    /**
     * 已批准待发货：订单已审批通过，库存已分配，等待仓库拣货
     *
     * 说明：
     * - 库存已预占（生成 outbound_tasks）
     * - 等待仓库员确认拣货
     *
     * 允许操作：
     * - 仓库员确认拣货（进入 SHIPPED）
     * - 取消订单（释放已预占库存）
     */
    APPROVED_AWAITING_SHIPMENT("已批准待发货"),

    /**
     * 已发货：所有商品已拣货完成，库存已扣减
     *
     * 禁止操作：
     * - 不允许修改或取消
     * - 已生成库存流水记录
     */
    SHIPPED("已发货"),

    /**
     * 已拒绝：经理审批拒绝
     *
     * 说明：
     * - 订单终止，不再流转
     * - 记录拒绝原因
     */
    REJECTED("已拒绝"),

    /**
     * 已取消：订单取消（业务流程终止）
     *
     * 说明：
     * - 用于订单异常终止场景
     * - 释放已预占的库存
     * - 不参与常规状态流转
     */
    CANCELLED("已取消");

    private final String description;

    SalesOrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否可以修改订单
     *
     * @return true 如果状态为 DRAFT 或 PENDING_APPROVAL
     */
    public boolean canModify() {
        return this == DRAFT || this == PENDING_APPROVAL;
    }

    /**
     * 判断是否可以取消订单
     *
     * @return true 如果状态为 DRAFT, PENDING_APPROVAL, 或 APPROVED_AWAITING_SHIPMENT
     */
    public boolean canCancel() {
        return this == DRAFT || this == PENDING_APPROVAL || this == APPROVED_AWAITING_SHIPMENT;
    }

    /**
     * 判断是否可以审批
     *
     * @return true 如果状态为 PENDING_APPROVAL
     */
    public boolean canApprove() {
        return this == PENDING_APPROVAL;
    }

    /**
     * 判断是否已完成（不可修改）
     *
     * @return true 如果状态为 SHIPPED, REJECTED, 或 CANCELLED
     */
    public boolean isFinalized() {
        return this == SHIPPED || this == REJECTED || this == CANCELLED;
    }

    /**
     * 判断是否需要审批
     *
     * @return true 如果状态为 PENDING_APPROVAL
     */
    public boolean needsApproval() {
        return this == PENDING_APPROVAL;
    }

    /**
     * 判断是否已批准（等待发货或已发货）
     *
     * @return true 如果状态为 APPROVED_AWAITING_SHIPMENT 或 SHIPPED
     */
    public boolean isApproved() {
        return this == APPROVED_AWAITING_SHIPMENT || this == SHIPPED;
    }
}
