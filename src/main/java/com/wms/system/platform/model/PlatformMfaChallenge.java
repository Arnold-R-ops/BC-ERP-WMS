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
@Table(name = "platform_mfa_challenges",
    uniqueConstraints = @UniqueConstraint(name = "uk_platform_mfa_challenge_token", columnNames = "token_hash"),
    indexes = @Index(name = "idx_platform_mfa_challenge_user_created", columnList = "platform_user_id,created_at"))
public class PlatformMfaChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "platform_user_id", nullable = false)
    private Long platformUserId;

    @Column(name = "target_platform_user_id")
    private Long targetPlatformUserId;

    @Column(nullable = false, length = 40)
    private String purpose;

    @Column(name = "action_context_hash", length = 64)
    private String actionContextHash;

    @Column(name = "target_security_version")
    private Long targetSecurityVersion;

    @Column(name = "pending_secret_encrypted", columnDefinition = "TEXT")
    private String pendingSecretEncrypted;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist void initialize() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
