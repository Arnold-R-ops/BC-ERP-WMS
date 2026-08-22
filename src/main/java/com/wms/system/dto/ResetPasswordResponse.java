package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Admin Password Reset Response (P0.5)
 *
 * Returned by POST /api/users/{id}/reset-password.
 *
 * The temporary password is returned EXACTLY ONCE in this response and is
 * never logged or persisted in plain text. The administrator passes it to
 * the user out-of-band; the account is restricted to the change-password
 * endpoint until the user sets their own password (mustChangePassword).
 *
 * @author WMS Team
 * @since 2026-07-09
 * @version P0.5 (Password Management)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordResponse {

    /**
     * ID of the user whose password was reset.
     */
    private Long userId;

    /**
     * Username of the user whose password was reset.
     */
    private String username;

    /**
     * The generated temporary password (shown once, never logged).
     */
    @ToString.Exclude
    private String temporaryPassword;

    /**
     * Always true after a reset: the user must change the password at first
     * login before any other API becomes available.
     */
    private Boolean mustChangePassword;
}
