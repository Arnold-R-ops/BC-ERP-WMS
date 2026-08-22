package com.wms.system.platform.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "wms.platform-mfa")
public class PlatformMfaProperties {
    @NotBlank private String encryptionKey;
    @Positive private int challengeMinutes = 5;
    @Positive private int maxAttempts = 5;
    @Positive private int lockMinutes = 15;
}
