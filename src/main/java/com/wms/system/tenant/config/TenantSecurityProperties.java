package com.wms.system.tenant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Getter @Setter @Component
@org.springframework.validation.annotation.Validated
@ConfigurationProperties(prefix = "wms.tenant-security")
public class TenantSecurityProperties {
    private boolean controlsEnabled = false;
    @jakarta.validation.constraints.Min(30) @jakarta.validation.constraints.Max(300)
    private int challengeSeconds = 300;
    @jakarta.validation.constraints.Positive
    private int inboxMaxEventsPerTenant = 10_000;
    @jakarta.validation.constraints.Positive
    private long inboxMaxBytesPerTenant = 104_857_600L;
    @jakarta.validation.constraints.Positive
    private long inboxMaxBytesGlobal = 1_073_741_824L;
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(16777216)
    private int webhookMaxBodyBytes = 1_048_576;
    @jakarta.validation.constraints.Positive
    private int webhookPerTenantPerMinute = 300;
    @jakarta.validation.constraints.Positive
    private int webhookGlobalPerMinute = 3_000;
    private List<Long> alertRecipientIds = List.of();
    private String credentialEncryptionKey;
}
