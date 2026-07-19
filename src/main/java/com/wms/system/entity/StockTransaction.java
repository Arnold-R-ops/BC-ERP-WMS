package com.wms.system.entity;

import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 库存流水实体�? * 记录每一次库存变动的详细日志（不可修改，只增不删�? *
 * 业务价值：
 * 1. **审计追溯**：回�?这批货什么时候入库的？谁操作的？"
 * 2. **数据分析**：计算商品的日均出库量、周转率
 * 3. **库存预测**：基于历史流水，预测未来 N 天的库存消�? * 4. **财务对账**：与采购单、销售单、调拨单关联，确保账实相�? *
 * 设计原则�? * - 只增不删：流水记录永久保留（可定期归档到历史表）
 * - 不可修改：一旦生成，不允许更新（除非管理员手动修正）
 * - 冗余存储：记录变动时的商品名称、库位编码，即使后续商品或库位被删除，流水仍可查
 *
 * ERP 扩展预留�? * - 可增加操作人字段（operatorId, operatorName�? * - 可增加审批人字段（approverId, approverName�? * - 可增加单据附件字段（attachmentUrl�? *
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
    name = "stock_transactions",
    indexes = {
        @Index(name = "idx_stock_tx_product_sku_id", columnList = "product_sku_id"),
        @Index(name = "idx_stock_tx_location_id", columnList = "location_id"),
        @Index(name = "idx_transaction_type", columnList = "transactionType"),
        @Index(name = "idx_source_type", columnList = "sourceType"),
        @Index(name = "idx_source_order_id", columnList = "sourceOrderId"),
        @Index(name = "idx_stock_tx_created_at", columnList = "created_at")  // 按时间查询流水（高频场景）
    }
)
public class StockTransaction extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联商品（多对一关系�?     * - FetchType.LAZY: 延迟加载，提升性能
     * - 即使商品被删除，流水记录仍保留（通过冗余字段 productName �?productBarcode 查看�?     */
    @NotNull(message = "商品不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_sku_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transaction_product"))
    private ProductSku productSku;

    /**
     * 关联库位（多对一关系�?     * - 记录库存变动发生在哪个库�?     * - 调拨场景：OUT 流水记录源库位，IN 流水记录目标库位
     */
    @NotNull(message = "库位不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transaction_location"))
    private Location location;

    /**
     * 变动类型（枚举）
     * - IN: 入库（增加库存）
     * - OUT: 出库（减少库存）
     * - ADJUST: 调整（盘点后修正�?     */
    @NotNull(message = "变动类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType;

    /**
     * 来源类型（枚举）
     * 标识库存变动的业务场景：
     * - PURCHASE_IN: 采购入库
     * - SALE_OUT: 销售出�?     * - RETURN_IN: 退货入�?     * - PRODUCTION_OUT: 生产领料
     * - TRANSFER_OUT: 调拨出库
     * - INVENTORY_GAIN: 盘盈入库
     * - INVENTORY_LOSS: 盘亏出库
     * - SCRAP_OUT: 报废出库
     * - MANUAL_ADJUST: 手动调整
     * ... �?     */
    @NotNull(message = "来源类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceType sourceType;

    /**
     * 变动数量（绝对值）
     * - 入库：正数（例如�?100�?     * - 出库：正数（例如�?0，表示出�?50 件）
     * - 调整：正数或负数（盘盈为正，盘亏为负�?     *
     * 注意�?     * - 数量的正负由 transactionType 决定
     * - 数据库存储绝对值，便于统计
     */
    @NotNull(message = "变动数量不能为空")
    @Column(nullable = false)
    private Integer quantity;

    /**
     * 变动前库存数量（快照�?     * 记录变动前的库存状态，便于追溯和核�?     */
    @Column(nullable = false)
    private Integer quantityBefore;

    /**
     * 变动后库存数量（快照�?     * 记录变动后的库存状态，便于追溯和核�?     * 计算公式�?     * - 入库：quantityAfter = quantityBefore + quantity
     * - 出库：quantityAfter = quantityBefore - quantity
     */
    @Column(nullable = false)
    private Integer quantityAfter;

    /**
     * 来源单据号（外部业务单据的唯一标识�?     * 用于关联外部系统的单据，便于追溯和对�?     *
     * 示例�?     * - 采购入库：采购单号（PO202501090001�?     * - 销售出库：销售订单号（SO202501090002�?     * - 调拨出库：调拨单号（TR202501090003�?     * - 盘点调整：盘点单号（ST202501090004�?     *
     * 格式建议�?     * - 采购单：PO + 年月�?+ 流水�?     * - 销售单：SO + 年月�?+ 流水�?     * - 调拨单：TR + 年月�?+ 流水�?     * - 盘点单：ST + 年月�?+ 流水�?     */
    @NotBlank(message = "来源单据号不能为空")
    @Size(max = 50, message = "来源单据号长度不能超过 50 个字符")
    @Column(nullable = false, length = 50)
    private String sourceOrderId;

    /**
     * 冗余字段：商品名称（防止商品被删除后无法查看流水�?     * 在生成流水时，从 product 实体复制过来
     */
    @Column(nullable = false, length = 200)
    private String productName;

    /**
     * 冗余字段：商品条形码（防止商品被删除后无法查看流水）
     */
    @Column(nullable = false, length = 50)
    private String productBarcode;

    /**
     * 冗余字段：库位编码（防止库位被删除后无法查看流水�?     * 示例�?WH01-ZONE_A-A-01-001"
     */
    @Column(nullable = false, length = 100)
    private String locationCode;

    /**
     * 操作人ID（可选，预留 ERP 扩展�?     * 记录是谁执行的这次操�?     * 未来可改�?@ManyToOne 关联�?User �?     */
    @Column
    private Long operatorId;

    /**
     * 操作人姓名（冗余字段，便于快速查看）
     */
    @Column(length = 100)
    private String operatorName;

    /**
     * 备注信息（可选）
     * 可记录特殊说明，如："客户紧急订�?�?过期商品报废"�?     */
    @Column(length = 1000)
    private String remark;

    // ========== V4.2 归因记录 (Reason Codes) ==========

    /**
     * 结构化原因码（V4.2 新增�?     *
     * 说明�?     * - 机器可读的标准化原因标识，用�?BI 分类聚合�?AI 异常检�?     * - �?sourceType（业务场景枚举）互补：sourceType 标识大类，reasonCode 标识具体原因
     * - 由生成流水的 Service 主动写入，不设置时由 @PrePersist 默认填充�?sourceType.name()
     *
     * 标准原因码（�?SourceType 对应）：
     * - PURCHASE_INBOUND  �?SourceType.PURCHASE_IN
     * - INBOUND_IN        �?SourceType.INBOUND_IN
     * - SALES_OUTBOUND    �?SourceType.SALE_OUT
     * - RETURN_INBOUND    �?SourceType.RETURN_IN
     * - TRANSFER_OUTBOUND �?SourceType.TRANSFER_OUT
     * - TRANSFER_INBOUND  �?SourceType.TRANSFER_IN
     * - INVENTORY_GAIN    �?SourceType.INVENTORY_GAIN
     * - INVENTORY_LOSS    �?SourceType.INVENTORY_LOSS
     * - EXPIRY_DAMAGE     �?SourceType.SCRAP_OUT（过期损耗专用）
     * - MANUAL_ADJUST     �?SourceType.MANUAL_ADJUST
     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    /**
     * 归因短注释（V4.2 新增�?     *
     * 说明�?     * - 简短的结构化归因描述，AI 可解析（�?00 字符�?     * - �?remark（自由文本，�?000 字符）区分：
     *   remarks 聚焦�?为什�?，remark 聚焦�?操作说明"
     *
     * 示例�?     * - "临期报废批次 BC2026001，距过期 3 �?
     * - "Shopify 订单 #12345 出库"
     * - "盘点发现实际库存盈余 50 �?
     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "remarks", length = 500)
    private String remarks;

    /**
     * JPA 生命周期回调：在持久化前自动填充冗余字段
     */
    @PrePersist
    private void fillRedundantFields() {
        if (productSku != null) {
            this.productName = productSku.getName();
            this.productBarcode = productSku.getBarcode();
        }
        if (location != null) {
            this.locationCode = location.getLocationCode();
        }
        // V4.2: If reasonCode is not explicitly set, default to sourceType name.
        if (this.reasonCode == null && this.sourceType != null) {
            this.reasonCode = this.sourceType.name();
        }
    }

    /**
     * 业务方法：计算变动后库存
     * @param currentQuantity 当前库存数量
     * @return 变动后的库存数量
     */
    public Integer calculateQuantityAfter(Integer currentQuantity) {
        return switch (transactionType) {
            case IN -> currentQuantity + quantity;
            case OUT -> currentQuantity - quantity;
            case ADJUST -> currentQuantity + quantity;  // ADJUST �?quantity 可以是正数（盘盈）或负数（盘亏）
        };
    }
}



