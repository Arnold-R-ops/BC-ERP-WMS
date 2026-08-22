package com.wms.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Issues and strictly verifies the two non-interchangeable BCWMS JWT types.
 * A token is parsed exactly once with the key, issuer and audience belonging
 * to the request surface; legacy claim-less tokens are intentionally rejected.
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    static final String CLAIM_COMPANY_ID = "company_id";
    static final String CLAIM_USERNAME = "username";
    static final String CLAIM_PRINCIPAL_TYPE = "principal_type";
    static final String CLAIM_CURRENT_ROLE = "current_role";
    static final String CLAIM_AVAILABLE_ROLES = "available_roles";
    static final String CLAIM_SECURITY_VERSION = "security_version";
    static final String CLAIM_SESSION_STARTED_AT = "session_started_at";
    static final String CLAIM_PLATFORM_EMAIL = "normalized_email";
    static final String CLAIM_PLATFORM_ROLES = "platform_roles";

    private final JwtProperties properties;
    private SecretKey tenantSigningKey;
    private SecretKey platformSigningKey;

    @PostConstruct
    void initializeKeys() {
        tenantSigningKey = decodeKey(properties.getTenantSecret(), "company");
        platformSigningKey = decodeKey(properties.getPlatformSecret(), "platform");
        if (java.security.MessageDigest.isEqual(
                tenantSigningKey.getEncoded(), platformSigningKey.getEncoded())) {
            throw new IllegalStateException(
                "Company and platform JWT signing keys must be different");
        }
    }

    public String generateTenantToken(
        Long userId,
        Long companyId,
        String username,
        String currentRole,
        List<String> availableRoles,
        Long securityVersion
    ) {
        return generateTenantToken(userId, companyId, username, currentRole,
            availableRoles, securityVersion, System.currentTimeMillis());
    }

    public String generateTenantToken(
        Long userId,
        Long companyId,
        String username,
        String currentRole,
        List<String> availableRoles,
        Long securityVersion,
        long sessionStartedAtEpochMillis
    ) {
        requirePositive(userId, "userId");
        requirePositive(companyId, "companyId");
        requirePositive(securityVersion, "securityVersion");
        if (sessionStartedAtEpochMillis <= 0) {
            throw new IllegalArgumentException("sessionStartedAtEpochMillis must be positive");
        }
        requireText(username, "username");
        requireText(currentRole, "currentRole");

        Map<String, Object> claims = Map.of(
            CLAIM_COMPANY_ID, companyId,
            CLAIM_USERNAME, username,
            CLAIM_PRINCIPAL_TYPE, JwtPrincipalType.TENANT.name(),
            CLAIM_CURRENT_ROLE, currentRole,
            CLAIM_AVAILABLE_ROLES, sanitizeRoles(availableRoles),
            CLAIM_SECURITY_VERSION, securityVersion,
            CLAIM_SESSION_STARTED_AT, sessionStartedAtEpochMillis
        );
        long now = System.currentTimeMillis();
        long absoluteSessionEnd = Math.addExact(
            sessionStartedAtEpochMillis, properties.getTenantSessionMaxAge());
        long tokenEnd = Math.min(
            Math.addExact(now, properties.getExpiration()), absoluteSessionEnd);
        if (tokenEnd <= now) {
            throw new JwtException("Tenant session maximum age has been reached");
        }
        return buildTokenAt(
            userId,
            claims,
            properties.getTenantAudience(),
            tenantSigningKey,
            now,
            tokenEnd
        );
    }

    public String generateTenantToken(
        Long userId,
        Long companyId,
        String username,
        String currentRole,
        Long securityVersion
    ) {
        return generateTenantToken(
            userId, companyId, username, currentRole, List.of(currentRole), securityVersion);
    }

    public String generatePlatformToken(
        Long platformUserId,
        String normalizedEmail,
        List<String> roles,
        Long securityVersion
    ) {
        requirePositive(platformUserId, "platformUserId");
        requirePositive(securityVersion, "securityVersion");
        requireText(normalizedEmail, "normalizedEmail");

        Map<String, Object> claims = Map.of(
            CLAIM_PLATFORM_EMAIL, normalizedEmail,
            CLAIM_PRINCIPAL_TYPE, JwtPrincipalType.PLATFORM.name(),
            CLAIM_PLATFORM_ROLES, sanitizeRoles(roles),
            CLAIM_SECURITY_VERSION, securityVersion
        );
        return buildToken(
            platformUserId,
            claims,
            properties.getPlatformAudience(),
            platformSigningKey,
            properties.getPlatformExpiration()
        );
    }

    public TenantJwtClaims parseTenantToken(String token) {
        Claims claims = parse(
            token,
            tenantSigningKey,
            properties.getTenantAudience(),
            JwtPrincipalType.TENANT
        );
        return new TenantJwtClaims(
            positiveLong(claims.getSubject(), "sub"),
            positiveLong(claims.get(CLAIM_COMPANY_ID), CLAIM_COMPANY_ID),
            requiredString(claims, CLAIM_USERNAME),
            requiredString(claims, CLAIM_CURRENT_ROLE),
            stringList(claims, CLAIM_AVAILABLE_ROLES),
            positiveLong(claims.get(CLAIM_SECURITY_VERSION), CLAIM_SECURITY_VERSION),
            claims.getIssuedAt().getTime(),
            claims.getExpiration().getTime(),
            optionalPositiveLong(
                claims.get(CLAIM_SESSION_STARTED_AT),
                claims.getIssuedAt().getTime(),
                CLAIM_SESSION_STARTED_AT)
        );
    }

    public PlatformJwtClaims parsePlatformToken(String token) {
        Claims claims = parse(
            token,
            platformSigningKey,
            properties.getPlatformAudience(),
            JwtPrincipalType.PLATFORM
        );
        if (claims.containsKey(CLAIM_COMPANY_ID)) {
            throw new JwtException("Platform token must not contain company_id");
        }
        return new PlatformJwtClaims(
            positiveLong(claims.getSubject(), "sub"),
            requiredString(claims, CLAIM_PLATFORM_EMAIL),
            stringList(claims, CLAIM_PLATFORM_ROLES),
            positiveLong(claims.get(CLAIM_SECURITY_VERSION), CLAIM_SECURITY_VERSION)
        );
    }

    public long getTenantTokenRemainingTime(String token) {
        return parseTenantToken(token).expiresAtEpochMillis() - System.currentTimeMillis();
    }

    public long getTenantSessionEndsAt(String token) {
        TenantJwtClaims claims = parseTenantToken(token);
        return Math.addExact(
            claims.sessionStartedAtEpochMillis(), properties.getTenantSessionMaxAge());
    }

    public Date getPlatformTokenExpiration(String token) {
        Claims claims = parse(
            token,
            platformSigningKey,
            properties.getPlatformAudience(),
            JwtPrincipalType.PLATFORM
        );
        return claims.getExpiration();
    }

    public boolean tenantTokenNeedsRefresh(String token) {
        long remaining = getTenantTokenRemainingTime(token);
        return remaining > 0 && remaining < properties.getRefreshThreshold();
    }

    private String buildToken(
        Long subjectId,
        Map<String, Object> claims,
        String audience,
        SecretKey signingKey,
        long expirationMillis
    ) {
        long now = System.currentTimeMillis();
        return buildTokenAt(subjectId, claims, audience, signingKey, now,
            Math.addExact(now, expirationMillis));
    }

    private String buildTokenAt(
        Long subjectId,
        Map<String, Object> claims,
        String audience,
        SecretKey signingKey,
        long issuedAtEpochMillis,
        long expiresAtEpochMillis
    ) {
        Date now = new Date(issuedAtEpochMillis);
        return Jwts.builder()
            .claims(claims)
            .subject(String.valueOf(subjectId))
            .issuer(properties.getIssuer())
            .audience().single(audience)
            .issuedAt(now)
            .expiration(new Date(expiresAtEpochMillis))
            .signWith(signingKey)
            .compact();
    }

    private Claims parse(
        String token,
        SecretKey signingKey,
        String audience,
        JwtPrincipalType principalType
    ) {
        requireText(token, "token");
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(properties.getIssuer())
            .requireAudience(audience)
            .require(CLAIM_PRINCIPAL_TYPE, principalType.name())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private static SecretKey decodeKey(String encoded, String keyName) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length < 32) {
                throw new IllegalStateException(
                    keyName + " JWT key must contain at least 256 bits");
            }
            return Keys.hmacShaKeyFor(bytes);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                keyName + " JWT key must be valid Base64", exception);
        }
    }

    private static Long positiveLong(Object value, String claimName) {
        Long parsed = null;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else if (value instanceof String text) {
            try {
                parsed = Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                // Converted to a generic invalid-claim result below.
            }
        }
        if (parsed == null || parsed <= 0) {
            throw new JwtException("JWT claim " + claimName + " must be a positive integer");
        }
        return parsed;
    }

    private static long optionalPositiveLong(Object value, long fallback, String claimName) {
        if (value == null) {
            return fallback;
        }
        return positiveLong(value, claimName);
    }

    private static String requiredString(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new JwtException("JWT claim " + claimName + " is required");
        }
        return text;
    }

    private static List<String> stringList(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (!(value instanceof List<?> list)) {
            throw new JwtException("JWT claim " + claimName + " must be a list");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String text) || text.isBlank()) {
                throw new JwtException("JWT claim " + claimName + " contains an invalid value");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static List<String> sanitizeRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                sanitized.add(role);
            }
        }
        return List.copyOf(sanitized);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
