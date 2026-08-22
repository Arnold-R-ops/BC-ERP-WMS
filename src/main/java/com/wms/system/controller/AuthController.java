package com.wms.system.controller;

import com.wms.system.dto.LoginRequest;
import com.wms.system.dto.LoginResponse;
import com.wms.system.dto.RevokeOwnSessionsRequest;
import com.wms.system.dto.SwitchRoleRequest;
import com.wms.system.dto.SwitchRoleResponse;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SysRoleRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.JwtUtil;
import com.wms.system.security.SecurityUser;
import com.wms.system.security.TenantJwtClaims;
import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.service.DynamicPermissionService;
import com.wms.system.service.UserRoleService;
import com.wms.system.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication Controller
 *
 * Provides authentication endpoints for user login and role switching.
 *
 * API Endpoints:
 * - POST /api/auth/login: User login with username and password
 * - POST /api/auth/switch-role: Switch active role without re-authentication
 *
 * Multi-Role System (v3.3+):
 * - Users can have multiple roles assigned
 * - Login returns current role and all available roles
 * - Users can switch between assigned roles via /switch-role endpoint
 * - JWT token contains current_role claim for authorization
 *
 * Authentication Flow:
 * 1. Client sends POST request with username and password (JSON body)
 * 2. Spring Security validates credentials via AuthenticationManager
 * 3. Load user's assigned roles from sys_user_role table
 * 4. Select default role (user's default_role_id or first active role)
 * 5. Generate JWT token with current_role and available_roles
 * 6. Return token + user info + role information to client
 * 7. Client stores token and can switch roles without re-authentication
 *
 * Error Handling:
 * - Invalid credentials: Returns AUTH_INVALID_CREDENTIALS (401)
 * - Account disabled: Returns USER_ACCOUNT_DISABLED (403)
 * - User not found: Returns AUTH_INVALID_CREDENTIALS (401, no enumeration)
 * - No roles assigned: Returns USER_NO_ROLES (403)
 * - No active roles: Returns USER_NO_ACTIVE_ROLES (403)
 * - Validation errors: Returns VALIDATION_FAILED (400)
 *
 * Security Notes:
 * - DO NOT reveal whether username or password is wrong (prevents username enumeration)
 * - Rate limiting should be implemented to prevent brute force attacks
 * - HTTPS required in production to protect credentials in transit
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 3.3 (Multi-Role RBAC System)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final UserRoleService userRoleService;
    private final DynamicPermissionService dynamicPermissionService;
    private final TenancyProperties tenancyProperties;
    private final UserManagementService userManagementService;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    /**
     * ⭐ User Login (Multi-Role System)
     *
     * Authenticates user with username and password, returns JWT token with multi-role information.
     *
     * API Endpoint:
     * POST /api/auth/login
     *
     * Request Body:
     * <pre>
     * {
     *   "username": "john_doe",
     *   "password": "password123"
     * }
     * </pre>
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "tokenType": "Bearer",
     *   "username": "john_doe",
     *   "currentRole": "WAREHOUSE_ADMIN",
     *   "availableRoles": ["WAREHOUSE_ADMIN", "SALESPERSON"],
     *   "expiresIn": 86400000
     * }
     * </pre>
     *
     * @param request Login request (username + password)
     * @return ResponseEntity<LoginResponse> JWT token and multi-role user info
     * @throws BusinessException if authentication fails or user has no roles
    */
    @PostMapping("/login")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt: username={}", request.getUsername());

        try {
            // 1. Authenticate user credentials via Spring Security
            TenantContext requestContext = TenantContextHolder.current().orElse(null);
            Authentication authentication;
            if (requestContext != null
                    && requestContext.surface() == RequestSurface.TENANT) {
                User credentialUser = userRepository.findByCompanyIdAndUsername(
                        requestContext.tenantId(), request.getUsername())
                    .orElseThrow(() -> new BusinessException(
                        ErrorKeys.AUTH_INVALID_CREDENTIALS, Map.of()));
                authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                        credentialUser.getUsername(), request.getPassword()));
            } else {
                authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));
            }

            log.info("Authentication successful: username={}", request.getUsername());

            // 2. Load user entity from database
            if (tenancyProperties.isEnabled()
                    && (requestContext == null
                        || requestContext.surface() != RequestSurface.TENANT)) {
                throw new BusinessException(ErrorKeys.AUTH_INVALID_CREDENTIALS, Map.of());
            }
            User user = (requestContext != null
                    && requestContext.surface() == RequestSurface.TENANT)
                ? userRepository.findByCompanyIdAndUsername(
                        requestContext.tenantId(), request.getUsername())
                    .orElseThrow(() -> new BusinessException(
                        ErrorKeys.AUTH_INVALID_CREDENTIALS, Map.of()))
                : userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.error("User not found after successful authentication: username={}",
                        request.getUsername());
                    throw new BusinessException(
                        ErrorKeys.USER_NOT_FOUND,
                        Map.of("username", request.getUsername())
                    );
                });

            // 3. Load user's assigned roles from sys_user_role table
            List<SysRole> userRoles = userRoleService.getUserRoles(
                user.getCompanyId(), user.getId());

            log.debug("User roles loaded: username={}, roleCount={}", user.getUsername(), userRoles.size());

            // 4. Validate user has at least one role
            if (userRoles.isEmpty()) {
                log.error("User has no roles assigned: username={}, userId={}",
                    user.getUsername(), user.getId());
                throw new BusinessException(
                    ErrorKeys.USER_NO_ROLES,
                    Map.of("userId", user.getId(), "username", user.getUsername())
                );
            }

            // 5. Select default role (active roles only)
            SysRole currentRole = selectDefaultRole(user, userRoles);

            log.info("Default role selected: username={}, currentRole={}",
                user.getUsername(), currentRole.getRoleCode());

            // 6. Extract all available role codes for JWT and response
            List<String> availableRoles = userRoles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());

            // 7. Generate JWT token with current_role and available_roles
            String token = jwtUtil.generateTenantToken(
                user.getId(),
                user.getCompanyId(),
                user.getUsername(),
                currentRole.getRoleCode(),
                availableRoles,
                user.getSecurityVersion()
            );

            log.info("JWT token generated: username={}, currentRole={}, availableRoles={}, tokenLength={}",
                user.getUsername(), currentRole.getRoleCode(), availableRoles, token.length());

            // 8. Update user's default_role_id for next login
            if (user.getDefaultRoleId() == null || !user.getDefaultRoleId().equals(currentRole.getId())) {
                user.setDefaultRoleId(currentRole.getId());
                userRepository.save(user);
                log.debug("Updated user default_role_id: username={}, defaultRoleId={}",
                    user.getUsername(), currentRole.getId());
            }

            // 9. Build response with multi-role information
            // mustChangePassword (P0.5): tells the client to force a password
            // change dialog after an admin reset; other APIs are blocked
            // server-side until the change succeeds.
            LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .currentRole(currentRole.getRoleCode())
                .availableRoles(availableRoles)
                .permissionCodes(resolvePermissionCodes(
                    user.getCompanyId(), user.getId(), currentRole.getRoleCode(),
                    user.getSecurityVersion()))
                .expiresIn(jwtExpiration)
                .sessionEndsAt(jwtUtil.getTenantSessionEndsAt(token))
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();

            log.info("Login successful: username={}, currentRole={}, availableRoleCount={}",
                user.getUsername(), currentRole.getRoleCode(), availableRoles.size());

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            log.warn("Login failed: Invalid credentials for username={}", request.getUsername());
            throw new BusinessException(
                ErrorKeys.AUTH_INVALID_CREDENTIALS,
                Map.of()  // DO NOT include username to prevent enumeration
            );

        } catch (DisabledException e) {
            log.warn("Login failed: Account disabled for username={}", request.getUsername());
            throw new BusinessException(
                ErrorKeys.USER_ACCOUNT_DISABLED,
                Map.of("username", request.getUsername())
            );

        } catch (BusinessException e) {
            // Re-throw BusinessException (already has error key)
            throw e;

        } catch (Exception e) {
            log.error("Login failed: Unexpected error for username={}", request.getUsername(), e);
            throw new BusinessException(
                ErrorKeys.AUTH_FAILED,
                Map.of("reason", "Unexpected authentication error")
            );
        }
    }

    /**
     * Select Default Role for Login
     *
     * Priority:
     * 1. User's default_role_id (if set and role is active)
     * 2. Role with minimum sort_order (if multiple active roles)
     * 3. First active role (if sort_order is same)
     *
     * @param user User entity
     * @param userRoles List of all user roles
     * @return Selected default role
     * @throws BusinessException if no active roles found
     */
    private SysRole selectDefaultRole(User user, List<SysRole> userRoles) {
        // Filter for active roles only
        List<SysRole> activeRoles = userRoles.stream()
            .filter(SysRole::isActive)
            .collect(Collectors.toList());

        if (activeRoles.isEmpty()) {
            log.error("User has no active roles: username={}, userId={}, totalRoles={}",
                user.getUsername(), user.getId(), userRoles.size());
            throw new BusinessException(
                ErrorKeys.USER_NO_ACTIVE_ROLES,
                Map.of(
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "totalRoles", userRoles.size()
                )
            );
        }

        // Priority 1: User's default_role_id (if set and active)
        if (user.getDefaultRoleId() != null) {
            Optional<SysRole> defaultRole = activeRoles.stream()
                .filter(r -> r.getId().equals(user.getDefaultRoleId()))
                .findFirst();

            if (defaultRole.isPresent()) {
                log.debug("Using user's default role: roleCode={}", defaultRole.get().getRoleCode());
                return defaultRole.get();
            } else {
                log.warn("User's default_role_id not found in active roles, selecting by sort_order: " +
                    "defaultRoleId={}", user.getDefaultRoleId());
            }
        }

        // Priority 2 & 3: Role with minimum sort_order, then first assigned
        SysRole selectedRole = activeRoles.stream()
            .min(Comparator.comparing(SysRole::getSortOrder))
            .orElse(activeRoles.get(0));

        log.debug("Selected role by sort_order: roleCode={}, sortOrder={}",
            selectedRole.getRoleCode(), selectedRole.getSortOrder());

        return selectedRole;
    }

    /**
     * ⭐ Switch Role (Identity Switching)
     *
     * Allows a user to switch their active role without re-authentication.
     * Generates a new JWT token with the updated current_role claim.
     *
     * API Endpoint:
     * POST /api/auth/switch-role
     *
     * Authentication Required: Bearer Token
     *
     * Request Body:
     * <pre>
     * {
     *   "targetRoleCode": "SALESPERSON"
     * }
     * </pre>
     *
     * Success Response (200 OK):
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
     * Error Responses:
     * - 404: ROLE_NOT_FOUND - Target role does not exist
     * - 403: ROLE_NOT_ASSIGNED - User does not have target role assigned
     * - 403: ROLE_DISABLED - Target role is inactive
     *
     * Security:
     * - Role switching advances security_version and invalidates the old token
     * - Client should immediately replace the old token with the returned token
     * - User's default_role_id is updated for future logins
     *
     * @param request Switch role request containing target role code
     * @param authentication Spring Security authentication object
     * @return ResponseEntity<SwitchRoleResponse> New token with updated role
     * @throws BusinessException if role switch fails
     */
    @PostMapping("/switch-role")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<SwitchRoleResponse> switchRole(
        @Valid @RequestBody SwitchRoleRequest request,
        Authentication authentication,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        // Check if authentication is null (user not authenticated)
        if (authentication == null) {
            log.warn("Role switch attempt without authentication");
            throw new BusinessException(
                ErrorKeys.AUTH_TOKEN_MISSING,
                Map.of("message", "Authentication required for role switching")
            );
        }

        if (!(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            throw new BusinessException(ErrorKeys.AUTH_TOKEN_INVALID, Map.of());
        }
        String username = securityUser.getUsername();
        String targetRoleCode = request.getTargetRoleCode();

        log.info("Role switch request: username={}, targetRole={}", username, targetRoleCode);

        try {
            // 1. Load user entity
            User user = userRepository.findByIdAndCompanyId(
                    securityUser.getId(), securityUser.getUser().getCompanyId())
                .orElseThrow(() -> {
                    log.error("User not found during role switch: username={}", username);
                    throw new BusinessException(
                        ErrorKeys.USER_NOT_FOUND,
                        Map.of("username", username)
                    );
                });

            // 2. Verify target role exists
            SysRole targetRole = roleRepository.findByCompanyIdAndRoleCode(
                    user.getCompanyId(), targetRoleCode)
                .orElseThrow(() -> {
                    log.warn("Target role not found: username={}, roleCode={}",
                        username, targetRoleCode);
                    throw new BusinessException(
                        ErrorKeys.ROLE_NOT_FOUND,
                        Map.of("roleCode", targetRoleCode)
                    );
                });

            // 3. Verify user has this role assigned
            boolean hasRole = userRoleService.userHasRole(
                user.getCompanyId(), user.getId(), targetRole.getId());
            if (!hasRole) {
                log.warn("User does not have target role: username={}, roleCode={}",
                    username, targetRoleCode);
                throw new BusinessException(
                    ErrorKeys.ROLE_NOT_ASSIGNED,
                    Map.of(
                        "roleCode", targetRoleCode,
                        "roleId", targetRole.getId(),
                        "userId", user.getId(),
                        "username", username
                    )
                );
            }

            // 4. Verify target role is active
            if (!targetRole.isActive()) {
                log.warn("Target role is disabled: username={}, roleCode={}",
                    username, targetRoleCode);
                throw new BusinessException(
                    ErrorKeys.ROLE_DISABLED,
                    Map.of(
                        "roleId", targetRole.getId(),
                        "roleCode", targetRole.getRoleCode(),
                        "roleName", targetRole.getRoleName()
                    )
                );
            }

            // 5. Load all user roles for available_roles claim
            List<SysRole> userRoles = userRoleService.getUserRoles(
                user.getCompanyId(), user.getId());
            List<String> availableRoles = userRoles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());

            // 6. Persist the new default role and revoke the previous role token.
            user.setDefaultRoleId(targetRole.getId());
            long nextSecurityVersion = (user.getSecurityVersion() == null
                    ? 0L
                    : user.getSecurityVersion()) + 1L;
            user.setSecurityVersion(nextSecurityVersion);
            userRepository.save(user);

            log.debug("Updated role context: username={}, defaultRoleId={}, securityVersion={}",
                username, targetRole.getId(), nextSecurityVersion);

            // 7. Generate a new token bound to the advanced security version.
            String newToken;
            if (authorization == null || authorization.isBlank()) {
                newToken = jwtUtil.generateTenantToken(
                    user.getId(), user.getCompanyId(), username,
                    targetRole.getRoleCode(), availableRoles, nextSecurityVersion);
            } else {
                long sessionStartedAt = resolveSessionStartedAt(authorization, securityUser);
                newToken = jwtUtil.generateTenantToken(
                    user.getId(), user.getCompanyId(), username,
                    targetRole.getRoleCode(), availableRoles, nextSecurityVersion,
                    sessionStartedAt);
            }

            log.info("JWT token generated for role switch: username={}, newRole={}, tokenLength={}",
                username, targetRole.getRoleCode(), newToken.length());

            // 8. Build response
            SwitchRoleResponse response = SwitchRoleResponse.builder()
                .token(newToken)
                .tokenType("Bearer")
                .currentRole(targetRole.getRoleCode())
                .permissionCodes(resolvePermissionCodes(
                    user.getCompanyId(), user.getId(), targetRole.getRoleCode(),
                    nextSecurityVersion))
                .message(String.format("Role switched successfully to %s (%s)",
                    targetRole.getRoleCode(), targetRole.getRoleName()))
                .expiresIn(authorization == null || authorization.isBlank()
                    ? jwtExpiration
                    : jwtUtil.getTenantTokenRemainingTime(newToken))
                .sessionEndsAt(jwtUtil.getTenantSessionEndsAt(newToken))
                .build();

            log.info("Role switch successful: username={}, newRole={}, roleName={}",
                username, targetRole.getRoleCode(), targetRole.getRoleName());

            return ResponseEntity.ok(response);

        } catch (BusinessException e) {
            // Re-throw BusinessException (already has error key)
            throw e;

        } catch (Exception e) {
            log.error("Role switch failed: username={}, targetRole={}, error={}",
                username, targetRoleCode, e.getMessage(), e);
            throw new BusinessException(
                ErrorKeys.ROLE_SWITCH_FAILED,
                Map.of(
                    "targetRoleCode", targetRoleCode,
                    "username", username,
                    "reason", e.getMessage()
                )
            );
        }
    }

    /** Compatibility overload used by controller unit tests and direct callers. */
    ResponseEntity<SwitchRoleResponse> switchRole(
        SwitchRoleRequest request,
        Authentication authentication
    ) {
        return switchRole(request, authentication, null);
    }

    /**
     * Refresh an active tenant token only inside the configured threshold.
     * The original sign-in time is retained, so refresh never extends the
     * absolute seven-day tenant ERP session window.
     */
    @PostMapping("/refresh-token")
    @Transactional(readOnly = true)
    public ResponseEntity<LoginResponse> refreshToken(
        @RequestHeader("Authorization") String authorization,
        Authentication authentication
    ) {
        if (!(authentication != null
                && authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            throw new BusinessException(ErrorKeys.AUTH_TOKEN_INVALID, Map.of());
        }
        String token = bearerToken(authorization);
        TenantJwtClaims claims = jwtUtil.parseTenantToken(token);
        if (!claims.userId().equals(securityUser.getId())
                || !claims.companyId().equals(securityUser.getUser().getCompanyId())) {
            throw new BusinessException(ErrorKeys.AUTH_TOKEN_INVALID, Map.of());
        }
        if (!jwtUtil.tenantTokenNeedsRefresh(token)) {
            return ResponseEntity.noContent().build();
        }

        User user = userRepository.findByIdAndCompanyId(
                claims.userId(), claims.companyId())
            .orElseThrow(() -> new BusinessException(
                ErrorKeys.AUTH_TOKEN_INVALID, Map.of()));
        List<SysRole> activeRoles = userRoleService
            .getUserRoles(user.getCompanyId(), user.getId()).stream()
            .filter(SysRole::isActive)
            .toList();
        if (activeRoles.stream().noneMatch(
                role -> role.getRoleCode().equals(claims.currentRole()))) {
            throw new BusinessException(ErrorKeys.AUTH_TOKEN_INVALID, Map.of());
        }
        List<String> availableRoles = activeRoles.stream()
            .map(SysRole::getRoleCode)
            .toList();
        String refreshedToken = jwtUtil.generateTenantToken(
            user.getId(), user.getCompanyId(), user.getUsername(),
            claims.currentRole(), availableRoles, user.getSecurityVersion(),
            claims.sessionStartedAtEpochMillis());

        return ResponseEntity.ok(LoginResponse.builder()
            .token(refreshedToken)
            .tokenType("Bearer")
            .username(user.getUsername())
            .currentRole(claims.currentRole())
            .availableRoles(availableRoles)
            .permissionCodes(resolvePermissionCodes(
                user.getCompanyId(), user.getId(), claims.currentRole(),
                user.getSecurityVersion()))
            .expiresIn(jwtUtil.getTenantTokenRemainingTime(refreshedToken))
            .sessionEndsAt(jwtUtil.getTenantSessionEndsAt(refreshedToken))
            .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
            .build());
    }

    /** Revoke every tenant JWT for the caller after password confirmation. */
    @PostMapping("/revoke-all-sessions")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Void> revokeAllSessions(
        @Valid @RequestBody RevokeOwnSessionsRequest request,
        Authentication authentication
    ) {
        if (!(authentication != null
                && authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            throw new BusinessException(ErrorKeys.AUTH_TOKEN_INVALID, Map.of());
        }
        userManagementService.revokeOwnSessions(
            securityUser.getId(), request.getCurrentPassword());
        return ResponseEntity.noContent().build();
    }

    private long resolveSessionStartedAt(
        String authorization,
        SecurityUser securityUser
    ) {
        if (authorization == null || authorization.isBlank()) {
            return System.currentTimeMillis();
        }
        TenantJwtClaims claims = jwtUtil.parseTenantToken(bearerToken(authorization));
        if (!claims.userId().equals(securityUser.getId())
                || !claims.companyId().equals(securityUser.getUser().getCompanyId())) {
            throw new BusinessException(ErrorKeys.AUTH_TOKEN_INVALID, Map.of());
        }
        return claims.sessionStartedAtEpochMillis();
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7) {
            throw new BusinessException(ErrorKeys.AUTH_TOKEN_MISSING, Map.of());
        }
        return authorization.substring(7);
    }

    private List<String> resolvePermissionCodes(
            Long companyId, Long userId, String roleCode, Long securityVersion) {
        Set<String> permissionCodes = dynamicPermissionService
            .getUserPermissionsForRole(
                companyId, userId, roleCode, securityVersion)
            .getPermissionCodes();
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return List.of();
        }
        return permissionCodes.stream().sorted().toList();
    }

    /**
     * Health Check for Authentication Service
     *
     * API Endpoint:
     * GET /api/auth/health
     *
     * Success Response (200 OK):
     * <pre>
     * {
     *   "status": "UP",
     *   "service": "AuthenticationService"
     * }
     * </pre>
     *
     * @return ResponseEntity with health status
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "AuthenticationService"
        ));
    }
}
