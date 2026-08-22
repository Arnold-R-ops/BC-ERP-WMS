package com.wms.system.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "platform_export_jobs")
public class PlatformExportJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;
    @Column(name = "platform_user_id", nullable = false)
    private Long platformUserId;
    @Column(name = "target_tenant_id", nullable = false)
    private Long targetTenantId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "requested_resource", nullable = false, length = 80)
    private String requestedResource;
    @Column(name = "file_path", length = 1000)
    private String filePath;
    @Column(name = "file_sha256", length = 64)
    private String fileSha256;
    @Column(name = "record_count")
    private Long recordCount;
    @Column(name = "error_code", length = 80)
    private String errorCode;
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "started_at")
    private OffsetDateTime startedAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist void initialize() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
