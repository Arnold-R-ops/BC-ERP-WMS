package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_disposition_execution_line")
public class InventoryDispositionExecutionLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private InventoryDispositionExecution execution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disposition_line_id", nullable = false)
    private InventoryDispositionLine dispositionLine;

    @Column(name = "inventory_batch_id", nullable = false)
    private Long inventoryBatchId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;

    @Column(name = "stock_transaction_id", nullable = false)
    private Long stockTransactionId;

    @Column(name = "posting_id", nullable = false, length = 120)
    private String postingId;
}
