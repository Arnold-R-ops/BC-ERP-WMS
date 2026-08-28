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
    name = "purchase_asn_item",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_purchase_asn_item_order_item",
            columnNames = {"company_id", "purchase_asn_id", "purchase_order_item_id"}
        )
    },
    indexes = @Index(name = "idx_purchase_asn_item_sku", columnList = "company_id,product_sku_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAsnItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_asn_id", nullable = false, updatable = false)
    private PurchaseAsn purchaseAsn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false, updatable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_allocation_id", nullable = false)
    private PurchaseOrderAllocation purchaseOrderAllocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku_id", nullable = false, updatable = false)
    private ProductSku productSku;

    @Column(name = "shipped_quantity", nullable = false)
    private Integer shippedQuantity;

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
        return shippedQuantity - receivedQuantity - closedQuantity;
    }
}
