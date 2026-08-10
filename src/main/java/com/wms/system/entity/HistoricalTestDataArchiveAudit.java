package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Append-only audit containing the complete before/after archive snapshot. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "historical_test_data_archive_audit")
public class HistoricalTestDataArchiveAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Builder.Default
    private Long companyId = 1L;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_username", nullable = false, length = 100)
    private String operatorUsername;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "snapshot_fingerprint", nullable = false, length = 64)
    private String snapshotFingerprint;

    @Column(name = "before_snapshot", nullable = false, columnDefinition = "TEXT")
    private String beforeSnapshot;

    @Column(name = "after_snapshot", nullable = false, columnDefinition = "TEXT")
    private String afterSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
