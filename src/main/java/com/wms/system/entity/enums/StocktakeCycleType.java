package com.wms.system.entity.enums;

/**
 * 盘点周期类型枚举
 *
 * V3.8 架构：智能盘点系统
 *
 * 周期类型：
 * - MONTHLY: 月度盘点（选取动销率高或高价值商品）
 * - QUARTERLY: 季度盘点（全量商品）
 * - ANNUAL: 年度盘点（全量商品）
 * - ADHOC: 临时盘点（按需盘点）
 *
 * @author WMS Team
 * @since 2026-01-29
 * @version 3.8 (Smart Stocktake System)
 */
public enum StocktakeCycleType {
    /**
     * 月度盘点
     *
     * 选品策略：
     * - 选取动销率高的商品（最近30天有变动）
     * - 选取高价值商品（单价高或库存金额大）
     * - 目的：快速发现高频商品的库存差异
     */
    MONTHLY("月度盘点"),

    /**
     * 季度盘点
     *
     * 选品策略：
     * - 选取全量商品
     * - 或覆盖上一季度未盘过的所有冷门品
     * - 目的：全面盘点，确保库存准确性
     */
    QUARTERLY("季度盘点"),

    /**
     * 年度盘点
     *
     * 选品策略：
     * - 全量商品盘点
     * - 目的：年度财务审计
     */
    ANNUAL("年度盘点"),

    /**
     * 临时盘点
     *
     * 选品策略：
     * - 按需选择商品
     * - 目的：处理异常情况或特殊需求
     */
    ADHOC("临时盘点");

    private final String description;

    StocktakeCycleType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为周期性盘点
     *
     * @return true 如果是 MONTHLY, QUARTERLY, 或 ANNUAL
     */
    public boolean isPeriodic() {
        return this == MONTHLY || this == QUARTERLY || this == ANNUAL;
    }

    /**
     * 判断是否需要全量盘点
     *
     * @return true 如果是 QUARTERLY 或 ANNUAL
     */
    public boolean isFullInventory() {
        return this == QUARTERLY || this == ANNUAL;
    }
}
