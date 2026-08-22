package com.wms.system.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "platform_access_grants")
public class PlatformAccessGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "grantee_platform_user_id", nullable = false) private Long granteePlatformUserId;
    @Column(name = "granted_by_platform_user_id", nullable = false) private Long grantedByPlatformUserId;
    @Column(nullable = false, length = 20) private String capability;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "dataset_code", nullable = false, length = 80) private String datasetCode;
    @Column(name = "effective_from", nullable = false) private OffsetDateTime effectiveFrom;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "revoked_at") private OffsetDateTime revokedAt;
    @Column(name = "revoked_by_platform_user_id") private Long revokedByPlatformUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void initialize() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
}
