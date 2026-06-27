package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Product SPU Entity (Standard Product Unit - 标准产品单元/产品家族)
 *
 * V3.3 Architecture: SPU-SKU Hierarchical Management
 *
 * 概念说明：
 * - SPU (Standard Product Unit): 标准产品单元，代表"产品家族"或"产品系列"
 * - SKU (Stock Keeping Unit): 库存保有单元，代表具体可销售的商品规格
 *
 * 业务场景示例：
 * - SPU: "茶叶" (Tea)
 *   - SKU1: "绿茶-箱装-12盒/箱" (barcode: 6901234567890)
 *   - SKU2: "红茶-箱装-12盒/箱" (barcode: 6901234567891)
 *   - SKU3: "乌龙茶-散装-1盒" (barcode: 6901234567892)
 *
 * - SPU: "可乐" (Cola)
 *   - SKU1: "可口可乐-330ml-24罐/箱" (barcode: 6921168500102)
 *   - SKU2: "可口可乐-500ml-12瓶/箱" (barcode: 6921168500103)
 *
 * 架构优势：
 * 1. 统一管理同一产品系列的 SKU
 * 2. 便于产品分类和报表统计
 * 3. 简化新增 SKU 的流程（继承 SPU 的分类信息）
 * 4. 支持 SPU 级别的营销活动和价格策略
 *
 * 数据库设计：
 * - 表名: product_spu
 * - 关系: 一个 SPU 对应多个 Product (SKU)
 * - 索引: code (唯一), name
 *
 * @author WMS Team
 * @since V3.3
 * @version 3.3 (SPU-SKU Hierarchical Management)
 */
@Entity
@Table(
    name = "product_spu",
    indexes = {
        @Index(name = "idx_spu_code", columnList = "spu_code"),
        @Index(name = "idx_spu_name", columnList = "spu_name"),
        @Index(name = "idx_spu_category", columnList = "category")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_spu_company_code", columnNames = {"company_id", "spu_code"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class ProductSpu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SPU 编码（唯一标识符）
     *
     * 编码规则建议：
     * - 前缀 + 分类码 + 序号
     * - 例如: "SPU-TEA-001", "SPU-COLA-001"
     *
     * 用途：
     * - 业务层唯一标识
     * - 便于人工识别和管理
     * - 导入导出时的关键字段
     */
    @NotBlank(message = "SPU 编码不能为空")
    @Size(max = 50, message = "SPU 编码长度不能超过 50 个字符")
    @Column(name = "spu_code", nullable = false, length = 50)
    private String spuCode;

    /**
     * SPU 名称（产品家族名称）
     *
     * 示例：
     * - "茶叶"
     * - "可乐"
     * - "洗衣液"
     * - "笔记本电脑"
     */
    @NotBlank(message = "SPU 名称不能为空")
    @Size(max = 200, message = "SPU 名称长度不能超过 200 个字符")
    @Column(name = "spu_name", nullable = false, length = 200)
    private String spuName;

    /**
     * 产品分类
     *
     * 示例：
     * - "饮料"
     * - "食品"
     * - "日用品"
     * - "电子产品"
     *
     * 未来扩展：
     * - 可改为 @ManyToOne 关联到独立的 Category 表
     * - 支持多级分类树结构
     */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * SPU 描述（产品系列说明）
     *
     * 用途：
     * - 记录产品系列的整体特点
     * - 市场定位和目标客户描述
     * - 供应链和采购相关信息
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * 是否启用
     *
     * - true: 正常使用（下属 SKU 可正常销售）
     * - false: 已停用（下属 SKU 不允许新增入库，但可继续出库清仓）
     *
     * 业务场景：
     * - 产品线下架
     * - 品牌停产
     * - 季节性产品淡季停用
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 品牌名称（可选）
     *
     * 示例：
     * - "可口可乐"
     * - "雀巢"
     * - "联合利华"
     *
     * 未来扩展：
     * - 可改为 @ManyToOne 关联到独立的 Brand 表
     */
    @Column(name = "brand", length = 100)
    private String brand;

    /**
     * 关联的 SKU 列表（一对多）
     *
     * 关系说明：
     * - 一个 SPU 包含多个 Product (SKU)
     * - 级联操作：不级联删除（防止误删 SKU）
     * - 懒加载：避免性能问题
     *
     * 用途：
     * - 查询某个 SPU 下的所有 SKU
     * - 统计 SPU 级别的库存和销量
     */
    @OneToMany(mappedBy = "spu", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<Product> skus = new ArrayList<>();

    // ========== 便利方法 (Helper Methods) ==========

    /**
     * 添加 SKU
     *
     * 用途：
     * - 建立双向关联
     * - 确保 SKU 的 spu 字段正确设置
     *
     * @param sku SKU 产品
     */
    public void addSku(Product sku) {
        this.skus.add(sku);
        sku.setSpu(this);
    }

    /**
     * 移除 SKU
     *
     * 用途：
     * - 解除双向关联
     * - 确保 SKU 的 spu 字段正确清除
     *
     * @param sku SKU 产品
     */
    public void removeSku(Product sku) {
        this.skus.remove(sku);
        sku.setSpu(null);
    }

    /**
     * 获取启用的 SKU 数量
     *
     * @return 启用的 SKU 数量
     */
    public long getEnabledSkuCount() {
        return skus.stream()
            .filter(Product::getEnabled)
            .count();
    }

    /**
     * 检查 SPU 是否可用（SPU 启用且至少有一个启用的 SKU）
     *
     * @return true 如果可用
     */
    public boolean isAvailable() {
        return this.enabled && getEnabledSkuCount() > 0;
    }
}
