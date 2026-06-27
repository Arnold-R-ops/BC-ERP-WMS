package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * System Role Entity
 *
 * Represents roles in the RBAC permission system.
 * Supports role inheritance for complex permission scenarios (e.g., CHAIRMAN inherits multiple roles).
 *
 * Business Rules:
 * - role_code is unique identifier (e.g., SUPER_ADMIN, CHAIRMAN)
 * - role_type: SYSTEM (preset) or CUSTOM (user-defined)
 * - status: ACTIVE (enabled) or DISABLED (disabled)
 * - Supports multi-level role inheritance via SysRoleInherit table
 *
 * Use Cases:
 * - Predefined roles: SUPER_ADMIN, CHAIRMAN, WAREHOUSE_ADMIN, BUYER, SELLER
 * - Custom roles: Can be created by administrators
 * - Role inheritance: CHAIRMAN automatically inherits permissions from WAREHOUSE_ADMIN, BUYER, SELLER
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "sys_role",
    indexes = {
        @Index(name = "idx_role_type_status", columnList = "role_type,status"),
        @Index(name = "idx_role_code", columnList = "role_code")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_role_company_code", columnNames = {"company_id", "role_code"})
    }
)
public class SysRole extends BaseEntity {

    /**
     * Primary Key (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Role Code (unique identifier)
     * Examples: SUPER_ADMIN, CHAIRMAN, WAREHOUSE_ADMIN
     */
    @NotBlank(message = "Role code cannot be blank")
    @Size(max = 50, message = "Role code must not exceed 50 characters")
    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;

    /**
     * Role Name (display name)
     * Examples: 超级管理员, 董事长, 仓库管理员
     */
    @NotBlank(message = "Role name cannot be blank")
    @Size(max = 100, message = "Role name must not exceed 100 characters")
    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    /**
     * Role Description
     */
    @Column(length = 500)
    private String description;

    /**
     * Role Type
     * - SYSTEM: System predefined role (cannot be deleted)
     * - CUSTOM: User-defined role (can be managed)
     */
    @Column(name = "role_type", nullable = false, length = 20)
    @Builder.Default
    private String roleType = "CUSTOM";

    /**
     * Role Status
     * - ACTIVE: Enabled (permissions take effect)
     * - DISABLED: Disabled (permissions do not take effect)
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Sort Order (for display ordering)
     */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * Parent Roles (Role Inheritance)
     *
     * Many-to-Many relationship via sys_role_inherit table.
     * Example: CHAIRMAN role has parent roles [WAREHOUSE_ADMIN, BUYER, SELLER]
     *
     * Lazy loading to avoid performance issues.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "sys_role_inherit",
        joinColumns = @JoinColumn(name = "child_role_id"),
        inverseJoinColumns = @JoinColumn(name = "parent_role_id")
    )
    @Builder.Default
    private Set<SysRole> parentRoles = new HashSet<>();

    /**
     * Child Roles (Inverse of Role Inheritance)
     *
     * Roles that inherit from this role.
     * Example: WAREHOUSE_ADMIN is a parent role for CHAIRMAN
     */
    @ManyToMany(mappedBy = "parentRoles", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<SysRole> childRoles = new HashSet<>();

    // ========== Helper Methods ==========

    /**
     * Add parent role (for role inheritance)
     */
    public void addParentRole(SysRole parentRole) {
        this.parentRoles.add(parentRole);
        parentRole.getChildRoles().add(this);
    }

    /**
     * Remove parent role
     */
    public void removeParentRole(SysRole parentRole) {
        this.parentRoles.remove(parentRole);
        parentRole.getChildRoles().remove(this);
    }

    /**
     * Check if role is system predefined
     */
    public boolean isSystemRole() {
        return "SYSTEM".equals(this.roleType);
    }

    /**
     * Check if role is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }
}
