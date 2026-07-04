package com.wms.system.entity;

import com.wms.system.entity.enums.BatchTrackingMode;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Product SKU Entity (Stock Keeping Unit - 库存保有单元)
 *
 * V3.3 Architecture: SKU Level in SPU-SKU Hierarchy
 *
 * 概念说明：
 * - SKU (Stock Keeping Unit): 库存保有单元，代表具体可销售的商品规格
 * - 每个 SKU 必须归属于一个 SPU (Standard Product Unit - 产品家族)
 * - SKU 是库存管理、采购、销售的最小单位
 *
 * 业务场景示例：
 * - SPU: "茶叶" → SKU: "绿茶-箱装-12盒/箱" (barcode: 6901234567890)
 * - SPU: "可乐" → SKU: "可口可乐-330ml-24罐/箱" (barcode: 6921168500102)
 *
 * V3.1 自动拆包属性（保留）：
 * - packUnit: 包装单位（如 "Box"）
 * - conversionRate: 换算率（如 12 = 1箱12瓶）
 * - 支持 Loose Item First 策略（零头优先出库）
 *
 * 核心字段：
 * - barcode: 条形码唯一标识（SKU 级别）
 * - name: SKU 完整名称（用于显示和搜索）
 * - skuName: SKU 特定名称（简短标识，如"绿茶-箱装"）
 * - specs: 规格描述（JSON 或字符串，如 "12盒/箱, 250g/盒"）
 *
 * 库存预测逻辑（ERP 扩展）：
 * 1. 当 当前库存 < minStock 时，系统自动预警
 * 2. 建议补货量 = 日均出库量 × leadTime + 安全库存
 * 3. 通过分析 StockTransaction 流水，可计算日均出库量
 *
 * @author WMS Team
 * @since 2025-01-09
 * @version 3.3 (upgraded to SKU level in SPU-SKU hierarchy)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@SQLDelete(sql = "UPDATE products SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_barcode", columnList = "barcode"),
        @Index(name = "idx_name", columnList = "name"),
        @Index(name = "idx_spu_id", columnList = "spu_id")  // V3.3: SPU-SKU hierarchy
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_products_company_barcode", columnNames = {"company_id", "barcode"})
    }
)
public class Product extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 乐观锁版本号（V4.4 架构加固）
     *
     * 说明：
     * - JPA 自动管理版本号，每次更新自动递增
     * - 并发更新时，如果版本号不匹配，抛出 OptimisticLockException
     * - 防止高并发场景下的价格、库存阈值等字段的并发冲突
     *
     * 使用场景：
     * - 商品价格更新（unitPrice, minSalesPrice）
     * - 库存阈值调整（minStock, maxStock, safetyStock）
     * - 商品状态变更（enabled, isDeleted）
     *
     * @since V4.4 (Optimistic Locking)
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    // ========== V3.3 SPU-SKU 层级关联 (SPU-SKU Hierarchy) ==========

    /**
     * 关联的 SPU (Standard Product Unit - 产品家族)
     *
     * V3.3 架构说明：
     * - 每个 SKU 必须归属于一个 SPU
     * - SPU 代表产品系列，SKU 代表具体规格
     * - 例如: SPU "茶叶" → SKU "绿茶-箱装", "红茶-箱装"
     *
     * 数据迁移：
     * - 旧数据会被关联到默认 SPU (ID=1, Name="Default SPU")
     * - nullable=false 确保每个 SKU 都有 SPU
     *
     * @since V3.3
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spu_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_spu"))
    private ProductSpu spu;

    /**
     * SKU 特定名称（V3.3 新增）
     *
     * 说明：
     * - SKU 的简短标识名称
     * - 与 SPU 名称组合形成完整描述
     * - 用于前端显示和快速识别
     *
     * 示例：
     * - SPU: "茶叶" + skuName: "绿茶-箱装" = "茶叶 绿茶-箱装"
     * - SPU: "可乐" + skuName: "330ml-24罐" = "可乐 330ml-24罐"
     *
     * 命名建议：
     * - 包含关键规格信息
     * - 简洁明了（50 字符以内）
     * - 便于区分同 SPU 下的其他 SKU
     *
     * @since V3.3
     */
    @NotBlank(message = "SKU 名称不能为空")
    @Size(max = 100, message = "SKU 名称长度不能超过 100 个字符")
    @Column(name = "sku_name", nullable = false, length = 100)
    private String skuName;

    /**
     * 规格描述（V3.3 新增）
     *
     * 说明：
     * - 详细的商品规格信息
     * - 可以是 JSON 格式或纯文本
     * - 用于前端详情页展示
     *
     * 示例（JSON 格式）：
     * {
     *   "packaging": "12盒/箱",
     *   "weight": "250g/盒",
     *   "volume": "500ml",
     *   "color": "红色",
     *   "size": "XL"
     * }
     *
     * 示例（纯文本）：
     * - "12盒/箱, 250g/盒"
     * - "330ml, 24罐/箱"
     * - "500ml, 瓶装"
     *
     * @since V3.3
     */
    @Column(name = "specs", length = 500)
    private String specs;

    /**
     * 商品条形码（唯一标识符）
     * 可以是 EAN-13, UPC-A, Code128 等标准格式
     * 用于扫码枪快速识别商品
     */
    @NotBlank(message = "条形码不能为空")
    @Size(max = 50, message = "条形码长度不能超过 50 个字符")
    @Column(nullable = false, length = 50)
    private String barcode;

    /**
     * 商品名称
     */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称长度不能超过 200 个字符")
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 商品规格（可选）
     * 示例："500ml", "1kg", "100粒/盒", "XL码"
     */
    @Column(length = 100)
    private String specification;

    /**
     * 单价（单位：元）
     * 精度：小数点后 2 位（例如：99.99）
     *
     * 技术说明：
     * - precision = 10: 总位数（整数部分 + 小数部分）
     * - scale = 2: 小数位数
     * - 可表示范围：-99999999.99 ~ 99999999.99
     */
    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.00", message = "单价不能为负数")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * 安全库存（最小库存预警阈值）
     * 当实际库存 < minStock 时，系统应自动触发预警（需配合定时任务）
     *
     * 示例场景：
     * - 快消品：minStock = 100（保证 3-5 天销量）
     * - 慢销品：minStock = 10（避免积压）
     *
     * 默认值：0（不设置预警）
     */
    @Min(value = 0, message = "安全库存不能为负数")
    @Column(nullable = false)
    @Builder.Default
    private Integer minStock = 0;

    /**
     * 采购提前期（天数）
     * 从下单到商品到货的时间周期
     *
     * 用途：
     * 1. 计算最晚补货时间点 = leadTime 天后库存将用完
     * 2. 建议补货量 = (日均出库量 × leadTime) + minStock
     *
     * 示例：
     * - 本地供应商：leadTime = 1 天
     * - 外地供应商：leadTime = 3-7 天
     * - 进口商品：leadTime = 15-30 天
     *
     * 默认值：7 天
     */
    @Min(value = 0, message = "采购提前期不能为负数")
    @Column(nullable = false)
    @Builder.Default
    private Integer leadTime = 7;

    /**
     * 安全库存（V3.6 新增）
     * 用于计算预警状态
     *
     * 说明：
     * - 当实际库存 < safetyStock 时，系统显示预警状态（红色）
     * - 当实际库存 >= safetyStock 时，系统显示充足状态（绿色）
     *
     * 示例：
     * - 快消品：safetyStock = 100（保证 3-5 天销量）
     * - 慢销品：safetyStock = 10（避免积压）
     *
     * 默认值：0（不设置预警）
     *
     * @since V3.6
     */
    @Min(value = 0, message = "安全库存不能为负数")
    @Column(name = "safety_stock", nullable = false)
    @Builder.Default
    private Integer safetyStock = 0;

    /**
     * 商品描述（可选）
     */
    @Column(length = 1000)
    private String description;

    /**
     * 商品状态（是否启用）
     * - true: 正常销售
     * - false: 已下架（不允许入库/出库）
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 商品分类（可选，预留 ERP 扩展）
     * 未来可改为 @ManyToOne 关联到独立的 Category 表
     */
    @Column(length = 100)
    private String category;

    /**
     * 供应商名称（可选，预留 ERP 扩展）
     * 未来可改为 @ManyToOne 关联到独立的 Supplier 表
     */
    @Column(length = 200)
    private String supplier;

    // ========== V3.1 多单位自动拆包策略 (Multi-Unit & Auto-Unpacking Strategy) ==========

    /**
     * 包装单位名称（V3.1 新增）
     *
     * 说明：
     * - 大包装单位名称，例如 "Box"（箱）、"Carton"（纸箱）、"Case"（件）
     * - 与 conversionRate 配合使用，实现多单位换算
     * - 用于前端显示和日志输出（如 "2 Box, 1 Unit"）
     *
     * 示例：
     * - 饮料：packUnit = "Box"（箱）
     * - 电子产品：packUnit = "Carton"（纸箱）
     * - 日用品：packUnit = "Case"（件）
     *
     * 默认值："Box"
     *
     * @since V3.1
     */
    @NotBlank(message = "包装单位不能为空")
    @Column(name = "pack_unit", nullable = false, length = 20)
    @Builder.Default
    private String packUnit = "Box";

    /**
     * 换算率（V3.1 新增）
     *
     * 定义：1 个大包装单位 = conversionRate 个基础单位
     *
     * 说明：
     * - 例如：1 箱 = 12 瓶，则 conversionRate = 12
     * - 用于自动拆包计算和库存单位转换
     * - 默认值为 1（防止除以 0 错误）
     *
     * 业务逻辑：
     * - 零头批次：quantity % conversionRate != 0（已开箱）
     * - 整箱批次：quantity % conversionRate == 0（未开箱）
     *
     * FIFO 拆包策略：
     * - 优先出库零头批次（Loose Item First）
     * - 零头不足时再拆新箱（整箱批次）
     *
     * 示例：
     * - 1 箱 12 瓶装饮料：conversionRate = 12
     * - 1 箱 24 罐装饮料：conversionRate = 24
     * - 散装商品（不分箱）：conversionRate = 1
     *
     * 默认值：1
     *
     * @since V3.1
     */
    @Min(value = 1, message = "换算率必须大于等于 1")
    @Column(name = "conversion_rate", nullable = false)
    @Builder.Default
    private Integer conversionRate = 1;

    // ========== V3.1 便利方法 (Helper Methods) ==========

    /**
     * 格式化数量（支持多单位显示）
     *
     * 功能：
     * - 将基础单位数量转换为 "包装单位 + 基础单位" 格式
     * - 方便前端显示和日志输出
     *
     * 逻辑：
     * - 大包装数量 = quantity / conversionRate
     * - 零头数量 = quantity % conversionRate
     *
     * 示例（conversionRate = 12）：
     * - formatQuantity(25) → "2 Box, 1 Unit"
     * - formatQuantity(12) → "1 Box"
     * - formatQuantity(5)  → "5 Unit"
     * - formatQuantity(0)  → "0 Unit"
     *
     * 特殊情况：
     * - conversionRate = 1 时，直接返回 "N Unit"（不显示包装单位）
     *
     * @param quantity 基础单位数量
     * @return 格式化的字符串（例如 "2 Box, 1 Unit"）
     * @since V3.1
     */
    public String formatQuantity(Integer quantity) {
        if (quantity == null || quantity == 0) {
            return "0 Unit";
        }

        // 特殊情况：conversionRate = 1（散装商品，不分箱）
        if (this.conversionRate == 1) {
            return quantity + " Unit";
        }

        int packCount = quantity / this.conversionRate;
        int looseCount = quantity % this.conversionRate;

        // 构建格式化字符串
        StringBuilder result = new StringBuilder();

        if (packCount > 0) {
            result.append(packCount).append(" ").append(this.packUnit);
        }

        if (looseCount > 0) {
            if (packCount > 0) {
                result.append(", ");
            }
            result.append(looseCount).append(" Unit");
        }

        return result.toString();
    }

    /**
     * 检查是否为零头数量（已开箱）
     *
     * 定义：quantity % conversionRate != 0
     *
     * 用途：
     * - FIFO 拆包策略中判断批次是否为零头批次
     * - 零头批次优先出库（Loose Item First）
     *
     * @param quantity 基础单位数量
     * @return true 如果是零头数量（已开箱）
     * @since V3.1
     */
    public boolean isLooseQuantity(Integer quantity) {
        if (quantity == null || quantity == 0) {
            return false;
        }
        return quantity % this.conversionRate != 0;
    }

    /**
     * 检查是否为整箱数量（未开箱）
     *
     * 定义：quantity % conversionRate == 0
     *
     * 用途：
     * - FIFO 拆包策略中判断批次是否为整箱批次
     * - 整箱批次在零头不足时才出库（避免开新箱）
     *
     * @param quantity 基础单位数量
     * @return true 如果是整箱数量（未开箱）
     * @since V3.1
     */
    public boolean isFullPack(Integer quantity) {
        if (quantity == null || quantity == 0) {
            return false;
        }
        return quantity % this.conversionRate == 0;
    }

    // ========== V3.7 销售与出库系统字段 (Sales and Outbound System) ==========

    /**
     * 最低限价（V3.7 新增）
     *
     * 说明：
     * - 销售订单单价低于此值时，触发审批流程
     * - 用于风控检查，防止低价销售
     * - 默认值：0.00（不设置限价）
     *
     * 示例：
     * - 成本价 50 元，最低限价设为 60 元（保证 20% 利润）
     * - 销售员报价 55 元时，需要经理审批
     *
     * @since V3.7
     */
    @DecimalMin(value = "0.00", message = "最低限价不能为负数")
    @Column(name = "min_sales_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minSalesPrice = BigDecimal.ZERO;

    /**
     * 临期天数阈值（V3.7 新增）
     *
     * 说明：
     * - 距离过期日期小于此天数时，标记为临期商品
     * - 用于智能分配算法中的临期过滤
     * - 客户可选择拒收临期商品
     *
     * 计算公式：
     * - 临期判断：CURRENT_DATE + near_expiry_days >= expiry_date
     *
     * 示例：
     * - 食品类：near_expiry_days = 90（3个月内过期）
     * - 药品类：near_expiry_days = 180（6个月内过期）
     * - 日用品：near_expiry_days = 30（1个月内过期）
     *
     * 默认值：90 天
     *
     * @since V3.7
     */
    @Min(value = 0, message = "临期天数阈值不能为负数")
    @Column(name = "near_expiry_days", nullable = false)
    @Builder.Default
    private Integer nearExpiryDays = 90;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_tracking_mode", nullable = false, length = 30)
    @Builder.Default
    private BatchTrackingMode batchTrackingMode = BatchTrackingMode.PRINTED_LABEL;

    /**
     * 箱规（每箱数量）（V3.7 新增）
     *
     * 说明：
     * - 每箱包含的基础单位数量
     * - 用于智能分配算法中的整箱策略
     * - 与 conversionRate 概念相同，但用于销售场景
     *
     * 整箱策略：
     * - 如果订单数量是箱规的整数倍，优先从整箱批次分配
     * - 避免拆箱，提高拣货效率
     *
     * 示例：
     * - 饮料：per_pack_qty = 24（24瓶/箱）
     * - 零食：per_pack_qty = 12（12包/箱）
     * - 散装商品：per_pack_qty = 1（不分箱）
     *
     * 默认值：1（与 conversionRate 保持一致）
     *
     * @since V3.7
     */
    @Min(value = 1, message = "箱规必须大于等于 1")
    @Column(name = "per_pack_qty", nullable = false)
    @Builder.Default
    private Integer perPackQty = 1;

    // ========== V4.2 逻辑删除 (Soft Delete) ==========

    /**
     * 逻辑删除标记（V4.2 新增）
     *
     * 说明：
     * - true: 商品已下架并逻辑删除（不出现在任何查询结果中）
     * - false: 正常商品
     *
     * 注意：
     * - 此字段与 enabled（是否启用）并存
     * - enabled=false 仅禁止新的出入库，商品仍可查询
     * - is_deleted=true 则从所有查询中彻底过滤，用于彻底停用的 SKU
     *
     * 技术实现：
     * - @SQLDelete 拦截 JPA delete 操作，改写为 UPDATE SET is_deleted = true
     * - @SQLRestriction 自动在所有查询中追加 WHERE is_deleted = false
     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;
}
