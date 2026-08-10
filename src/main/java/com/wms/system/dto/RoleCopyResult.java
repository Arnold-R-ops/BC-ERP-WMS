package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a completed permission-package snapshot copy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCopyResult {
    private RoleDTO role;
    private Long sourceRoleId;
    private String sourceRoleCode;
    private Integer permissionCount;
    private Integer highRiskCount;
    private String snapshotFingerprint;
    private Long auditId;
}

