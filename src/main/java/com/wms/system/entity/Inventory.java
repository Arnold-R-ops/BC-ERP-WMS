package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 库存实体类（核心表）
 * 记录"哪个商品在哪个库位有多少库存"的实时快照
 *
 * 业务说明：
 * - 每个 (商品 + 库位) 组合对应一条库存记录
 * - quantity: 当前可用库存数量（实时更新）
 * - version: 乐观锁版本号，防止并发更新冲突
 *
 * 并发控制机制（乐观锁）：
 * 场景：两个用户同时对同一库位的同一商品进行出库操作
 * 1. 用户A 读取库存 quantity=100, version=1
 * 2. 用户B 读取库存 quantity=100, version=1
 * 3. 用户A 提交：quantity=90, version=2（成功）
 * 4. 用户B 提交：quantity=80, version=2（失败！因为数据库的 version 已经变成 2）
 * 5. 系统提示用户B："库存已被修改，请重新操作"
 *
 * 技术实现：
 * - @Version 注解会在每次更新时自动递增版本号
 * - 更新失败时抛出 OptimisticLockException，业务层需捕获并重试
 *
 * ERP 扩展预留：
 * - 未来可增加批次号字段（batchNumber）
 * - 可增加生产日期字段（productionDate）
 * - 可增加过期日期字段（expiryDate）
 * - 可增加锁定数量字段（lockedQuantity，用于预留库存）
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "inventory",
    indexes = {
        @Index(name = "idx_inventory_product_id", columnList = "product_id"),
        @Index(name = "idx_inventory_location_id", columnList = "location_id")
    },
    uniqueConstraints = {
        // 确保同一个库位只能存储一个商品的一个批次（当前不考虑批次，所以一个库位一个商品）
        @UniqueConstraint(name = "uk_inventory_company_product_location", columnNames = {"company_id", "product_id", "location_id"})
    }
)
public class Inventory extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联商品（多对一关系）
     * - 多条库存记录可以对应同一个商品（不同库位）
     * - FetchType.LAZY: 延迟加载，仅在访问 product 属性时才查询商品信息（提升性能）
     * - JoinColumn: 指定外键列名为 product_id
     */
    @NotNull(message = "商品不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_product"))
    private Product product;

    /**
     * 关联库位（多对一关系）
     * - 多条库存记录可以对应同一个库位（不同商品）
     * - FetchType.LAZY: 延迟加载
     * - JoinColumn: 指定外键列名为 location_id
     */
    @NotNull(message = "库位不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inventory_location"))
    private Location location;

    /**
     * 当前库存数量
     * 规则：
     * - 最小值：0（不允许负库存，业务层需校验）
     * - 入库时：quantity += 入库数量
     * - 出库时：quantity -= 出库数量（需先检查库存是否充足）
     */
    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存数量不能为负数")
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /**
     * 乐观锁版本号（用于并发控制）
     * 工作原理：
     * 1. 每次更新库存时，JPA 会自动执行如下 SQL：
     *    UPDATE inventory SET quantity = ?, version = version + 1
     *    WHERE id = ? AND version = ?
     * 2. 如果 WHERE 条件匹配失败（说明数据已被其他事务修改），更新失败
     * 3. JPA 抛出 OptimisticLockException
     *
     * 业务处理：
     * - 捕获异常后，可以重新加载数据并重试
     * - 或者提示用户"库存已变化，请刷新后重试"
     *
     * 注意：
     * - 不要手动修改 version 字段，JPA 会自动管理
     * - 初始值为 0，每次更新后自动 +1
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * 备注信息（可选）
     * 可记录特殊说明，如："临期商品"、"质检待定"等
     */
    @Column(length = 500)
    private String remark;

    /**
     * 业务方法：入库（增加库存）
     * @param inboundQuantity 入库数量
     * @throws IllegalArgumentException 如果入库数量 <= 0
     */
    public void increaseQuantity(Integer inboundQuantity) {
        if (inboundQuantity == null || inboundQuantity <= 0) {
            throw new IllegalArgumentException("入库数量必须大于 0");
        }
        this.quantity += inboundQuantity;
    }

    /**
     * 业务方法：出库（减少库存）
     * @param outboundQuantity 出库数量
     * @throws IllegalArgumentException 如果出库数量 <= 0
     * @throws IllegalStateException 如果库存不足
     */
    public void decreaseQuantity(Integer outboundQuantity) {
        if (outboundQuantity == null || outboundQuantity <= 0) {
            throw new IllegalArgumentException("出库数量必须大于 0");
        }
        if (this.quantity < outboundQuantity) {
            throw new IllegalStateException(
                String.format("库存不足！当前库存：%d，出库数量：%d", this.quantity, outboundQuantity)
            );
        }
        this.quantity -= outboundQuantity;
    }

    /**
     * 业务方法：检查库存是否低于安全库存（需要预警）
     * @return true 表示需要预警
     */
    public boolean isBelowMinStock() {
        if (product == null || product.getMinStock() == null) {
            return false;
        }
        return this.quantity < product.getMinStock();
    }
}
