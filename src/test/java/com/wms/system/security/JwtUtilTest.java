package com.wms.system.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        String key = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901"
                        .getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtUtil, "SECRET_KEY", key);
        ReflectionTestUtils.setField(jwtUtil, "EXPIRATION_TIME", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "ISSUER", "WMS-Test");
    }

    @Test
    void tokenCarriesAndValidatesSecurityVersion() {
        String token = jwtUtil.generateTokenWithRoles(
                "worker",
                "WAREHOUSE_STAFF",
                List.of("WAREHOUSE_STAFF", "PURCHASER"),
                9L
        );

        assertThat(jwtUtil.extractSecurityVersion(token)).isEqualTo(9L);
        assertThat(jwtUtil.isTokenValid(token, "worker", 9L)).isTrue();
        assertThat(jwtUtil.isTokenValid(token, "worker", 10L)).isFalse();
    }
}
