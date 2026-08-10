package com.wms.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_permission_request")
public class SysPermissionRequest {
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Builder.Default
    private Long companyId = 1L;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "target_username", nullable = false, length = 100)
    private String targetUsername;

    @Column(name = "requested_role_id", nullable = false)
    private Long requestedRoleId;

    @Column(name = "requested_role_code", nullable = false, length = 50)
    private String requestedRoleCode;

    @Column(name = "requested_role_name", nullable = false, length = 100)
    private String requestedRoleName;

    @Column(name = "request_reason", length = 500)
    private String requestReason;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = STATUS_PENDING_REVIEW;

    @Column(name = "high_risk_permission_count", nullable = false)
    @Builder.Default
    private Integer highRiskPermissionCount = 0;

    @Column(name = "permission_codes", length = 4000)
    private String permissionCodes;

    @Column(name = "snapshot_fingerprint", nullable = false, length = 64)
    private String snapshotFingerprint;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(name = "submitted_by_username", nullable = false, length = 100)
    private String submittedByUsername;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_username", length = 100)
    private String reviewedByUsername;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoked_by_username", length = 100)
    private String revokedByUsername;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_comment", length = 500)
    private String revocationComment;

    @Version
    @Column(nullable = false)
    private Long version;
}

