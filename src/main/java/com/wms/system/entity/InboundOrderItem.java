package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单明细表
 *
 * 核心字段：
 * - plan_qty: 计划数量（Excel 导入或手动录入）
 * - confirmed_qty: 确认数量（采购员确认后填写，可与 plan_qty 不同）
 * - actual_qty: 实收数量（仓库实际收货数量）
 * - batch_code: 批次码（在确认阶段生成，格式：SPU-SKU-DATE）
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "inbound_order_items",
    indexes = {
        @Index(name = "idx_inbound_item_order", columnList = "inbound_order_id"),
        @Index(name = "idx_inbound_item_product", columnList = "product_sku_id"),
        @Index(name = "idx_inbound_item_batch", columnList = "batch_code"),
        @Index(name = "idx_inbound_item_expiry", columnList = "expiry_date")
    }
)
public class InboundOrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 入库单
     */
    @NotNull(message = "入库单不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inbound_item_order"))
    private InboundOrder inboundOrder;

    /**
     * 产品
     */
    @NotNull(message = "产品不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_sku_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inbound_item_product"))
    private ProductSku productSku;

    /**
     * 计划数量
     */
    @NotNull(message = "计划数量不能为空")
    @Min(value = 1, message = "计划数量必须大于0")
    @Column(name = "plan_qty", nullable = false)
    private Integer planQty;

    /**
     * 确认数量
     */
    @Min(value = 0, message = "确认数量不能为负数")
    @Column(name = "confirmed_qty")
    private Integer confirmedQty;

    /**
     * 实收数量
     */
    @Builder.Default
    @Min(value = 0, message = "实收数量不能为负数")
    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty = 0;

    /**
     * 批次码（格式：SPU-SKU-DATE）
     */
    @Size(max = 50, message = "批次码长度不能超过50")
    @Column(name = "batch_code", length = 50)
    private String batchCode;

    /**
     * 过期日期
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * 生产日期
     */
    @Column(name = "production_date")
    private LocalDate productionDate;

    /**
     * 外部批次码（供应商提供）
     */
    @Size(max = 100, message = "外部批次码长度不能超过100")
    @Column(name = "external_batch_code", length = 100)
    private String externalBatchCode;

    /**
     * 目标仓库
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_warehouse_id", foreignKey = @ForeignKey(name = "fk_inbound_item_warehouse"))
    private Warehouse targetWarehouse;

    /**
     * 目标库位
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_location_id", foreignKey = @ForeignKey(name = "fk_inbound_item_location"))
    private Location targetLocation;

    /**
     * 单位成本
     */
    @DecimalMin(value = "0.0", message = "单位成本不能为负数")
    @Column(name = "unit_cost", precision = 10, scale = 2)
    private BigDecimal unitCost;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500")
    @Column(name = "remark", length = 500)
    private String remark;
}
