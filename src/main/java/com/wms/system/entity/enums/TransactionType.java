package com.wms.system.entity.enums;

/**
 * 库存变动类型枚举
 * 记录库存流水的变动方向，用于财务对账和库存预测
 *
 * @author WMS Team
 * @since 2025-01-09
 */
public enum TransactionType {
    /**
     * 入库：增加库存（采购入库、退货入库、调拨入库等）
     */
    IN("入库", 1),

    /**
     * 出库：减少库存（销售出库、生产领料、调拨出库等）
     */
    OUT("出库", -1),

    /**
     * 调整：库存盘点后的数量调整（盘盈或盘亏）
     */
    ADJUST("调整", 0);

    private final String description;

    /**
     * 数量系数：IN=+1, OUT=-1, ADJUST=0（需配合实际变动数量判断正负）
     */
    private final int quantityFactor;

    TransactionType(String description, int quantityFactor) {
        this.description = description;
        this.quantityFactor = quantityFactor;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantityFactor() {
        return quantityFactor;
    }

    /**
     * 判断是否为入库操作
     */
    public boolean isInbound() {
        return this == IN;
    }

    /**
     * 判断是否为出库操作
     */
    public boolean isOutbound() {
        return this == OUT;
    }
}
