package com.wms.system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Role Inheritance Association Entity
 *
 * Defines role inheritance relationships for complex permission scenarios.
 * Supports multi-level inheritance (e.g., CHAIRMAN inherits from WAREHOUSE_ADMIN, BUYER, SELLER).
 *
 * Business Rules:
 * - child_role_id: The role that inherits (e.g., CHAIRMAN)
 * - parent_role_id: The role being inherited from (e.g., WAREHOUSE_ADMIN)
 * - Unique constraint on (child_role_id, parent_role_id) to prevent duplicate inheritance
 * - Cascade deletion: When role is deleted, inheritance relationships are removed
 *
 * Inheritance Logic:
 * - Child role automatically gets ALL permissions of parent role
 * - Supports multi-parent inheritance (one child can inherit from multiple parents)
 * - Supports multi-level inheritance (A inherits B, B inherits C → A gets C's permissions)
 * - Recursive query required to get all inherited permissions
 *
 * Use Cases:
 * - CHAIRMAN inherits [WAREHOUSE_ADMIN, BUYER, SELLER]
 * - When querying CHAIRMAN's permissions, include:
 *   1. CHAIRMAN's direct permissions
 *   2. WAREHOUSE_ADMIN's permissions
 *   3. BUYER's permissions
 *   4. SELLER's permissions
 *
 * Implementation Notes:
 * - Use recursive CTE in PostgreSQL for efficient querying
 * - Cache the resolved permissions for performance
 * - Prevent circular inheritance (validation required in service layer)
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
    name = "sys_role_inherit",
    indexes = {
        @Index(name = "idx_role_inherit_child", columnList = "child_role_id"),
        @Index(name = "idx_role_inherit_parent", columnList = "parent_role_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_inherit", columnNames = {"child_role_id", "parent_role_id"})
    }
)
public class SysRoleInherit {

    /**
     * Primary Key (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Child Role ID (the role that inherits)
     *
     * Example: CHAIRMAN role (inherits from others)
     */
    @Column(name = "child_role_id", nullable = false)
    private Long childRoleId;

    /**
     * Parent Role ID (the role being inherited from)
     *
     * Example: WAREHOUSE_ADMIN role (inherited by CHAIRMAN)
     */
    @Column(name = "parent_role_id", nullable = false)
    private Long parentRoleId;

    /**
     * Creation Timestamp
     * When the inheritance relationship was established
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ========== JPA Relationships (Optional, for navigation) ==========

    /**
     * Child Role Entity (Many-to-One)
     * Lazy loading to avoid performance issues
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_role_id", insertable = false, updatable = false)
    private SysRole childRole;

    /**
     * Parent Role Entity (Many-to-One)
     * Lazy loading to avoid performance issues
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_role_id", insertable = false, updatable = false)
    private SysRole parentRole;
}
