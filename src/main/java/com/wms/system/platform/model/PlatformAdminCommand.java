package com.wms.system.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "platform_admin_commands",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_platform_admin_command_actor_key",
        columnNames = {"actor_platform_user_id", "idempotency_key"}
    ),
    indexes = @Index(
        name = "idx_platform_admin_command_target_created",
        columnList = "target_platform_user_id,created_at"
    )
)
public class PlatformAdminCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_platform_user_id", nullable = false)
    private Long actorPlatformUserId;

    @Column(name = "target_platform_user_id", nullable = false)
    private Long targetPlatformUserId;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "safe_response_json", columnDefinition = "TEXT")
    private String safeResponseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
