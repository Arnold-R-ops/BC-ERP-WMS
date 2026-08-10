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

/** Immutable audit record for a permission-package snapshot copy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_role_copy_audit")
public class SysRoleCopyAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Builder.Default
    private Long companyId = 1L;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_username", nullable = false, length = 100)
    private String operatorUsername;

    @Column(name = "source_role_id", nullable = false)
    private Long sourceRoleId;

    @Column(name = "source_role_code", nullable = false, length = 50)
    private String sourceRoleCode;

    @Column(name = "target_role_id", nullable = false)
    private Long targetRoleId;

    @Column(name = "target_role_code", nullable = false, length = 50)
    private String targetRoleCode;

    @Column(name = "permission_count", nullable = false)
    private Integer permissionCount;

    @Column(name = "high_risk_count", nullable = false)
    private Integer highRiskCount;

    @Column(name = "high_risk_permission_codes", length = 2000)
    private String highRiskPermissionCodes;

    @Column(name = "operation_reason", length = 500)
    private String operationReason;

    @Column(name = "snapshot_fingerprint", nullable = false, length = 64)
    private String snapshotFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

