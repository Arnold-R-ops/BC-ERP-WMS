package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
    name = "receiving_task_item",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_receiving_task_item_asn_item",
            columnNames = {"company_id", "receiving_task_id", "purchase_asn_item_id"}
        ),
        @UniqueConstraint(
            name = "uk_receiving_task_item_other_inbound_item",
            columnNames = {"company_id", "receiving_task_id", "other_inbound_application_item_id"}
        )
    },
    indexes = @Index(name = "idx_receiving_task_item_sku", columnList = "company_id,product_sku_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingTaskItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_task_id", nullable = false, updatable = false)
    private ReceivingTask receivingTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_asn_item_id", updatable = false)
    private PurchaseAsnItem purchaseAsnItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id", updatable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "other_inbound_application_item_id", updatable = false)
    private OtherInboundApplicationItem otherInboundApplicationItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false, updatable = false)
    private ProductSku productSku;

    @Column(name = "expected_quantity", nullable = false, updatable = false)
    private Integer expectedQuantity;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "closed_quantity", nullable = false)
    @Builder.Default
    private Integer closedQuantity = 0;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "external_batch_code", length = 100)
    private String externalBatchCode;

    @Column(length = 500)
    private String remark;

    public int remainingQuantity() {
        return expectedQuantity - receivedQuantity - closedQuantity;
    }
}
