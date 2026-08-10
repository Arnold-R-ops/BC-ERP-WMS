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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_permission_request_audit")
public class SysPermissionRequestAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    @Builder.Default
    private Long companyId = 1L;

    @Column(name = "permission_request_id", nullable = false)
    private Long permissionRequestId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_username", nullable = false, length = 100)
    private String operatorUsername;

    @Column(name = "operator_role_code", nullable = false, length = 50)
    private String operatorRoleCode;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "target_username", nullable = false, length = 100)
    private String targetUsername;

    @Column(name = "requested_role_id", nullable = false)
    private Long requestedRoleId;

    @Column(name = "requested_role_code", nullable = false, length = 50)
    private String requestedRoleCode;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "warehouse_ids", length = 1000)
    private String warehouseIds;

    @Column(name = "high_risk_permission_count", nullable = false)
    private Integer highRiskPermissionCount;

    @Column(name = "permission_codes", length = 4000)
    private String permissionCodes;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

