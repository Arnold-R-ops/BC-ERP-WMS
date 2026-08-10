package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestAuditDTO {
    private Long id;
    private Long permissionRequestId;
    private String action;
    private Long operatorId;
    private String operatorUsername;
    private String operatorRoleCode;
    private Long targetUserId;
    private String targetUsername;
    private Long requestedRoleId;
    private String requestedRoleCode;
    private String fromStatus;
    private String toStatus;
    private String warehouseIds;
    private Integer highRiskPermissionCount;
    private String permissionCodes;
    private String reason;
    private LocalDateTime createdAt;
}

