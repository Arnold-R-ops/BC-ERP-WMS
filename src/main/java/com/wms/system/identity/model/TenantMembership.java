package com.wms.system.identity.model;

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
    name = "tenant_memberships",
    indexes = @Index(name = "idx_tenant_memberships_tenant", columnList = "tenant_id"),
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_memberships_identity_tenant", columnNames = {"identity_id", "tenant_id"}),
        @UniqueConstraint(name = "uk_tenant_memberships_tenant_user", columnNames = {"tenant_id", "tenant_user_id"})
    }
)
public class TenantMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identity_id", nullable = false)
    private Long identityId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "tenant_user_id", nullable = false)
    private Long tenantUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.INVITED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void initialize() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void refresh() {
        updatedAt = OffsetDateTime.now();
    }
}
