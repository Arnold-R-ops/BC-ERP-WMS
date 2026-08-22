package com.wms.system.platform.model;

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
    name = "platform_users",
    uniqueConstraints = @UniqueConstraint(name = "uk_platform_users_email", columnNames = "normalized_email")
)
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "security_version", nullable = false)
    @Builder.Default
    private Long securityVersion = 1L;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret_encrypted", columnDefinition = "TEXT")
    private String mfaSecretEncrypted;

    @Column(name = "recovery_code_hashes_json", columnDefinition = "TEXT")
    private String recoveryCodeHashesJson;

    @Column(name = "mfa_failed_attempts", nullable = false)
    @Builder.Default
    private Integer mfaFailedAttempts = 0;

    @Column(name = "mfa_locked_until")
    private OffsetDateTime mfaLockedUntil;

    @Column(name = "mfa_enrolled_at")
    private OffsetDateTime mfaEnrolledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void initialize() {
        OffsetDateTime now = OffsetDateTime.now();
        normalizedEmail = normalize(normalizedEmail);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void refresh() {
        normalizedEmail = normalize(normalizedEmail);
        updatedAt = OffsetDateTime.now();
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
