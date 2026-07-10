package com.wms.system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

/**
 * User Entity
 *
 * Records system user information for authentication and authorization.
 *
 * Business Rules:
 * - Username is unique and used for login
 * - Password is stored encrypted (BCrypt algorithm recommended)
 * - Users can have multiple roles assigned via sys_user_role table
 * - Default role determines initial active role upon login
 *
 * Security Integration:
 * - Implements UserDetails interface for Spring Security integration
 * - Provides authentication information (username, password, enabled status)
 * - Authorization (roles) loaded dynamically from JWT token
 * - BCrypt password encoding ensures password security
 *
 * Multi-Role System (v3.3+):
 * - Role assignments managed via sys_user_role junction table
 * - Users can switch roles without re-authentication
 * - JWT token contains current_role claim for active role
 * - Supports identity switching for users with multiple roles
 *
 * @author WMS Team
 * @since 2025-01-09
 * @version 3.3 (Multi-Role RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@SQLDelete(sql = "UPDATE users SET is_deleted = true, enabled = false, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_username", columnList = "username")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_company_username", columnNames = {"company_id", "username"})
    }
)
public class User extends BaseEntity implements UserDetails {

    /**
     * Primary Key (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Username (unique, used for login)
     * Length: 3-50 characters
     */
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(nullable = false, length = 50)
    private String username;

    /**
     * Encrypted Password (store encrypted text, NEVER store plain text!)
     * Recommended: BCryptPasswordEncoder
     *
     * Example:
     * BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
     * String encodedPassword = encoder.encode("original_password");
     */
    @NotBlank(message = "Password cannot be blank")
    @Column(nullable = false, length = 255)
    private String password;

    /**
     * Default Role ID (User's preferred role for login)
     *
     * This field stores the user's last used or preferred role.
     * When user logs in, system will use this role as the initial active role.
     * If NULL, system selects the role with minimum sort_order.
     *
     * @since v3.3 (Multi-Role System)
     */
    @Column(name = "default_role_id")
    private Long defaultRoleId;

    /**
     * Legacy single-role field retained only for compatibility with historical data.
     * Authorization must use sys_user_role as the single source of truth.
     */
    @Deprecated
    @Column(name = "role", insertable = false, updatable = false)
    private String legacyRole;

    /**
     * Default Role Relationship (Lazy-loaded)
     *
     * Provides access to the full SysRole entity for the default role.
     * Use LAZY fetch to avoid unnecessary database queries.
     *
     * @since v3.3 (Multi-Role System)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_role_id", insertable = false, updatable = false)
    private SysRole defaultRole;

    /**
     * Display Name (optional, used for UI display)
     */
    @Column(length = 100)
    private String displayName;

    /**
     * Account Status (enabled or disabled)
     * - true: Active (can login)
     * - false: Disabled (login prohibited)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Remarks (optional)
     */
    @Column(length = 500)
    private String remark;

    /**
     * Forces a password change before the account can use other APIs (P0.5).
     *
     * Set to TRUE when an administrator resets this account's password to a
     * temporary value; cleared when the user changes the password themselves.
     * While TRUE, DynamicAuthorizationManager only allows the change-password
     * endpoint (and /api/auth/**) for this account.
     */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private Boolean mustChangePassword = false;

    /**
     * Soft-delete marker. Deleted users are automatically excluded from ORM queries.
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * Audit timestamp for logical deletion.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * User ID of the administrator who performed the logical deletion.
     */
    @Column(name = "deleted_by")
    private Long deletedBy;

    // ========== UserDetails Interface Implementation (Spring Security) ==========

    /**
     * Get User Authorities (Permissions)
     *
     * Multi-Role System (v3.3+):
     * Returns empty list because authorities are loaded dynamically from JWT token.
     * The JWT contains 'current_role' claim which determines active permissions.
     * This approach supports identity switching without reloading user entity.
     *
     * Spring Security Integration:
     * JwtAuthenticationFilter extracts current_role from JWT and sets it in SecurityContext.
     * @PreAuthorize annotations check against the role in JWT, not this method.
     *
     * @return Empty collection (authorities loaded from JWT)
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Return empty list - authorities are dynamically loaded from JWT token
        return List.of();
    }

    /**
     * Get Password (for authentication)
     *
     * Returns BCrypt encrypted password stored in database.
     * Spring Security will compare this with user-provided password during login.
     *
     * @return Encrypted password
     */
    @Override
    public String getPassword() {
        return this.password;
    }

    /**
     * Get Username (for authentication)
     *
     * Returns unique username for login.
     *
     * @return Username
     */
    @Override
    public String getUsername() {
        return this.username;
    }

    /**
     * Check if Account is Not Expired
     *
     * Current Implementation: Always returns true (no expiration logic)
     * Future Enhancement: Can implement expiration date field and check logic
     *
     * @return true if account is not expired
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Check if Account is Not Locked
     *
     * Current Implementation: Always returns true (no lock mechanism)
     * Future Enhancement: Can implement lock mechanism after failed login attempts
     *
     * @return true if account is not locked
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Check if Credentials (Password) are Not Expired
     *
     * Current Implementation: Always returns true (password never expires)
     * Future Enhancement: Can implement password expiration policy (e.g., change password every 90 days)
     *
     * @return true if credentials are not expired
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Check if Account is Enabled
     *
     * Uses database 'enabled' field to control account status.
     * Admin can disable user accounts to prevent login.
     *
     * @return true if account is enabled
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}
