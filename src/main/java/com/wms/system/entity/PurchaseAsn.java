package com.wms.system.entity;

import com.wms.system.entity.enums.PurchaseAsnStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "purchase_asn",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_asn_company_number", columnNames = {"company_id", "asn_number"})
    },
    indexes = {
        @Index(name = "idx_purchase_asn_order_status", columnList = "company_id,purchase_order_id,status"),
        @Index(name = "idx_purchase_asn_warehouse_status", columnList = "company_id,warehouse_id,status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAsn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "asn_number", nullable = false, length = 40, updatable = false)
    private String asnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseAsnStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_of_id")
    private PurchaseAsn correctionOf;

    @Column(name = "supplier_shipment_no", length = 100)
    private String supplierShipmentNo;

    @Column(name = "carrier", length = 100)
    private String carrier;

    @Column(name = "tracking_no", length = 100)
    private String trackingNo;

    @Column(name = "expected_arrival_date")
    private LocalDate expectedArrivalDate;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_by_name", nullable = false, length = 100, updatable = false)
    private String createdByName;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_by_name", length = 100)
    private String confirmedByName;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_by_name", length = 100)
    private String closedByName;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_by_name", length = 100)
    private String cancelledByName;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(length = 500)
    private String remark;

    @OneToMany(mappedBy = "purchaseAsn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseAsnItem> items = new ArrayList<>();

    public void addItem(PurchaseAsnItem item) {
        items.add(item);
        item.setPurchaseAsn(this);
    }

    public boolean hasReceipts() {
        return items.stream().anyMatch(item -> item.getReceivedQuantity() != null && item.getReceivedQuantity() > 0);
    }
}
