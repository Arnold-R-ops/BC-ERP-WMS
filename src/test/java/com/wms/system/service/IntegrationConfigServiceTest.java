package com.wms.system.service;

import com.wms.system.dto.integration.IntegrationConfigRequest;
import com.wms.system.dto.integration.IntegrationConfigResponse;
import com.wms.system.entity.IntegrationConfig;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.integration.ShopifyApiClient;
import com.wms.system.integration.ShopifyTokenProvider;
import com.wms.system.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IntegrationConfigService 单元测试（P1 批次1）
 */
@ExtendWith(MockitoExtension.class)
class IntegrationConfigServiceTest {

    @Mock
    private IntegrationConfigRepository repository;

    @Mock
    private ShopifyApiClient shopifyApiClient;

    @Mock
    private ShopifyTokenProvider tokenProvider;

    @InjectMocks
    private IntegrationConfigService service;

    private IntegrationConfig existing;

    @BeforeEach
    void setUp() {
        existing = IntegrationConfig.builder()
            .id(1L)
            .platform("SHOPIFY")
            .storeUrl("old-store.myshopify.com")
            .clientId("old-client-id")
            .clientSecret("shpss_old_secret_value")
            .isActive(true)
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNormalizesStoreUrl() {
        when(repository.save(any(IntegrationConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        IntegrationConfigRequest request = IntegrationConfigRequest.builder()
            .storeUrl("https://new-store.myshopify.com/")
            .clientId("cid")
            .clientSecret("shpss_new")
            .build();

        IntegrationConfigResponse response = service.create(request);

        assertThat(response.getStoreUrl()).isEqualTo("new-store.myshopify.com");
        assertThat(response.getAuthMode()).isEqualTo(IntegrationConfigResponse.AUTH_CLIENT_CREDENTIALS);
        assertThat(response.getRetailMode()).isFalse();
    }

    @Test
    void responseMasksSecretAndNeverExposesToken() {
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        IntegrationConfigResponse response = service.get(1L);

        assertThat(response.getClientSecretMasked()).isEqualTo("shpss_old***");
        assertThat(response.getClientSecretMasked()).doesNotContain("secret_value");
    }

    @Test
    void updateKeepsSecretWhenBlank() {
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(IntegrationConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        IntegrationConfigRequest request = IntegrationConfigRequest.builder()
            .storeUrl("new-store.myshopify.com")
            .clientSecret("")   // 留空 = 保留原值
            .build();

        service.update(1L, request);

        ArgumentCaptor<IntegrationConfig> captor = ArgumentCaptor.forClass(IntegrationConfig.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getClientSecret()).isEqualTo("shpss_old_secret_value");
        assertThat(captor.getValue().getStoreUrl()).isEqualTo("new-store.myshopify.com");
        // 凭据可能变化：令牌缓存必须失效
        verify(tokenProvider).invalidate(1L);
    }

    @Test
    void deleteRequiresDeactivationFirst() {
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.OPERATION_NOT_ALLOWED);

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteWorksWhenInactive() {
        existing.setIsActive(false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(repository).delete(existing);
        verify(tokenProvider).invalidate(1L);
    }

    @Test
    void getNotFoundThrows() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorKey", ErrorKeys.INTEGRATION_CONFIG_NOT_FOUND);
    }

    @Test
    void testConnectionReturnsShopSummary() {
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(shopifyApiClient.fetchShopInfo(existing)).thenReturn(Map.of(
            "name", "Bubble Crush UK",
            "currency", "GBP",
            "iana_timezone", "Europe/London",
            "plan_display_name", "Basic"
        ));

        Map<String, Object> result = service.testConnection(1L);

        assertThat(result.get("connected")).isEqualTo(true);
        assertThat(result.get("shopName")).isEqualTo("Bubble Crush UK");
        assertThat(result.get("currency")).isEqualTo("GBP");
    }

    @Test
    void superAdminCanEnableRetailMode() {
        authenticateAs("SUPER_ADMIN");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(IntegrationConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        IntegrationConfigRequest request = IntegrationConfigRequest.builder()
            .retailMode(true)
            .build();

        IntegrationConfigResponse response = service.update(1L, request);

        assertThat(response.getRetailMode()).isTrue();
        assertThat(existing.getRetailMode()).isTrue();
    }

    @Test
    void nonSuperAdminCannotChangeRetailMode() {
        authenticateAs("system:admin");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        IntegrationConfigRequest request = IntegrationConfigRequest.builder()
            .retailMode(true)
            .build();

        assertThatThrownBy(() -> service.update(1L, request))
            .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(IntegrationConfig.class));
    }

    private void authenticateAs(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))
            )
        );
    }
}
