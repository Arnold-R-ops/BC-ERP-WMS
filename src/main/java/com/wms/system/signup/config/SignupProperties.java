package com.wms.system.signup.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Arrays;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "wms.signup")
public class SignupProperties {
    public enum DeliveryMode { DEVELOPMENT, SMTP }

    private final Environment environment;

    @NotBlank
    private String tokenPepper = "local-development-signup-pepper-change-before-production";
    private DeliveryMode deliveryMode = DeliveryMode.DEVELOPMENT;
    @NotBlank
    private String fromAddress = "no-reply@bcwms.com";
    @NotBlank
    private String fromName = "BCWMS";
    @Positive
    private int verificationCodeMinutes = 10;
    @Positive
    private int signupExpiryHours = 24;
    @Positive
    private int resendSeconds = 60;
    @Positive
    private int dailySendLimit = 10;
    @Positive
    private int maxAttempts = 5;
    @Positive
    private int handoffSeconds = 60;

    public SignupProperties(Environment environment) {
        this.environment = environment;
    }

    public Duration verificationTtl() { return Duration.ofMinutes(verificationCodeMinutes); }
    public Duration signupTtl() { return Duration.ofHours(signupExpiryHours); }
    public Duration resendDelay() { return Duration.ofSeconds(resendSeconds); }
    public Duration handoffTtl() { return Duration.ofSeconds(handoffSeconds); }

    @AssertTrue(message = "Production signup must use SMTP and a non-default token pepper")
    public boolean isProductionDeliverySafe() {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!production) return true;
        return deliveryMode == DeliveryMode.SMTP
            && !tokenPepper.startsWith("local-development-");
    }
}
