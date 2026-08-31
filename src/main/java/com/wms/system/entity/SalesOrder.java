package com.wms.system.entity;

import com.wms.system.entity.enums.AllocationPolicy;
import com.wms.system.entity.enums.CommercialStatus;
import com.wms.system.entity.enums.SalesAllocationStatus;
import com.wms.system.entity.enums.SalesApprovalMode;
import com.wms.system.entity.enums.SalesLogisticsStatus;
import com.wms.system.entity.enums.SalesWarehouseStatus;
import com.wms.system.entity.enums.SalesOrderPurpose;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * - commercialStatus: 业务主状态
 * - allocationStatus: 库存分配状态
 * - warehouseStatus: 仓库执行状态
 * - logisticsStatus: 物流状态
 * - reviewReason: 审批原因
 *
 * 审批流程：
 * 1. DIRECT：提交即批准并进入分配
 * 2. RISK_BASED：低价或超额时触发审批
 * 3. ALWAYS：所有提交均进入审批
 * 4. 审批通过后进入分配，驳回则终止
 *
 * 状态流转：
 * DRAFT → PENDING_APPROVAL → APPROVED → COMPLETED
 *                          ↘ REJECTED
 * APPROVED → CANCELLED（未发货）或 CLOSED（部分发货后关闭剩余数量）
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
@Table(
    name = "sales_orders",
    indexes = {
        @Index(name = "idx_sales_order_no", columnList = "order_no"),
        @Index(name = "idx_sales_commercial_status", columnList = "commercial_status"),
        @Index(name = "idx_sales_allocation_status", columnList = "allocation_status"),
        @Index(name = "idx_sales_warehouse_status", columnList = "warehouse_status"),
        @Index(name = "idx_sales_customer", columnList = "customer_id"),
        @Index(name = "idx_sales_applicant", columnList = "applicant_id"),
        @Index(name = "idx_sales_created_at", columnList = "created_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sales_orders_company_order_no", columnNames = {"company_id", "order_no"}),
        @UniqueConstraint(
            name = "uq_sales_orders_company_channel_external_order",
            columnNames = {"company_id", "channel", "external_order_id"}
        )
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
    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    /** Replacement orders use the ordinary sales workflow but are excluded from new-sales revenue. */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_purpose", nullable = false, length = 30)
    @Builder.Default
    private SalesOrderPurpose orderPurpose = SalesOrderPurpose.NORMAL;

    @Column(name = "source_rma_id")
    private Long sourceRmaId;

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

    /** Business acceptance lifecycle; never describes inventory or warehouse work. */
    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_status", nullable = false, length = 40)
    @Builder.Default
    private CommercialStatus commercialStatus = CommercialStatus.DRAFT;

    /** Inventory commitment summary derived from reservation and shortage facts. */
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_status", nullable = false, length = 40)
    @Builder.Default
    private SalesAllocationStatus allocationStatus = SalesAllocationStatus.UNALLOCATED;

    /** Warehouse execution summary derived from outbound tasks. */
    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_status", nullable = false, length = 40)
    @Builder.Default
    private SalesWarehouseStatus warehouseStatus = SalesWarehouseStatus.NOT_RELEASED;

    /** Carrier-facing progress, independent from warehouse execution. */
    @Enumerated(EnumType.STRING)
    @Column(name = "logistics_status", nullable = false, length = 40)
    @Builder.Default
    private SalesLogisticsStatus logisticsStatus = SalesLogisticsStatus.NOT_CREATED;

    /** Review policy captured when the order is submitted. */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode_snapshot", nullable = false, length = 30)
    @Builder.Default
    private SalesApprovalMode approvalModeSnapshot = SalesApprovalMode.DIRECT;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_policy", nullable = false, length = 30)
    @Builder.Default
    private AllocationPolicy allocationPolicy = AllocationPolicy.FULL_ONLY;

    @Column(name = "requested_ship_date")
    private LocalDate requestedShipDate;

    @Column(name = "promised_ship_date")
    private LocalDate promisedShipDate;

    @Column(name = "shortage_reason", length = 500)
    private String shortageReason;

    @Version
    @Column(name = "status_version", nullable = false)
    @Builder.Default
    private Long statusVersion = 0L;

    @Size(max = 500, message = "状态原因长度不能超过 500 个字符")
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_by")
    private Long statusChangedBy;

    @Column(name = "status_changed_by_name", length = 100)
    private String statusChangedByName;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

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
    @Column(length = 50, nullable = false)
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

    // Order-time shipping snapshot. These fields are intentionally detached
    // from the customer master because a customer may use a different address
    // for every order.
    @Column(name = "consignee_name", length = 200)
    private String consigneeName;

    @Column(name = "consignee_phone", length = 50)
    private String consigneePhone;

    @Column(name = "ship_address1", length = 255)
    private String shipAddress1;

    @Column(name = "ship_address2", length = 255)
    private String shipAddress2;

    @Column(name = "ship_city", length = 100)
    private String shipCity;

    @Column(name = "ship_province", length = 100)
    private String shipProvince;

    @Column(name = "ship_zip", length = 30)
    private String shipZip;

    @Column(name = "ship_country_code", length = 10)
    private String shipCountryCode;

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
     * 审批通过时间（V4.2 新增）
     *
     * 说明：
     * - 仅在 commercial_status → APPROVED 时由 Service 写入
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
     * - 在 commercial_status → COMPLETED 时由状态投影服务写入
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
        return commercialStatus != null && commercialStatus.canModify();
    }

    /**
     * 判断是否可以取消订单
     *
     * @return true 如果状态为 PENDING_APPROVAL 或 APPROVED
     */
    public boolean canCancel() {
        return commercialStatus == CommercialStatus.PENDING_APPROVAL
            || commercialStatus == CommercialStatus.APPROVED;
    }

    /**
     * 判断是否可以审批
     *
     * @return true 如果状态为 PENDING_APPROVAL
     */
    public boolean canApprove() {
        return commercialStatus != null && commercialStatus.canApprove();
    }

    /**
     * 判断是否已完成（不可修改）
     *
     * @return true 如果业务主状态已经终结
     */
    public boolean isFinalized() {
        return commercialStatus != null && commercialStatus.isTerminal();
    }
}
