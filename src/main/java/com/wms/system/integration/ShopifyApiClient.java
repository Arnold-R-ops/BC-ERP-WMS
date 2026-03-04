package com.wms.system.integration;

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
 * Shopify API 瀹㈡埛绔?
 *
 * 璐熻矗璋冪敤 Shopify Admin API 鑾峰彇璁㈠崟鏁版嵁
 *
 * API 鏂囨。锛歨ttps://shopify.dev/docs/api/admin-rest
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyApiClient {

    private final RestTemplate restTemplate;

    /**
     * Shopify API 鐗堟湰
     */
    private static final String API_VERSION = "2024-01";

    /**
     * 鑾峰彇 Shopify 璁㈠崟鍒楄〃
     *
     * API 绔偣锛欸ET https://{store_url}/admin/api/{version}/orders.json
     * 鏌ヨ鍙傛暟锛歴tatus=open&financial_status=paid
     * 璇锋眰澶达細X-Shopify-Access-Token: {access_token}
     *
     * @param config 闆嗘垚閰嶇疆
     * @return Shopify 璁㈠崟鍝嶅簲
     * @throws BusinessException 褰?API 璋冪敤澶辫触鏃?
     */
    public ShopifyOrdersResponse fetchOrders(IntegrationConfig config) {
        String storeUrl = config.getStoreUrl();
        String accessToken = config.getAccessToken();

        // 鏋勫缓 API URL
        String url = String.format("https://%s/admin/api/%s/orders.json?status=open&financial_status=paid",
                storeUrl, API_VERSION);

        // 鏋勫缓璇锋眰澶?
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.info("璋冪敤 Shopify API: url={}", url);

            // 鍙戦€?GET 璇锋眰
            ResponseEntity<ShopifyOrdersResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    ShopifyOrdersResponse.class
            );

            ShopifyOrdersResponse ordersResponse = response.getBody();

            if (ordersResponse == null || ordersResponse.getOrders() == null) {
                log.warn("Shopify API 杩斿洖绌哄搷搴? storeUrl={}", storeUrl);
                ordersResponse = new ShopifyOrdersResponse();
                ordersResponse.setOrders(java.util.Collections.emptyList());
            }

            log.info("鎴愬姛鑾峰彇 Shopify 璁㈠崟: storeUrl={}, count={}", storeUrl, ordersResponse.getOrders().size());
            return ordersResponse;

        } catch (HttpClientErrorException e) {
            // 澶勭悊 4xx 閿欒
            int statusCode = e.getStatusCode().value();

            if (statusCode == 401 || statusCode == 403) {
                // 璁よ瘉澶辫触
                log.error("Shopify 璁よ瘉澶辫触: storeUrl={}, statusCode={}", storeUrl, statusCode);
                throw new BusinessException(ErrorKeys.SHOPIFY_AUTH_FAILED,
                        Map.of(
                                "storeUrl", storeUrl,
                                "statusCode", statusCode
                        ));
            } else if (statusCode == 429) {
                // 閫熺巼闄愬埗
                log.error("Shopify 閫熺巼闄愬埗: storeUrl={}", storeUrl);
                throw new BusinessException(ErrorKeys.SHOPIFY_RATE_LIMIT,
                        Map.of("storeUrl", storeUrl));
            } else {
                // 鍏朵粬瀹㈡埛绔敊璇?
                log.error("Shopify API 瀹㈡埛绔敊璇? storeUrl={}, statusCode={}, error={}",
                        storeUrl, statusCode, e.getMessage());
                throw new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
                        Map.of(
                                "storeUrl", storeUrl,
                                "endpoint", url,
                                "statusCode", statusCode,
                                "error", e.getMessage()
                        ));
            }

        } catch (HttpServerErrorException e) {
            // 澶勭悊 5xx 閿欒
            int statusCode = e.getStatusCode().value();
            log.error("Shopify 鏈嶅姟鍣ㄩ敊璇? storeUrl={}, statusCode={}, error={}",
                    storeUrl, statusCode, e.getMessage());
            throw new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
                    Map.of(
                            "storeUrl", storeUrl,
                            "endpoint", url,
                            "statusCode", statusCode,
                            "error", e.getMessage()
                    ));

        } catch (RestClientException e) {
            // 澶勭悊鍏朵粬 REST 瀹㈡埛绔紓甯革紙缃戠粶閿欒銆佽秴鏃剁瓑锛?
            log.error("Shopify API 璋冪敤澶辫触: storeUrl={}, error={}", storeUrl, e.getMessage(), e);
            throw new BusinessException(ErrorKeys.SHOPIFY_API_ERROR,
                    Map.of(
                            "storeUrl", storeUrl,
                            "endpoint", url,
                            "error", e.getMessage()
                    ));
        }
    }
}






