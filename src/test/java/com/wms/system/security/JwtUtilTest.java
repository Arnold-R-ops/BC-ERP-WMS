package com.wms.system.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtProperties properties;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        properties = properties(
            "tenant-signing-key-0123456789-abcdef",
            "platform-signing-key-01234567-fedcba");
        jwtUtil = initialized(properties);
    }

    @Test
    void companyTokenCarriesStrictTenantIdentityContract() {
        String token = jwtUtil.generateTenantToken(
            91L, 42L, "worker", "WAREHOUSE_STAFF",
            List.of("WAREHOUSE_STAFF", "PURCHASER"), 9L);

        TenantJwtClaims claims = jwtUtil.parseTenantToken(token);

        assertThat(claims.userId()).isEqualTo(91L);
        assertThat(claims.companyId()).isEqualTo(42L);
        assertThat(claims.username()).isEqualTo("worker");
        assertThat(claims.currentRole()).isEqualTo("WAREHOUSE_STAFF");
        assertThat(claims.availableRoles())
            .containsExactly("WAREHOUSE_STAFF", "PURCHASER");
        assertThat(claims.securityVersion()).isEqualTo(9L);
    }

    @Test
    void platformTokenHasNoCompanyAndCannotBeParsedAsCompanyToken() {
        String token = jwtUtil.generatePlatformToken(
            7L, "developer@bcwms.com",
            List.of("PLATFORM_SUPER_ADMIN"), 3L);

        PlatformJwtClaims claims = jwtUtil.parsePlatformToken(token);

        assertThat(claims.platformUserId()).isEqualTo(7L);
        assertThat(claims.normalizedEmail()).isEqualTo("developer@bcwms.com");
        assertThat(claims.roles()).containsExactly("PLATFORM_SUPER_ADMIN");
        assertThat(claims.securityVersion()).isEqualTo(3L);
        assertThatThrownBy(() -> jwtUtil.parseTenantToken(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void platformAndTenantTokensUseIndependentExpirationWindows() {
        properties.setExpiration(86_400_000L);
        properties.setPlatformExpiration(43_200_000L);
        long before = System.currentTimeMillis();

        String tenantToken = jwtUtil.generateTenantToken(91L, 42L, "worker", "WAREHOUSE_STAFF", 9L);
        String platformToken = jwtUtil.generatePlatformToken(
            7L, "developer@bcwms.com", List.of("PLATFORM_SUPER_ADMIN"), 3L);

        assertThat(jwtUtil.getTenantTokenRemainingTime(tenantToken)).isBetween(86_398_000L, 86_400_000L);
        assertThat(jwtUtil.getPlatformTokenExpiration(platformToken).getTime() - before)
            .isBetween(43_198_000L, 43_200_000L);
    }

    @Test
    void tenantRefreshKeepsOriginalSessionStartAndCapsExpirationAtSevenDays() {
        properties.setExpiration(86_400_000L);
        properties.setTenantSessionMaxAge(7 * 86_400_000L);
        long sessionStartedAt = System.currentTimeMillis()
            - properties.getTenantSessionMaxAge() + 30 * 60_000L;

        String token = jwtUtil.generateTenantToken(
            91L, 42L, "worker", "WAREHOUSE_STAFF",
            List.of("WAREHOUSE_STAFF"), 9L, sessionStartedAt);
        TenantJwtClaims claims = jwtUtil.parseTenantToken(token);

        assertThat(claims.sessionStartedAtEpochMillis()).isEqualTo(sessionStartedAt);
        assertThat(claims.expiresAtEpochMillis()
            - claims.sessionStartedAtEpochMillis())
            .isLessThanOrEqualTo(properties.getTenantSessionMaxAge());
        assertThat(jwtUtil.getTenantTokenRemainingTime(token))
            .isBetween(29 * 60_000L, 30 * 60_000L);
    }

    @Test
    void tenantTokenCannotBeIssuedAfterAbsoluteSessionDeadline() {
        long sessionStartedAt = System.currentTimeMillis()
            - properties.getTenantSessionMaxAge() - 1L;

        assertThatThrownBy(() -> jwtUtil.generateTenantToken(
            91L, 42L, "worker", "WAREHOUSE_STAFF",
            List.of("WAREHOUSE_STAFF"), 9L, sessionStartedAt))
            .isInstanceOf(JwtException.class)
            .hasMessageContaining("maximum age");
    }

    @Test
    void companyTokenCannotBeParsedByPlatformKeyOrAudience() {
        String token = jwtUtil.generateTenantToken(
            91L, 42L, "worker", "WAREHOUSE_STAFF", 9L);

        assertThatThrownBy(() -> jwtUtil.parsePlatformToken(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void wrongIssuerIsRejectedEvenWithSameSigningKey() {
        String token = jwtUtil.generateTenantToken(
            91L, 42L, "worker", "WAREHOUSE_STAFF", 9L);
        JwtProperties otherProperties = properties(
            "tenant-signing-key-0123456789-abcdef",
            "platform-signing-key-01234567-fedcba");
        otherProperties.setIssuer("Another-Issuer");
        JwtUtil other = initialized(otherProperties);

        assertThatThrownBy(() -> other.parseTenantToken(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void equalSigningKeysFailFast() {
        JwtProperties invalid = properties(
            "same-signing-key-0123456789012345678",
            "same-signing-key-0123456789012345678");

        assertThat(invalid.isSigningKeySeparationValid()).isFalse();
        assertThatThrownBy(() -> initialized(invalid))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be different");
    }

    private JwtUtil initialized(JwtProperties jwtProperties) {
        JwtUtil util = new JwtUtil(jwtProperties);
        util.initializeKeys();
        return util;
    }

    private JwtProperties properties(String tenantRawKey, String platformRawKey) {
        JwtProperties result = new JwtProperties();
        result.setTenantSecret(encoded(tenantRawKey));
        result.setPlatformSecret(encoded(platformRawKey));
        result.setExpiration(60_000L);
        result.setIssuer("BCWMS-Test");
        result.setTenantAudience("bcwms-tenant-api");
        result.setPlatformAudience("bcwms-platform-api");
        result.setRefreshThreshold(30_000L);
        result.setTenantSessionMaxAge(604_800_000L);
        return result;
    }

    private String encoded(String raw) {
        return Base64.getEncoder().encodeToString(
            raw.getBytes(StandardCharsets.UTF_8));
    }
}
