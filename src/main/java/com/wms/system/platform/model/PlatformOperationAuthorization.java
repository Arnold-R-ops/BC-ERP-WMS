package com.wms.system.platform.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "platform_operation_authorizations",
    indexes = @Index(
        name = "idx_platform_operation_auth_tenant_expiry",
        columnList = "tenant_id,expires_at"
    )
)
public class PlatformOperationAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "requested_by_platform_user_id")
    private Long requestedByPlatformUserId;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 100)
    private String resourceId;

    @Column(name = "request_payload_json", nullable = false, columnDefinition = "TEXT")
    private String requestPayloadJson;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "resource_scope", nullable = false, length = 100)
    private String resourceScope;

    @Column(name = "approved_by_tenant_user_id")
    private Long approvedByTenantUserId;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "executed_by_platform_user_id")
    private Long executedByPlatformUserId;

    @Column(name = "execution_key", length = 80)
    private String executionKey;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
