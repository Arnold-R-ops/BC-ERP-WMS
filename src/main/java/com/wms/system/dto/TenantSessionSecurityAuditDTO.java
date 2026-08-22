package com.wms.system.dto;

import com.wms.system.entity.TenantSessionSecurityAudit;

import java.time.LocalDateTime;

/** Read-only projection for tenant session revocation audit history. */
public record TenantSessionSecurityAuditDTO(
    Long id,
    String action,
    Long operatorId,
    String operatorUsername,
    Long targetUserId,
    String targetUsername,
    String reason,
    String result,
    LocalDateTime createdAt
) {
    public static TenantSessionSecurityAuditDTO from(TenantSessionSecurityAudit audit) {
        return new TenantSessionSecurityAuditDTO(
            audit.getId(),
            audit.getAction(),
            audit.getOperatorId(),
            audit.getOperatorUsername(),
            audit.getTargetUserId(),
            audit.getTargetUsername(),
            audit.getReason(),
            audit.getResult(),
            audit.getCreatedAt()
        );
    }
}
