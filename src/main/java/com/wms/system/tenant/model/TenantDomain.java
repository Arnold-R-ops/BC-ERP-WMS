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
    name = "tenant_domains",
    indexes = {
        @Index(name = "idx_tenant_domains_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tenant_domains_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_domains_hostname", columnNames = "hostname")
    }
)
public class TenantDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 253)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_type", nullable = false, length = 30)
    private TenantDomainType domainType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TenantDomainStatus status = TenantDomainStatus.PENDING;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean primary = false;

    @Column(name = "verification_token_hash", length = 128)
    private String verificationTokenHash;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void initializeTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        hostname = normalizeHostname(hostname);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void refreshUpdatedAt() {
        hostname = normalizeHostname(hostname);
        updatedAt = OffsetDateTime.now();
    }

    private static String normalizeHostname(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
