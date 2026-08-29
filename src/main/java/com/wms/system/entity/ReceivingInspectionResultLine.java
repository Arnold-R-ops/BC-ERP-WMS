package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "receiving_inspection_result_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingInspectionResultLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_result_id", nullable = false, updatable = false)
    private ReceivingInspectionResult inspectionResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_task_line_id", nullable = false, updatable = false)
    private ReceivingInspectionTaskLine inspectionTaskLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false, updatable = false)
    private InventoryBatch sourceBatch;

    @Column(name = "passed_quantity", nullable = false, updatable = false)
    private Integer passedQuantity;

    @Column(name = "quarantined_quantity", nullable = false, updatable = false)
    private Integer quarantinedQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passed_batch_id")
    private InventoryBatch passedBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quarantined_batch_id")
    private InventoryBatch quarantinedBatch;
}
