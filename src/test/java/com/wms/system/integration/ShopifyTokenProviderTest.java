package com.wms.system.integration;

import com.wms.system.entity.IntegrationConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShopifyTokenProvider 单元测试（P1 批次1）
 */
@ExtendWith(MockitoExtension.class)
class ShopifyTokenProviderTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ShopifyTokenProvider tokenProvider;

    private IntegrationConfig ccConfig;

    @BeforeEach
    void setUp() {
        ccConfig = IntegrationConfig.builder()
            .id(1L)
            .platform("SHOPIFY")
            .storeUrl("test-store.myshopify.com")
            .clientId("client-id-123")
            .clientSecret("shpss_secret")
            .isActive(true)
            .build();
    }

    private Map<String, Object> tokenResponse(String token, long expiresIn) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("access_token", token);
        resp.put("expires_in", expiresIn);
        resp.put("scope", "read_orders");
        return resp;
    }

    @Test
    void fetchesTokenViaClientCredentialsAndCaches() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
            .thenReturn(tokenResponse("shpat_fresh", 86400));

        String first = tokenProvider.resolveAccessToken(ccConfig);
        String second = tokenProvider.resolveAccessToken(ccConfig);

        assertThat(first).isEqualTo("shpat_fresh");
        assertThat(second).isEqualTo("shpat_fresh");
        // 命中缓存：令牌接口只被调用一次
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void refreshesAfterInvalidate() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
            .thenReturn(tokenResponse("shpat_one", 86400))
            .thenReturn(tokenResponse("shpat_two", 86400));

        assertThat(tokenProvider.resolveAccessToken(ccConfig)).isEqualTo("shpat_one");
        tokenProvider.invalidate(ccConfig.getId());
        assertThat(tokenProvider.resolveAccessToken(ccConfig)).isEqualTo("shpat_two");

        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void refreshesWhenTokenExpiringSoon() {
        // expires_in 低于安全窗口（300 秒）→ 下次调用即换新
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
            .thenReturn(tokenResponse("shpat_short", 10))
            .thenReturn(tokenResponse("shpat_renewed", 86400));

        assertThat(tokenProvider.resolveAccessToken(ccConfig)).isEqualTo("shpat_short");
        assertThat(tokenProvider.resolveAccessToken(ccConfig)).isEqualTo("shpat_renewed");
    }

    @Test
    void fallsBackToStaticTokenWithoutClientCredentials() {
        IntegrationConfig legacy = IntegrationConfig.builder()
            .id(2L)
            .platform("SHOPIFY")
            .storeUrl("legacy.myshopify.com")
            .accessToken("shpat_static")
            .build();

        assertThat(tokenProvider.resolveAccessToken(legacy)).isEqualTo("shpat_static");
        verify(restTemplate, times(0)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void throwsWhenNothingConfigured() {
        IntegrationConfig empty = IntegrationConfig.builder()
            .id(3L)
            .platform("SHOPIFY")
            .storeUrl("empty.myshopify.com")
            .build();

        assertThatThrownBy(() -> tokenProvider.resolveAccessToken(empty))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.INTEGRATION_CONFIG_NOT_FOUND);
    }

    @Test
    void throwsAuthFailedWhenEndpointReturnsNoToken() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
            .thenReturn(Map.of("error", "invalid_client"));

        assertThatThrownBy(() -> tokenProvider.resolveAccessToken(ccConfig))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_AUTH_FAILED);
    }

    @Test
    void throwsAuthFailedOnNetworkError() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> tokenProvider.resolveAccessToken(ccConfig))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_AUTH_FAILED);
    }
}
