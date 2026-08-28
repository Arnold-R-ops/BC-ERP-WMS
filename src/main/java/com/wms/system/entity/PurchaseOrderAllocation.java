package com.wms.system.entity;

import com.wms.system.entity.enums.PurchaseOrderAllocationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "purchase_order_allocation",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_purchase_allocation_item_warehouse",
        columnNames = {"company_id", "purchase_order_item_id", "warehouse_id"}
    ),
    indexes = @Index(
        name = "idx_purchase_allocation_order_item_status",
        columnList = "company_id,purchase_order_item_id,status"
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false, updatable = false)
    private PurchaseOrderItem purchaseOrderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Column(name = "planned_quantity", nullable = false)
    private Integer plannedQuantity;

    @Column(name = "shipped_quantity", nullable = false)
    @Builder.Default
    private Integer shippedQuantity = 0;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private Integer receivedQuantity = 0;

    @Column(name = "closed_quantity", nullable = false)
    @Builder.Default
    private Integer closedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderAllocationStatus status;

    public int outstandingQuantity() {
        return plannedQuantity - receivedQuantity - closedQuantity;
    }

    public int availableToShipQuantity() {
        // closedQuantity is the unreceived remainder of quantities already
        // counted in shippedQuantity, so subtracting it again would understate
        // the quantity that can be covered by a later ASN.
        return plannedQuantity - shippedQuantity;
    }
}
