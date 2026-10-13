package com.wms.system.tenant.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "wms.tenancy")
public class TenancyProperties {

    private boolean enabled = false;

    @NotBlank
    private String baseDomain = "bcwms.com";

    @NotBlank
    private String appHost = "app.bcwms.com";

    @NotBlank
    private String platformHost = "platform.bcwms.com";
    private boolean allowLocalDevelopmentHost = true;
    private boolean requireSafeDatabaseRole = false;
    private Set<String> reservedSlugs = new LinkedHashSet<>(Set.of(
        "www", "app", "api", "platform", "admin", "support", "mail",
        "static", "cdn", "status", "help"
    ));

    @AssertTrue(message = "Enabled tenancy requires a safe database role and forbids local host fallback")
    public boolean isSafeToEnable() {
        return !enabled || (requireSafeDatabaseRole && !allowLocalDevelopmentHost);
    }

    @AssertTrue(message = "Public host must be the base domain or its child; platform must be a distinct child")
    public boolean isHostTopologyValid() {
        String base = normalize(baseDomain);
        String app = normalize(appHost);
        String platform = normalize(platformHost);
        return validHostname(base) && validHostname(app) && validHostname(platform)
            && !app.equals(platform)
            && (app.equals(base) || app.endsWith("." + base))
            && platform.endsWith("." + base);
    }

    private static boolean validHostname(String value) {
        return value.length() <= 253 && value.contains(".")
            && java.util.Arrays.stream(value.split("\\.", -1)).allMatch(label ->
                label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void setBaseDomain(String value) { baseDomain = normalize(value); }
    public void setAppHost(String value) { appHost = normalize(value); }
    public void setPlatformHost(String value) { platformHost = normalize(value); }
}
