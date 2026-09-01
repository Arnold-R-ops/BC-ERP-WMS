package com.wms.system.entity;

import com.wms.system.entity.enums.TransferReceiptDisposition;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transfer_receipt_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferReceiptLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false, updatable = false)
    private TransferReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_line_id", nullable = false, updatable = false)
    private TransferDispatchLine dispatchLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_line_id", nullable = false, updatable = false)
    private TransferOrderLine transferLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_batch_id", nullable = false, updatable = false)
    private InventoryBatch destinationBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false, updatable = false)
    private InventoryBatch sourceBatch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TransferReceiptDisposition disposition;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "posting_id", nullable = false, length = 120, updatable = false)
    private String postingId;

    @Column(name = "stock_transaction_id", nullable = false, updatable = false)
    private Long stockTransactionId;
}
