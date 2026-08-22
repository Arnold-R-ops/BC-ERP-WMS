package com.wms.system.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.security.MessageDigest;
import java.util.Base64;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    private String tenantSecret;

    @NotBlank
    private String platformSecret;

    @Positive
    private long expiration = 86_400_000L;

    @Positive
    private long platformExpiration = 43_200_000L;

    @NotBlank
    private String issuer = "WMS-System";

    @NotBlank
    private String tenantAudience = "bcwms-tenant-api";

    @NotBlank
    private String platformAudience = "bcwms-platform-api";

    @Positive
    private long refreshThreshold = 3_600_000L;

    /** Absolute tenant ERP sign-in window, preserved across token refreshes. */
    @Positive
    private long tenantSessionMaxAge = 604_800_000L;

    @AssertTrue(message = "Company and platform JWT signing keys must be different")
    public boolean isSigningKeySeparationValid() {
        if (tenantSecret == null || platformSecret == null) {
            return true;
        }
        try {
            byte[] tenantKey = Base64.getDecoder().decode(tenantSecret);
            byte[] platformKey = Base64.getDecoder().decode(platformSecret);
            return !MessageDigest.isEqual(tenantKey, platformKey);
        } catch (IllegalArgumentException ignored) {
            // JwtUtil reports the more precise invalid-Base64/key-length error.
            return true;
        }
    }

    @AssertTrue(message = "Company and platform JWT audiences must be different")
    public boolean isAudienceSeparationValid() {
        return tenantAudience == null || platformAudience == null
            || !tenantAudience.equals(platformAudience);
    }
}
