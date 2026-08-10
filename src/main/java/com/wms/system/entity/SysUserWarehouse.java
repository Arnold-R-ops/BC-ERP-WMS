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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Warehouse assignment used only when the activated role is WAREHOUSE_STAFF. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "sys_user_warehouse",
    indexes = {
        @Index(name = "idx_user_warehouse_user", columnList = "user_id"),
        @Index(name = "idx_user_warehouse_warehouse", columnList = "warehouse_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sys_user_warehouse_company_user_warehouse",
            columnNames = {"company_id", "user_id", "warehouse_id"}
        )
    }
)
public class SysUserWarehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Builder.Default
    private Long companyId = 1L;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "assigned_by")
    private Long assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    private Warehouse warehouse;
}
