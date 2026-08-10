package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Create User Request DTO
 *
 * Used by SUPER_ADMIN to create new user accounts with role assignments.
 *
 * Request Format:
 * <pre>
 * POST /api/users
 * Authorization: Bearer {super_admin_token}
 * Content-Type: application/json
 *
 * {
 *   "username": "new_employee",
 *   "password": "Welcome2026!",
 *   "displayName": "New Employee",
 *   "roleIds": [3, 5],
 *   "enabled": true,
 *   "remark": "Hired in January 2026"
 * }
 * </pre>
 *
 * Validation Rules:
 * - username: 3-50 characters, must be unique
 * - password: At least 6 characters (BCrypt encoded before storage)
 * - roleIds: Must contain at least one valid role ID
 * - enabled: Defaults to true if not provided
 *
 * Business Logic:
 * 1. Verify username uniqueness
 * 2. BCrypt encode the password
 * 3. Create user entity
 * 4. Assign specified roles via sys_user_role table
 * 5. Set first role as default_role_id
 *
 * Security:
 * - Only accessible by SUPER_ADMIN role
 * - Password is immediately encrypted (never stored in plain text)
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    /**
     * Username (unique, used for login)
     */
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * Plain text password (will be BCrypt encoded)
     *
     * Minimum length: 6 characters
     * Recommended: Include uppercase, lowercase, numbers, and special characters
     */
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /**
     * Display name (for UI presentation)
     *
     * Optional field, can be null or empty
     */
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    private String displayName;

    /**
     * List of role IDs to assign to this user
     *
     * Must contain at least one valid role ID from sys_role table.
     * First role in the list becomes the default_role_id.
     *
     * Example: [3, 5, 7] for WAREHOUSE_ADMIN, SALESPERSON, PURCHASER
     */
    @NotEmpty(message = "User must be assigned at least one role")
    private List<Long> roleIds;

    /** Required and non-empty when roleIds contains WAREHOUSE_STAFF. */
    private List<Long> warehouseIds;

    /**
     * Account enabled status
     *
     * Defaults to true if not provided.
     * Set to false to create a disabled account.
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Optional remarks about the user
     *
     * Example: "Hired in January 2026", "Temporary contractor"
     */
    @Size(max = 500, message = "Remark cannot exceed 500 characters")
    private String remark;
}
