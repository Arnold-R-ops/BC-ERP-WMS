package com.wms.system.entity;

import com.wms.system.entity.enums.SalesOrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单主表实体
 *
 * V3.7 架构：销售订单管理模块
 *
 * 说明：
 * - 存储销售订单主信息
 * - 支持审批流程（低价或高额触发）
 * - 支持订单修改和取消
 * - 支持审计日志记录
 *
 * 核心字段：
 * - orderNo: 订单编号（唯一标识）
 * - customerId: 客户ID
 * - totalAmount: 订单总金额
 * - status: 订单状态
 * - reviewReason: 审批原因
 *
 * 审批流程：
 * 1. 单价低于最低限价 → 触发审批
 * 2. 订单总额超过审批阈值 → 触发审批
 * 3. 经理审批通过 → 进入分配
 * 4. 经理拒绝 → 订单终止
 *
 * 状态流转：
 * DRAFT → PENDING_APPROVAL → APPROVED_AWAITING_SHIPMENT → SHIPPED
 *                          ↘ REJECTED
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@SQLDelete(sql = "UPDATE sales_orders SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(
    name = "sales_orders",
    indexes = {
        @Index(name = "idx_sales_order_no", columnList = "order_no", unique = true),
        @Index(name = "idx_sales_status", columnList = "status"),
        @Index(name = "idx_sales_customer", columnList = "customer_id"),
        @Index(name = "idx_sales_applicant", columnList = "applicant_id"),
        @Index(name = "idx_sales_created_at", columnList = "created_at")
    }
)
public class SalesOrder extends BaseEntity {

    /**
     * 主键ID（自增）
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 订单编号（唯一标识）
     *
     * 格式：SO + yyyyMMdd + 序号
     * 示例：SO20260128001
     */
    @NotBlank(message = "订单编号不能为空")
    @Size(max = 30, message = "订单编号长度不能超过 30 个字符")
    @Column(name = "order_no", nullable = false, unique = true, length = 30)
    private String orderNo;

    /**
     * 客户ID
     */
    @NotNull(message = "客户ID不能为空")
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /**
     * 客户实体（懒加载）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    /**
     * 订单总金额
     *
     * 说明：
     * - 所有订单明细的小计之和
     * - 用于审批阈值检查
     */
    @DecimalMin(value = "0.00", message = "订单总金额不能为负数")
    @Column(name = "total_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * 订单状态
     *
     * 状态说明：
     * - DRAFT: 草稿
     * - PENDING_APPROVAL: 待审批
     * - APPROVED_AWAITING_SHIPMENT: 已批准待发货
     * - SHIPPED: 已发货
     * - REJECTED: 已拒绝
     * - CANCELLED: 已取消
     */
    @NotNull(message = "订单状态不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalesOrderStatus status;

    /**
     * 审批原因
     *
     * 说明：
     * - 记录触发审批的原因
     * - 例如："单价低于最低限价"、"订单总额超过审批阈值"
     */
    @Size(max = 500, message = "审批原因长度不能超过 500 个字符")
    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    // ========== 审批信息 ==========

    /**
     * 审批人ID
     */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /**
     * 审批时间
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * 审批意见
     */
    @Size(max = 500, message = "审批意见长度不能超过 500 个字符")
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    // ========== 申请人信息 ==========

    /**
     * 申请人ID
     */
    @NotNull(message = "申请人ID不能为空")
    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    /**
     * 申请人姓名
     */
    @NotBlank(message = "申请人姓名不能为空")
    @Size(max = 100, message = "申请人姓名长度不能超过 100 个字符")
    @Column(name = "applicant_name", nullable = false, length = 100)
    private String applicantName;

    // ========== 渠道集成信息 ==========

    /**
     * 订单渠道
     *
     * 说明：
     * - MANUAL: 手动创建（默认）
     * - SHOPIFY: Shopify 同步
     * - 未来可扩展其他渠道（AMAZON, EBAY 等）
     */
    @Size(max = 50, message = "订单渠道长度不能超过 50 个字符")
    @Column(length = 50)
    @Builder.Default
    private String channel = "MANUAL";

    /**
     * 外部订单 ID（用于去重）
     *
     * 说明：
     * - Shopify: 存储 Shopify 订单 ID（Long 类型转 String）
     * - 用于防止重复同步
     */
    @Size(max = 100, message = "外部订单ID长度不能超过 100 个字符")
    @Column(name = "external_order_id", length = 100)
    private String externalOrderId;

    /**
     * 外部订单号（用于显示）
     *
     * 说明：
     * - Shopify: 存储 Shopify 订单号（如 #1001）
     * - 用于在界面上显示原始订单号
     */
    @Size(max = 100, message = "外部订单号长度不能超过 100 个字符")
    @Column(name = "external_order_no", length = 100)
    private String externalOrderNo;

    // ========== 审计日志 ==========

    /**
     * 审计日志（JSON 格式）
     *
     * 说明：
     * - 记录订单的所有操作历史
     * - 包括创建、修改、审批、取消等操作
     *
     * 示例：
     * [
     *   {
     *     "timestamp": "2026-01-28T10:00:00",
     *     "operator": "张三",
     *     "action": "CREATE",
     *     "details": "创建销售订单"
     *   },
     *   {
     *     "timestamp": "2026-01-28T10:30:00",
     *     "operator": "李四",
     *     "action": "APPROVE",
     *     "details": "审批通过"
     *   }
     * ]
     */
    @Column(name = "audit_log", columnDefinition = "TEXT")
    private String auditLog;

    // ========== V4.2 AI 基建升级 ==========

    /**
     * 逻辑删除标记（V4.2 新增）
     *
     * 说明：
     * - true: 已逻辑删除（不出现在任何查询结果中）
     * - false: 正常状态
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

    /**
     * 审批通过时间（V4.2 新增）
     *
     * 说明：
     * - 仅在 status → APPROVED_AWAITING_SHIPMENT 时由 Service 写入
     * - 与 reviewedAt 区分：reviewedAt 在审批人操作时写入（含拒绝），
     *   approvedAt 仅在通过时写入
     *
     * BI 用途：
     * - 计算审批等待时长 = approvedAt - createdAt
     * - 计算审批效率分布（小时级）
     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * 发货完成时间（V4.2 新增）
     *
     * 说明：
     * - 在 status → SHIPPED 时由出库服务（OutboundService）写入
     * - null 表示订单尚未完成发货
     *
     * BI 用途：
     * - 计算履约周期 = shippedAt - createdAt
     * - 计算发货及时率（相对于承诺发货日期）
     *
     * @since V4.2 (AI Foundation Patch)
     */
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    // ========== 便利方法 ==========

    /**
     * 判断是否可以修改订单
     *
     * @return true 如果状态为 DRAFT 或 PENDING_APPROVAL
     */
    public boolean canModify() {
        return status != null && status.canModify();
    }

    /**
     * 判断是否可以取消订单
     *
     * @return true 如果状态为 DRAFT, PENDING_APPROVAL, 或 APPROVED_AWAITING_SHIPMENT
     */
    public boolean canCancel() {
        return status != null && status.canCancel();
    }

    /**
     * 判断是否可以审批
     *
     * @return true 如果状态为 PENDING_APPROVAL
     */
    public boolean canApprove() {
        return status != null && status.canApprove();
    }

    /**
     * 判断是否已完成（不可修改）
     *
     * @return true 如果状态为 SHIPPED, REJECTED, 或 CANCELLED
     */
    public boolean isFinalized() {
        return status != null && status.isFinalized();
    }
}
