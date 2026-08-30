package com.wms.system.service.inventory;

import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.InventoryDispositionReviewMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryDispositionPolicyServiceTest {
    @Mock private SystemConfigRepository repository;
    private InventoryDispositionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new InventoryDispositionPolicyService(repository);
    }

    @Test
    void ordinaryTenantDefaultsToDirectDispositionAutomaticExpiryAndLondonTimezone() {
        when(repository.findByConfigKey(anyString())).thenReturn(Optional.empty());

        var policy = service.getPolicy();

        assertThat(policy.reviewMode()).isEqualTo(InventoryDispositionReviewMode.DIRECT_WITH_REASON);
        assertThat(policy.automaticExpiryEnabled()).isTrue();
        assertThat(policy.businessTimezone()).isEqualTo("Europe/London");
        assertThat(policy.supportedReviewModes()).containsExactly(
            InventoryDispositionReviewMode.DIRECT_WITH_REASON,
            InventoryDispositionReviewMode.APPROVAL_REQUIRED);
    }

    @Test
    void invalidConfiguredTimezoneFailsClosedInsteadOfSilentlyUsingServerTime() {
        when(repository.findByConfigKey(InventoryDispositionPolicyService.BUSINESS_TIMEZONE_KEY))
            .thenReturn(Optional.of(SystemConfig.builder()
                .configKey(InventoryDispositionPolicyService.BUSINESS_TIMEZONE_KEY)
                .configValue("UK/LONDON-NOT-A-ZONE")
                .configType("STRING")
                .build()));

        assertThatThrownBy(service::businessTimezone)
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorKey())
                    .isEqualTo(ErrorKeys.INVENTORY_DISPOSITION_POLICY_INVALID);
                assertThat(exception.getParam("field")).isEqualTo("businessTimezone");
            });
    }
}
