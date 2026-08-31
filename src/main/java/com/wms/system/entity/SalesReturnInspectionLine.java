package com.wms.system.entity;

import com.wms.system.entity.enums.RmaDisposition;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_return_inspection_line")
public class SalesReturnInspectionLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private SalesReturnInspection inspection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_line_id", nullable = false)
    private SalesReturnReceiptLine receiptLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RmaDisposition disposition;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "refund_eligible_quantity", nullable = false)
    private Integer refundEligibleQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_batch_id", nullable = false)
    private InventoryBatch resultBatch;
}
