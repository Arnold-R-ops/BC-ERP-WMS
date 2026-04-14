package com.wms.system.entity.enums;

/**
 * 采购单状态枚举
 *
 * 三阶段状态流转：
 * ORDERING → IN_TRANSIT → PARTIALLY_RECEIVED → COMPLETED
 *
 * 支持分批入库和状态回退（IN_TRANSIT可回退至ORDERING）
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management)
 */
public enum PurchaseOrderStatus {
    /**
     * 下单中：采购单已创建，等待确认并生成批次码
     *
     * 允许操作：
     * - 修改采购单明细
     * - 确认并生成批次码（进入 IN_TRANSIT）
     */
    ORDERING("下单中"),

    /**
     * 待入库：批次码已生成，等待实物入库
     *
     * 允许操作：
     * - 确认入库（进入 PARTIALLY_RECEIVED 或 COMPLETED）
     * - 回退至 ORDERING（作废批次码）
     */
    IN_TRANSIT("待入库"),

    /**
     * 部分收货：部分商品已入库，等待剩余入库
     *
     * 允许操作：
     * - 继续入库（进入 COMPLETED）
     *
     * 说明：
     * - receivedQuantity < orderedQuantity
     * - 支持多次入库操作
     */
    PARTIALLY_RECEIVED("部分收货"),

    /**
     * 已入库：所有商品已入库完成
     *
     * 禁止操作：
     * - 不允许修改或回退
     * - 已生成库存和流水记录
     */
    COMPLETED("已入库"),

    /**
     * 已取消：采购单取消（业务失败，AI 学习）
     *
     * 说明：
     * - 真实的业务失败（如供应商断货、价格谈崩）
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

    PurchaseOrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否可以确认并生成批次码（进入第二阶段）
     *
     * @return true 如果状态为 ORDERING
     */
    public boolean canConfirmAndGenerateBatch() {
        return this == ORDERING;
    }

    /**
     * 判断是否可以确认入库（进入第三阶段）
     *
     * @return true 如果状态为 IN_TRANSIT 或 PARTIALLY_RECEIVED
     */
    public boolean canReceive() {
        return this == IN_TRANSIT || this == PARTIALLY_RECEIVED;
    }

    /**
     * 判断是否可以回退至 ORDERING 状态
     *
     * @return true 如果状态为 IN_TRANSIT
     */
    public boolean canRollbackToOrdering() {
        return this == IN_TRANSIT;
    }

    /**
     * 判断是否已完成（不可修改）
     *
     * @return true 如果状态为 COMPLETED, CANCELLED, 或 VOIDED
     */
    public boolean isFinalized() {
        return this == COMPLETED || this == CANCELLED || this == VOIDED;
    }

    /**
     * 判断是否可以物理删除（仅草稿期）
     *
     * @return true 如果状态为 ORDERING
     */
    public boolean canPhysicallyDelete() {
        return this == ORDERING;
    }

    /**
     * 判断是否可以取消（业务取消）
     *
     * @return true 如果状态为 ORDERING 或 IN_TRANSIT
     */
    public boolean canCancel() {
        return this == ORDERING || this == IN_TRANSIT;
    }

    /**
     * 判断是否可以作废（系统作废）
     *
     * @return true 如果状态为 ORDERING 或 IN_TRANSIT
     */
    public boolean canVoid() {
        return this == ORDERING || this == IN_TRANSIT;
    }

    /**
     * 判断是否正在入库流程中
     *
     * @return true 如果状态为 IN_TRANSIT 或 PARTIALLY_RECEIVED
     */
    public boolean isInReceivingProcess() {
        return this == IN_TRANSIT || this == PARTIALLY_RECEIVED;
    }
}
