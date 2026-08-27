package com.wms.system.entity;

import com.wms.system.entity.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "idempotency_request",
    indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_idempotency_status", columnList = "status"),
        @Index(
            name = "idx_idempotency_company_status_lease",
            columnList = "company_id,status,locked_until"
        )
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_company_key", columnNames = {"company_id", "idempotency_key"})
    }
)
public class IdempotencyRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "operation", nullable = false, length = 80)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Identifies the worker that currently owns the PROCESSING lease.
     *
     * <p>A completion is accepted only when this token still matches the
     * database row. This prevents a worker whose lease expired from
     * overwriting the result produced by a later lease owner.</p>
     */
    @Column(name = "lease_token", length = 64)
    private String leaseToken;
}
