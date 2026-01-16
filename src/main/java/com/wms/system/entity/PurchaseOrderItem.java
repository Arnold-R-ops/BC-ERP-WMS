package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Order Item Entity (采购单明细实体)
 *
 * Master-Detail 架构的明细表，存储每个商品的采购信息。
 *
 * Key Features:
 * - 支持分批入库（receivedQuantity 累加）
 * - 第一阶段可选填写 expiryDate 和 externalBatchCode
 * - 第二阶段必须确保 expiryDate 已填写
 * - 每个明细项可生成多个 InventoryBatch（不同入库时间）
 *
 * Batch Generation Logic:
 * - Stage 1 (ORDERING): 数据可选
 * - Stage 2 (IN_TRANSIT): 生成 Hashids 批次码，expiryDate 必填
 * - Stage 3 (COMPLETED): 分配库位，记录 entryDate
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management + Batch Management)
 */
@Entity
@Table(name = "purchase_order_item", indexes = {
    @Index(name = "idx_purchase_order_id", columnList = "purchase_order_id"),
    @Index(name = "idx_product_id", columnList = "product_id"),
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
     * 关联采购单（多对一）
     *
     * FetchType: LAZY（延迟加载）
     * Optional: false（必须关联到采购单）
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_po_item_po"))
    private PurchaseOrder purchaseOrder;

    /**
     * 关联产品（多对一）
     *
     * FetchType: LAZY（延迟加载）
     * Optional: false（必须关联到产品）
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_po_item_product"))
    private Product product;

    /**
     * 订单数量（采购订单中的数量）
     *
     * 第一阶段录入：创建采购单时填写
     */
    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    /**
     * 实收数量（实际入库数量，支持分批入库）
     *
     * 累加规则:
     * - 初始值: 0
     * - 每次入库: receivedQuantity += 本次入库数量
     * - 完全入库: receivedQuantity == orderedQuantity
     *
     * 状态判断:
     * - receivedQuantity == 0: 未入库
     * - 0 < receivedQuantity < orderedQuantity: 部分入库 (PARTIALLY_RECEIVED)
     * - receivedQuantity == orderedQuantity: 完全入库 (COMPLETED)
     */
    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    /**
     * 单价（采购单价）
     *
     * 隐私保护: STAFF 角色返回 null
     * 精度: 10位数字，2位小数（如 999999.99）
     */
    @Column(name = "unit_cost", nullable = true, precision = 10, scale = 2)
    private BigDecimal unitCost;

    /**
     * 保质期（产品过期日期）
     *
     * 录入规则:
     * - 第一阶段 (ORDERING): 可选
     * - 第二阶段 (IN_TRANSIT): 必填（生成批次码前必须确保已填写）
     *
     * 用途:
     * - FIFO 出库排序（优先出库最早过期的批次）
     * - 过期商品预警
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
     * 用途:
     * - 供应商索赔追溯
     * - 质量问题追踪
     *
     * 区别:
     * - externalBatchCode: 厂家提供的原始批次号
     * - InventoryBatch.batchCode: 系统生成的 Hashids 批次码（内部使用）
     */
    @Column(name = "external_batch_code", nullable = true, length = 100)
    private String externalBatchCode;

    /**
     * 备注
     */
    @Column(name = "remark", nullable = true, length = 500)
    private String remark;

    /**
     * 关联批次（一对多关系）
     *
     * 说明:
     * - 第二阶段生成批次码时创建 InventoryBatch 记录
     * - 支持分批入库：同一明细项可生成多个批次（不同 entryDate）
     * - 每次入库操作可能生成新的 InventoryBatch 记录
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
     * 用于分批入库场景，累加本次入库数量
     *
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
     * 检查是否完全入库
     *
     * @return true 如果实收数量等于订单数量
     */
    public boolean isFullyReceived() {
        return this.receivedQuantity.equals(this.orderedQuantity);
    }

    /**
     * 检查是否部分入库
     *
     * @return true 如果实收数量大于 0 但小于订单数量
     */
    public boolean isPartiallyReceived() {
        return this.receivedQuantity > 0 && this.receivedQuantity < this.orderedQuantity;
    }

    /**
     * 获取剩余待入库数量
     *
     * @return 订单数量 - 实收数量
     */
    public Integer getRemainingQuantity() {
        return this.orderedQuantity - this.receivedQuantity;
    }

    /**
     * 计算总成本
     *
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
     * - 状态为 ORDERING（尚未生成批次码）
     * - expiryDate 已填写
     *
     * @return true 如果满足生成批次码条件
     */
    public boolean needsGenerateBatchCode() {
        return this.batches.isEmpty() && this.expiryDate != null;
    }

    /**
     * 检查是否已生成批次码
     *
     * @return true 如果存在至少一个批次记录
     */
    public boolean hasBatchCode() {
        return !this.batches.isEmpty();
    }

    /**
     * 获取所有活跃批次（active = true）
     *
     * @return 活跃批次列表
     */
    public List<InventoryBatch> getActiveBatches() {
        return batches.stream()
            .filter(InventoryBatch::isActive)
            .toList();
    }

    /**
     * 获取所有已作废批次（active = false）
     *
     * @return 已作废批次列表
     */
    public List<InventoryBatch> getInactiveBatches() {
        return batches.stream()
            .filter(batch -> !batch.isActive())
            .toList();
    }
}
