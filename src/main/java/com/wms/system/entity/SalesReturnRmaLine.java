package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_return_rma_line", uniqueConstraints =
    @UniqueConstraint(name = "uk_sales_return_rma_line_dispatch", columnNames = {"company_id", "rma_id", "outbound_task_id"}))
public class SalesReturnRmaLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rma_id", nullable = false)
    private SalesReturnRma rma;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_item_id", nullable = false)
    private SalesOrderItem salesOrderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbound_task_id", nullable = false)
    private OutboundTask outboundTask;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_batch_id", nullable = false)
    private InventoryBatch originalBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false)
    private ProductSku productSku;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "authorized_quantity", nullable = false)
    @Builder.Default
    private Integer authorizedQuantity = 0;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "inspected_quantity", nullable = false)
    @Builder.Default
    private Integer inspectedQuantity = 0;

    @Column(name = "restock_quantity", nullable = false)
    @Builder.Default
    private Integer restockQuantity = 0;

    @Column(name = "damaged_quantity", nullable = false)
    @Builder.Default
    private Integer damagedQuantity = 0;

    @Column(name = "quarantine_quantity", nullable = false)
    @Builder.Default
    private Integer quarantineQuantity = 0;

    @Column(name = "rejected_quantity", nullable = false)
    @Builder.Default
    private Integer rejectedQuantity = 0;

    @Column(name = "refund_eligible_quantity", nullable = false)
    @Builder.Default
    private Integer refundEligibleQuantity = 0;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;
}
