package com.wms.system.entity;

import com.wms.system.entity.enums.ReceivingTaskStatus;
import com.wms.system.entity.enums.ReceivingTaskSourceType;
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
import jakarta.persistence.OneToOne;
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
    name = "receiving_task",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_receiving_task_company_number", columnNames = {"company_id", "task_number"}),
        @UniqueConstraint(name = "uk_receiving_task_company_asn", columnNames = {"company_id", "purchase_asn_id"}),
        @UniqueConstraint(
            name = "uk_receiving_task_company_other_inbound",
            columnNames = {"company_id", "other_inbound_application_id"}
        )
    },
    indexes = {
        @Index(name = "idx_receiving_task_warehouse_status", columnList = "company_id,warehouse_id,status"),
        @Index(name = "idx_receiving_task_plan", columnList = "company_id,receiving_plan_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "task_number", nullable = false, length = 40, updatable = false)
    private String taskNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_plan_id", updatable = false)
    private ReceivingPlan receivingPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30, updatable = false)
    private ReceivingTaskSourceType sourceType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_asn_id", updatable = false)
    private PurchaseAsn purchaseAsn;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "other_inbound_application_id", updatable = false)
    private OtherInboundApplication otherInboundApplication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceivingTaskStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_by_name", nullable = false, length = 100, updatable = false)
    private String createdByName;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_by_name", length = 100)
    private String closedByName;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @OneToMany(mappedBy = "receivingTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceivingTaskItem> items = new ArrayList<>();

    public void addItem(ReceivingTaskItem item) {
        items.add(item);
        item.setReceivingTask(this);
    }
}
