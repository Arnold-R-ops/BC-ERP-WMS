package com.wms.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Assign Roles Request DTO
 *
 * Used by TENANT_ADMIN to batch assign roles to a user.
 *
 * Request Format:
 * <pre>
 * POST /api/users/{userId}/roles
 * Authorization: Bearer {super_admin_token}
 * Content-Type: application/json
 *
 * {
 *   "roleIds": [3, 5, 7]
 * }
 * </pre>
 *
 * Behavior:
 * - REPLACES all existing role assignments for the user
 * - NOT additive (does not append to existing roles)
 * - To add a single role, include all existing roles + new role
 *
 * Validation Rules:
 * - roleIds: Must contain at least one valid role ID
 * - All role IDs must exist in sys_role table
 * - All roles must be active (is_active = true)
 *
 * Business Logic:
 * 1. Verify user exists
 * 2. Verify all role IDs are valid and active
 * 3. Remove all existing role assignments for the user
 * 4. Assign new roles to the user
 * 5. If user's default_role_id is not in new roles, update to first role
 * 6. Clear permission cache for the user
 *
 * Example Scenarios:
 * - Current roles: [WAREHOUSE_ADMIN, SALESPERSON]
 * - Request: {"roleIds": [3, 5, 7]}
 * - Result: User now has [WAREHOUSE_ADMIN, SALESPERSON, PURCHASER]
 *
 * - Current roles: [WAREHOUSE_ADMIN, SALESPERSON]
 * - Request: {"roleIds": [4]}
 * - Result: User now has [PURCHASER] only
 *
 * Security:
 * - Only accessible by TENANT_ADMIN role
 * - Cannot leave user with zero roles (validation enforced)
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRolesRequest {

    /**
     * List of role IDs to assign to the user
     *
     * This list REPLACES all existing role assignments.
     * Must contain at least one valid role ID.
     *
     * Example: [3, 5, 7] for WAREHOUSE_ADMIN, SALESPERSON, PURCHASER
     */
    @NotEmpty(message = "User must be assigned at least one role")
    private List<Long> roleIds;

    /**
     * Optional default role for the resulting assignment set. When omitted,
     * the current default is retained if possible, otherwise the first role
     * in roleIds becomes the default.
     */
    private Long defaultRoleId;

    /** Required and non-empty when roleIds contains WAREHOUSE_STAFF. */
    private List<Long> warehouseIds;
}
