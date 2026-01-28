package com.wms.system.entity.enums;

/**
 * 库存流水来源类型枚举
 * 标识库存变动的业务场景，便于追溯和报表分析
 *
 * @author WMS Team
 * @since 2025-01-09
 */
public enum SourceType {
    /**
     * 采购入库：从供应商采购的商品入库
     */
    PURCHASE_IN("采购入库"),

    /**
     * 入库单入库：V3.5 新入库系统的入库流水
     */
    INBOUND_IN("入库单入库"),

    /**
     * 销售出库：客户订单发货出库
     */
    SALE_OUT("销售出库"),

    /**
     * 退货入库：客户退货商品入库
     */
    RETURN_IN("退货入库"),

    /**
     * 生产领料：生产部门领用原材料出库
     */
    PRODUCTION_OUT("生产领料"),

    /**
     * 成品入库：生产完成的成品入库
     */
    PRODUCTION_IN("成品入库"),

    /**
     * 调拨出库：从当前仓库调出到其他仓库
     */
    TRANSFER_OUT("调拨出库"),

    /**
     * 调拨入库：从其他仓库调入到当前仓库
     */
    TRANSFER_IN("调拨入库"),

    /**
     * 盘盈入库：盘点发现实际库存大于账面库存
     */
    INVENTORY_GAIN("盘盈入库"),

    /**
     * 盘亏出库：盘点发现实际库存小于账面库存
     */
    INVENTORY_LOSS("盘亏出库"),

    /**
     * 报废出库：商品过期、损坏等原因报废出库
     */
    SCRAP_OUT("报废出库"),

    /**
     * 赠品出库：营销活动赠送商品出库
     */
    GIFT_OUT("赠品出库"),

    /**
     * 样品出库：研发或市场部门领取样品
     */
    SAMPLE_OUT("样品出库"),

    /**
     * 手动调整：管理员手动修正库存（需要严格权限控制）
     */
    MANUAL_ADJUST("手动调整");

    private final String description;

    SourceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为入库类型
     */
    public boolean isInbound() {
        return this == PURCHASE_IN
            || this == INBOUND_IN
            || this == RETURN_IN
            || this == PRODUCTION_IN
            || this == TRANSFER_IN
            || this == INVENTORY_GAIN;
    }

    /**
     * 判断是否为出库类型
     */
    public boolean isOutbound() {
        return this == SALE_OUT
            || this == PRODUCTION_OUT
            || this == TRANSFER_OUT
            || this == INVENTORY_LOSS
            || this == SCRAP_OUT
            || this == GIFT_OUT
            || this == SAMPLE_OUT;
    }
}
