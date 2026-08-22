package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Login Request DTO
 *
 * Used for user authentication at login endpoint.
 *
 * Validation Rules:
 * - username: Cannot be blank, 3-50 characters
 * - password: Cannot be blank, minimum 3 characters
 *
 * Security Notes:
 * - Password is transmitted in plain text (must use HTTPS in production)
 * - Backend validates against BCrypt hashed password in database
 * - Failed login attempts should be rate-limited to prevent brute force attacks
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 1.0 (JWT Authentication)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Username (must match database record)
     */
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * Password (plain text, will be validated against BCrypt hash)
     */
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 3, message = "Password must be at least 3 characters")
    @ToString.Exclude
    private String password;
}
