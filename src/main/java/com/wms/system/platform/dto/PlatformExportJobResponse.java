package com.wms.system.platform.dto;

import com.wms.system.platform.model.PlatformExportJob;
import java.time.OffsetDateTime;

public record PlatformExportJobResponse(
    String id, Long companyId, String status, String resource, Long recordCount,
    String sha256, OffsetDateTime expiresAt, OffsetDateTime createdAt, OffsetDateTime completedAt
) {
    public static PlatformExportJobResponse from(PlatformExportJob job) {
        return new PlatformExportJobResponse(job.getPublicId(), job.getTargetTenantId(), job.getStatus(),
            job.getRequestedResource(), job.getRecordCount(), job.getFileSha256(), job.getExpiresAt(),
            job.getCreatedAt(), job.getCompletedAt());
    }
}
