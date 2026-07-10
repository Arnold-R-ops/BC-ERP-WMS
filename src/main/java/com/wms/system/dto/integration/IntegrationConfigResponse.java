package com.wms.system.dto.integration;

import com.wms.system.entity.IntegrationConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 店铺集成配置响应（P1 批次1）
 *
 * 安全约定：密钥永不明文返回——clientSecret 只回前 8 位 + ***，
 * accessToken 只回是否配置的布尔标记。
 *
 * @author WMS Team
 * @since 2026-07-10
 * @version P1-B1 (Channel Integration Foundation)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationConfigResponse {

    public static final String AUTH_CLIENT_CREDENTIALS = "CLIENT_CREDENTIALS";
    public static final String AUTH_STATIC_TOKEN = "STATIC_TOKEN";
    public static final String AUTH_UNCONFIGURED = "UNCONFIGURED";

    private Long id;
    private String platform;
    private String storeUrl;
    private String clientId;

    /**
     * 脱敏后的 Client Secret（如 shpss_2fc***），仅供识别是否配置正确
     */
    private String clientSecretMasked;

    /**
     * 是否配置了静态令牌（旧模式）
     */
    private Boolean hasStaticToken;

    /**
     * 生效的认证模式：CLIENT_CREDENTIALS / STATIC_TOKEN / UNCONFIGURED
     */
    private String authMode;

    private Boolean isActive;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static IntegrationConfigResponse from(IntegrationConfig c) {
        boolean hasCc = StringUtils.hasText(c.getClientId()) && StringUtils.hasText(c.getClientSecret());
        boolean hasToken = StringUtils.hasText(c.getAccessToken());
        String mode = hasCc ? AUTH_CLIENT_CREDENTIALS : (hasToken ? AUTH_STATIC_TOKEN : AUTH_UNCONFIGURED);

        return IntegrationConfigResponse.builder()
            .id(c.getId())
            .platform(c.getPlatform())
            .storeUrl(c.getStoreUrl())
            .clientId(c.getClientId())
            .clientSecretMasked(mask(c.getClientSecret()))
            .hasStaticToken(hasToken)
            .authMode(mode)
            .isActive(c.getIsActive())
            .lastSyncAt(c.getLastSyncAt())
            .createdAt(c.getCreatedAt())
            .updatedAt(c.getUpdatedAt())
            .build();
    }

    private static String mask(String secret) {
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        return secret.substring(0, Math.min(9, secret.length())) + "***";
    }
}
