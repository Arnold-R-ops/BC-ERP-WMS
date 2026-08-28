package com.wms.system.entity;

import com.wms.system.entity.enums.OtherInboundBusinessType;
import com.wms.system.entity.enums.OtherInboundStatus;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "other_inbound_application",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_other_inbound_company_number",
        columnNames = {"company_id", "application_number"}),
    indexes = {
        @Index(name = "idx_other_inbound_company_status", columnList = "company_id,status"),
        @Index(name = "idx_other_inbound_company_warehouse", columnList = "company_id,warehouse_id,status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherInboundApplication extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "application_number", nullable = false, length = 40, updatable = false)
    private String applicationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 40)
    private OtherInboundBusinessType businessType;

    @Column(name = "source_reference", nullable = false, length = 120)
    private String sourceReference;

    @Column(name = "business_reason", nullable = false, length = 500)
    private String businessReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** Read-only trace to a pre-P0-02 inbound record; never accepted from API. */
    @Column(name = "legacy_inbound_order_id", updatable = false)
    private Long legacyInboundOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtherInboundStatus status;

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

    @Column(name = "terminated_by")
    private Long terminatedBy;

    @Column(name = "terminated_by_name", length = 100)
    private String terminatedByName;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    @Column(length = 500)
    private String remark;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OtherInboundApplicationItem> items = new ArrayList<>();

    public void addItem(OtherInboundApplicationItem item) {
        items.add(item);
        item.setApplication(this);
    }

    public int plannedQuantity() {
        return items.stream().mapToInt(OtherInboundApplicationItem::getPlannedQuantity).sum();
    }

    public int receivedQuantity() {
        return items.stream().mapToInt(OtherInboundApplicationItem::getReceivedQuantity).sum();
    }
}
