package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only lifecycle history shown in the permission-package workspace. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleGovernanceAuditDTO {
    private Long id;
    private String action;
    private Long operatorId;
    private String operatorUsername;
    private String fromReviewStatus;
    private String toReviewStatus;
    private String fromRuntimeStatus;
    private String toRuntimeStatus;
    private Integer permissionCount;
    private Integer highRiskCount;
    private List<String> permissionCodes;
    private List<String> addedPermissionCodes;
    private List<String> removedPermissionCodes;
    private String reason;
    private String snapshotFingerprint;
    private LocalDateTime createdAt;
}
