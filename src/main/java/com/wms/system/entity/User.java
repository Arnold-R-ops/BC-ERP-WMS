package com.wms.system.entity;

import com.wms.system.entity.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * User Entity
 *
 * Records system user information for authentication and authorization.
 *
 * Business Rules:
 * - Username is unique and used for login
 * - Password is stored encrypted (BCrypt algorithm recommended)
 * - Role determines user permissions (ADMIN: full access, STAFF: basic operations)
 *
 * Security Integration:
 * - Implements UserDetails interface for Spring Security integration
 * - Provides authentication and authorization information
 * - BCrypt password encoding ensures password security
 *
 * ERP Extension:
 * - Can add department field (departmentId) in future
 * - Can add employee number field (employeeNumber)
 * - Can support multi-role via @ManyToMany relationship
 *
 * @author WMS Team
 * @since 2025-01-09
 * @version 2.0 (Spring Security Integration)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_username", columnList = "username", unique = true)
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
    @Column(nullable = false, unique = true, length = 50)
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
     * User Role (enum type)
     * - ADMIN: Administrator (full permissions)
     * - STAFF: Regular employee (basic operation permissions)
     *
     * Storage Method:
     * - @Enumerated(EnumType.STRING): Store as string ("ADMIN", "STAFF"), easier to read and maintain
     * - @Enumerated(EnumType.ORDINAL): Store as integer (0, 1), saves space but not recommended (enum order changes cause data corruption)
     */
    @NotNull(message = "Role cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

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

    // ========== UserDetails Interface Implementation (Spring Security) ==========

    /**
     * Get User Authorities (Permissions)
     *
     * Spring Security uses GrantedAuthority to represent permissions.
     * Naming Convention: ROLE_ prefix (e.g., ROLE_ADMIN, ROLE_STAFF)
     *
     * @return Collection of GrantedAuthority
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Convert role enum to GrantedAuthority
        // "ROLE_" prefix is Spring Security convention for role-based authorization
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
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
