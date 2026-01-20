package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User Permission DTO
 *
 * Aggregated permission information for a user.
 * Includes all permissions from user's roles (including inherited roles).
 *
 * Use Cases:
 * - Load into SecurityContext for authorization
 * - Return to frontend for menu/button rendering
 * - Cache for performance optimization
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionDTO {

    /**
     * User ID
     */
    private Long userId;

    /**
     * Username
     */
    private String username;

    /**
     * User's direct role IDs
     */
    private Set<Long> roleIds;

    /**
     * User's direct role codes
     * Examples: [CHAIRMAN, WAREHOUSE_ADMIN]
     */
    private Set<String> roleCodes;

    /**
     * All effective role IDs (including inherited roles)
     * Example: If user has CHAIRMAN role, this includes [CHAIRMAN_ID, WAREHOUSE_ADMIN_ID, BUYER_ID, SELLER_ID]
     */
    private Set<Long> effectiveRoleIds;

    /**
     * All permissions (deduplicated)
     */
    @Builder.Default
    private List<PermissionDTO> permissions = new ArrayList<>();

    /**
     * Menu permissions only (for frontend rendering)
     */
    @Builder.Default
    private List<PermissionDTO> menuPermissions = new ArrayList<>();

    /**
     * API permissions only (for backend authorization)
     */
    @Builder.Default
    private List<PermissionDTO> apiPermissions = new ArrayList<>();

    /**
     * Button permissions only (for frontend button visibility)
     */
    @Builder.Default
    private List<PermissionDTO> buttonPermissions = new ArrayList<>();

    /**
     * Permission codes (for quick lookup)
     * Examples: [inventory:view, purchase:create, global:view]
     */
    private Set<String> permissionCodes;

    // ========== Helper Methods ==========

    /**
     * Check if user has specific permission code
     *
     * @param permissionCode Permission code to check
     * @return true if user has the permission
     */
    public boolean hasPermission(String permissionCode) {
        return permissionCodes != null && permissionCodes.contains(permissionCode);
    }

    /**
     * Check if user has any of the specified permission codes
     *
     * @param permissionCodes Permission codes to check
     * @return true if user has any of the permissions
     */
    public boolean hasAnyPermission(String... permissionCodes) {
        if (this.permissionCodes == null || permissionCodes == null) {
            return false;
        }
        for (String code : permissionCodes) {
            if (this.permissionCodes.contains(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user has all of the specified permission codes
     *
     * @param permissionCodes Permission codes to check
     * @return true if user has all permissions
     */
    public boolean hasAllPermissions(String... permissionCodes) {
        if (this.permissionCodes == null || permissionCodes == null) {
            return false;
        }
        for (String code : permissionCodes) {
            if (!this.permissionCodes.contains(code)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if user has specific role code
     *
     * @param roleCode Role code to check
     * @return true if user has the role
     */
    public boolean hasRole(String roleCode) {
        return roleCodes != null && roleCodes.contains(roleCode);
    }
}
