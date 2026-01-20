package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Switch Role Request DTO
 *
 * Used when a user wants to switch their active identity without re-authentication.
 *
 * Request Format:
 * <pre>
 * POST /api/auth/switch-role
 * Authorization: Bearer {current_token}
 * Content-Type: application/json
 *
 * {
 *   "targetRoleCode": "SALESPERSON"
 * }
 * </pre>
 *
 * Validation Rules:
 * - targetRoleCode: Must be a valid role code assigned to the user
 * - User must have the target role in their sys_user_role assignments
 * - Target role must be active (is_active = true)
 *
 * Business Logic:
 * 1. Verify target role exists in sys_role table
 * 2. Verify user has this role assigned in sys_user_role table
 * 3. Verify target role is active
 * 4. Generate new JWT token with updated 'current_role' claim
 * 5. Update user's default_role_id for future logins
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchRoleRequest {

    /**
     * Target role code to switch to
     *
     * Must be one of the user's assigned roles from availableRoles list.
     *
     * Examples: "SUPER_ADMIN", "WAREHOUSE_ADMIN", "SALESPERSON", "PURCHASER"
     */
    @NotBlank(message = "Target role code cannot be blank")
    private String targetRoleCode;
}
