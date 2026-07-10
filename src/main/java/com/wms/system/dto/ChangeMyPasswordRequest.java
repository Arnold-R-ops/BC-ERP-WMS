package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Change Own Password Request (P0.5)
 *
 * Request body for PUT /api/users/me/password.
 *
 * Validation:
 * - Both fields are required.
 * - Old password must match the caller's current password (verified in service).
 * - New password strength policy (8-64 chars, at least one letter and one digit)
 *   is enforced in UserManagementService so violations surface as structured
 *   BusinessException error keys (PASSWORD_TOO_WEAK), not bare 400s.
 *
 * @author WMS Team
 * @since 2026-07-09
 * @version P0.5 (Password Management)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeMyPasswordRequest {

    /**
     * Current password, verified before any change is applied.
     */
    @NotBlank(message = "Old password cannot be blank")
    private String oldPassword;

    /**
     * New password. Policy: 8-64 characters, at least one letter and one digit.
     */
    @NotBlank(message = "New password cannot be blank")
    private String newPassword;
}
