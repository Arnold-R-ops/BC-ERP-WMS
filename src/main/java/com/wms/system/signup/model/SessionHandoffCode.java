package com.wms.system.signup.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "session_handoff_codes",
    indexes = @Index(name = "idx_session_handoff_expiry", columnList = "expires_at"),
    uniqueConstraints = @UniqueConstraint(name = "uk_session_handoff_codes_hash", columnNames = "code_hash"))
public class SessionHandoffCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signup_request_id", nullable = false, updatable = false)
    private Long signupRequestId;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "identity_id", nullable = false, updatable = false)
    private Long identityId;

    @Column(name = "tenant_user_id", nullable = false, updatable = false)
    private Long tenantUserId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
