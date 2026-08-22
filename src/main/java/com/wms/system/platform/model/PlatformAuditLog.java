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
    name = "platform_audit_logs",
    indexes = {
        @Index(name = "idx_platform_audit_tenant_created", columnList = "target_tenant_id,created_at"),
        @Index(name = "idx_platform_audit_actor_created", columnList = "platform_user_id,created_at")
    }
)
public class PlatformAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_user_id", nullable = false)
    private Long platformUserId;

    // Directory-level reads span multiple tenants, so they deliberately have
    // no single target tenant. Tenant-specific data reads remain non-null.
    @Column(name = "target_tenant_id")
    private Long targetTenantId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "resource_type", length = 80)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(nullable = false, length = 30)
    private String result;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
