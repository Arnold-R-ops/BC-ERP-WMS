package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "transfer_dispatch_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferDispatchLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_id", nullable = false, updatable = false)
    private TransferDispatch dispatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_line_id", nullable = false, updatable = false)
    private TransferOrderLine transferLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocation_id", nullable = false, updatable = false)
    private TransferAllocation allocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false, updatable = false)
    private InventoryBatch sourceBatch;

    @Column(name = "source_batch_code", nullable = false, length = 50, updatable = false)
    private String sourceBatchCode;

    @Column(name = "external_batch_code", length = 100, updatable = false)
    private String externalBatchCode;

    @Column(name = "production_date", updatable = false)
    private LocalDate productionDate;

    @Column(name = "expiry_date", updatable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "posting_id", nullable = false, length = 120, updatable = false)
    private String postingId;

    @Column(name = "stock_transaction_id", nullable = false, updatable = false)
    private Long stockTransactionId;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "damaged_quantity", nullable = false)
    @Builder.Default
    private Integer damagedQuantity = 0;

    @Column(name = "quarantined_quantity", nullable = false)
    @Builder.Default
    private Integer quarantinedQuantity = 0;

    @Column(name = "shortage_closed_quantity", nullable = false)
    @Builder.Default
    private Integer shortageClosedQuantity = 0;

    public int openQuantity() {
        return quantity - receivedQuantity - shortageClosedQuantity;
    }
}
