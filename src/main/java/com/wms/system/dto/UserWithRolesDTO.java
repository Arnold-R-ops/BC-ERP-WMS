package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User With Roles DTO
 *
 * Used for displaying user information with their assigned roles.
 * Typically returned by GET /api/users endpoint.
 *
 * Response Format:
 * <pre>
 * {
 *   "id": 1,
 *   "username": "john_doe",
 *   "displayName": "John Doe",
 *   "enabled": true,
 *   "roleCodes": ["WAREHOUSE_ADMIN", "SALESPERSON"],
 *   "roleNames": ["仓库管理员", "销售员"],
 *   "defaultRoleCode": "WAREHOUSE_ADMIN",
 *   "createdAt": "2026-01-10T10:00:00",
 *   "updatedAt": "2026-01-20T15:30:00",
 *   "remark": "Multi-role test user"
 * }
 * </pre>
 *
 * Usage:
 * - User management interfaces (SUPER_ADMIN only)
 * - Display user list with role assignments
 * - User profile viewing
 *
 * Security:
 * - Password is NEVER included in this DTO
 * - Only accessible by SUPER_ADMIN role
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWithRolesDTO {

    /**
     * User ID
     */
    private Long id;

    /**
     * Username (unique identifier for login)
     */
    private String username;

    /**
     * Display name (for UI presentation)
     */
    private String displayName;

    /**
     * Account enabled status
     */
    private Boolean enabled;

    /**
     * List of role IDs assigned to this user.
     */
    private List<Long> roleIds;

    /**
     * List of role codes assigned to this user
     *
     * Example: ["WAREHOUSE_ADMIN", "SALESPERSON", "PURCHASER"]
     */
    private List<String> roleCodes;

    /**
     * List of role names (localized display names)
     *
     * Example: ["仓库管理员", "销售员", "采购员"]
     */
    private List<String> roleNames;

    /**
     * Default role code (user's preferred role)
     *
     * This is the role used automatically upon login.
     * Can be null if user has no default preference.
     *
     * Example: "WAREHOUSE_ADMIN"
     */
    private String defaultRoleCode;

    /**
     * Account creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Optional remarks about the user
     */
    private String remark;
}
