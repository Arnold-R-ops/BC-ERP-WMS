package com.wms.system.entity;

import com.wms.system.entity.enums.ReceivingInspectionReviewMode;
import com.wms.system.entity.enums.ReceivingInspectionSourceType;
import com.wms.system.entity.enums.ReceivingInspectionTaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receiving_inspection_task")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingInspectionTask extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "task_number", nullable = false, length = 40, updatable = false)
    private String taskNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, updatable = false)
    private ReceivingInspectionSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_event_id", updatable = false)
    private ReceiptEvent receiptEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_task_id", updatable = false)
    private ReceivingTask receivingTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_of_result_id", updatable = false)
    private ReceivingInspectionResult correctionOfResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Column(name = "source_reference", nullable = false, length = 120, updatable = false)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceivingInspectionTaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode_snapshot", nullable = false, length = 30, updatable = false)
    private ReceivingInspectionReviewMode reviewModeSnapshot;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_by_name", nullable = false, length = 100, updatable = false)
    private String createdByName;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "inspectionTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceivingInspectionTaskLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "inspectionTask")
    @OrderBy("id DESC")
    @Builder.Default
    private List<ReceivingInspectionResult> results = new ArrayList<>();

    public void addLine(ReceivingInspectionTaskLine line) {
        lines.add(line);
        line.setInspectionTask(this);
    }
}
