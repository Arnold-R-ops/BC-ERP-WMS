package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Inventory Batch Entity (批次管理实体 - V3.3 架构：多库位批次管理)
 *
 * ⚠️ V3.0 架构决策：废弃 Inventory 汇总表
 * - InventoryBatch 是唯一库存数据源（Single Source of Truth）
 * - 所有库存查询基于本表实时聚合（SUM(quantity) WHERE active = true）
 * - 性能优化：使用 Redis 缓存热点商品库存汇总
 *
 * ⭐ V3.3 架构升级：多库位批次管理
 * - 同一 batch_code 可以存在于多个库位（一批货分散存放）
 * - 新增 locationCode 字段：库位编号字符串（如 "A-1-101"）
 * - 取消 batch_code 唯一约束，改用复合索引 (batch_code, location_code)
 * - 支持 Split Putaway：入库时将一个批次分配到多个库位
 *
 * Key Features:
 * - Hashids 批次码生成（如 R7M4K9，6字符短码）
 * - 批次-库位多对多设计（一批次多库位）
 * - 时间字段区分：createdAt（批次码生成） vs entryDate（实物入库）
 * - 乐观锁并发控制（@Version）
 * - 支持状态回退（active 标志作废批次）
 *
 * Three-Stage Workflow:
 * - Stage 1 (ORDERING): 采购单创建，批次未生成
 * - Stage 2 (IN_TRANSIT): 生成批次码，createdAt 记录
 * - Stage 3 (COMPLETED): 分配库位，entryDate 记录
 *
 * Multi-Location Example:
 * - Batch R7M4K9 (100 units total):
 *   - Location A-1-101: 60 units
 *   - Location A-1-102: 40 units
 * - Query by batch_code returns 2 rows
 * - Total quantity = 60 + 40 = 100
 *
 * @author WMS Team
 * @since 2025-01-13
 * @version 3.3 (Multi-Location Batch Management)
 */
@Entity
@Table(name = "inventory_batch", indexes = {
    @Index(name = "idx_batch_code", columnList = "batch_code"),  // V3.3: 移除 unique 约束
    @Index(name = "idx_batch_location", columnList = "batch_code, location_code"),  // V3.3: 复合索引
    @Index(name = "idx_product_expiry_active", columnList = "product_id, expiry_date, active"),  // FIFO 查询核心
    @Index(name = "idx_location_id", columnList = "location_id"),
    @Index(name = "idx_location_code", columnList = "location_code"),  // V3.3: 库位编号索引
    @Index(name = "idx_active", columnList = "active"),
    @Index(name = "idx_entry_date", columnList = "entry_date")  // 财务周转率查询
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 系统批次码（Hashids 生成，全局唯一）
     *
     * 生成规则:
     * - 输入: productId + timestamp + randomSeed
     * - 输出: 6字符字母数字码（如 R7M4K9）
     * - 算法: Hashids 1.0.3
     * - Salt: ${BATCH_SALT} 环境变量
     *
     * V3.5 更新：支持 SPU-SKU-DATE 格式
     * - 格式: SPU001-SKU001-20260126 (最长约30字符)
     * - 支持序号后缀: SPU001-SKU001-20260126-01
     *
     * 特点:
     * - 短码（6字符）易于打印和扫码
     * - 非连续性（不暴露业务信息）
     * - 可逆编码（可解码验证）
     * - V3.3: 不再全局唯一（同一批次可分散多个库位）
     *
     * 用途:
     * - 内部贴标、扫码识别
     * - FIFO 出库排序
     * - 批次追踪和溯源
     */
    @Column(name = "batch_code", nullable = false, length = 50)
    private String batchCode;

    // ========== V3.3 多库位批次管理 (Multi-Location Batch Management) ==========

    /**
     * 库位编号（V3.3 新增）
     *
     * 说明：
     * - 存储库位的字符串编号（如 "A-1-101", "B-2-205"）
     * - 与 batch_code 组合，唯一标识一条库存记录
     * - 支持同一批次在多个库位分散存放
     *
     * 业务场景：
     * - Split Putaway: 100 箱货物，60 箱放 A-1-101，40 箱放 A-1-102
     * - 库位清理策略: 优先扣减数量较小的库位，腾空货架
     * - 路径优化: 按库位路径排序，优化拣货路线
     *
     * 命名规范建议：
     * - 区域-巷道-货架-层-位（如 A-01-05-02-03）
     * - 或简化为 区域-编号（如 A-101）
     * - 建议长度不超过 20 字符
     *
     * 与 location (Location 实体) 的关系：
     * - location: 外键关联，用于复杂查询（如查询某库区所有批次）
     * - locationCode: 冗余字段，用于快速查询和显示
     * - 两者保持同步，locationCode = location.code
     *
     * @since V3.3
     */
    @Column(name = "location_code", nullable = false, length = 50)
    private String locationCode;

    /**
     * 关联采购单明细（多对一）
     *
     * 用于回溯批次来源和采购订单
     * V3.5: 改为可选，支持入库单系统（不依赖采购单）
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "purchase_order_item_id", nullable = true, foreignKey = @ForeignKey(name = "fk_batch_po_item"))
    private PurchaseOrderItem purchaseOrderItem;

    /**
     * 关联产品（多对一）
     *
     * 冗余设计: 方便直接查询（无需通过 purchaseOrderItem.product）
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_batch_product"))
    private Product product;

    /**
     * 关联库位（多对一）
     *
     * 第三阶段分配:
     * - Stage 2: location = null（批次码已生成，库位未分配）
     * - Stage 3: location = 具体库位（实物入库，分配库位）
     *
     * 批次-库位分离设计:
     * - 批次码不包含库位 ID（支持未来库位变更）
     * - 库位可变更，批次码不变（标签无需重新打印）
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "location_id", nullable = true, foreignKey = @ForeignKey(name = "fk_batch_location"))
    private Location location;

    /**
     * 批次当前库存数量（V3.0 核心字段：唯一库存数据源）
     *
     * 动态变化:
     * - 初始值: 入库时设置（= initialQuantity）
     * - 出库时: quantity -= 出库数量
     * - 调整时: quantity = 调整后数量
     *
     * 查询总库存:
     * SELECT SUM(quantity) FROM inventory_batch
     * WHERE product_id = ? AND active = true
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Quantity reserved by approved sales orders but not yet shipped.
     *
     * Available quantity = quantity - reservedQuantity.
     */
    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    /**
     * 初始入库数量（不可变）
     *
     * 用途:
     * - 库存周转率计算
     * - 出库比例统计
     * - 审计追踪
     */
    @Column(name = "initial_quantity", nullable = false)
    private Integer initialQuantity;

    /**
     * 保质期（产品过期日期）
     *
     * 用途:
     * - FIFO 出库排序（ORDER BY expiry_date ASC）
     * - 过期商品预警
     * - 自动拦截过期批次出库
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /**
     * 生产日期（产品生产日期，可选）
     */
    @Column(name = "production_date", nullable = true)
    private LocalDate productionDate;

    /**
     * 厂家批次码（外部批次号，可选）
     *
     * 区别:
     * - batchCode: 系统内部批次码（Hashids，用于内部贴标扫码）
     * - externalBatchCode: 厂家原始批次码（用于供应商索赔追溯）
     */
    @Column(name = "external_batch_code", nullable = true, length = 100)
    private String externalBatchCode;

    /**
     * 实物入库时间（第三阶段记录）
     *
     * ⚠️ 关键时间字段区别:
     * - createdAt (继承自 BaseEntity): 批次码生成时间（第二阶段）
     * - entryDate: 实物入库时间（第三阶段）
     *
     * 用途:
     * - 财务 DSI 计算（Days Sales in Inventory）
     * - 库存周转天数统计
     * - 分批入库时间区分
     *
     * 说明:
     * - 第二阶段: entryDate = null（批次码已生成，实物未入库）
     * - 第三阶段: entryDate = 当前服务器时间（实物已入库）
     */
    @Column(name = "entry_date", nullable = true)
    private LocalDateTime entryDate;

    /**
     * 是否有效（批次状态标志）
     *
     * 状态流转:
     * - true: 正常批次（可用于查询和出库）
     * - false: 已作废批次（状态回退时标记）
     *
     * 用途:
     * - 状态回退: IN_TRANSIT → ORDERING 时，批次 active = false
     * - 库存查询: 只统计 active = true 的批次
     * - 审计追踪: 保留作废批次记录（不物理删除）
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * 备注（状态回退时记录作废原因）
     *
     * 格式示例:
     * - "Invalidated on 2025-01-13T10:30:00: 供应商更换"
     * - "Invalidated on 2025-01-13T11:00:00: 数据错误，重新录入"
     */
    @Column(name = "remark", nullable = true, length = 500)
    private String remark;

    /**
     * 乐观锁版本号
     *
     * 并发控制:
     * - 库存扣减时使用乐观锁防止超卖
     * - 碰撞时自动重试（@Retryable，最多3次）
     * - 重试失败抛出 STOCK_CONCURRENCY_CONFLICT 错误
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    // ========== 便利方法 (Helper Methods) ==========

    /**
     * 减少库存数量（出库操作）
     *
     * @param deductQuantity 扣减数量
     * @throws IllegalArgumentException 如果扣减数量大于当前库存
     */
    public void decreaseQuantity(Integer deductQuantity) {
        if (this.quantity < deductQuantity) {
            throw new IllegalArgumentException(
                String.format("Insufficient batch stock: batchCode=%s, current=%d, requested=%d",
                    this.batchCode, this.quantity, deductQuantity)
            );
        }
        this.quantity -= deductQuantity;
    }

    public int getAvailableQuantity() {
        return this.quantity - (this.reservedQuantity == null ? 0 : this.reservedQuantity);
    }

    public void reserveQuantity(Integer reserveQuantity) {
        if (reserveQuantity == null || reserveQuantity <= 0) {
            throw new IllegalArgumentException("Reserve quantity must be positive");
        }
        if (getAvailableQuantity() < reserveQuantity) {
            throw new IllegalArgumentException(
                String.format("Insufficient available stock: batchCode=%s, available=%d, requested=%d",
                    this.batchCode, getAvailableQuantity(), reserveQuantity)
            );
        }
        this.reservedQuantity = (this.reservedQuantity == null ? 0 : this.reservedQuantity) + reserveQuantity;
    }

    public void releaseReservedQuantity(Integer releaseQuantity) {
        if (releaseQuantity == null || releaseQuantity <= 0) {
            return;
        }
        int currentReserved = this.reservedQuantity == null ? 0 : this.reservedQuantity;
        if (releaseQuantity > currentReserved) {
            throw new IllegalArgumentException(
                String.format("Release quantity exceeds reserved quantity: batchCode=%s, reserved=%d, release=%d",
                    this.batchCode, currentReserved, releaseQuantity)
            );
        }
        this.reservedQuantity = currentReserved - releaseQuantity;
    }

    public void consumeReservedQuantity(Integer consumeQuantity) {
        releaseReservedQuantity(consumeQuantity);
        decreaseQuantity(consumeQuantity);
    }

    /**
     * 增加库存数量（退货、调整操作）
     *
     * @param addQuantity 增加数量
     */
    public void increaseQuantity(Integer addQuantity) {
        this.quantity += addQuantity;
    }

    /**
     * 标记为已作废（状态回退）
     *
     * @param reason 作废原因
     */
    public void markAsInactive(String reason) {
        this.active = false;
        this.remark = String.format("Invalidated on %s: %s",
            LocalDateTime.now().toString().replace('T', ' '), reason);
    }

    /**
     * 分配库位（第三阶段入库）
     *
     * @param location 库位
     */
    public void assignLocation(Location location) {
        this.location = location;
        this.locationCode = location != null ? location.getLocationCode() : null;
        this.entryDate = LocalDateTime.now();  // 记录实物入库时间
    }

    /**
     * 检查是否已入库（库位已分配）
     *
     * @return true 如果库位不为空且 entryDate 已记录
     */
    public boolean isReceived() {
        return this.location != null && this.entryDate != null;
    }

    /**
     * 检查是否库存已耗尽
     *
     * @return true 如果数量为 0
     */
    public boolean isExhausted() {
        return this.quantity == 0;
    }

    /**
     * 检查是否已过期
     *
     * @return true 如果当前日期超过保质期
     */
    public boolean isExpired() {
        return LocalDate.now().isAfter(this.expiryDate);
    }

    /**
     * 检查是否即将过期（7天内过期）
     *
     * @return true 如果距离过期日期小于等于 7 天
     */
    public boolean isExpiringSoon() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        return !this.expiryDate.isBefore(today) && this.expiryDate.isBefore(sevenDaysLater);
    }

    /**
     * 获取剩余天数（距离过期日期）
     *
     * @return 剩余天数（负数表示已过期）
     */
    public long getDaysUntilExpiry() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), this.expiryDate);
    }

    /**
     * 获取库存使用率（已出库比例）
     *
     * @return 已出库比例（0.0 ~ 1.0）
     */
    public double getUsageRate() {
        return 1.0 - ((double) this.quantity / this.initialQuantity);
    }

    /**
     * 检查批次是否有效（兼容 Lombok 命名规范）
     *
     * 说明:
     * - Lombok 对 Boolean 类型生成 getActive() 方法
     * - 此方法提供 isActive() 命名，符合 boolean 语义习惯
     * - 处理 null 值，返回 false（防御性编程）
     *
     * @return true 如果批次有效，false 如果无效或 null
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(this.active);
    }
}
