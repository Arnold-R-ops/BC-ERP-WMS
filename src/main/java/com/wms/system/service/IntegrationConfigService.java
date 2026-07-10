package com.wms.system.service;

import com.wms.system.dto.integration.IntegrationConfigRequest;
import com.wms.system.dto.integration.IntegrationConfigResponse;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.integration.ShopifyApiClient;
import com.wms.system.integration.ShopifyTokenProvider;
import com.wms.system.repository.IntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 店铺集成配置服务（P1 批次1）
 *
 * 告别手写 SQL：店铺配置的全生命周期管理 + 只读连通性测试。
 * 密钥处理约定：
 * - 更新时 clientSecret/accessToken 传空 = 保留原值
 * - 任何读取路径都只返回脱敏形态（见 IntegrationConfigResponse）
 * - 凭据变更后主动失效令牌缓存
 *
 * @author WMS Team
 * @since 2026-07-10
 * @version P1-B1 (Channel Integration Foundation)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationConfigService {

    private final IntegrationConfigRepository repository;
    private final ShopifyApiClient shopifyApiClient;
    private final ShopifyTokenProvider tokenProvider;

    @Transactional(readOnly = true)
    public List<IntegrationConfigResponse> list() {
        return repository.findAll().stream().map(IntegrationConfigResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public IntegrationConfigResponse get(Long id) {
        return IntegrationConfigResponse.from(load(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationConfigResponse create(IntegrationConfigRequest request) {
        IntegrationConfig config = IntegrationConfig.builder()
            .platform(StringUtils.hasText(request.getPlatform()) ? request.getPlatform() : "SHOPIFY")
            .storeUrl(normalizeStoreUrl(request.getStoreUrl()))
            .clientId(trimToNull(request.getClientId()))
            .clientSecret(trimToNull(request.getClientSecret()))
            .accessToken(trimToNull(request.getAccessToken()))
            .isActive(request.getIsActive() == null || request.getIsActive())
            .build();

        config = repository.save(config);
        log.info("Integration config created: id={}, platform={}, store={}",
            config.getId(), config.getPlatform(), config.getStoreUrl());
        return IntegrationConfigResponse.from(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationConfigResponse update(Long id, IntegrationConfigRequest request) {
        IntegrationConfig config = load(id);

        if (StringUtils.hasText(request.getPlatform())) {
            config.setPlatform(request.getPlatform());
        }
        if (StringUtils.hasText(request.getStoreUrl())) {
            config.setStoreUrl(normalizeStoreUrl(request.getStoreUrl()));
        }
        if (StringUtils.hasText(request.getClientId())) {
            config.setClientId(request.getClientId().trim());
        }
        // 密钥留空 = 保留原值（避免编辑界面每次都要重新粘贴）
        if (StringUtils.hasText(request.getClientSecret())) {
            config.setClientSecret(request.getClientSecret().trim());
        }
        if (StringUtils.hasText(request.getAccessToken())) {
            config.setAccessToken(request.getAccessToken().trim());
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }

        config = repository.save(config);
        // 凭据可能变更：失效令牌缓存，下次调用换新
        tokenProvider.invalidate(config.getId());
        log.info("Integration config updated: id={}, store={}", config.getId(), config.getStoreUrl());
        return IntegrationConfigResponse.from(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public IntegrationConfigResponse setActive(Long id, boolean active) {
        IntegrationConfig config = load(id);
        config.setIsActive(active);
        config = repository.save(config);
        log.info("Integration config {}: id={}, store={}",
            active ? "activated" : "deactivated", config.getId(), config.getStoreUrl());
        return IntegrationConfigResponse.from(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        IntegrationConfig config = load(id);
        if (Boolean.TRUE.equals(config.getIsActive())) {
            throw new BusinessException(ErrorKeys.OPERATION_NOT_ALLOWED,
                Map.of("operation", "Delete integration config",
                       "reason", "Deactivate the config before deleting it"));
        }
        repository.delete(config);
        tokenProvider.invalidate(id);
        log.info("Integration config deleted: id={}, store={}", id, config.getStoreUrl());
    }

    /**
     * 只读连通性测试：换令牌 + 调 shop.json，返回店铺概要。
     * 不写店铺任何数据，不触发订单同步。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> testConnection(Long id) {
        IntegrationConfig config = load(id);
        Map<String, Object> shop = shopifyApiClient.fetchShopInfo(config);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configId", config.getId());
        result.put("storeUrl", config.getStoreUrl());
        result.put("connected", true);
        result.put("shopName", shop.get("name"));
        result.put("domain", shop.get("domain"));
        result.put("currency", shop.get("currency"));
        result.put("timezone", shop.get("iana_timezone"));
        result.put("plan", shop.get("plan_display_name"));
        return result;
    }

    private IntegrationConfig load(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorKeys.INTEGRATION_CONFIG_NOT_FOUND,
                Map.of("platform", "ANY", "configId", id)));
    }

    /**
     * 归一化店铺域名：剥掉协议前缀和尾部斜杠（用户常整段粘贴 URL）
     */
    private String normalizeStoreUrl(String storeUrl) {
        return storeUrl.trim()
            .replaceFirst("^https?://", "")
            .replaceAll("/+$", "");
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
