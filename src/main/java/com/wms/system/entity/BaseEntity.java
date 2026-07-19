package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
     * SaaS tenant placeholder. Current single-company deployment always uses 1.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

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
        updatedAt = LocalDateTime.now();
    }
}
