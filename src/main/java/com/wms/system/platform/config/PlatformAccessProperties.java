package com.wms.system.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.nio.file.Path;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "wms.platform-access")
public class PlatformAccessProperties {
    private Path exportDirectory = Path.of("data", "platform-exports");
    private int exportRetentionHours = 24;
    private int maxPageSize = 100;
}
