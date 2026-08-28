package com.wms.system.service;

import com.wms.system.entity.SystemConfig;
import com.wms.system.entity.enums.PurchaseApprovalMode;
import com.wms.system.entity.enums.ShortReceiptClosureMode;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.SystemConfigRepository;
import com.wms.system.subscription.service.SubscriptionEntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseGovernancePolicyServiceTest {
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private SubscriptionEntitlementService entitlementService;

    private PurchaseGovernancePolicyService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseGovernancePolicyService(systemConfigRepository, entitlementService);
    }

    @Test
    void ordinaryTenantAlwaysUsesSimpleDirectPolicy() {
        when(systemConfigRepository.findByConfigKey(any())).thenReturn(Optional.empty());
        when(entitlementService.hasEntitlement(PurchaseGovernancePolicyService.APPROVAL_ENTITLEMENT))
            .thenReturn(false);

        var policy = service.getPolicy();

        assertThat(policy.getEffectivePurchaseApprovalMode()).isEqualTo(PurchaseApprovalMode.NONE);
        assertThat(policy.getEffectiveShortReceiptClosureMode())
            .isEqualTo(ShortReceiptClosureMode.DIRECT_WITH_REASON);
        assertThat(policy.getAvailablePurchaseApprovalModes()).containsExactly(PurchaseApprovalMode.NONE);
    }

    @Test
    void ordinaryTenantCannotEnableEnterpriseApproval() {
        when(entitlementService.hasEntitlement(PurchaseGovernancePolicyService.APPROVAL_ENTITLEMENT))
            .thenReturn(false);

        assertThatThrownBy(() -> service.updatePolicy(
            PurchaseApprovalMode.SIMPLE,
            ShortReceiptClosureMode.APPROVAL_REQUIRED,
            "enable governance",
            7L,
            "admin"
        )).isInstanceOf(BusinessException.class);
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void entitledTenantCanChooseModesIndependently() {
        when(entitlementService.hasEntitlement(PurchaseGovernancePolicyService.APPROVAL_ENTITLEMENT))
            .thenReturn(true);
        when(systemConfigRepository.findByConfigKeyForUpdate(any())).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var policy = service.updatePolicy(
            PurchaseApprovalMode.NONE,
            ShortReceiptClosureMode.APPROVAL_REQUIRED,
            "separate exception approval",
            7L,
            "admin"
        );

        assertThat(policy.getEffectivePurchaseApprovalMode()).isEqualTo(PurchaseApprovalMode.NONE);
        assertThat(policy.getEffectiveShortReceiptClosureMode())
            .isEqualTo(ShortReceiptClosureMode.APPROVAL_REQUIRED);
        verify(systemConfigRepository, times(3)).save(any(SystemConfig.class));
    }
}
