package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * System Role Entity
 *
 * Represents roles in the RBAC permission system.
 * Supports role inheritance for complex permission scenarios (e.g., CHAIRMAN inherits multiple roles).
 *
 * Business Rules:
 * - role_code is unique identifier (e.g., TENANT_ADMIN, CHAIRMAN)
 * - role_type: SYSTEM (preset) or CUSTOM (user-defined)
 * - status: ACTIVE (enabled) or DISABLED (disabled)
 * - Supports multi-level role inheritance via SysRoleInherit table
 *
 * Use Cases:
 * - Predefined roles: TENANT_ADMIN, CHAIRMAN, WAREHOUSE_ADMIN, BUYER, SELLER
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

    public static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    public static final String ROLE_TYPE_CUSTOM = "CUSTOM";
    public static final String SYSTEM_CATEGORY_BUSINESS_TEMPLATE = "BUSINESS_TEMPLATE";
    public static final String SYSTEM_CATEGORY_PRIVILEGED = "PRIVILEGED";
    public static final String TENANT_ADMIN_ROLE_CODE = "TENANT_ADMIN";
    public static final String SECURITY_ADMIN_ROLE_CODE = "SECURITY_ADMIN";
    public static final String SIMPLE_APPROVAL_TEMPLATE_CODE = "ROLE_PACKAGE_SIMPLE_APPROVAL";
    public static final String REVIEW_STATUS_DRAFT = "DRAFT";
    public static final String REVIEW_STATUS_PENDING = "PENDING_REVIEW";
    public static final String REVIEW_STATUS_APPROVED = "APPROVED";

    /**
     * Primary Key (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Role Code (unique identifier)
     * Examples: TENANT_ADMIN, CHAIRMAN, WAREHOUSE_ADMIN
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
    private String roleType = ROLE_TYPE_CUSTOM;

    /**
     * Governance category for system roles.
     *
     * - BUSINESS_TEMPLATE: reusable business baseline, subject to import review
     * - PRIVILEGED: protected identity role that must never be copied/imported
     * - null: custom role
     */
    @Column(name = "system_category", length = 30)
    private String systemCategory;

    /**
     * Whether this role may be used as a source for a role/permission-package copy.
     * Protected roles always remain non-importable even if persisted data is wrong.
     */
    @Column(name = "import_allowed", nullable = false)
    @Builder.Default
    private Boolean importAllowed = false;

    /** Approval template bound to a custom permission package. */
    @Column(name = "approval_template_code", length = 50)
    private String approvalTemplateCode;

    /** Governance state, independent from the runtime ACTIVE/DISABLED state. */
    @Column(name = "review_status", nullable = false, length = 30)
    @Builder.Default
    private String reviewStatus = REVIEW_STATUS_APPROVED;

    @Column(name = "review_submitted_by")
    private Long reviewSubmittedBy;

    @Column(name = "review_submitted_by_username", length = 100)
    private String reviewSubmittedByUsername;

    @Column(name = "review_submitted_at")
    private LocalDateTime reviewSubmittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_username", length = 100)
    private String reviewedByUsername;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

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
        return ROLE_TYPE_SYSTEM.equals(this.roleType);
    }

    /**
     * Check if this role is a protected privileged identity.
     * TENANT_ADMIN is an immutable safety invariant independent of database metadata.
     */
    public boolean isPrivilegedRole() {
        return TENANT_ADMIN_ROLE_CODE.equals(this.roleCode)
                || SYSTEM_CATEGORY_PRIVILEGED.equals(this.systemCategory);
    }

    /**
     * Check if this role can be used as a copy/import source.
     */
    public boolean canImportPermissions() {
        return Boolean.TRUE.equals(this.importAllowed)
                && !isPrivilegedRole()
                && isActive()
                && (isSystemRole() || isApproved());
    }

    /**
     * Check if role is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }

    public boolean isDraft() {
        return REVIEW_STATUS_DRAFT.equals(this.reviewStatus);
    }

    public boolean isPendingReview() {
        return REVIEW_STATUS_PENDING.equals(this.reviewStatus);
    }

    public boolean isApproved() {
        return REVIEW_STATUS_APPROVED.equals(this.reviewStatus);
    }

    public boolean isAssignableToUsers() {
        return isActive() && (isSystemRole() || isApproved());
    }
}
