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
@Table(
    name = "platform_user_roles",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_platform_user_roles_user_role",
        columnNames = {"platform_user_id", "platform_role_id"}
    )
)
public class PlatformUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_user_id", nullable = false)
    private Long platformUserId;

    @Column(name = "platform_role_id", nullable = false)
    private Long platformRoleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void initialize() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
