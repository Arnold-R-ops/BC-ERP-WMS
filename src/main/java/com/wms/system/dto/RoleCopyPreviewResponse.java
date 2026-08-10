package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Effective permission snapshot and risk summary shown before a role copy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCopyPreviewResponse {
    private Long sourceRoleId;
    private String sourceRoleCode;
    private String sourceRoleName;
    private String sourceRoleType;
    private String sourceSystemCategory;
    private Integer permissionCount;
    private Integer normalRiskCount;
    private Integer highRiskCount;
    private Integer criticalRiskCount;
    private List<PermissionDTO> permissions;
    private List<PermissionDTO> highRiskPermissions;
    private String snapshotFingerprint;
    private Boolean copyAllowed;
    private List<String> blockers;
    private LocalDateTime generatedAt;
}

