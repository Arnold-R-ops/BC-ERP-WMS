package com.wms.system.controller;

import com.wms.system.dto.LoginRequest;
import com.wms.system.dto.LoginResponse;
import com.wms.system.entity.User;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.UserRepository;
import com.wms.system.security.JwtUtil;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication Controller
 *
 * Provides authentication endpoints for user login.
 *
 * API Endpoints:
 * - POST /api/auth/login: User login with username and password
 *
 * Authentication Flow:
 * 1. Client sends POST request with username and password (JSON body)
 * 2. Spring Security validates credentials via AuthenticationManager
 * 3. If valid, generate JWT token via JwtUtil
 * 4. Return token + user info to client
 * 5. Client stores token and includes in subsequent requests
 *
 * Error Handling:
 * - Invalid credentials: Returns AUTH_INVALID_CREDENTIALS (401)
 * - Account disabled: Returns USER_ACCOUNT_DISABLED (403)
 * - User not found: Returns AUTH_INVALID_CREDENTIALS (401, no enumeration)
 * - Validation errors: Returns VALIDATION_FAILED (400)
 *
 * Security Notes:
 * - DO NOT reveal whether username or password is wrong (prevents username enumeration)
 * - Rate limiting should be implemented to prevent brute force attacks
 * - HTTPS required in production to protect credentials in transit
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 1.0 (JWT Authentication)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    /**
     * ⭐ User Login
     *
     * Authenticates user with username and password, returns JWT token if successful.
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
     *   "role": "ADMIN",
     *   "expiresIn": 86400000
     * }
     * </pre>
     *
     * Error Response (401 Unauthorized - Invalid Credentials):
     * <pre>
     * {
     *   "errorKey": "AUTH_INVALID_CREDENTIALS",
     *   "params": {},
     *   "timestamp": "2025-01-11T10:30:00",
     *   "path": "/api/auth/login",
     *   "status": 401
     * }
     * </pre>
     *
     * Error Response (403 Forbidden - Account Disabled):
     * <pre>
     * {
     *   "errorKey": "USER_ACCOUNT_DISABLED",
     *   "params": {
     *     "username": "john_doe"
     *   },
     *   "timestamp": "2025-01-11T10:30:00",
     *   "path": "/api/auth/login",
     *   "status": 403
     * }
     * </pre>
     *
     * @param request Login request (username + password)
     * @return ResponseEntity<LoginResponse> JWT token and user info
     * @throws BusinessException if authentication fails
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt: username={}", request.getUsername());

        try {
            // 1. Authenticate user credentials via Spring Security
            // This internally calls:
            // - CustomUserDetailsService.loadUserByUsername()
            // - BCryptPasswordEncoder to compare passwords
            // - Checks User.isEnabled(), isAccountNonLocked(), etc.
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getUsername(),
                    request.getPassword()
                )
            );

            log.info("Authentication successful: username={}, authorities={}",
                request.getUsername(), authentication.getAuthorities());

            // 2. Load user details from database
            // (Authentication object contains UserDetails, but we need full User entity for role)
            User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.error("User not found after successful authentication: username={}",
                        request.getUsername());

                    throw new BusinessException(
                        ErrorKeys.USER_NOT_FOUND,
                        Map.of("username", request.getUsername())
                    );
                });

            // 3. Generate JWT token
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

            log.info("JWT token generated: username={}, role={}, tokenLength={}",
                user.getUsername(), user.getRole(), token.length());

            // 4. Build response with token and user info
            LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtExpiration)
                .build();

            log.info("Login successful: username={}, role={}", user.getUsername(), user.getRole());

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            // Invalid username or password
            log.warn("Login failed: Invalid credentials for username={}", request.getUsername());

            throw new BusinessException(
                ErrorKeys.AUTH_INVALID_CREDENTIALS,
                Map.of()  // DO NOT include username to prevent enumeration
            );

        } catch (DisabledException e) {
            // Account disabled
            log.warn("Login failed: Account disabled for username={}", request.getUsername());

            throw new BusinessException(
                ErrorKeys.USER_ACCOUNT_DISABLED,
                Map.of("username", request.getUsername())
            );

        } catch (BusinessException e) {
            // Re-throw BusinessException (already has error key)
            throw e;

        } catch (Exception e) {
            // Unexpected authentication error
            log.error("Login failed: Unexpected error for username={}",
                request.getUsername(), e);

            throw new BusinessException(
                ErrorKeys.AUTH_FAILED,
                Map.of("reason", "Unexpected authentication error")
            );
        }
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
