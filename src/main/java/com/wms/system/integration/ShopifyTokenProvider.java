package com.wms.system.integration;

import com.wms.system.entity.IntegrationConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shopify 访问令牌管理器（P1 批次1）
 *
 * 2026 起 Shopify dev dashboard 应用不再直接提供永久令牌，标准做法是
 * 用 Client ID + Client Secret 走 client_credentials 模式换取约 24 小时
 * 有效的访问令牌（批次0 已在真实店验证通过）。
 *
 * 职责：
 * - 按配置缓存令牌，过期前自动换新（提前 5 分钟视为过期）
 * - API 调用遇 401 时可主动失效缓存（invalidate），下次调用即换新
 * - 兼容旧模式：配置里只有静态 accessToken 时直接使用
 *
 * 缓存为进程内存（单实例部署足够）；多实例化时升级为 Redis（ROADMAP P3）。
 *
 * @author WMS Team
 * @since 2026-07-10
 * @version P1-B1 (Channel Integration Foundation)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyTokenProvider {

    /**
     * 提前视为过期的安全窗口（秒）：令牌剩余寿命低于此值就换新
     */
    private static final long EXPIRY_SAFETY_WINDOW_SECONDS = 300;

    private final RestTemplate restTemplate;

    private final ConcurrentHashMap<Long, CachedToken> cache = new ConcurrentHashMap<>();

    /**
     * 解析配置应使用的访问令牌。
     * 优先 client_credentials（有 clientId+clientSecret 时），否则回退静态令牌。
     */
    public String resolveAccessToken(IntegrationConfig config) {
        boolean hasClientCredentials = StringUtils.hasText(config.getClientId())
            && StringUtils.hasText(config.getClientSecret());

        if (!hasClientCredentials) {
            if (StringUtils.hasText(config.getAccessToken())) {
                return config.getAccessToken();
            }
            throw new BusinessException(ErrorKeys.INTEGRATION_CONFIG_NOT_FOUND,
                Map.of(
                    "platform", config.getPlatform(),
                    "configId", config.getId(),
                    "reason", "Neither client credentials nor static access token configured"
                ));
        }

        CachedToken cached = cache.get(config.getId());
        if (cached != null && !cached.isExpiringSoon()) {
            return cached.token();
        }

        synchronized (this) {
            cached = cache.get(config.getId());
            if (cached != null && !cached.isExpiringSoon()) {
                return cached.token();
            }
            CachedToken fresh = requestToken(config);
            cache.put(config.getId(), fresh);
            return fresh.token();
        }
    }

    /**
     * 主动失效缓存（API 调用返回 401 时调用，下一次 resolve 即换新令牌）
     */
    public void invalidate(Long configId) {
        cache.remove(configId);
        log.info("Shopify token cache invalidated: configId={}", configId);
    }

    private CachedToken requestToken(IntegrationConfig config) {
        String url = String.format("https://%s/admin/oauth/access_token", config.getStoreUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
            "grant_type", "client_credentials",
            "client_id", config.getClientId(),
            "client_secret", config.getClientSecret()
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);

            if (resp == null || !(resp.get("access_token") instanceof String token) || token.isBlank()) {
                throw new BusinessException(ErrorKeys.SHOPIFY_AUTH_FAILED,
                    Map.of("storeUrl", config.getStoreUrl(), "statusCode", 200,
                           "reason", "Token endpoint returned no access_token"));
            }

            long expiresIn = resp.get("expires_in") instanceof Number n ? n.longValue() : 86400L;
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);

            log.info("Shopify token obtained via client credentials: configId={}, store={}, expiresIn={}s",
                config.getId(), config.getStoreUrl(), expiresIn);

            return new CachedToken(token, expiresAt);

        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Shopify token request failed: store={}, error={}", config.getStoreUrl(), e.getMessage());
            throw new BusinessException(ErrorKeys.SHOPIFY_AUTH_FAILED,
                Map.of("storeUrl", config.getStoreUrl(), "statusCode", 0,
                       "reason", "Token request failed: " + e.getMessage()));
        }
    }

    record CachedToken(String token, Instant expiresAt) {
        boolean isExpiringSoon() {
            return Instant.now().plusSeconds(EXPIRY_SAFETY_WINDOW_SECONDS).isAfter(expiresAt);
        }
    }
}
