package com.wms.system.entity;

import com.wms.system.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_return_rma", uniqueConstraints = {
    @UniqueConstraint(name = "uk_sales_return_rma_company_number", columnNames = {"company_id", "rma_number"}),
    @UniqueConstraint(name = "uk_sales_return_rma_company_channel_case", columnNames = {"company_id", "channel", "external_case_id"})
}, indexes = {
    @Index(name = "idx_sales_return_rma_order", columnList = "company_id,sales_order_id"),
    @Index(name = "idx_sales_return_rma_status", columnList = "company_id,business_status,receipt_status,inspection_status,resolution_status")
})
public class SalesReturnRma extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rma_number", nullable = false, length = 40)
    private String rmaNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private SalesOrderShipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "rma_type", nullable = false, length = 30)
    private RmaType rmaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_status", nullable = false, length = 30)
    private RmaBusinessStatus businessStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false, length = 30)
    private RmaReceiptStatus receiptStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_status", nullable = false, length = 30)
    private RmaInspectionStatus inspectionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false, length = 40)
    private RmaResolutionStatus resolutionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode_snapshot", nullable = false, length = 30)
    private RmaApprovalMode approvalModeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type_snapshot", nullable = false, length = 20)
    private CustomerType customerTypeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "statutory_basis", nullable = false, length = 40)
    private RmaStatutoryBasis statutoryBasis;

    @Column(name = "standard_return_days_snapshot", nullable = false)
    private Integer standardReturnDaysSnapshot;

    @Column(name = "outside_standard_window", nullable = false)
    @Builder.Default
    private Boolean outsideStandardWindow = false;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "reason_detail", length = 1000)
    private String reasonDetail;

    @Column(name = "customer_shipped_at")
    private LocalDateTime customerShippedAt;

    @Column(name = "return_carrier", length = 80)
    private String returnCarrier;

    @Column(name = "return_tracking_no", length = 120)
    private String returnTrackingNo;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_by_name", nullable = false, length = 100)
    private String requestedByName;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "cancelled_or_closed_by")
    private Long cancelledOrClosedBy;

    @Column(name = "cancelled_or_closed_by_name", length = 100)
    private String cancelledOrClosedByName;

    @Column(name = "cancelled_or_closed_at")
    private LocalDateTime cancelledOrClosedAt;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_currency", length = 3)
    private String refundCurrency;

    @Column(name = "refund_reference", length = 120)
    private String refundReference;

    @Column(name = "advance_resolution", nullable = false)
    @Builder.Default
    private Boolean advanceResolution = false;

    @Column(name = "replacement_sales_order_id")
    private Long replacementSalesOrderId;

    @Column(name = "channel", length = 50)
    private String channel;

    @Column(name = "external_case_id", length = 120)
    private String externalCaseId;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "rma", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SalesReturnRmaLine> lines = new ArrayList<>();

    public void addLine(SalesReturnRmaLine line) {
        lines.add(line);
        line.setRma(this);
    }
}
