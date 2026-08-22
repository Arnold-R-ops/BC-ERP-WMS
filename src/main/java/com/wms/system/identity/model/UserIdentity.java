package com.wms.system.identity.model;

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
    name = "user_identities",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_identities_email",
        columnNames = "normalized_email"
    )
)
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email_verified_at", nullable = false)
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "security_version", nullable = false)
    @Builder.Default
    private Long securityVersion = 1L;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

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
