package com.wms.system.security;

import com.wms.system.dto.PermissionDTO;
import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.service.DynamicPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.function.Supplier;

/**
 * Dynamic Authorization Manager
 *
 * Intercepts HTTP requests and performs dynamic permission checking.
 * Integrates with DynamicPermissionService for role-based authorization.
 *
 * Authorization Flow:
 * 1. Extract request URI and HTTP method
 * 2. Get authenticated user from SecurityContext
 * 3. Load user's permissions (cached)
 * 4. Match request against API permissions (Ant path matching)
 * 5. Grant or deny access
 *
 * Permission Matching Logic:
 * - Uses Ant path matcher for flexible pattern matching
 * - Supports wildcards: /api/inventory/** matches all inventory endpoints
 * - Supports HTTP method matching: GET, POST, PUT, DELETE, * (all methods)
 *
 * Public Endpoints (No Authentication Required):
 * - /api/auth/** (login, register)
 * - /health/** (health check)
 * - /actuator/** (monitoring)
 *
 * Performance Optimization:
 * - User permissions are cached (30 minutes)
 * - Ant matcher is lightweight (< 1ms per match)
 * - Early return for public endpoints
 *
 * Security Features:
 * - Default deny (if no permission matches, access denied)
 * - Unauthenticated requests are denied
 * - SUPER_ADMIN bypass (optional, see code)
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final DynamicPermissionService permissionService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Public endpoints (no authentication required)
     *
     * These patterns are checked before permission matching.
     * Requests to these endpoints are always granted.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/auth/login",      // Login endpoint
        "/api/auth/health",     // Authentication service health check
        "/api/webhooks/**",     // Channel webhooks (P1-B3): no JWT, secured by HMAC signature inside
        "/health/**",           // Health check
        "/actuator/**",         // Spring Boot Actuator
        "/error",               // Error page
        "/favicon.ico",         // Favicon
        "/v3/api-docs/**",      // OpenAPI contract (P2-FE): disabled entirely in prod profile
        "/swagger-ui/**",       // Swagger UI (P2-FE): disabled entirely in prod profile
        "/swagger-ui.html"      // Swagger UI entry
    };

    /**
     * Authenticated endpoints (require authentication but no specific permission)
     *
     * These endpoints are accessible to any authenticated user.
     * Useful for user-specific operations like switching roles, updating profile, etc.
     */
    private static final String[] AUTHENTICATED_ENDPOINTS = {
        "/api/auth/switch-role",  // Any authenticated user can switch their own role
        "/api/auth/logout",       // Any authenticated user can logout
        "/api/auth/refresh-token", // Any authenticated user can refresh token
        "/api/users/me/password"  // Any authenticated user can change their own password (P0.5)
    };

    /**
     * Endpoints reachable while a password change is pending (P0.5).
     *
     * When users.must_change_password is set (after an admin reset to a
     * temporary password), the account is restricted to these patterns so the
     * temporary password cannot be used to operate the system.
     */
    private static final String[] PASSWORD_CHANGE_WHITELIST = {
        "/api/users/me/password",
        "/api/auth/**"
    };

    /**
     * Main authorization decision method
     *
     * Called by Spring Security for every HTTP request.
     * Returns AuthorizationDecision(true) to grant access.
     * Returns AuthorizationDecision(false) to deny access.
     *
     * @param authentication Supplier of current authentication
     * @param context Request authorization context
     * @return Authorization decision
     */
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String requestUri = request.getRequestURI();
        String httpMethod = request.getMethod();

        log.debug("Authorizing request: {} {}", httpMethod, requestUri);

        // 1. Check if endpoint is public (no authentication required)
        if (isPublicEndpoint(requestUri)) {
            log.debug("Public endpoint accessed: {}", requestUri);
            return new AuthorizationDecision(true);
        }

        // 2. Get authenticated user
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            log.debug("Unauthenticated request to protected endpoint: {}", requestUri);
            return new AuthorizationDecision(false);
        }

        // 3. Extract user ID from authentication
        Long userId = extractUserId(auth);
        if (userId == null) {
            log.warn("Failed to extract user ID from authentication: {}", auth.getName());
            return new AuthorizationDecision(false);
        }

        String currentRole = extractCurrentRole(auth);
        Long securityVersion = extractSecurityVersion(auth);
        if (currentRole == null || securityVersion == null || !isEnabled(auth)) {
            log.warn("Access denied because active security context is invalid: userId={}, role={}",
                    userId, currentRole);
            return new AuthorizationDecision(false);
        }

        // 3.5 Enforce pending password change (P0.5).
        // JwtAuthenticationFilter loads the User entity fresh on every request,
        // so this flag reflects the database state, not a stale JWT claim.
        // Applies to ALL roles including SUPER_ADMIN - a temporary password
        // must be changed before the account can do anything else.
        if (mustChangePassword(auth) && !isPasswordChangeWhitelisted(requestUri)) {
            log.warn("Access denied for user {} to {} {} (password change required)",
                    userId, httpMethod, requestUri);
            return new AuthorizationDecision(false);
        }

        // 4. Load user permissions (from cache or database)
        UserPermissionDTO userPermissions;
        try {
            userPermissions = permissionService.getUserPermissionsForRole(
                    userId,
                    currentRole,
                    securityVersion
            );
        } catch (Exception e) {
            log.warn("Failed to load active-role permissions for user {} and role {}: {}",
                    userId, currentRole, e.getMessage());
            return new AuthorizationDecision(false);
        }

        // 5. SUPER_ADMIN bypass applies only when it is the currently active role.
        if ("SUPER_ADMIN".equals(currentRole)) {
            log.debug("SUPER_ADMIN bypass for user {}: {} {}", userId, httpMethod, requestUri);
            return new AuthorizationDecision(true);
        }

        // 6. Check if endpoint requires only authentication (no specific permission)
        if (isAuthenticatedEndpoint(requestUri)) {
            log.debug("Authenticated endpoint accessed by user {}: {} {}", userId, httpMethod, requestUri);
            return new AuthorizationDecision(true);
        }

        // 7. Match request against user's API permissions
        boolean hasPermission = matchPermission(userPermissions, requestUri, httpMethod);

        if (hasPermission) {
            log.debug("Access granted for user {} to {} {}", userId, httpMethod, requestUri);
        } else {
            log.warn("Access denied for user {} to {} {} (no matching permission)",
                    userId, httpMethod, requestUri);
        }

        return new AuthorizationDecision(hasPermission);
    }

    /**
     * Check if request URI matches any public endpoint pattern
     *
     * @param requestUri Request URI
     * @return true if public endpoint
     */
    private boolean isPublicEndpoint(String requestUri) {
        for (String pattern : PUBLIC_ENDPOINTS) {
            if (pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if request URI matches any authenticated endpoint pattern
     *
     * Authenticated endpoints require authentication but no specific permission.
     * Any authenticated user can access these endpoints.
     *
     * @param requestUri Request URI
     * @return true if authenticated endpoint
     */
    private boolean isAuthenticatedEndpoint(String requestUri) {
        for (String pattern : AUTHENTICATED_ENDPOINTS) {
            if (pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the authenticated account has a pending forced password change (P0.5)
     *
     * Reads the flag from the live User entity wrapped in SecurityUser
     * (loaded from the database by JwtAuthenticationFilter on this request).
     *
     * @param authentication Spring Security Authentication
     * @return true if the account must change its password before proceeding
     */
    private boolean mustChangePassword(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof SecurityUser) {
            SecurityUser securityUser = (SecurityUser) principal;
            return Boolean.TRUE.equals(securityUser.getUser().getMustChangePassword());
        }

        return false;
    }

    /**
     * Check if request URI is reachable while a password change is pending (P0.5)
     *
     * @param requestUri Request URI
     * @return true if whitelisted during forced password change
     */
    private boolean isPasswordChangeWhitelisted(String requestUri) {
        for (String pattern : PASSWORD_CHANGE_WHITELIST) {
            if (pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract user ID from Authentication object
     *
     * Expects principal to be SecurityUser (set by CustomUserDetailsService).
     * If principal is not SecurityUser, returns null.
     *
     * @param authentication Spring Security Authentication
     * @return User ID or null
     */
    private Long extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getId();
        }

        log.warn("Unexpected principal type: {}",
                principal == null ? "null" : principal.getClass().getName());
        return null;
    }

    private String extractCurrentRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }

    private Long extractSecurityVersion(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUser().getSecurityVersion();
        }
        return null;
    }

    private boolean isEnabled(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        return principal instanceof SecurityUser securityUser && securityUser.isEnabled();
    }

    /**
     * Match request against user's API permissions
     *
     * Iterates through user's API permissions and checks if any match:
     * 1. Permission must be API type
     * 2. HTTP method must match (or permission has wildcard *)
     * 3. Resource path must match (Ant pattern matching)
     *
     * @param userPermissions User's permission DTO
     * @param currentRole currently activated role code
     * @param securityVersion live user authorization version
     * @param requestUri Request URI
     * @param httpMethod HTTP method
     * @return true if user has permission
     */
    private boolean matchPermission(UserPermissionDTO userPermissions,
                                   String requestUri,
                                   String httpMethod) {
        // Get API permissions only (filter out MENU and BUTTON types)
        for (PermissionDTO permission : userPermissions.getApiPermissions()) {
            if (matchesPermission(permission, requestUri, httpMethod)) {
                log.debug("Matched permission: {} ({})", permission.getPermissionCode(),
                        permission.getResourcePath());
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a single permission matches the request
     *
     * Matching criteria:
     * 1. Permission type must be API
     * 2. Permission must be ACTIVE
     * 3. HTTP method must match (or * for all methods)
     * 4. Resource path must match using Ant pattern matching
     *
     * Examples:
     * - Permission: GET /api/inventory/** → Matches: GET /api/inventory/list
     * - Permission: POST /api/inventory → Matches: POST /api/inventory
     * - Permission: * /api/inventory/** → Matches: any method to /api/inventory/**
     *
     * @param permission Permission DTO
     * @param requestUri Request URI
     * @param httpMethod HTTP method
     * @return true if permission matches
     */
    private boolean matchesPermission(PermissionDTO permission,
                                     String requestUri,
                                     String httpMethod) {
        // 1. Must be API permission
        if (!"API".equals(permission.getPermissionType())) {
            return false;
        }

        // 2. Must be active
        if (!"ACTIVE".equals(permission.getStatus())) {
            return false;
        }

        // 3. Check HTTP method match
        String permMethod = permission.getHttpMethod();
        if (permMethod != null && !"*".equals(permMethod)) {
            if (!httpMethod.equalsIgnoreCase(permMethod)) {
                return false;
            }
        }

        // 4. Check resource path match (Ant pattern)
        String resourcePath = permission.getResourcePath();
        if (resourcePath == null || resourcePath.isEmpty()) {
            return false;
        }

        return pathMatcher.match(resourcePath, requestUri);
    }

    /**
     * Verify method (optional, for testing)
     *
     * This method can be called to manually verify if a user has access.
     * Not used by Spring Security framework.
     *
     * @param userId User ID
     * @param requestUri Request URI
     * @param httpMethod HTTP method
     * @return true if user has permission
     */
    public boolean verify(
            Long userId,
            String currentRole,
            Long securityVersion,
            String requestUri,
            String httpMethod
    ) {
        if (isPublicEndpoint(requestUri)) {
            return true;
        }

        UserPermissionDTO userPermissions = permissionService.getUserPermissionsForRole(
                userId,
                currentRole,
                securityVersion
        );
        return matchPermission(userPermissions, requestUri, httpMethod);
    }
}
