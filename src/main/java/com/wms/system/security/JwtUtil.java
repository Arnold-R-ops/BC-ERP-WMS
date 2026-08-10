package com.wms.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT Utility Class
 *
 * Core Responsibilities:
 * 1. Generate JWT Token (username + current_role + security_version + expiration)
 * 2. Parse JWT Token (extract username, current_role, security_version, claims)
 * 3. Validate JWT Token (signature + expiration)
 * 4. Check Token expiration status
 *
 * Multi-Role System (v3.3+):
 * - Token contains 'current_role' claim (active role for authorization)
 * - Token optionally contains 'available_roles' claim (all user roles)
 * - Token contains 'security_version' so authorization changes revoke old tokens
 * - Supports identity switching without re-authentication
 * - Role switching generates new token with updated current_role
 *
 * Technical Implementation:
 * - Library: JJWT 0.12.3 (io.jsonwebtoken)
 * - Algorithm: HMAC-SHA256 (HS256)
 * - Secret Key: Base64 encoded, minimum 256 bits (32 bytes)
 * - Token Structure: Header.Payload.Signature
 *
 * Security Features:
 * - Signature verification prevents token tampering
 * - Expiration time enforces token validity period
 * - Claims include username and current_role for authorization
 *
 * Usage Example:
 * <pre>
 * // Generate token with current role
 * String token = jwtUtil.generateToken("john_doe", "WAREHOUSE_ADMIN");
 *
 * // Generate token with all roles
 * List&lt;String&gt; roles = Arrays.asList("WAREHOUSE_ADMIN", "SALESPERSON");
 * String token = jwtUtil.generateTokenWithRoles("john_doe", "WAREHOUSE_ADMIN", roles);
 *
 * // Extract current role
 * String currentRole = jwtUtil.extractCurrentRole(token);
 *
 * // Extract available roles
 * List&lt;String&gt; availableRoles = jwtUtil.extractAvailableRoles(token);
 *
 * // Validate token
 * boolean isValid = jwtUtil.isTokenValid(token, username);
 * </pre>
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 3.3 (Multi-Role RBAC System)
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT Secret Key (from application.yml)
     * ⚠️ MUST be Base64 encoded, minimum 256 bits (32 bytes)
     */
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    /**
     * JWT Token Expiration Time (milliseconds)
     * Default: 86400000 ms = 24 hours
     */
    @Value("${jwt.expiration}")
    private Long EXPIRATION_TIME;

    /**
     * JWT Token Issuer (from application.yml)
     * Identifies the system that issued the token
     */
    @Value("${jwt.issuer}")
    private String ISSUER;

    /**
     * ⭐ Generate JWT Token (Basic)
     *
     * Generates a JWT token with current role only (no available_roles list).
     * Use this method for simple authentication scenarios.
     *
     * Token Structure:
     * - Subject: username (unique identifier)
     * - Claim "current_role": user's active role (SUPER_ADMIN, WAREHOUSE_ADMIN, etc.)
     * - Issuer: WMS-System
     * - Issued At: current timestamp
     * - Expiration: current timestamp + expiration time
     * - Signature: HMAC-SHA256
     *
     * @param username User's username (cannot be null)
     * @param currentRoleCode User's active role code (e.g., "SUPER_ADMIN", "WAREHOUSE_ADMIN")
     * @return JWT Token string (e.g., "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
     * @since v3.3 (Multi-Role System)
     */
    public String generateToken(String username, String currentRoleCode) {
        return generateToken(username, currentRoleCode, 1L);
    }

    /**
     * Generate a role token bound to the user's current security version.
     */
    public String generateToken(String username, String currentRoleCode, Long securityVersion) {
        log.debug("Generating JWT token for user: username={}, currentRole={}", username, currentRoleCode);

        Map<String, Object> claims = new HashMap<>();
        claims.put("current_role", currentRoleCode);  // Changed from "role" to "current_role"
        claims.put("security_version", securityVersion);

        String token = Jwts.builder()
            .claims(claims)                                          // Add custom claims
            .subject(username)                                       // Set subject (username)
            .issuer(ISSUER)                                          // Set issuer
            .issuedAt(new Date())                                    // Set issued time
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))  // Set expiration
            .signWith(getSigningKey())                               // Sign with secret key
            .compact();

        log.info("JWT token generated successfully for user: {} with role: {}", username, currentRoleCode);
        return token;
    }

    /**
     * ⭐ Generate JWT Token (With Available Roles)
     *
     * Generates a JWT token with both current role and all available roles.
     * Use this method for multi-role scenarios with identity switching support.
     *
     * Token Structure:
     * - Subject: username (unique identifier)
     * - Claim "current_role": user's active role
     * - Claim "available_roles": list of all user's assigned roles
     * - Issuer: WMS-System
     * - Issued At: current timestamp
     * - Expiration: current timestamp + expiration time
     * - Signature: HMAC-SHA256
     *
     * @param username User's username (cannot be null)
     * @param currentRoleCode User's active role code
     * @param availableRoles List of all role codes assigned to the user
     * @return JWT Token string with embedded role information
     * @since v3.3 (Multi-Role System)
     */
    public String generateTokenWithRoles(String username, String currentRoleCode, List<String> availableRoles) {
        return generateTokenWithRoles(username, currentRoleCode, availableRoles, 1L);
    }

    /**
     * Generate a multi-role token bound to the user's current security version.
     */
    public String generateTokenWithRoles(
            String username,
            String currentRoleCode,
            List<String> availableRoles,
            Long securityVersion
    ) {
        log.debug("Generating JWT token for user: username={}, currentRole={}, availableRoles={}",
            username, currentRoleCode, availableRoles);

        Map<String, Object> claims = new HashMap<>();
        claims.put("current_role", currentRoleCode);
        claims.put("available_roles", availableRoles);
        claims.put("security_version", securityVersion);

        String token = Jwts.builder()
            .claims(claims)
            .subject(username)
            .issuer(ISSUER)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(getSigningKey())
            .compact();

        log.info("JWT token generated successfully for user: {} with current role: {} and {} available roles",
            username, currentRoleCode, availableRoles.size());
        return token;
    }

    /**
     * ⭐ Extract Username from Token
     *
     * Extracts the "subject" claim from JWT token payload.
     *
     * @param token JWT Token string
     * @return Username (subject)
     * @throws ExpiredJwtException if token is expired
     * @throws MalformedJwtException if token format is invalid
     * @throws SignatureException if signature verification fails
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract Current Role from Token
     *
     * Extracts the "current_role" claim from JWT token payload.
     * This is the active role used for authorization checks.
     *
     * @param token JWT Token string
     * @return Current active role code (e.g., "SUPER_ADMIN", "WAREHOUSE_ADMIN")
     * @since v3.3 (Multi-Role System)
     */
    public String extractCurrentRole(String token) {
        return extractClaim(token, claims -> claims.get("current_role", String.class));
    }

    /**
     * Extract Available Roles from Token
     *
     * Extracts the "available_roles" claim from JWT token payload.
     * Returns all roles assigned to the user for identity switching.
     *
     * @param token JWT Token string
     * @return List of available role codes (e.g., ["WAREHOUSE_ADMIN", "SALESPERSON"])
     *         Returns null if available_roles claim is not present in token
     * @since v3.3 (Multi-Role System)
     */
    @SuppressWarnings("unchecked")
    public List<String> extractAvailableRoles(String token) {
        return extractClaim(token, claims -> claims.get("available_roles", List.class));
    }

    /**
     * Extract the authorization context version carried by the token.
     *
     * Tokens issued before this claim was introduced return {@code null} and
     * are rejected by JwtAuthenticationFilter.
     */
    public Long extractSecurityVersion(String token) {
        return extractClaim(token, claims -> {
            Object value = claims.get("security_version");
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                return Long.parseLong(text);
            }
            return null;
        });
    }

    /**
     * Extract User Role from Token
     *
     * @param token JWT Token string
     * @return User role (e.g., "ADMIN", "STAFF")
     * @deprecated Since v3.3. Use {@link #extractCurrentRole(String)} instead.
     *             The "role" claim has been replaced with "current_role" in multi-role system.
     */
    @Deprecated(since = "v3.3", forRemoval = true)
    public String extractRole(String token) {
        // For backward compatibility, try current_role first, then fall back to old "role" claim
        String currentRole = extractCurrentRole(token);
        if (currentRole != null) {
            return currentRole;
        }
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extract Expiration Date from Token
     *
     * @param token JWT Token string
     * @return Expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * ⭐ Validate Token
     *
     * Checks if token is valid by verifying:
     * 1. Username matches the expected username
     * 2. Token has not expired
     * 3. Signature is valid (verified during claim extraction)
     *
     * @param token JWT Token string
     * @param username Expected username
     * @return true if token is valid, false otherwise
     */
    public boolean isTokenValid(String token, String username) {
        return isTokenValid(token, username, null);
    }

    /**
     * Validate identity, expiry, and optionally the live authorization version.
     */
    public boolean isTokenValid(String token, String username, Long expectedSecurityVersion) {
        try {
            String tokenUsername = extractUsername(token);
            boolean usernameMatches = tokenUsername.equals(username);
            boolean notExpired = !isTokenExpired(token);
            Long tokenSecurityVersion = extractSecurityVersion(token);
            boolean securityVersionMatches = expectedSecurityVersion == null
                    || expectedSecurityVersion.equals(tokenSecurityVersion);

            boolean isValid = usernameMatches && notExpired && securityVersionMatches;

            log.debug("Token validation: username={}, usernameMatches={}, notExpired={}, " +
                            "securityVersionMatches={}, isValid={}",
                username, usernameMatches, notExpired, securityVersionMatches, isValid);

            return isValid;
        } catch (ExpiredJwtException e) {
            log.warn("Token validation failed: Token expired for user={}", username);
            return false;
        } catch (SignatureException e) {
            log.error("Token validation failed: Invalid signature for user={}", username);
            return false;
        } catch (MalformedJwtException e) {
            log.error("Token validation failed: Malformed token for user={}", username);
            return false;
        } catch (Exception e) {
            log.error("Token validation failed: Unexpected error for user={}", username, e);
            return false;
        }
    }

    /**
     * Check if Token is Expired
     *
     * @param token JWT Token string
     * @return true if expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        boolean expired = expiration.before(new Date());

        if (expired) {
            log.debug("Token expired: expirationDate={}, currentDate={}", expiration, new Date());
        }

        return expired;
    }

    /**
     * Extract Single Claim from Token
     *
     * Generic method to extract any claim from token payload.
     *
     * @param token JWT Token string
     * @param claimsResolver Function to extract specific claim
     * @param <T> Claim type
     * @return Claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract All Claims from Token
     *
     * Parses and verifies JWT token, then extracts all claims from payload.
     *
     * JJWT 0.12.x API:
     * - Use Jwts.parser() to create parser
     * - Use verifyWith(key) to set verification key
     * - Use build() to finalize parser configuration
     * - Use parseSignedClaims(token) to parse and verify token
     * - Use getPayload() to extract claims
     *
     * @param token JWT Token string
     * @return Claims object containing all token claims
     * @throws ExpiredJwtException if token is expired
     * @throws MalformedJwtException if token format is invalid
     * @throws SignatureException if signature verification fails
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())  // Set verification key
            .build()                      // Build parser
            .parseSignedClaims(token)     // Parse and verify token
            .getPayload();                // Extract payload (claims)
    }

    /**
     * Get Signing Key for Token Generation and Verification
     *
     * Converts Base64 encoded secret key to SecretKey object.
     *
     * Security Notes:
     * - Secret key must be at least 256 bits (32 bytes) for HS256
     * - Base64 encoding prevents special character issues
     * - Key is stored in application.yml and loaded via @Value
     *
     * @return SecretKey for HMAC-SHA256 algorithm
     */
    private SecretKey getSigningKey() {
        // Decode Base64 secret key
        byte[] keyBytes = Base64.getDecoder().decode(SECRET_KEY);

        // Create HMAC-SHA256 key from bytes
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Calculate Token Remaining Validity Time
     *
     * Useful for token refresh logic.
     *
     * @param token JWT Token string
     * @return Remaining time in milliseconds (negative if expired)
     */
    public long getTokenRemainingTime(String token) {
        Date expiration = extractExpiration(token);
        long remainingTime = expiration.getTime() - System.currentTimeMillis();

        log.debug("Token remaining time: {} ms (expires at: {})", remainingTime, expiration);

        return remainingTime;
    }

    /**
     * Check if Token Needs Refresh
     *
     * Returns true if token will expire within the refresh threshold.
     *
     * @param token JWT Token string
     * @return true if token needs refresh, false otherwise
     */
    public boolean needsRefresh(String token) {
        long remainingTime = getTokenRemainingTime(token);

        // Refresh if remaining time is less than 1 hour (3600000 ms)
        // This value can be configured in application.yml
        boolean needsRefresh = remainingTime > 0 && remainingTime < 3600000;

        log.debug("Token refresh check: remainingTime={} ms, needsRefresh={}", remainingTime, needsRefresh);

        return needsRefresh;
    }
}
