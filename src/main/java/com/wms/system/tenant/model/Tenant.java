package com.wms.system.tenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Locale;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "tenants",
    indexes = {
        @Index(name = "idx_tenants_status", columnList = "status"),
        @Index(name = "idx_tenants_purge_due_at", columnList = "purge_due_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenants_tenant_code", columnNames = "tenant_code"),
        @UniqueConstraint(name = "uk_tenants_slug", columnNames = "slug")
    }
)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_code", nullable = false, length = 40)
    private String tenantCode;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 30)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TenantStatus status = TenantStatus.PROVISIONING;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String timezone = "Asia/Shanghai";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String locale = "zh-CN";

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "purge_due_at")
    private OffsetDateTime purgeDueAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void initializeTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        slug = normalizeSlug(slug);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void refreshUpdatedAt() {
        slug = normalizeSlug(slug);
        updatedAt = OffsetDateTime.now();
    }

    private static String normalizeSlug(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
