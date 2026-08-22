package com.wms.system.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** One-time local bootstrap input for the first platform developer account. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wms.platform-bootstrap")
public class PlatformBootstrapProperties {
    private boolean enabled;
    private String email;
    private String password;
    private String displayName = "平台超级管理员";
}
