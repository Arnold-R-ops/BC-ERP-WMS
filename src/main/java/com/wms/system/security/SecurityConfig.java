package com.wms.system.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security Configuration
 *
 * Core Responsibilities:
 * 1. Configure security filter chain (which endpoints require authentication)
 * 2. Configure password encoder (BCrypt for secure password hashing)
 * 3. Configure authentication provider (how to validate credentials)
 * 4. Add JWT authentication filter to filter chain
 * 5. Disable CSRF (not needed for stateless JWT authentication)
 * 6. Configure session management (stateless for JWT)
 *
 * Security Rules:
 * - ✅ ALLOW: /api/auth/** (login, register endpoints)
 * - ✅ ALLOW: /health/** (health check endpoints)
 * - 🔒 REQUIRE AUTH: /api/inventory/** (inventory management APIs)
 * - 🔒 REQUIRE AUTH: /api/predictions/** (prediction APIs)
 * - 🔒 REQUIRE AUTH: All other endpoints
 *
 * Authentication Flow:
 * 1. Client sends request with JWT token in Authorization header
 * 2. JwtAuthenticationFilter intercepts and validates token
 * 3. If valid, sets Authentication in SecurityContext
 * 4. SecurityFilterChain checks if endpoint requires authentication
 * 5. If authenticated, request proceeds to controller
 * 6. If not authenticated, returns 401 Unauthorized
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 1.0 (JWT Authentication)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enable @PreAuthorize, @Secured annotations (for role-based access control)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * ⭐ Configure Security Filter Chain
     *
     * Defines which endpoints are public and which require authentication.
     *
     * Configuration Details:
     * - CSRF disabled: JWT is stateless, CSRF not needed
     * - Session management: STATELESS (no HTTP session, JWT only)
     * - Authorization rules:
     *   - /api/auth/** → permitAll (public login/register)
     *   - /health/** → permitAll (public health check)
     *   - /api/inventory/** → authenticated (requires JWT)
     *   - /api/predictions/** → authenticated (requires JWT)
     *   - anyRequest → authenticated (default deny)
     * - JWT filter: Added BEFORE UsernamePasswordAuthenticationFilter
     *
     * @param http HttpSecurity configuration object
     * @return SecurityFilterChain Configured security filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (Cross-Site Request Forgery protection)
            // JWT is stateless, no cookies used, CSRF not needed
            .csrf(AbstractHttpConfigurer::disable)

            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // ✅ Public endpoints (no authentication required)
                .requestMatchers("/api/auth/**").permitAll()     // Login, register
                .requestMatchers("/health/**").permitAll()       // Health check

                // 🔒 Protected endpoints (authentication required)
                .requestMatchers("/api/inventory/**").authenticated()    // Inventory APIs
                .requestMatchers("/api/predictions/**").authenticated()  // Prediction APIs

                // 🔒 Default rule: All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Configure session management (STATELESS for JWT)
            // No HTTP session created, authentication state stored in JWT only
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Add JWT authentication filter BEFORE UsernamePasswordAuthenticationFilter
            // This ensures JWT token is validated before standard authentication
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * ⭐ Password Encoder Bean
     *
     * BCrypt password hashing algorithm.
     *
     * Security Features:
     * - One-way hash (cannot reverse)
     * - Adaptive (can increase complexity over time)
     * - Salt included (prevents rainbow table attacks)
     * - Industry standard for password storage
     *
     * Usage:
     * - Registration: passwordEncoder.encode(plainPassword)
     * - Login: Spring Security automatically compares encoded password
     *
     * @return PasswordEncoder BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * ⭐ Authentication Provider Bean
     *
     * Configures how Spring Security validates user credentials.
     *
     * Configuration:
     * - UserDetailsService: CustomUserDetailsService (loads user from database)
     * - PasswordEncoder: BCryptPasswordEncoder (validates password hash)
     *
     * Authentication Process:
     * 1. Load user by username via CustomUserDetailsService
     * 2. Compare provided password with stored BCrypt hash
     * 3. If match, authentication succeeds
     * 4. If no match, throw BadCredentialsException
     *
     * @return AuthenticationProvider Configured authentication provider
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);  // How to load user
        provider.setPasswordEncoder(passwordEncoder());      // How to validate password
        return provider;
    }

    /**
     * ⭐ Authentication Manager Bean
     *
     * Central authentication coordinator used by AuthController.
     *
     * Usage in Login Flow:
     * <pre>
     * Authentication auth = authenticationManager.authenticate(
     *     new UsernamePasswordAuthenticationToken(username, password)
     * );
     * </pre>
     *
     * This triggers:
     * 1. CustomUserDetailsService.loadUserByUsername()
     * 2. BCryptPasswordEncoder password comparison
     * 3. Returns Authentication object if successful
     * 4. Throws exception if credentials invalid
     *
     * @param config AuthenticationConfiguration from Spring Security
     * @return AuthenticationManager Authentication manager
     * @throws Exception if configuration fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
