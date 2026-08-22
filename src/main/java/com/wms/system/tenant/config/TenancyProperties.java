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

    @AssertTrue(message = "wms.tenancy.enabled cannot be true before all isolation enforcement is ready")
    public boolean isSafeToEnable() {
        // Foundation-release safety interlock. Enabling tenancy must require a
        // deliberate code change after onboarding, platform-access APIs and
        // end-to-end production routing are complete; it cannot be bypassed by
        // a second environment variable.
        return !enabled;
    }

    @AssertTrue(message = "Tenant app/platform hosts must be distinct subdomains of the base domain")
    public boolean isHostTopologyValid() {
        String base = normalize(baseDomain);
        String app = normalize(appHost);
        String platform = normalize(platformHost);
        return !base.isBlank()
            && !app.equals(platform)
            && app.endsWith("." + base)
            && platform.endsWith("." + base);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
