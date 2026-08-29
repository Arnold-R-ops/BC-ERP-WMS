package com.wms.system.entity;

import com.wms.system.entity.enums.ReceivingInspectionMethod;
import com.wms.system.entity.enums.ReceivingInspectionResultStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receiving_inspection_result")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingInspectionResult extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "result_number", nullable = false, length = 40, updatable = false)
    private String resultNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_task_id", nullable = false, updatable = false)
    private ReceivingInspectionTask inspectionTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceivingInspectionResultStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_method", nullable = false, length = 20, updatable = false)
    private ReceivingInspectionMethod inspectionMethod;

    @Column(name = "sample_quantity", updatable = false)
    private Integer sampleQuantity;

    @Column(name = "defect_quantity", nullable = false, updatable = false)
    @Builder.Default
    private Integer defectQuantity = 0;

    @Column(name = "reason_code", nullable = false, length = 80, updatable = false)
    private String reasonCode;

    @Column(name = "reason_detail", length = 500, updatable = false)
    private String reasonDetail;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private Long submittedBy;

    @Column(name = "submitted_by_name", nullable = false, length = 100, updatable = false)
    private String submittedByName;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @OneToMany(mappedBy = "inspectionResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceivingInspectionResultLine> lines = new ArrayList<>();

    public void addLine(ReceivingInspectionResultLine line) {
        lines.add(line);
        line.setInspectionResult(this);
    }
}
