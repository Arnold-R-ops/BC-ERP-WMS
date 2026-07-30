package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "sales_daily_summary",
    indexes = {
        @Index(name = "idx_sales_daily_summary_date", columnList = "summary_date"),
        @Index(name = "idx_sales_daily_summary_refreshed", columnList = "refreshed_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sales_daily_summary_company_date", columnNames = {"company_id", "summary_date"})
    }
)
public class SalesDailySummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "total_order_count", nullable = false)
    @Builder.Default
    private Long totalOrderCount = 0L;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "draft_count", nullable = false)
    @Builder.Default
    private Long draftCount = 0L;

    @Column(name = "pending_approval_count", nullable = false)
    @Builder.Default
    private Long pendingApprovalCount = 0L;

    @Column(name = "approved_awaiting_shipment_count", nullable = false)
    @Builder.Default
    private Long approvedAwaitingShipmentCount = 0L;

    @Column(name = "shipped_count", nullable = false)
    @Builder.Default
    private Long shippedCount = 0L;

    @Column(name = "rejected_count", nullable = false)
    @Builder.Default
    private Long rejectedCount = 0L;

    @Column(name = "cancelled_count", nullable = false)
    @Builder.Default
    private Long cancelledCount = 0L;

    @Column(name = "voided_count", nullable = false)
    @Builder.Default
    private Long voidedCount = 0L;

    @Column(name = "refreshed_at", nullable = false)
    private LocalDateTime refreshedAt;
}
