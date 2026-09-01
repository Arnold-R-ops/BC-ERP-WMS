package com.wms.system.entity;

import com.wms.system.entity.enums.TransferAllocationStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transfer_allocation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferAllocation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_line_id", nullable = false, updatable = false)
    private TransferOrderLine transferLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_batch_id", nullable = false, updatable = false)
    private InventoryBatch sourceBatch;

    @Column(name = "allocated_quantity", nullable = false, updatable = false)
    private Integer allocatedQuantity;

    @Column(name = "consumed_quantity", nullable = false)
    @Builder.Default
    private Integer consumedQuantity = 0;

    @Column(name = "released_quantity", nullable = false)
    @Builder.Default
    private Integer releasedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferAllocationStatus status;

    public int remainingQuantity() {
        return allocatedQuantity - consumedQuantity - releasedQuantity;
    }
}
