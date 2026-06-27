package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Order Item Entity (采购单明细实�?
 *
 * Master-Detail 架构的明细表，存储每个商品的采购信息�? *
 * Key Features:
 * - 支持分批入库（receivedQuantity 累加�? * - 第一阶段可选填�?expiryDate �?externalBatchCode
 * - 第二阶段必须确保 expiryDate 已填�? * - 每个明细项可生成多个 InventoryBatch（不同入库时间）
 *
 * Batch Generation Logic:
 * - Stage 1 (ORDERING): 数据可�? * - Stage 2 (IN_TRANSIT): 生成 Hashids 批次码，expiryDate 必填
 * - Stage 3 (COMPLETED): 分配库位，记�?entryDate
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management + Batch Management)
 */
@Entity
@Table(name = "purchase_order_item", indexes = {
    @Index(name = "idx_purchase_order_id", columnList = "purchase_order_id"),
    @Index(name = "idx_po_item_product_id", columnList = "product_id"),
    @Index(name = "idx_expiry_date", columnList = "expiry_date")  // FIFO 查询优化
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联采购单（多对一�?     *
     * FetchType: LAZY（延迟加载）
     * Optional: false（必须关联到采购单）
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_po_item_po"))
    private PurchaseOrder purchaseOrder;

    /**
     * 关联产品（多对一�?     *
     * FetchType: LAZY（延迟加载）
     * Optional: false（必须关联到产品�?     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_po_item_product"))
    private Product product;

    /**
     * 订单数量（采购订单中的数量）
     *
     * 第一阶段录入：创建采购单时填�?     */
    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    /**
     * 实收数量（实际入库数量，支持分批入库�?     *
     * 累加规则:
     * - 初始�? 0
     * - 每次入库: receivedQuantity += 本次入库数量
     * - 完全入库: receivedQuantity == orderedQuantity
     *
     * 状态判�?
     * - receivedQuantity == 0: 未入�?     * - 0 < receivedQuantity < orderedQuantity: 部分入库 (PARTIALLY_RECEIVED)
     * - receivedQuantity == orderedQuantity: 完全入库 (COMPLETED)
     */
    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    /**
     * Quantity already promised to sales backorders before physical receipt.
     */
    @Column(name = "committed_qty", nullable = false)
    @Builder.Default
    private Integer committedQty = 0;

    /**
     * 单价（采购单价）
     *
     * 隐私保护: STAFF 角色返回 null
     * 精度: 10位数字，2位小数（�?999999.99�?     */
    @Column(name = "unit_cost", nullable = true, precision = 10, scale = 2)
    private BigDecimal unitCost;

    /**
     * 保质期（产品过期日期�?     *
     * 录入规则:
     * - 第一阶段 (ORDERING): 可�?     * - 第二阶段 (IN_TRANSIT): 必填（生成批次码前必须确保已填写�?     *
     * 用�?
     * - FIFO 出库排序（优先出库最早过期的批次�?     * - 过期商品预警
     */
    @Column(name = "expiry_date", nullable = true)
    private LocalDate expiryDate;

    /**
     * 生产日期（产品生产日期，可选）
     */
    @Column(name = "production_date", nullable = true)
    private LocalDate productionDate;

    /**
     * 厂家批次码（外部批次号，可选）
     *
     * 用�?
     * - 供应商索赔追�?     * - 质量问题追踪
     *
     * 区别:
     * - externalBatchCode: 厂家提供的原始批次号
     * - InventoryBatch.batchCode: 系统生成�?Hashids 批次码（内部使用�?     */
    @Column(name = "external_batch_code", nullable = true, length = 100)
    private String externalBatchCode;

    /**
     * 备注
     */
    @Column(name = "remark", nullable = true, length = 500)
    private String remark;

    // ========== V4.2 历史价格快照 (Price Snapshot) ==========

    /**
     * 商品标准售价快照（V4.2 新增�?     *
     * 说明�?     * - 记录下单时刻 products.unit_price 的值（商品目录标价�?     * - �?unitCost（实际采购成本）区分：unitCost 是供应商报价�?     *   productPriceSnapshot 是系统标价，用于计算毛利空间参�?     * - 一经写入，不随 products.unit_price 后续变动而更�?     *
     * BI 用途：
     * - 成本利润率参�?= (productPriceSnapshot - unitCost) / productPriceSnapshot
     * - 采购价合理性分析（unitCost 应低�?productPriceSnapshot�?     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "product_price_snapshot", nullable = true, precision = 10, scale = 2)
    private java.math.BigDecimal productPriceSnapshot;

    /**
     * 关联批次（一对多关系�?     *
     * 说明:
     * - 第二阶段生成批次码时创建 InventoryBatch 记录
     * - 支持分批入库：同一明细项可生成多个批次（不�?entryDate�?     * - 每次入库操作可能生成新的 InventoryBatch 记录
     *
     * Cascade: ALL（级联保存、更新、删除）
     * OrphanRemoval: true（孤儿删除）
     * FetchType: LAZY（延迟加载）
     */
    @OneToMany(
        mappedBy = "purchaseOrderItem",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<InventoryBatch> batches = new ArrayList<>();

    // ========== 便利方法 (Helper Methods) ==========

    /**
     * 添加批次
     *
     * 自动维护双向关联关系
     *
     * @param batch 批次记录
     */
    public void addBatch(InventoryBatch batch) {
        batches.add(batch);
        batch.setPurchaseOrderItem(this);
    }

    /**
     * 删除批次
     *
     * 自动维护双向关联关系
     *
     * @param batch 批次记录
     */
    public void removeBatch(InventoryBatch batch) {
        batches.remove(batch);
        batch.setPurchaseOrderItem(null);
    }

    /**
     * 增加实收数量
     *
     * 用于分批入库场景，累加本次入库数�?     *
     * @param quantity 本次入库数量
     * @throws IllegalArgumentException 如果超过订单数量
     */
    public void increaseReceivedQuantity(Integer quantity) {
        if (this.receivedQuantity + quantity > this.orderedQuantity) {
            throw new IllegalArgumentException(
                String.format("Received quantity (%d + %d) exceeds ordered quantity (%d)",
                    this.receivedQuantity, quantity, this.orderedQuantity)
            );
        }
        this.receivedQuantity += quantity;
    }

    /**
     * 检查是否完全入�?     *
     * @return true 如果实收数量等于订单数量
     */
    public boolean isFullyReceived() {
        return this.receivedQuantity.equals(this.orderedQuantity);
    }

    /**
     * 检查是否部分入�?     *
     * @return true 如果实收数量大于 0 但小于订单数�?     */
    public boolean isPartiallyReceived() {
        return this.receivedQuantity > 0 && this.receivedQuantity < this.orderedQuantity;
    }

    /**
     * 获取剩余待入库数�?     *
     * @return 订单数量 - 实收数量
     */
    public Integer getRemainingQuantity() {
        return this.orderedQuantity - this.receivedQuantity;
    }

    public Integer getAvailableToPromiseQuantity() {
        return Math.max(0, getRemainingQuantity() - (committedQty == null ? 0 : committedQty));
    }

    public void commitToPromise(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return;
        }
        if (quantity > getAvailableToPromiseQuantity()) {
            throw new IllegalArgumentException("Committed quantity exceeds ATP quantity");
        }
        this.committedQty = (this.committedQty == null ? 0 : this.committedQty) + quantity;
    }

    /**
     * 计算总成�?     *
     * @return 订单数量 × 单价
     */
    public BigDecimal calculateTotalCost() {
        if (this.unitCost == null) {
            return BigDecimal.ZERO;
        }
        return this.unitCost.multiply(BigDecimal.valueOf(this.orderedQuantity));
    }

    /**
     * 检查是否需要生成批次码
     *
     * 判断规则:
     * - 状态为 ORDERING（尚未生成批次码�?     * - expiryDate 已填�?     *
     * @return true 如果满足生成批次码条�?     */
    public boolean needsGenerateBatchCode() {
        return this.batches.isEmpty() && this.expiryDate != null;
    }

    /**
     * 检查是否已生成批次�?     *
     * @return true 如果存在至少一个批次记�?     */
    public boolean hasBatchCode() {
        return !this.batches.isEmpty();
    }

    /**
     * 获取所有活跃批次（active = true�?     *
     * @return 活跃批次列表
     */
    public List<InventoryBatch> getActiveBatches() {
        return batches.stream()
            .filter(InventoryBatch::isActive)
            .toList();
    }

    /**
     * 获取所有已作废批次（active = false�?     *
     * @return 已作废批次列�?     */
    public List<InventoryBatch> getInactiveBatches() {
        return batches.stream()
            .filter(batch -> !batch.isActive())
            .toList();
    }
}

