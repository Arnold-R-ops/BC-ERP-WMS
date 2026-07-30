package com.wms.system.security;

import com.wms.system.entity.SysRole;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.service.DynamicPermissionService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JWT Authentication Filter
 *
 * Core Responsibility:
 * - Intercept ALL incoming HTTP requests BEFORE they reach Controllers
 * - Extract JWT token from Authorization header
 * - Validate token signature and expiration
 * - Extract current_role from token and set in SecurityContext
 * - Set Authentication object in SecurityContext if valid
 *
 * Multi-Role System (v3.3+):
 * - Extracts 'current_role' claim from JWT token
 * - Creates GrantedAuthority with ROLE_ prefix for authorization
 * - Supports identity switching via role-specific tokens
 * - Authorization checks use current_role, not all user roles
 *
 * Filter Chain Position:
 * - Executes BEFORE UsernamePasswordAuthenticationFilter
 * - Configured in SecurityConfig via addFilterBefore()
 *
 * Authentication Flow:
 * 1. Client sends request with Authorization header: "Bearer {token}"
 * 2. This filter extracts and validates the token
 * 3. Extracts current_role from token claims
 * 4. If valid, creates Authentication object with role-based authority
 * 5. Sets Authentication in SecurityContext for this request
 * 6. Subsequent filters and Controllers can access via SecurityContextHolder
 * 7. @PreAuthorize annotations check against the current_role in token
 *
 * Token Format:
 * - Header name: Authorization
 * - Header value: Bearer {jwt_token}
 * - Example: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 *
 * Exception Handling:
 * - Token expired: Log warning, continue without authentication
 * - Invalid signature: Log error, continue without authentication
 * - Malformed token: Log error, continue without authentication
 * - DO NOT throw exceptions here (handled by SecurityConfig and GlobalExceptionHandler)
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 3.3 (Multi-Role RBAC System)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final SysRoleRepository roleRepository;
    private final DynamicPermissionService permissionService;

    /**
     * ⭐ Filter Incoming Requests (Called for EVERY HTTP request)
     *
     * Execution Steps:
     * 1. Extract Authorization header from request
     * 2. Check if header starts with "Bearer "
     * 3. Extract JWT token (remove "Bearer " prefix)
     * 4. Extract username from token
     * 5. Load user details from database
     * 6. Validate token (signature + expiration)
     * 7. If valid, set Authentication in SecurityContext
     * 8. Continue filter chain (pass request to next filter/controller)
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain Filter chain (to pass request to next filter)
     * @throws ServletException if servlet error occurs
     * @throws IOException if I/O error occurs
     */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract Authorization header
        String authorizationHeader = request.getHeader("Authorization");

        // 2. Check if Authorization header exists and starts with "Bearer "
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.debug("No JWT token found in request: path={}", request.getRequestURI());

            // No token provided, continue without authentication
            // SecurityConfig will reject if path requires authentication
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 3. Extract JWT token (remove "Bearer " prefix)
            String token = authorizationHeader.substring(7);  // "Bearer ".length() = 7

            log.debug("JWT token found in request: path={}, token={}",
                request.getRequestURI(), token.substring(0, Math.min(20, token.length())) + "...");

            // 4. Extract username from token
            String username = jwtUtil.extractUsername(token);

            // 5. Extract current role from token (Multi-Role System v3.3+)
            String currentRole = jwtUtil.extractCurrentRole(token);

            log.debug("JWT token parsed: username={}, currentRole={}", username, currentRole);

            // 6. Check if user is NOT already authenticated
            // (SecurityContextHolder.getContext().getAuthentication() == null means not authenticated yet)
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 7. Load user details from database
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 8. Validate token (signature + expiration + username match)
                if (jwtUtil.isTokenValid(token, username)) {
                    log.info("JWT token validated successfully: username={}, currentRole={}, path={}",
                        username, currentRole, request.getRequestURI());

                    // 9. Create authorities list with current role from JWT token
                    // Multi-Role System: Authorization is based on current_role in token, not all user roles
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    if (currentRole != null && !currentRole.isEmpty()) {
                        // Add both forms because controllers use both hasRole("...")
                        // and hasAnyAuthority("SUPER_ADMIN", "...") during API tests.
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + currentRole));
                        authorities.add(new SimpleGrantedAuthority(currentRole));

                        // Method-level @PreAuthorize checks use permission codes
                        // (for example "outbound:view"). Resolve them strictly from
                        // the role selected in this JWT, including its inherited roles;
                        // never union permissions from the user's other assigned roles.
                        SysRole selectedRole = roleRepository.findByRoleCode(currentRole)
                            .filter(SysRole::isActive)
                            .orElseThrow(() -> new IllegalStateException(
                                "Current role is missing or disabled: " + currentRole));
                        Set<Long> effectiveRoleIds =
                            permissionService.getInheritedRoleIds(Set.of(selectedRole.getId()));
                        permissionService.getPermissionsByRoleIds(effectiveRoleIds).stream()
                            .map(permission -> permission.getPermissionCode())
                            .filter(code -> code != null && !code.isBlank())
                            .map(SimpleGrantedAuthority::new)
                            .forEach(authorities::add);

                        log.debug("Authorities granted for current role {}: {}",
                            currentRole, authorities);
                    } else {
                        log.warn("No current_role found in JWT token for user: {}", username);
                    }

                    // 10. Create Authentication object
                    // This object contains:
                    // - Principal: UserDetails (user information)
                    // - Credentials: null (no password needed after authentication)
                    // - Authorities: Current role from JWT token (not from User entity)
                    UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,           // Principal (authenticated user)
                            null,                  // Credentials (not needed)
                            authorities            // Authorities (current role from JWT)
                        );

                    // 11. Set authentication details (request info like IP address)
                    authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // 12. Set Authentication in SecurityContext
                    // This makes user "authenticated" for this request
                    // Subsequent filters and controllers can access via SecurityContextHolder
                    // @PreAuthorize checks will validate against the current_role
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                    log.debug("Authentication set in SecurityContext: username={}, authorities={}",
                        username, authorities);
                } else {
                    log.warn("JWT token validation failed: username={}, token might be expired or invalid",
                        username);
                }
            }

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: path={}, message={}",
                request.getRequestURI(), e.getMessage());

            // Token expired, continue without authentication
            // SecurityConfig will reject if path requires authentication

        } catch (SignatureException e) {
            log.error("JWT token signature invalid: path={}, message={}",
                request.getRequestURI(), e.getMessage());

            // Invalid signature (token tampered), continue without authentication

        } catch (MalformedJwtException e) {
            log.error("JWT token malformed: path={}, message={}",
                request.getRequestURI(), e.getMessage());

            // Malformed token format, continue without authentication

        } catch (Exception e) {
            log.error("JWT token processing error: path={}, error={}",
                request.getRequestURI(), e.getMessage(), e);

            // Unexpected error, continue without authentication
        }

        // 13. Continue filter chain (pass request to next filter/controller)
        // If authentication was set, request is authenticated with current_role
        // If not, request continues unauthenticated (will be rejected by SecurityConfig if needed)
        filterChain.doFilter(request, response);
    }

    /**
     * Determine if Filter Should Execute for This Request
     *
     * Override this method to skip filtering for certain paths (optimization).
     * For example, public endpoints like /api/auth/login don't need JWT validation.
     *
     * Current Implementation: Filter ALL requests (including public endpoints)
     * This is safe because SecurityConfig handles access control.
     *
     * @param request HTTP request
     * @return true to skip this filter, false to execute it
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Optional optimization: Skip filtering for public endpoints
        // String path = request.getRequestURI();
        // return path.startsWith("/api/auth/");

        // Current: Filter all requests
        return false;
    }
}
