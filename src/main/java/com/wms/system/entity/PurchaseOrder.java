package com.wms.system.entity;

import com.wms.system.entity.enums.PurchaseOrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Order Entity (采购单主表实�?
 *
 * Master-Detail 架构的主表，包含采购单头信息�? *
 * Business Flow (三阶段状态流�?:
 * 1. ORDERING: 创建采购单，上传 Excel 或手动录�? * 2. IN_TRANSIT: 确认并生成批次码（Hashids�? * 3. PARTIALLY_RECEIVED / COMPLETED: 实物入库（分批入库支持）
 *
 * Features:
 * - 支持分批入库（receivedQuantity 累加�? * - 支持状态回退（IN_TRANSIT �?ORDERING，批次码作废�? * - 审计日志记录（auditLog�? * - 隐私字段掩码（supplier, totalCost �?STAFF 角色隐藏�? *
 * @author WMS Team
 * @since 2025-01-13
 * @version 1.0 (Purchase Order Management + Batch Management)
 */
@Entity
@SQLDelete(sql = "UPDATE purchase_order SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(name = "purchase_order", indexes = {
    @Index(name = "idx_po_number", columnList = "po_number", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_supplier", columnList = "supplier"),
    @Index(name = "idx_purchase_order_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 采购单号（系统自动生成，格式：PO-YYYYMMDD-XXX�?     *
     * 生成规则�?     * - PO: Purchase Order 前缀
     * - YYYYMMDD: 创建日期
     * - XXX: 当日流水号（001-999�?     *
     * 示例: PO-20250113-001
     */
    @Column(name = "po_number", nullable = false, unique = true, length = 20)
    private String poNumber;

    /**
     * 供应商名�?     *
     * 隐私保护: STAFF 角色掩码�?"***"
     */
    @Column(name = "supplier", nullable = false, length = 200)
    private String supplier;

    /**
     * 采购单状�?     *
     * 状态流�?
     * ORDERING �?IN_TRANSIT �?PARTIALLY_RECEIVED �?COMPLETED
     * IN_TRANSIT 可回退�?ORDERING
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PurchaseOrderStatus status;

    /**
     * 总采购数量（汇总所有明细项�?orderedQuantity�?     *
     * 计算规则: SUM(PurchaseOrderItem.orderedQuantity)
     */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    /**
     * 总成本（汇总所有明细项�?orderedQuantity × unitCost�?     *
     * 计算规则: SUM(PurchaseOrderItem.orderedQuantity × PurchaseOrderItem.unitCost)
     * 隐私保护: STAFF 角色返回 null
     * 精度: 10位数字，2位小数（�?99999999.99�?     */
    @Column(name = "total_cost", nullable = true, precision = 10, scale = 2)
    private BigDecimal totalCost;

    /**
     * 期望到货日期（采购单创建时填写）
     */
    @Column(name = "expected_date", nullable = true)
    private LocalDate expectedDate;

    /**
     * 实际入库时间（第三阶段完成时自动记录�?     *
     * 记录时机: 状态变更为 COMPLETED �?     */
    @Column(name = "actual_entry_date", nullable = true)
    private LocalDateTime actualEntryDate;

    /**
     * 操作�?ID（创建采购单的用�?ID�?     */
    @Column(name = "operator_id", nullable = true)
    private Long operatorId;

    /**
     * 操作员姓名（创建采购单的用户姓名�?     */
    @Column(name = "operator_name", nullable = true, length = 100)
    private String operatorName;

    /**
     * 备注
     */
    @Column(name = "remark", nullable = true, length = 500)
    private String remark;

    /**
     * 审计日志（记录状态变更历史）
     *
     * 格式: JSON 字符串或分隔符文�?     * 示例: "2025-01-13 10:00:00 - Created by admin\n2025-01-13 11:00:00 - Status changed to IN_TRANSIT by admin"
     *
     * 用�? 状态回退、异常追踪、审计合�?     */
    @Column(name = "audit_log", nullable = true, columnDefinition = "TEXT")
    private String auditLog;


    /**
     * 采购单明细（一对多关系�?     *
     * Cascade: ALL（级联保存、更新、删除）
     * OrphanRemoval: true（孤儿删除）
     * FetchType: LAZY（延迟加载）
     */
    @OneToMany(
        mappedBy = "purchaseOrder",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

    /**
     * Soft delete flag.
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    // ========== 便利方法 (Helper Methods) ==========

    /**
     * 添加采购单明细项
     *
     * 自动维护双向关联关系
     *
     * @param item 采购单明细项
     */
    public void addItem(PurchaseOrderItem item) {
        items.add(item);
        item.setPurchaseOrder(this);
    }

    /**
     * 删除采购单明细项
     *
     * 自动维护双向关联关系
     *
     * @param item 采购单明细项
     */
    public void removeItem(PurchaseOrderItem item) {
        items.remove(item);
        item.setPurchaseOrder(null);
    }

    /**
     * 追加审计日志
     *
     * 格式: "YYYY-MM-DD HH:MM:SS - 操作内容\n"
     *
     * @param logEntry 日志内容
     */
    public void appendAuditLog(String logEntry) {
        String timestamp = LocalDateTime.now().toString().replace('T', ' ');
        String formattedLog = timestamp + " - " + logEntry;

        if (this.auditLog == null || this.auditLog.isEmpty()) {
            this.auditLog = formattedLog;
        } else {
            this.auditLog += "\n" + formattedLog;
        }
    }

    /**
     * 计算总采购数�?     *
     * 汇总所有明细项�?orderedQuantity
     *
     * @return 总采购数�?     */
    public Integer calculateTotalQuantity() {
        return items.stream()
            .mapToInt(PurchaseOrderItem::getOrderedQuantity)
            .sum();
    }

    /**
     * 计算总成�?     *
     * 汇总所有明细项�?(orderedQuantity × unitCost)
     *
     * @return 总成�?     */
    public BigDecimal calculateTotalCost() {
        return items.stream()
            .map(item -> {
                BigDecimal quantity = BigDecimal.valueOf(item.getOrderedQuantity());
                BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
                return quantity.multiply(unitCost);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 更新总数量和总成�?     *
     * 调用时机: 添加/修改/删除明细项后
     */
    public void updateTotals() {
        this.totalQuantity = calculateTotalQuantity();
        this.totalCost = calculateTotalCost();
    }

    /**
     * 检查是否所有明细都已完全入�?     *
     * 判断规则: 所有明细项�?receivedQuantity == orderedQuantity
     *
     * @return true 如果所有明细都已完全入�?     */
    public boolean isFullyReceived() {
        return items.stream()
            .allMatch(item -> item.getReceivedQuantity().equals(item.getOrderedQuantity()));
    }

    /**
     * 检查是否有任何明细已入�?     *
     * 判断规则: 存在任意明细项的 receivedQuantity > 0
     *
     * @return true 如果有任何明细已入库
     */
    public boolean hasPartiallyReceived() {
        return items.stream()
            .anyMatch(item -> item.getReceivedQuantity() > 0);
    }
}
