package com.wms.system.entity;

import com.wms.system.entity.enums.InboundOrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 入库单主表
 *
 * 业务流程：
 * User (Apply) → GM (Approve Plan) → User (Confirm Order) → System (Generate Batch Code) → Warehouse (Receive)
 *
 * 状态流转：
 * PENDING_APPROVAL → APPROVED_PLAN → AWAITING_RECEIVAL → COMPLETED
 *
 * 核心字段：
 * - total_plan_qty: 计划总数量（Excel 导入或手动录入）
 * - total_confirmed_qty: 确认总数量（采购员确认后填写）
 * - total_actual_qty: 实收总数量（仓库实际收货数量）
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
    name = "inbound_orders",
    indexes = {
        @Index(name = "idx_inbound_order_no", columnList = "order_no"),
        @Index(name = "idx_inbound_status", columnList = "status"),
        @Index(name = "idx_inbound_supplier", columnList = "supplier_id"),
        @Index(name = "idx_inbound_applicant", columnList = "applicant_id"),
        @Index(name = "idx_inbound_created_at", columnList = "created_at")
    }
)
public class InboundOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 入库单号（唯一）
     */
    @NotBlank(message = "入库单号不能为空")
    @Size(max = 30, message = "入库单号长度不能超过30")
    @Column(name = "order_no", nullable = false, unique = true, length = 30)
    private String orderNo;

    /**
     * 供应商
     */
    @NotNull(message = "供应商不能为空")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inbound_supplier"))
    private Supplier supplier;

    /**
     * 状态
     */
    @NotNull(message = "状态不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InboundOrderStatus status;

    /**
     * 计划总数量
     */
    @Builder.Default
    @Min(value = 0, message = "计划总数量不能为负数")
    @Column(name = "total_plan_qty", nullable = false)
    private Integer totalPlanQty = 0;

    /**
     * 确认总数量
     */
    @Min(value = 0, message = "确认总数量不能为负数")
    @Column(name = "total_confirmed_qty")
    private Integer totalConfirmedQty;

    /**
     * 实收总数量
     */
    @Builder.Default
    @Min(value = 0, message = "实收总数量不能为负数")
    @Column(name = "total_actual_qty", nullable = false)
    private Integer totalActualQty = 0;

    /**
     * 预计到货日期
     */
    @Column(name = "expected_date")
    private LocalDate expectedDate;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500")
    @Column(name = "remark", length = 500)
    private String remark;

    // ==================== 总经理审批字段 ====================

    /**
     * 总经理审批人 ID
     */
    @Column(name = "gm_approved_by")
    private Long gmApprovedBy;

    /**
     * 总经理审批时间
     */
    @Column(name = "gm_approved_at")
    private LocalDateTime gmApprovedAt;

    /**
     * 总经理审批意见
     */
    @Size(max = 500, message = "总经理审批意见长度不能超过500")
    @Column(name = "gm_approval_comment", length = 500)
    private String gmApprovalComment;

    // ==================== 采购员确认字段 ====================

    /**
     * 采购员确认人 ID
     */
    @Column(name = "confirmed_by")
    private Long confirmedBy;

    /**
     * 采购员确认时间
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * 采购员确认意见
     */
    @Size(max = 500, message = "采购员确认意见长度不能超过500")
    @Column(name = "confirmation_comment", length = 500)
    private String confirmationComment;

    // ==================== 仓库收货字段 ====================

    /**
     * 仓库收货人 ID
     */
    @Column(name = "received_by")
    private Long receivedBy;

    /**
     * 仓库收货时间
     */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    // ==================== 申请人信息 ====================

    /**
     * 申请人 ID
     */
    @NotNull(message = "申请人ID不能为空")
    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    /**
     * 申请人姓名
     */
    @NotBlank(message = "申请人姓名不能为空")
    @Size(max = 100, message = "申请人姓名长度不能超过100")
    @Column(name = "applicant_name", nullable = false, length = 100)
    private String applicantName;

    // ==================== 审计日志 ====================

    /**
     * 审计日志（JSON 格式）
     */
    @Column(name = "audit_log", columnDefinition = "TEXT")
    private String auditLog;

    // ==================== 关联关系 ====================

    /**
     * 入库单明细列表
     */
    @OneToMany(mappedBy = "inboundOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InboundOrderItem> items = new ArrayList<>();

    // ==================== 业务方法 ====================

    /**
     * 添加明细项
     */
    public void addItem(InboundOrderItem item) {
        items.add(item);
        item.setInboundOrder(this);
    }

    /**
     * 移除明细项
     */
    public void removeItem(InboundOrderItem item) {
        items.remove(item);
        item.setInboundOrder(null);
    }

    /**
     * 重新计算计划总数量
     */
    public void recalculateTotalPlanQty() {
        this.totalPlanQty = items.stream()
            .mapToInt(InboundOrderItem::getPlanQty)
            .sum();
    }

    /**
     * 重新计算确认总数量
     */
    public void recalculateTotalConfirmedQty() {
        this.totalConfirmedQty = items.stream()
            .filter(item -> item.getConfirmedQty() != null)
            .mapToInt(InboundOrderItem::getConfirmedQty)
            .sum();
    }

    /**
     * 重新计算实收总数量
     */
    public void recalculateTotalActualQty() {
        this.totalActualQty = items.stream()
            .mapToInt(InboundOrderItem::getActualQty)
            .sum();
    }
}
