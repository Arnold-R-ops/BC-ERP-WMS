package com.wms.system.tenant.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "tenant_purge_audit_logs")
public class TenantPurgeAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;
    @Column(nullable = false, length = 20)
    private String result;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "deleted_row_count")
    private Long deletedRowCount;
    @Column(name = "error_detail", length = 1000)
    private String errorDetail;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @PrePersist void initialize() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
}
