package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.wms.system.tenant.context.CompanyScope;

import java.time.LocalDateTime;

/**
 * Base Entity for Audit Fields
 *
 * All entities inherit this class for automatic timestamp management.
 *
 * Technical Implementation:
 * - @MappedSuperclass: JPA mapped superclass (fields inherited, no separate table)
 * - @EntityListeners: Enable JPA auditing listener for auto-filling timestamps
 * - @CreatedDate: Auto-filled on first persist (immutable)
 * - @LastModifiedDate: Auto-updated on every modification
 *
 * Architecture Decision:
 * - Uses LocalDateTime (JVM default timezone is forced to UTC in WmsSystemApplication)
 * - Stored as UTC time in database
 * - JVM timezone: UTC (set via TimeZone.setDefault("UTC"))
 * - Compatible with PostgreSQL TIMESTAMP WITHOUT TIME ZONE
 *
 * @author WMS Team
 * @since 2025-01-09
 * @version 2.1 (Simplified Timezone Handling)
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /**
     * Authoritative company discriminator for every tenant-owned entity.
     *
     * <p>{@link TenantId} makes Hibernate add {@code company_id = currentCompany}
     * to entity loads and queries. The value is also assigned by Hibernate on
     * insert, so controllers and request payloads cannot choose a company.</p>
     */
    @TenantId
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * Creation timestamp (auto-filled on first save, immutable)
     *
     * Stored in UTC (JVM timezone is forced to UTC)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last modification timestamp (auto-updated on every change)
     *
     * Stored in UTC (JVM timezone is forced to UTC)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void initializeAuditTimestamps() {
        enforceCompanyBoundary();
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void refreshUpdatedAt() {
        enforceCompanyBoundary();
        updatedAt = LocalDateTime.now();
    }

    private void enforceCompanyBoundary() {
        Long currentCompanyId = CompanyScope.currentCompanyId();
        if (companyId == null) {
            companyId = currentCompanyId;
        } else if (!companyId.equals(currentCompanyId)) {
            throw new IllegalStateException(
                "Entity company does not match the current company context");
        }
    }
}
