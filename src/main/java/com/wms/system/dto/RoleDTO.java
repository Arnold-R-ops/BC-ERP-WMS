package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Role DTO
 *
 * Data transfer object for system roles.
 * Used for role management and permission assignment.
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDTO {

    /**
     * Role ID
     */
    private Long id;

    /**
     * Role Code (unique identifier)
     * Examples: SUPER_ADMIN, CHAIRMAN, WAREHOUSE_ADMIN
     */
    private String roleCode;

    /**
     * Role Name (display name)
     * Examples: 超级管理员, 董事长
     */
    private String roleName;

    /**
     * Description
     */
    private String description;

    /**
     * Role Type
     * - SYSTEM: System predefined role
     * - CUSTOM: User-defined role
     */
    private String roleType;

    /**
     * Status (ACTIVE, DISABLED)
     */
    private String status;

    /**
     * Sort Order
     */
    private Integer sortOrder;

    /**
     * Parent Role IDs (for role inheritance)
     * Example: CHAIRMAN inherits from [WAREHOUSE_ADMIN_ID, BUYER_ID, SELLER_ID]
     */
    private Set<Long> parentRoleIds;

    /**
     * Permission IDs assigned to this role
     */
    private Set<Long> permissionIds;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;

    // ========== Helper Methods ==========

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
