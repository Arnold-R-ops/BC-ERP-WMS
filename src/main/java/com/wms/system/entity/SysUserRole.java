package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User-Role Association Entity
 *
 * Many-to-Many relationship between users and roles.
 * One user can have multiple roles, and one role can be assigned to multiple users.
 *
 * Business Rules:
 * - Unique constraint on (user_id, role_id) to prevent duplicate assignments
 * - Cascade deletion: When user or role is deleted, associations are removed
 * - Audit fields: assigned_at (timestamp), assigned_by (administrator who made the assignment)
 *
 * Use Cases:
 * - Assign role to user: INSERT INTO sys_user_role (user_id, role_id, assigned_by)
 * - Remove role from user: DELETE FROM sys_user_role WHERE user_id = ? AND role_id = ?
 * - Query user's roles: SELECT role_id FROM sys_user_role WHERE user_id = ?
 * - Query role's users: SELECT user_id FROM sys_user_role WHERE role_id = ?
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "sys_user_role",
    indexes = {
        @Index(name = "idx_user_role_user", columnList = "user_id"),
        @Index(name = "idx_user_role_role", columnList = "role_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_role", columnNames = {"user_id", "role_id"})
    }
)
public class SysUserRole {

    /**
     * Primary Key (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User ID (foreign key to users table)
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Role ID (foreign key to sys_role table)
     */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * Assignment Timestamp
     * When the role was assigned to the user
     */
    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    /**
     * Assigned By (Administrator ID)
     * Which administrator performed this role assignment
     */
    @Column(name = "assigned_by")
    private Long assignedBy;

    // ========== JPA Relationships (Optional, for eager loading) ==========

    /**
     * User Entity (Many-to-One)
     * Lazy loading to avoid performance issues
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * Role Entity (Many-to-One)
     * Lazy loading to avoid performance issues
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private SysRole role;
}
