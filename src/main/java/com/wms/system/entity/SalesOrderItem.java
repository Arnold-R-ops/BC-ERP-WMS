package com.wms.system.entity;

import com.wms.system.entity.enums.FulfillmentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * 销售订单明细实体
 *
 * V3.7 架构：销售订单明细管理
 *
 * 说明：
 * - 存储销售订单的商品明细
 * - 支持客户偏好设置（拒收临期品）
 * - 支持销售员手动指定批次
 *
 * 核心字段：
 * - salesOrderId: 销售订单ID
 * - productId: 产品ID
 * - quantity: 销售数量
 * - unitPrice: 单价
 * - subtotal: 小计金额
 * - rejectNearExpiry: 是否拒收临期品
 * - specifiedBatchIds: 指定批次ID（JSON 数组）
 *
 * 业务场景：
 * 1. 客户要求不接受临期商品 → rejectNearExpiry = true
 * 2. 销售员指定特定批次 → specifiedBatchIds = "[123, 456]"
 * 3. 智能分配算法根据这些设置分配库存
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "sales_order_items",
    indexes = {
        @Index(name = "idx_sales_item_order", columnList = "sales_order_id"),
        @Index(name = "idx_sales_item_product", columnList = "product_id")
    }
)
public class SalesOrderItem extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 销售订单ID
     */
    @NotNull(message = "销售订单ID不能为空")
    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    /**
     * 销售订单实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", insertable = false, updatable = false)
    private SalesOrder salesOrder;

    /**
     * 产品ID
     */
    @NotNull(message = "产品ID不能为空")
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * 产品实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    /**
     * 销售数量
     */
    @NotNull(message = "销售数量不能为空")
    @Min(value = 1, message = "销售数量必须大于 0")
    @Column(nullable = false)
    private Integer quantity;

    /**
     * V4.5 requested quantity. Kept separate from legacy quantity so the
     * commercial request can remain stable while fulfillment counters change.
     */
    @Column(name = "requested_qty", nullable = false)
    @Builder.Default
    private Integer requestedQty = 0;

    @Column(name = "allocated_qty", nullable = false)
    @Builder.Default
    private Integer allocatedQty = 0;

    @Column(name = "shipped_qty", nullable = false)
    @Builder.Default
    private Integer shippedQty = 0;

    @Column(name = "backorder_qty", nullable = false)
    @Builder.Default
    private Integer backorderQty = 0;

    @Column(name = "cancelled_qty", nullable = false)
    @Builder.Default
    private Integer cancelledQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 40)
    @Builder.Default
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.UNALLOCATED;

    /**
     * 单价
     *
     * 说明：
     * - 销售单价
     * - 用于风控检查（是否低于最低限价）
     */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.00", message = "单价不能为负数")
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * 小计金额
     *
     * 计算公式：subtotal = quantity * unitPrice
     */
    @NotNull(message = "小计金额不能为空")
    @DecimalMin(value = "0.00", message = "小计金额不能为负数")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    // ========== 客户偏好设置 ==========

    /**
     * 是否拒收临期品
     *
     * 说明：
     * - true: 客户拒收临期商品，智能分配算法会过滤掉临期批次
     * - false: 客户接受临期商品，按 FEFO 正常分配
     *
     * 临期判断：
     * - CURRENT_DATE + product.near_expiry_days >= batch.expiry_date
     *
     * 默认值：false
     */
    @Column(name = "reject_near_expiry", nullable = false)
    @Builder.Default
    private Boolean rejectNearExpiry = false;

    // ========== 销售员手动指定批次 ==========

    /**
     * 指定批次ID（JSON 数组）
     *
     * 说明：
     * - 销售员可以手动指定特定批次
     * - 智能分配算法会优先使用指定批次
     * - 格式：JSON 数组字符串，例如 "[123, 456, 789]"
     *
     * 使用场景：
     * 1. 客户指定特定批次
     * 2. 销售员根据库存情况手动选择
     * 3. 特殊订单需求
     *
     * 示例：
     * - "[123]" - 指定单个批次
     * - "[123, 456]" - 指定多个批次
     * - null 或 "" - 不指定批次，由系统自动分配
     */
    @Column(name = "specified_batch_ids", columnDefinition = "TEXT")
    private String specifiedBatchIds;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    @Column(length = 500)
    private String remark;

    // ========== V4.2 历史价格快照 (Price Snapshot) ==========

    /**
     * 商品标准售价快照（V4.2 新增）
     *
     * 说明：
     * - 记录下单时刻 products.unit_price 的值（商品目录标价）
     * - 与 unitPrice（成交价）区分：unitPrice 可由销售员协商调整，
     *   productPriceSnapshot 固化下单时刻的官方标价
     * - 一经写入，不随 products.unit_price 后续变动而更新（价格历史隔离）
     *
     * BI 用途：
     * - 折扣率 = (productPriceSnapshot - unitPrice) / productPriceSnapshot
     * - 优惠金额 = (productPriceSnapshot - unitPrice) * quantity
     * - 价格执行监控（异常折扣预警）
     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "product_price_snapshot", precision = 10, scale = 2)
    private BigDecimal productPriceSnapshot;

    // ========== 便利方法 ==========

    /**
     * 判断是否有指定批次
     *
     * @return true 如果 specifiedBatchIds 不为空
     */
    public boolean hasSpecifiedBatches() {
        return specifiedBatchIds != null && !specifiedBatchIds.trim().isEmpty();
    }

    /**
     * 计算小计金额
     *
     * @return quantity * unitPrice
     */
    public BigDecimal calculateSubtotal() {
        if (quantity == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
