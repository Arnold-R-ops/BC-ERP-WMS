package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transfer_order_line")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferOrderLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_order_id", nullable = false, updatable = false)
    private TransferOrder transferOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false, updatable = false)
    private ProductSku productSku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_batch_id", updatable = false)
    private InventoryBatch preferredBatch;

    @Column(name = "backorder_line_id", updatable = false)
    private Long backorderLineId;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "allocated_quantity", nullable = false)
    @Builder.Default
    private Integer allocatedQuantity = 0;

    @Column(name = "dispatched_quantity", nullable = false)
    @Builder.Default
    private Integer dispatchedQuantity = 0;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "damaged_quantity", nullable = false)
    @Builder.Default
    private Integer damagedQuantity = 0;

    @Column(name = "quarantined_quantity", nullable = false)
    @Builder.Default
    private Integer quarantinedQuantity = 0;

    @Column(name = "closed_unshipped_quantity", nullable = false)
    @Builder.Default
    private Integer closedUnshippedQuantity = 0;

    @Column(name = "shortage_closed_quantity", nullable = false)
    @Builder.Default
    private Integer shortageClosedQuantity = 0;

    public int inTransitQuantity() {
        return dispatchedQuantity - receivedQuantity - shortageClosedQuantity;
    }

    public int openToAllocateQuantity() {
        return requestedQuantity - dispatchedQuantity - allocatedQuantity - closedUnshippedQuantity;
    }
}
