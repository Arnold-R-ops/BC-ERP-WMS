package com.wms.system.security;

import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Custom UserDetailsService Implementation
 *
 * Core Responsibility:
 * - Load user from database by username for Spring Security authentication
 * - Wrap User entity in SecurityUser for consistent security context
 *
 * Spring Security Integration:
 * - Implements UserDetailsService interface (required by Spring Security)
 * - Called by AuthenticationManager during login process
 * - Returns SecurityUser (wrapper) containing user credentials and authorities
 *
 * Authentication Flow:
 * 1. User submits login request (username + password)
 * 2. AuthenticationManager calls this service to load user by username
 * 3. Spring Security compares provided password with stored BCrypt hash
 * 4. If match, authentication succeeds and JWT token is generated
 * 5. If no match, authentication fails and exception is thrown
 *
 * Dynamic RBAC Integration:
 * - Returns SecurityUser wrapper (not User directly)
 * - DynamicAuthorizationManager extracts user ID from SecurityUser
 * - User permissions loaded dynamically during authorization
 *
 * Exception Handling:
 * - User not found: Throws UsernameNotFoundException (generic message)
 * - Account disabled: SecurityUser.isEnabled() returns false, Spring Security handles automatically
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * ⭐ Load User by Username (Required by Spring Security)
     *
     * This method is called by Spring Security during authentication process.
     *
     * Implementation Steps:
     * 1. Query user from database by username
     * 2. If user not found, throw exception (authentication fails)
     * 3. If user found, wrap in SecurityUser and return
     * 4. Spring Security automatically checks:
     *    - isEnabled()
     *    - isAccountNonLocked()
     *    - isAccountNonExpired()
     *    - isCredentialsNonExpired()
     *
     * Security Notes:
     * - DO NOT reveal whether username or password is wrong (prevents username enumeration)
     * - Use generic error message for both cases
     * - Log detailed error for debugging, but return generic error to client
     *
     * Dynamic RBAC Note:
     * - Returns SecurityUser (wrapper) for consistent security context
     * - DynamicAuthorizationManager extracts user ID from SecurityUser
     * - Permissions loaded dynamically during request authorization
     *
     * @param username Username (unique identifier)
     * @return UserDetails SecurityUser wrapper containing user and authorities
     * @throws UsernameNotFoundException if user not found (caught by Spring Security)
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        // Query user from database
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.warn("Authentication failed: User not found - username={}", username);

                // Throw generic exception to prevent username enumeration attacks
                // DO NOT tell client "user not found" vs "wrong password"
                throw new UsernameNotFoundException("Invalid username or password");
            });

        log.info("User loaded successfully: username={}, enabled={}",
            user.getUsername(), user.isEnabled());

        // Wrap User in SecurityUser for consistent security context
        // DynamicAuthorizationManager expects SecurityUser to extract user ID
        return new SecurityUser(user);
    }

    /**
     * Load User by Username (with Business Exception)
     *
     * Similar to loadUserByUsername(), but throws BusinessException instead of UsernameNotFoundException.
     * Used in business logic (not authentication) when we need consistent error handling.
     *
     * @param username Username
     * @return User entity
     * @throws BusinessException with AUTH_INVALID_CREDENTIALS if user not found
     */
    @Transactional(readOnly = true)
    public User loadUserByUsernameWithException(String username) {
        log.debug("Loading user with exception: username={}", username);

        return userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.warn("User not found: username={}", username);

                throw new BusinessException(
                    ErrorKeys.AUTH_INVALID_CREDENTIALS,
                    Map.of()  // DO NOT include username to prevent enumeration
                );
            });
    }

    /**
     * Check if Username Exists
     *
     * Used for registration to check username uniqueness.
     *
     * @param username Username to check
     * @return true if username exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        boolean exists = userRepository.existsByUsername(username);

        log.debug("Username existence check: username={}, exists={}", username, exists);

        return exists;
    }
}
