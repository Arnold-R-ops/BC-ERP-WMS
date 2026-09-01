package com.wms.system.entity;

import com.wms.system.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transfer_order")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferOrder extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "transfer_number", nullable = false, length = 40, updatable = false)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_status", nullable = false, length = 30)
    private TransferBusinessStatus businessStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "outbound_status", nullable = false, length = 30)
    private TransferOutboundStatus outboundStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "inbound_status", nullable = false, length = 30)
    private TransferInboundStatus inboundStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_mode_snapshot", nullable = false, length = 20)
    private TransferApprovalMode approvalModeSnapshot;

    @Column(name = "receiving_inspection_snapshot", nullable = false)
    @Builder.Default
    private Boolean receivingInspectionSnapshot = false;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "estimated_arrival_at")
    private LocalDateTime estimatedArrivalAt;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "requested_by_name", nullable = false, length = 100, updatable = false)
    private String requestedByName;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "terminated_by")
    private Long terminatedBy;

    @Column(name = "terminated_by_name", length = 100)
    private String terminatedByName;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    @OneToMany(mappedBy = "transferOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<TransferOrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "transferOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("direction ASC")
    @Builder.Default
    private List<TransferWarehouseTask> tasks = new ArrayList<>();

    public void addLine(TransferOrderLine line) {
        lines.add(line);
        line.setTransferOrder(this);
    }

    public void addTask(TransferWarehouseTask task) {
        tasks.add(task);
        task.setTransferOrder(this);
    }
}
