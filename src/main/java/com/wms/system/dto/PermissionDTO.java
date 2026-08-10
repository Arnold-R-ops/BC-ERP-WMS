package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Permission DTO
 *
 * Data transfer object for system permissions.
 * Used for dynamic authorization and frontend rendering.
 *
 * Use Cases:
 * - Dynamic authorization: Match request path against resource_path
 * - Frontend menu rendering: Build permission tree
 * - Button visibility control: Check button permissions
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {

    /**
     * Permission ID
     */
    private Long id;

    /**
     * Permission Code (unique identifier)
     * Examples: inventory:view, menu:inventory
     */
    private String permissionCode;

    /**
     * Permission Name (display name)
     * Examples: 查看库存, 库存管理
     */
    private String permissionName;

    /**
     * Permission Type
     * - MENU: Menu permission
     * - BUTTON: Button permission
     * - API: API permission
     */
    private String permissionType;

    /**
     * Parent Permission ID (for tree structure)
     */
    private Long parentId;

    // ========== API Permission Fields ==========

    /**
     * Resource Path (for API permissions)
     * Examples: /api/inventory/**, /api/purchase/*
     */
    private String resourcePath;

    /**
     * HTTP Method (for API permissions)
     * Values: GET, POST, PUT, DELETE, *
     */
    private String httpMethod;

    // ========== Menu Permission Fields ==========

    /**
     * Menu URL (for MENU permissions)
     * Examples: /inventory, /purchase
     */
    private String menuUrl;

    /**
     * Menu Icon (for MENU permissions)
     * Examples: icon-inventory
     */
    private String menuIcon;

    // ========== Data Permission Fields ==========

    /**
     * Data Scope (for data-level permissions)
     * Values: ALL, DEPT, SELF
     */
    private String dataScope;

    /**
     * Governance risk level (NORMAL, HIGH, CRITICAL).
     */
    private String riskLevel;

    /**
     * Whether this permission may be assigned to a custom role.
     */
    private Boolean customAssignable;

    /**
     * Description
     */
    private String description;

    /**
     * Status (ACTIVE, DISABLED)
     */
    private String status;

    /**
     * Sort Order
     */
    private Integer sortOrder;

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
}
