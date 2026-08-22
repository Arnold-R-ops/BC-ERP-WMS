package com.wms.system.signup.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "signup_requests",
    indexes = {
        @Index(name = "idx_signup_requests_email", columnList = "normalized_email"),
        @Index(name = "idx_signup_requests_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_signup_requests_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uk_signup_requests_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_signup_requests_id_tenant", columnNames = {"id", "tenant_id"})
    }
)
public class SignupRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, updatable = false)
    private String publicId;

    @Column(name = "idempotency_key", nullable = false, length = 80, updatable = false)
    private String idempotencyKey;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "requested_plan_code", nullable = false, length = 30)
    private String requestedPlanCode;

    @Column(name = "company_name", length = 160)
    private String companyName;

    @Column(length = 30)
    private String slug;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "request_fingerprint", length = 128, updatable = false)
    private String requestFingerprint;

    @Column(name = "details_fingerprint", length = 128)
    private String detailsFingerprint;

    @Column(name = "identity_id")
    private Long identityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SignupStatus status = SignupStatus.EMAIL_PENDING;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void initialize() {
        OffsetDateTime now = OffsetDateTime.now();
        if (publicId == null) publicId = UUID.randomUUID().toString();
        normalizedEmail = normalizeEmail(normalizedEmail);
        slug = normalizeSlug(slug);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void refresh() {
        normalizedEmail = normalizeEmail(normalizedEmail);
        slug = normalizeSlug(slug);
        updatedAt = OffsetDateTime.now();
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSlug(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
