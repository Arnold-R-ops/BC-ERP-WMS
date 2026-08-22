package com.wms.system.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update User Request DTO
 *
 * Used by TENANT_ADMIN to update existing user account information.
 *
 * Request Format:
 * <pre>
 * PUT /api/users/{id}
 * Authorization: Bearer {super_admin_token}
 * Content-Type: application/json
 *
 * {
 *   "displayName": "Updated Display Name",
 *   "enabled": false,
 *   "defaultRoleId": 5,
 *   "remark": "Account temporarily disabled"
 * }
 * </pre>
 *
 * Update Scope:
 * - This endpoint updates basic user information only
 * - Username and password are NOT updatable via this endpoint
 * - For role assignments, use POST /api/users/{id}/roles
 * - For role removal, use DELETE /api/users/{id}/roles/{roleId}
 *
 * Validation Rules:
 * - displayName: Max 100 characters
 * - defaultRoleId: Must be one of the user's assigned roles if provided
 * - enabled: Can be true/false to enable/disable account
 * - remark: Max 500 characters
 *
 * Business Logic:
 * 1. Verify user exists
 * 2. Update provided fields (null fields are ignored)
 * 3. If defaultRoleId is updated, verify user has that role
 * 4. Clear permission cache for the user
 *
 * Security:
 * - Only accessible by TENANT_ADMIN role
 * - Cannot update username (identity is immutable)
 * - Cannot update password (requires separate endpoint)
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    /**
     * Display name (for UI presentation)
     *
     * Set to null to keep current value unchanged.
     */
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    private String displayName;

    /**
     * Account enabled status
     *
     * Set to:
     * - true: Enable account (allow login)
     * - false: Disable account (prevent login)
     * - null: Keep current value unchanged
     */
    private Boolean enabled;

    /**
     * Default role ID (user's preferred role for login)
     *
     * Must be one of the user's assigned roles if provided.
     * Set to null to keep current value unchanged.
     *
     * Example: 5 (SALESPERSON role)
     */
    private Long defaultRoleId;

    /**
     * Optional remarks about the user
     *
     * Set to null to keep current value unchanged.
     * Set to empty string to clear existing remark.
     *
     * Example: "Promoted to team lead", "On leave until March"
     */
    @Size(max = 500, message = "Remark cannot exceed 500 characters")
    private String remark;
}
