package com.wms.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.shopify.ShopifyOrdersResponse;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Shopify API 客户端
 *
 * 负责调用 Shopify Admin API。认证令牌由 ShopifyTokenProvider 解析
 * （client_credentials 自动换取缓存，或旧模式静态令牌），调用遇 401
 * 时自动失效缓存并重试一次（令牌可能刚过期）。
 *
 * API 文档：https://shopify.dev/docs/api/admin-rest
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version P1-B1（原 V3.9，批次1 接入令牌管理器与原始报文拉取）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyApiClient {

    private final RestTemplate restTemplate;
    private final ShopifyTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    /**
     * Shopify API 版本
     */
    private static final String API_VERSION = "2024-01";

    /**
     * 拉取订单（类型化结果，兼容既有调用方）
     */
    public ShopifyOrdersResponse fetchOrders(IntegrationConfig config) {
        String raw = fetchOrdersRaw(config);
        try {
            ShopifyOrdersResponse resp = objectMapper.readValue(raw, ShopifyOrdersResponse.class);
            if (resp == null || resp.getOrders() == null) {
                resp = new ShopifyOrdersResponse();
                resp.setOrders(java.util.Collections.emptyList());
            }
            return resp;
        } catch (Exception e) {
            log.error("Shopify 订单响应解析失败: storeUrl={}, error={}", config.getStoreUrl(), e.getMessage());
            throw new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
                Map.of("storeUrl", config.getStoreUrl(), "endpoint", "orders.json",
                       "error", "Response parse failed: " + e.getMessage()));
        }
    }

    /**
     * 拉取订单的原始 JSON 报文（批次1：先落库再处理的原料）
     *
     * 查询条件与既有逻辑一致：status=open&financial_status=paid
     */
    public String fetchOrdersRaw(IntegrationConfig config) {
        return get(config, "orders.json?status=open&financial_status=paid");
    }

    /**
     * 拉取店铺信息（配置连通性测试用，只读）
     */
    public Map<String, Object> fetchShopInfo(IntegrationConfig config) {
        String raw = get(config, "shop.json");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> shop = (Map<String, Object>) parsed.get("shop");
            return shop != null ? shop : Map.of();
        } catch (Exception e) {
            throw new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
                Map.of("storeUrl", config.getStoreUrl(), "endpoint", "shop.json",
                       "error", "Response parse failed: " + e.getMessage()));
        }
    }

    /**
     * 统一 GET：解析令牌 → 调用 → 401 时失效缓存重试一次 → 错误映射
     */
    private String get(IntegrationConfig config, String pathWithQuery) {
        try {
            return doGet(config, pathWithQuery);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                // 令牌可能刚过期：失效缓存换新后重试一次
                log.warn("Shopify 认证失败，换新令牌重试: storeUrl={}, status={}",
                    config.getStoreUrl(), e.getStatusCode().value());
                tokenProvider.invalidate(config.getId());
                try {
                    return doGet(config, pathWithQuery);
                } catch (HttpClientErrorException retryEx) {
                    throw mapClientError(config, pathWithQuery, retryEx);
                }
            }
            throw mapClientError(config, pathWithQuery, e);
        } catch (HttpServerErrorException e) {
            log.error("Shopify 服务器错误: storeUrl={}, status={}", config.getStoreUrl(), e.getStatusCode().value());
            throw new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
                Map.of("storeUrl", config.getStoreUrl(), "endpoint", pathWithQuery,
                       "statusCode", e.getStatusCode().value(), "error", e.getMessage()));
        } catch (RestClientException e) {
            log.error("Shopify API 调用失败: storeUrl={}, error={}", config.getStoreUrl(), e.getMessage());
            throw new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
                Map.of("storeUrl", config.getStoreUrl(), "endpoint", pathWithQuery, "error", e.getMessage()));
        }
    }

    private String doGet(IntegrationConfig config, String pathWithQuery) {
        String accessToken = tokenProvider.resolveAccessToken(config);
        String url = String.format("https://%s/admin/api/%s/%s", config.getStoreUrl(), API_VERSION, pathWithQuery);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("调用 Shopify API: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getBody() != null ? response.getBody() : "{}";
    }

    private BusinessException mapClientError(IntegrationConfig config, String path, HttpClientErrorException e) {
        int statusCode = e.getStatusCode().value();
        if (statusCode == 401 || statusCode == 403) {
            log.error("Shopify 认证失败: storeUrl={}, statusCode={}", config.getStoreUrl(), statusCode);
            return new BusinessException(ErrorKeys.SHOPIFY_AUTH_FAILED,
                Map.of("storeUrl", config.getStoreUrl(), "statusCode", statusCode));
        }
        if (statusCode == 429) {
            log.error("Shopify 速率限制: storeUrl={}", config.getStoreUrl());
            return new BusinessException(ErrorKeys.SHOPIFY_RATE_LIMIT,
                Map.of("storeUrl", config.getStoreUrl()));
        }
        log.error("Shopify API 客户端错误: storeUrl={}, statusCode={}, error={}",
            config.getStoreUrl(), statusCode, e.getMessage());
        return new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
            Map.of("storeUrl", config.getStoreUrl(), "endpoint", path,
                   "statusCode", statusCode, "error", e.getMessage()));
    }
}
