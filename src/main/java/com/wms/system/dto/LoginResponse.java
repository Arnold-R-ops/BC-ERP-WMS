package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login Response DTO
 *
 * Returned to client after successful authentication.
 *
 * Response Format:
 * <pre>
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "tokenType": "Bearer",
 *   "username": "john_doe",
 *   "role": "ADMIN",
 *   "expiresIn": 86400000
 * }
 * </pre>
 *
 * Client Usage:
 * 1. Store token in localStorage or sessionStorage
 * 2. Include token in all subsequent requests:
 *    Authorization: Bearer {token}
 * 3. Refresh token before expiration
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 1.0 (JWT Authentication)
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
     * User role (ADMIN, STAFF, etc.)
     */
    private String role;

    /**
     * Token expiration time in milliseconds
     * Example: 86400000 ms = 24 hours
     */
    private Long expiresIn;
}
