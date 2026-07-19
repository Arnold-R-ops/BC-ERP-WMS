package com.wms.system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.shopify.ShopifyOrdersResponse;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * ShopifyApiClient 单元测试
 *
 * P1 批次1 重写：客户端改为令牌管理器解析令牌 + 原始报文拉取 + 401 换令牌重试
 */
@ExtendWith(MockitoExtension.class)
class ShopifyApiClientTest {

    @Mock
    private org.springframework.web.client.RestTemplate restTemplate;

    @Mock
    private ShopifyTokenProvider tokenProvider;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ShopifyApiClient shopifyApiClient;

    private IntegrationConfig config;

    private static final String ORDERS_JSON =
        "{\"orders\":[{\"id\":123,\"name\":\"#1001\",\"email\":\"a@b.com\",\"line_items\":[]}]}";

    @BeforeEach
    void setUp() {
        config = IntegrationConfig.builder()
                .id(1L)
                .platform("SHOPIFY")
                .storeUrl("test-store.myshopify.com")
                .clientId("cid")
                .clientSecret("shpss_x")
                .isActive(true)
                .build();
        when(tokenProvider.resolveAccessToken(any())).thenReturn("shpat_test");
    }

    private void stubGet(String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    @Test
    void fetchOrders_Success() {
        stubGet(ORDERS_JSON);

        ShopifyOrdersResponse result = shopifyApiClient.fetchOrders(config);

        assertThat(result.getOrders()).hasSize(1);
        assertThat(result.getOrders().get(0).getId()).isEqualTo(123L);
        assertThat(result.getOrders().get(0).getName()).isEqualTo("#1001");
    }

    @Test
    void fetchOrdersRaw_ReturnsRawJson() {
        stubGet(ORDERS_JSON);

        assertThat(shopifyApiClient.fetchOrdersRaw(config)).isEqualTo(ORDERS_JSON);
    }

    @Test
    void fetchOrdersForReconciliationRaw_FollowsPaginationAndCombinesPages() throws Exception {
        HttpHeaders firstHeaders = new HttpHeaders();
        firstHeaders.set(HttpHeaders.LINK,
            "<https://test-store.myshopify.com/admin/api/2026-07/orders.json?page_info=next>; rel=\"next\"");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"orders\":[{\"id\":1}]}", firstHeaders, HttpStatus.OK))
            .thenReturn(ResponseEntity.ok("{\"orders\":[{\"id\":2}]}"));

        String raw = shopifyApiClient.fetchOrdersForReconciliationRaw(
            config,
            OffsetDateTime.parse("2026-07-16T00:00:00Z")
        );

        assertThat(objectMapper.readTree(raw).path("orders")).hasSize(2);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
        verify(restTemplate).exchange(
            eq("https://test-store.myshopify.com/admin/api/2026-07/orders.json?page_info=next"),
            eq(HttpMethod.GET), any(), eq(String.class));
    }

    @Test
    void fetchOrders_EmptyResponseBody() {
        stubGet("{}");

        ShopifyOrdersResponse result = shopifyApiClient.fetchOrders(config);

        assertThat(result.getOrders()).isEmpty();
    }

    @Test
    void unauthorized_InvalidatesTokenAndRetriesOnce_Success() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, new byte[0], null))
                .thenReturn(ResponseEntity.ok(ORDERS_JSON));

        ShopifyOrdersResponse result = shopifyApiClient.fetchOrders(config);

        assertThat(result.getOrders()).hasSize(1);
        verify(tokenProvider).invalidate(1L);
    }

    @Test
    void unauthorized_Twice_ThrowsAuthFailed() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_AUTH_FAILED);
        verify(tokenProvider).invalidate(1L);
    }

    @Test
    void rateLimit_ThrowsRateLimitError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_RATE_LIMIT);
    }

    @Test
    void serverError_ThrowsApiError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Boom",
                        HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_API_ERROR);
    }

    @Test
    void networkError_ThrowsApiError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_API_ERROR);
    }

    @Test
    void malformedJson_ThrowsApiError() {
        stubGet("not-json{{{");

        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_API_ERROR);
    }

    @Test
    void fetchShopInfo_Success() {
        stubGet("{\"shop\":{\"name\":\"Bubble Crush UK\",\"currency\":\"GBP\",\"iana_timezone\":\"Europe/London\"}}");

        var shop = shopifyApiClient.fetchShopInfo(config);

        assertThat(shop.get("name")).isEqualTo("Bubble Crush UK");
        assertThat(shop.get("currency")).isEqualTo("GBP");
    }
}
