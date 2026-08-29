package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "receiving_inspection_task_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingInspectionTaskLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_task_id", nullable = false, updatable = false)
    private ReceivingInspectionTask inspectionTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_event_item_id", updatable = false)
    private ReceiptEventItem receiptEventItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false, updatable = false)
    private ProductSku productSku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false, updatable = false)
    private InventoryBatch sourceBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @Column(name = "sku_code", nullable = false, length = 100, updatable = false)
    private String skuCode;

    @Column(name = "product_name", nullable = false, length = 200, updatable = false)
    private String productName;

    @Column(name = "batch_code", nullable = false, length = 50, updatable = false)
    private String batchCode;

    @Column(name = "location_code", nullable = false, length = 50, updatable = false)
    private String locationCode;

    @Column(name = "planned_quantity", nullable = false, updatable = false)
    private Integer plannedQuantity;

    @Column(name = "pending_quantity", nullable = false)
    private Integer pendingQuantity;

    @Column(name = "pending_review_quantity", nullable = false)
    @Builder.Default
    private Integer pendingReviewQuantity = 0;

    @Column(name = "passed_quantity", nullable = false)
    @Builder.Default
    private Integer passedQuantity = 0;

    @Column(name = "quarantined_quantity", nullable = false)
    @Builder.Default
    private Integer quarantinedQuantity = 0;
}
