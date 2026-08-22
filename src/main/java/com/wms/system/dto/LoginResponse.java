package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Login Response DTO
 *
 * Returned to client after successful authentication.
 *
 * Response Format (Multi-Role System v3.3+):
 * <pre>
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "tokenType": "Bearer",
 *   "username": "john_doe",
 *   "currentRole": "WAREHOUSE_ADMIN",
 *   "availableRoles": ["WAREHOUSE_ADMIN", "SALESPERSON"],
 *   "expiresIn": 86400000
 * }
 * </pre>
 *
 * Multi-Role System Features:
 * - currentRole: The active role in the JWT token (used for authorization)
 * - availableRoles: All roles assigned to the user (for identity switching)
 * - Users can switch roles without re-authentication via /api/auth/switch-role
 *
 * Client Usage:
 * 1. Store token in localStorage or sessionStorage
 * 2. Include token in all subsequent requests:
 *    Authorization: Bearer {token}
 * 3. Display available roles for user to switch identities
 * 4. Refresh token before expiration
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 3.3 (Multi-Role RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT Token string
     */
    private String token;

    /**
     * Token type (always "Bearer" for JWT)
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Authenticated username
     */
    private String username;

    /**
     * Current active role code (used for authorization)
     *
     * This is the role embedded in the JWT token's 'current_role' claim.
     * All authorization checks (@PreAuthorize) are based on this role.
     *
     * Example: "TENANT_ADMIN", "WAREHOUSE_ADMIN", "SALESPERSON"
     *
     * @since v3.3 (Multi-Role System)
     */
    private String currentRole;

    /**
     * List of all available role codes for this user
     *
     * Contains all active roles assigned to the user via sys_user_role table.
     * Client can use this list to display role switching options.
     *
     * Example: ["WAREHOUSE_ADMIN", "SALESPERSON", "PURCHASER"]
     *
     * @since v3.3 (Multi-Role System)
     */
    private List<String> availableRoles;

    /**
     * Effective permission codes for the current active role only.
     *
     * The frontend uses this snapshot for menu and action visibility. The
     * backend remains authoritative and independently checks every request.
     */
    private List<String> permissionCodes;

    /**
     * Token expiration time in milliseconds
     * Example: 86400000 ms = 24 hours
     */
    private Long expiresIn;

    /** Absolute tenant ERP sign-in deadline in Unix epoch milliseconds. */
    private Long sessionEndsAt;

    /**
     * Forced password change indicator (P0.5)
     *
     * True when an administrator reset this account's password to a temporary
     * one. The client should immediately prompt for a password change: until
     * PUT /api/users/me/password succeeds, all APIs other than /api/auth/**
     * and the change-password endpoint are blocked for this account.
     *
     * @since P0.5 (Password Management)
     */
    private Boolean mustChangePassword;
}
