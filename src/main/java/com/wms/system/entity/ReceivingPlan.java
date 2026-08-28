package com.wms.system.entity;

import com.wms.system.entity.enums.ReceivingPlanStatus;
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

import java.time.LocalDateTime;

@Entity
@Table(
    name = "receiving_plan",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_receiving_plan_company_number", columnNames = {"company_id", "plan_number"}),
        @UniqueConstraint(
            name = "uk_receiving_plan_company_order_warehouse",
            columnNames = {"company_id", "purchase_order_id", "warehouse_id"}
        )
    },
    indexes = @Index(name = "idx_receiving_plan_status", columnList = "company_id,status")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivingPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "plan_number", nullable = false, length = 40, updatable = false)
    private String planNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceivingPlanStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_by_name", nullable = false, length = 100, updatable = false)
    private String createdByName;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "expected_window_start")
    private LocalDateTime expectedWindowStart;

    @Column(name = "expected_window_end")
    private LocalDateTime expectedWindowEnd;

    @Column(name = "assigned_receiver_id")
    private Long assignedReceiverId;

    @Column(name = "assigned_receiver_name", length = 100)
    private String assignedReceiverName;

    @Column(name = "assigned_team", length = 100)
    private String assignedTeam;

    @Column(name = "dock_area", length = 100)
    private String dockArea;

    @Column(name = "work_area", length = 100)
    private String workArea;

    @Column(name = "warehouse_notes", length = 500)
    private String warehouseNotes;
}
