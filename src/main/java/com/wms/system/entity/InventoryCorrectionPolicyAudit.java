package com.wms.system.entity;

import com.wms.system.entity.enums.EmergencyCorrectionApprovalMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

import java.time.LocalDateTime;

/** Append-only evidence for tenant inventory-correction policy changes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_correction_policy_audit")
public class InventoryCorrectionPolicyAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_mode", nullable = false, length = 20, updatable = false)
    private EmergencyCorrectionApprovalMode oldMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_mode", nullable = false, length = 20, updatable = false)
    private EmergencyCorrectionApprovalMode newMode;

    @Column(name = "operator_id", nullable = false, updatable = false)
    private Long operatorId;

    @Column(name = "operator_username", nullable = false, length = 100, updatable = false)
    private String operatorUsername;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "approval_entitled", nullable = false, updatable = false)
    private boolean approvalEntitled;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
