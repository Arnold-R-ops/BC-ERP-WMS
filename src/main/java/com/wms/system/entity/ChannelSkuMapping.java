package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 渠道 SKU 映射（P1 批次2，四层机制的"记忆层"）
 *
 * (channel, external_sku) → 内部商品，一次确认永久生效。
 * - quantityRatio：1 个外部单位 = N 个内部单位（如网店卖 20KG 大包，
 *   内部按 20 个 1KG 单位管理）
 * - VIRTUAL 类型：非库存行（运费差价/定制服务），转单时跳过履约但保留金额语义
 * - source=AUTO：条码自动命中后系统自学的映射（第三层漏斗回写第一层）
 *
 * @author WMS Team
 * @since 2026-07-11
 * @version P1-B2 (Channel SKU Mapping)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "channel_sku_mapping", uniqueConstraints = {
    @UniqueConstraint(name = "uk_sku_mapping", columnNames = {"company_id", "channel", "external_sku"})
})
public class ChannelSkuMapping extends BaseEntity {

    public static final String TYPE_PRODUCT = "PRODUCT";
    public static final String TYPE_VIRTUAL = "VIRTUAL";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AUTO = "AUTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(name = "store_identifier")
    private String storeIdentifier;

    /**
     * 渠道侧 SKU 原文（含空格等，如 "TOP0002 - 2-20KG"）
     */
    @Column(name = "external_sku", nullable = false, length = 200)
    private String externalSku;

    /**
     * 归一化形态（大写、去空白），第二层模糊命中用
     */
    @Column(name = "normalized_sku", nullable = false, length = 200)
    private String normalizedSku;

    /**
     * PRODUCT（映射到商品）/ VIRTUAL（非库存行，跳过履约）
     */
    @Column(name = "mapping_type", nullable = false, length = 20)
    @Builder.Default
    private String mappingType = TYPE_PRODUCT;

    /**
     * 内部商品（VIRTUAL 时为空）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /**
     * 数量换算：1 外部单位 = N 内部单位
     */
    @Column(name = "quantity_ratio", nullable = false)
    @Builder.Default
    private Integer quantityRatio = 1;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    /**
     * MANUAL（人工确认）/ AUTO（条码命中自学）
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String source = SOURCE_MANUAL;

    @Column(length = 500)
    private String remark;

    /**
     * 渠道侧库存项引用（P1-B4 库存回写用）：Shopify 的 inventory_item_id。
     * 回写按它定位库存记录；由商品目录懒解析缓存（read_products 权限）。
     */
    @Column(name = "external_item_ref", length = 100)
    private String externalItemRef;

    /**
     * 归一化规则（全系统唯一实现，勿另写）：大写 + 去所有空白字符
     */
    public static String normalize(String sku) {
        return sku == null ? null : sku.replaceAll("\\s+", "").toUpperCase();
    }
}
