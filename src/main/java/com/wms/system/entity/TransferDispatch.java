package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transfer_dispatch")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferDispatch extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_order_id", nullable = false, updatable = false)
    private TransferOrder transferOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private TransferWarehouseTask task;

    @Column(name = "dispatch_number", nullable = false, length = 40, updatable = false)
    private String dispatchNumber;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "handover_reference", length = 120, updatable = false)
    private String handoverReference;

    @Column(name = "estimated_arrival_at", updatable = false)
    private LocalDateTime estimatedArrivalAt;

    @Column(name = "warehouse_remark", length = 500, updatable = false)
    private String warehouseRemark;

    @Column(name = "dispatched_by", nullable = false, updatable = false)
    private Long dispatchedBy;

    @Column(name = "dispatched_by_name", nullable = false, length = 100, updatable = false)
    private String dispatchedByName;

    @Column(name = "dispatched_at", nullable = false, updatable = false)
    private LocalDateTime dispatchedAt;

    @OneToMany(mappedBy = "dispatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransferDispatchLine> lines = new ArrayList<>();

    public void addLine(TransferDispatchLine line) {
        lines.add(line);
        line.setDispatch(this);
    }
}
