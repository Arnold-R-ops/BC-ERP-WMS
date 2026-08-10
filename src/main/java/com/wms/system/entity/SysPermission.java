package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * System Permission/Resource Entity
 *
 * Unified management of menu, button, and API permissions.
 * Supports tree structure for hierarchical permission organization.
 *
 * Business Rules:
 * - permission_code is unique identifier (e.g., inventory:view, menu:inventory)
 * - permission_type: MENU (menu), BUTTON (button), API (interface)
 * - Supports parent-child relationships for permission tree structure
 * - API permissions use Ant path matching (e.g., /api/inventory/**)
 *
 * Permission Types:
 * 1. MENU: Frontend menu permissions (menu_url, menu_icon)
 * 2. BUTTON: Frontend button permissions (parent_id points to MENU)
 * 3. API: Backend API permissions (resource_path, http_method)
 *
 * Data Scope (Reserved for Future):
 * - ALL: All data
 * - DEPT: Department data only
 * - SELF: Personal data only
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
    name = "sys_permission",
    indexes = {
        @Index(name = "idx_permission_type_status", columnList = "permission_type,status"),
        @Index(name = "idx_permission_resource", columnList = "resource_path,http_method"),
        @Index(name = "idx_permission_parent", columnList = "parent_id"),
        @Index(name = "idx_permission_code", columnList = "permission_code")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_permission_company_code", columnNames = {"company_id", "permission_code"})
    }
)
public class SysPermission extends BaseEntity {

    public static final String RISK_LEVEL_NORMAL = "NORMAL";
    public static final String RISK_LEVEL_HIGH = "HIGH";
    public static final String RISK_LEVEL_CRITICAL = "CRITICAL";

    /**
     * Primary Key (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Permission Code (unique identifier)
     * Examples: inventory:view, menu:inventory, sales:create
     */
    @NotBlank(message = "Permission code cannot be blank")
    @Size(max = 100, message = "Permission code must not exceed 100 characters")
    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    /**
     * Permission Name (display name)
     * Examples: 查看库存, 库存管理, 创建销售订单
     */
    @NotBlank(message = "Permission name cannot be blank")
    @Size(max = 100, message = "Permission name must not exceed 100 characters")
    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    /**
     * Permission Type
     * - MENU: Menu permission (controls frontend menu visibility)
     * - BUTTON: Button permission (controls frontend button visibility)
     * - API: API permission (controls backend endpoint access)
     */
    @NotBlank(message = "Permission type cannot be blank")
    @Column(name = "permission_type", nullable = false, length = 20)
    private String permissionType;

    /**
     * Parent Permission ID (for tree structure)
     *
     * Examples:
     * - MENU: null (root level menu)
     * - BUTTON/API: points to parent MENU
     */
    @Column(name = "parent_id")
    private Long parentId;

    // ========== API Permission Fields ==========

    /**
     * Resource Path (for API permissions)
     *
     * Supports Ant path matching:
     * - /api/inventory/** (matches all inventory endpoints)
     * - /api/inventory/* (matches one level)
     * - /api/inventory/transfer (exact match)
     */
    @Column(name = "resource_path", length = 200)
    private String resourcePath;

    /**
     * HTTP Method (for API permissions)
     *
     * Values: GET, POST, PUT, DELETE, PATCH, * (wildcard for all methods)
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    // ========== Menu Permission Fields ==========

    /**
     * Menu URL (for MENU permissions)
     * Examples: /inventory, /purchase, /sales
     */
    @Column(name = "menu_url", length = 200)
    private String menuUrl;

    /**
     * Menu Icon (for MENU permissions)
     * Examples: icon-inventory, icon-purchase
     */
    @Column(name = "menu_icon", length = 50)
    private String menuIcon;

    // ========== Data Permission Fields (Reserved) ==========

    /**
     * Data Scope (reserved for data-level permissions)
     *
     * - ALL: All data (default)
     * - DEPT: Department data only
     * - SELF: Personal data only
     */
    @Column(name = "data_scope", length = 20)
    @Builder.Default
    private String dataScope = "ALL";

    /**
     * Governance risk level used by assignment and future copy previews.
     */
    @Column(name = "risk_level", nullable = false, length = 20)
    @Builder.Default
    private String riskLevel = RISK_LEVEL_NORMAL;

    /**
     * Whether this permission may be assigned to a CUSTOM role.
     * Reserved IAM/system-control permissions are always false.
     */
    @Column(name = "custom_assignable", nullable = false)
    @Builder.Default
    private Boolean customAssignable = true;

    // ========== Common Fields ==========

    /**
     * Permission Description
     */
    @Column(length = 500)
    private String description;

    /**
     * Permission Status
     * - ACTIVE: Enabled
     * - DISABLED: Disabled
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

    // ========== Helper Methods ==========

    /**
     * Check if this is a menu permission
     */
    public boolean isMenuPermission() {
        return "MENU".equals(this.permissionType);
    }

    /**
     * Check if this is an API permission
     */
    public boolean isApiPermission() {
        return "API".equals(this.permissionType);
    }

    /**
     * Check if this is a button permission
     */
    public boolean isButtonPermission() {
        return "BUTTON".equals(this.permissionType);
    }

    /**
     * Check if permission is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }

    /**
     * Check whether a custom role may receive this permission.
     */
    public boolean isCustomAssignable() {
        return Boolean.TRUE.equals(this.customAssignable);
    }

    /**
     * Identify IAM/system-control permission codes that must remain system-only.
     */
    public static boolean isReservedPermissionCode(String permissionCode) {
        if (permissionCode == null) {
            return false;
        }
        return "menu:system".equals(permissionCode)
                || "system:admin".equals(permissionCode)
                || permissionCode.startsWith("system:role:")
                || permissionCode.startsWith("system:permission:")
                || permissionCode.startsWith("system:user:");
    }

    /**
     * Check if this is a root permission (no parent)
     */
    public boolean isRootPermission() {
        return this.parentId == null;
    }
}
