package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 补货建议 DTO
 * 库存预测服务的返回结果，包含补货相关的所有信息
 *
 * 业务逻辑：
 * 建议补货量 = (日均出库量 × 采购提前期) + 安全库存
 *
 * 使用场景：
 * 1. 库存预警页面：展示需要补货的商品列表
 * 2. 采购订单生成：根据补货建议自动生成采购单
 * 3. 库存分析报表：分析商品的库存周转情况
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderSuggestion {

    /**
     * 商品ID
     */
    private Long productSkuId;

    /**
     * 商品条形码
     */
    private String barcode;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 商品规格
     */
    private String specification;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 供应商名称
     */
    private String supplier;

    /**
     * 当前总库存（所有库位汇总）
     */
    private Integer currentStock;

    /**
     * 安全库存（最小库存阈值）
     */
    private Integer minStock;

    /**
     * 采购提前期（天数）
     */
    private Integer leadTime;

    /**
     * 日均出库量（基于最近N天的出库流水计算）
     */
    private Double dailyAverageOutbound;

    /**
     * 计算周期（天数，例如：30 表示基于最近30天的数据）
     */
    private Integer calculationPeriod;

    /**
     * 建议补货量
     * 公式：(日均出库量 × 采购提前期) + 安全库存
     */
    private Integer suggestedReorderQuantity;

    /**
     * 预计消耗完的天数
     * 公式：当前库存 / 日均出库量
     * 如果小于采购提前期，说明需要紧急补货
     * null 表示统计周期内没有出库记录，无法按当前销速预测缺货时间
     */
    private Double estimatedDaysUntilStockout;

    /**
     * 紧急程度（枚举类型）
     * - CRITICAL: 极度紧急（库存已低于安全库存）
     * - HIGH: 高度紧急（预计N天内耗尽，且N < 采购提前期）
     * - MEDIUM: 中度紧急（预计N天内耗尽，且N ≈ 采购提前期）
     * - LOW: 低度紧急（库存充足）
     */
    private UrgencyLevel urgencyLevel;

    /**
     * 建议补货金额
     * 公式：建议补货量 × 单价
     */
    private BigDecimal estimatedCost;

    /**
     * 备注信息（可选）
     */
    private String remark;

    /**
     * 紧急程度枚举
     */
    public enum UrgencyLevel {
        /**
         * 极度紧急：当前库存 < 安全库存
         */
        CRITICAL("极度紧急", 1),

        /**
         * 高度紧急：预计耗尽天数 < 采购提前期
         */
        HIGH("高度紧急", 2),

        /**
         * 中度紧急：预计耗尽天数 ≈ 采购提前期（±3天）
         */
        MEDIUM("中度紧急", 3),

        /**
         * 低度紧急：库存充足
         */
        LOW("低度紧急", 4);

        private final String description;
        private final int priority;  // 优先级（数字越小，优先级越高）

        UrgencyLevel(String description, int priority) {
            this.description = description;
            this.priority = priority;
        }

        public String getDescription() {
            return description;
        }

        public int getPriority() {
            return priority;
        }
    }

    /**
     * 业务方法：计算紧急程度
     * 根据当前库存和预计消耗天数自动判断
     */
    public void calculateUrgencyLevel() {
        if (currentStock < minStock) {
            // 当前库存 < 安全库存：极度紧急
            this.urgencyLevel = UrgencyLevel.CRITICAL;
        } else if (estimatedDaysUntilStockout == null) {
            // No outbound history and stock is not below the safety threshold.
            this.urgencyLevel = UrgencyLevel.LOW;
        } else if (estimatedDaysUntilStockout < leadTime) {
            // 预计耗尽天数 < 采购提前期：高度紧急
            this.urgencyLevel = UrgencyLevel.HIGH;
        } else if (Math.abs(estimatedDaysUntilStockout - leadTime) <= 3) {
            // 预计耗尽天数 ≈ 采购提前期（±3天）：中度紧急
            this.urgencyLevel = UrgencyLevel.MEDIUM;
        } else {
            // 库存充足：低度紧急
            this.urgencyLevel = UrgencyLevel.LOW;
        }
    }

    /**
     * 业务方法：计算建议补货金额
     */
    public void calculateEstimatedCost() {
        if (unitPrice != null && suggestedReorderQuantity != null) {
            this.estimatedCost = unitPrice.multiply(BigDecimal.valueOf(suggestedReorderQuantity));
        }
    }

    /**
     * 业务方法：检查是否需要补货
     */
    public boolean needsReorder() {
        return urgencyLevel == UrgencyLevel.CRITICAL
            || urgencyLevel == UrgencyLevel.HIGH
            || urgencyLevel == UrgencyLevel.MEDIUM;
    }
}
