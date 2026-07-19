package com.wms.system.entity;

import com.wms.system.entity.enums.EmergencyCorrectionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "emergency_stock_correction",
    indexes = {
        @Index(name = "idx_emergency_correction_no", columnList = "correction_no"),
        @Index(name = "idx_emergency_correction_status", columnList = "status"),
        @Index(name = "idx_emergency_correction_product", columnList = "product_sku_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_emergency_correction_company_no", columnNames = {"company_id", "correction_no"})
    }
)
public class EmergencyStockCorrection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correction_no", nullable = false, length = 40)
    private String correctionNo;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "inventory_batch_id")
    private Long inventoryBatchId;

    @Column(name = "batch_code", length = 80)
    private String batchCode;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "counted_qty", nullable = false)
    private Integer countedQty;

    @Column(name = "adjustment_qty", nullable = false)
    private Integer adjustmentQty;

    @Column(name = "reason_code", nullable = false, length = 80)
    private String reasonCode;

    @Column(name = "reason_detail", length = 1000)
    private String reasonDetail;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Column(name = "related_sales_order_id")
    private Long relatedSalesOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EmergencyCorrectionStatus status = EmergencyCorrectionStatus.DRAFT;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "applied_by")
    private Long appliedBy;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;
}
