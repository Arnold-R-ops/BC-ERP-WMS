package com.wms.system.integration;

import com.wms.system.dto.shopify.ShopifyOrderDto;
import com.wms.system.dto.shopify.ShopifyOrdersResponse;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ShopifyApiClient 单元测试
 *
 * @author WMS Team
 * @since 2026-02-05
 * @version 3.9 (Shopify Integration)
 */
@ExtendWith(MockitoExtension.class)
class ShopifyApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ShopifyApiClient shopifyApiClient;

    private IntegrationConfig config;

    @BeforeEach
    void setUp() {
        config = IntegrationConfig.builder()
                .platform("SHOPIFY")
                .storeUrl("test-store.myshopify.com")
                .accessToken("test-access-token")
                .isActive(true)
                .build();
    }

    @Test
    void fetchOrders_Success() {
        // Given
        ShopifyOrdersResponse mockResponse = new ShopifyOrdersResponse();
        ShopifyOrderDto order = new ShopifyOrderDto();
        order.setId(123L);
        order.setName("#1001");
        mockResponse.setOrders(Collections.singletonList(order));

        ResponseEntity<ShopifyOrdersResponse> responseEntity = ResponseEntity.ok(mockResponse);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>any(),
                eq(ShopifyOrdersResponse.class)
        )).thenReturn(responseEntity);

        // When
        ShopifyOrdersResponse result = shopifyApiClient.fetchOrders(config);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOrders()).hasSize(1);
        assertThat(result.getOrders().get(0).getId()).isEqualTo(123L);
        assertThat(result.getOrders().get(0).getName()).isEqualTo("#1001");
    }

    @Test
    void fetchOrders_EmptyResponse() {
        // Given
        ResponseEntity<ShopifyOrdersResponse> responseEntity = ResponseEntity.ok(null);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>any(),
                eq(ShopifyOrdersResponse.class)
        )).thenReturn(responseEntity);

        // When
        ShopifyOrdersResponse result = shopifyApiClient.fetchOrders(config);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOrders()).isEmpty();
    }

    @Test
    void fetchOrders_AuthenticationFailed_401() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>any(),
                eq(ShopifyOrdersResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        // When & Then
        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_AUTH_FAILED);
    }

    @Test
    void fetchOrders_AuthenticationFailed_403() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>any(),
                eq(ShopifyOrdersResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        // When & Then
        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_AUTH_FAILED);
    }

    @Test
    void fetchOrders_RateLimitExceeded_429() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>any(),
                eq(ShopifyOrdersResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        // When & Then
        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_RATE_LIMIT);
    }

    @Test
    void fetchOrders_ServerError_500() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>any(),
                eq(ShopifyOrdersResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_API_ERROR);
    }

    @Test
    void fetchOrders_ClientError_400() {
        // Given
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<String>>any(),
                eq(ShopifyOrdersResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // When & Then
        assertThatThrownBy(() -> shopifyApiClient.fetchOrders(config))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.SHOPIFY_API_ERROR);
    }
}

