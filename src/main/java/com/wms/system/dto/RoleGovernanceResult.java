package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a permission-package lifecycle command. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleGovernanceResult {
    private RoleDTO role;
    private String action;
    private Long auditId;
    private Integer permissionCount;
    private Integer highRiskCount;
    private String snapshotFingerprint;
}
