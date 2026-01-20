package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Role-Permission Association Entity
 *
 * Many-to-Many relationship between roles and permissions.
 * One role can have multiple permissions, and one permission can belong to multiple roles.
 *
 * Business Rules:
 * - Unique constraint on (role_id, permission_id) to prevent duplicate assignments
 * - Cascade deletion: When role or permission is deleted, associations are removed
 * - Audit fields: granted_at (timestamp), granted_by (administrator who made the grant)
 *
 * Use Cases:
 * - Assign permission to role: INSERT INTO sys_role_permission (role_id, permission_id, granted_by)
 * - Remove permission from role: DELETE FROM sys_role_permission WHERE role_id = ? AND permission_id = ?
 * - Query role's permissions: SELECT permission_id FROM sys_role_permission WHERE role_id = ?
 * - Query permission's roles: SELECT role_id FROM sys_role_permission WHERE permission_id = ?
 *
 * Dynamic Permission Loading:
 * - When loading user permissions, join this table with sys_user_role to get all permissions
 * - Support role inheritance: Recursively query parent roles' permissions
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
    name = "sys_role_permission",
    indexes = {
        @Index(name = "idx_role_permission_role", columnList = "role_id"),
        @Index(name = "idx_role_permission_permission", columnList = "permission_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_id", "permission_id"})
    }
)
public class SysRolePermission {

    /**
     * Primary Key (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Role ID (foreign key to sys_role table)
     */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /**
     * Permission ID (foreign key to sys_permission table)
     */
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    /**
     * Grant Timestamp
     * When the permission was granted to the role
     */
    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private LocalDateTime grantedAt = LocalDateTime.now();

    /**
     * Granted By (Administrator ID)
     * Which administrator performed this permission grant
     */
    @Column(name = "granted_by")
    private Long grantedBy;

    // ========== JPA Relationships (Optional, for eager loading) ==========

    /**
     * Role Entity (Many-to-One)
     * Lazy loading to avoid performance issues
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private SysRole role;

    /**
     * Permission Entity (Many-to-One)
     * Eager loading for permission details (commonly needed)
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false)
    private SysPermission permission;
}
