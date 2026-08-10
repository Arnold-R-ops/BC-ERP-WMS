package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Immutable lifecycle audit for custom permission packages. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_role_governance_audit")
public class SysRoleGovernanceAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Builder.Default
    private Long companyId = 1L;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_username", nullable = false, length = 100)
    private String operatorUsername;

    @Column(name = "from_review_status", length = 30)
    private String fromReviewStatus;

    @Column(name = "to_review_status", length = 30)
    private String toReviewStatus;

    @Column(name = "from_runtime_status", length = 20)
    private String fromRuntimeStatus;

    @Column(name = "to_runtime_status", length = 20)
    private String toRuntimeStatus;

    @Column(name = "permission_count", nullable = false)
    private Integer permissionCount;

    @Column(name = "high_risk_count", nullable = false)
    private Integer highRiskCount;

    @Column(name = "permission_codes", length = 4000)
    private String permissionCodes;

    @Column(name = "added_permission_codes", length = 2000)
    private String addedPermissionCodes;

    @Column(name = "removed_permission_codes", length = 2000)
    private String removedPermissionCodes;

    @Column(length = 500)
    private String reason;

    @Column(name = "snapshot_fingerprint", length = 64)
    private String snapshotFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
