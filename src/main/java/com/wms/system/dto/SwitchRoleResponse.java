package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Switch Role Response DTO
 *
 * Returned to client after successfully switching identity.
 *
 * Response Format:
 * <pre>
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "tokenType": "Bearer",
 *   "currentRole": "SALESPERSON",
 *   "message": "Role switched successfully to SALESPERSON (销售员)",
 *   "expiresIn": 86400000
 * }
 * </pre>
 *
 * Client Responsibilities:
 * 1. Replace old token with new token in storage
 * 2. Update UI to reflect new active role
 * 3. Refresh permissions or reload protected resources if needed
 * 4. Old token remains valid until expiration (no revocation mechanism)
 *
 * Security Considerations:
 * - New token contains updated 'current_role' claim
 * - Role switching advances security_version, so the old token is invalidated
 * - Client must replace the old token with this token
 * - Token expiration remains the same as original login token
 *
 * @author WMS Team
 * @since 2026-01-20
 * @version 3.3 (Multi-Role System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchRoleResponse {

    /**
     * New JWT token with updated current_role claim
     */
    private String token;

    /**
     * Token type (always "Bearer" for JWT)
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * New active role code
     *
     * This is now the current_role in the JWT token.
     *
     * Example: "SALESPERSON", "WAREHOUSE_ADMIN"
     */
    private String currentRole;

    /** Effective permission codes for the newly activated role only. */
    private List<String> permissionCodes;

    /**
     * Success message describing the role switch
     *
     * Example: "Role switched successfully to SALESPERSON (销售员)"
     */
    private String message;

    /**
     * Token expiration time in milliseconds
     *
     * Example: 86400000 ms = 24 hours
     */
    private Long expiresIn;

    /** Absolute tenant ERP sign-in deadline in Unix epoch milliseconds. */
    private Long sessionEndsAt;
}
