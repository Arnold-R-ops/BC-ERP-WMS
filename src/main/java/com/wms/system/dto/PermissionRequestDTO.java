package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDTO {
    private Long id;
    private Long targetUserId;
    private String targetUsername;
    private Long requestedRoleId;
    private String requestedRoleCode;
    private String requestedRoleName;
    private List<AssignableWarehouseDTO> warehouses;
    private String requestReason;
    private String status;
    private Integer highRiskPermissionCount;
    private String submittedByUsername;
    private LocalDateTime submittedAt;
    private String reviewedByUsername;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String revokedByUsername;
    private LocalDateTime revokedAt;
    private String revocationComment;
}

