package com.wms.system.subscription.service;

import com.wms.system.subscription.model.LimitKind;
import com.wms.system.subscription.model.PlanLimit;
import com.wms.system.subscription.model.SubscriptionStatus;
import com.wms.system.subscription.model.TenantSubscription;
import com.wms.system.subscription.repository.PlanLimitRepository;
import com.wms.system.subscription.repository.TenantSubscriptionRepository;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionEntitlementServiceTest {

    private static final long COMPANY_ID = 42L;
    private static final long PLAN_ID = 7L;
    private static final String ENTITLEMENT = "INVENTORY_CORRECTION_APPROVAL";

    @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock private PlanLimitRepository planLimitRepository;

    private SubscriptionEntitlementService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionEntitlementService(
            tenantSubscriptionRepository,
            planLimitRepository
        );
        TenantContextHolder.set(new TenantContext(
            COMPANY_ID,
            "company-42",
            "company-42.example.test",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void activeAndFreeSubscriptionsGrantConfiguredEntitlement() {
        when(planLimitRepository.findAllByPlanId(PLAN_ID))
            .thenReturn(List.of(entitlement(ENTITLEMENT)));

        for (SubscriptionStatus status : List.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.FREE
        )) {
            when(tenantSubscriptionRepository.findByTenantId(COMPANY_ID))
                .thenReturn(Optional.of(subscription(status, null)));

            assertThat(service.hasEntitlement(ENTITLEMENT)).isTrue();
        }

        verify(planLimitRepository, times(2)).findAllByPlanId(PLAN_ID);
    }

    @Test
    void unexpiredTrialGrantsWildcardEntitlement() {
        when(tenantSubscriptionRepository.findByTenantId(COMPANY_ID))
            .thenReturn(Optional.of(subscription(
                SubscriptionStatus.TRIALING,
                OffsetDateTime.now().plusDays(1)
            )));
        when(planLimitRepository.findAllByPlanId(PLAN_ID))
            .thenReturn(List.of(entitlement(SubscriptionEntitlementService.ALL_SERVICES)));

        assertThat(service.hasEntitlement(ENTITLEMENT)).isTrue();
    }

    @Test
    void expiredOrUndatedTrialDoesNotGrantEntitlement() {
        for (OffsetDateTime trialEndsAt : java.util.Arrays.asList(
            OffsetDateTime.now().minusSeconds(1),
            null
        )) {
            when(tenantSubscriptionRepository.findByTenantId(COMPANY_ID))
                .thenReturn(Optional.of(subscription(
                    SubscriptionStatus.TRIALING,
                    trialEndsAt
                )));

            assertThat(service.hasEntitlement(ENTITLEMENT)).isFalse();
        }

        verifyNoInteractions(planLimitRepository);
    }

    @Test
    void terminalAndDelinquentStatusesDoNotGrantEntitlement() {
        for (SubscriptionStatus status : List.of(
            SubscriptionStatus.CANCELED,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.PAST_DUE
        )) {
            when(tenantSubscriptionRepository.findByTenantId(COMPANY_ID))
                .thenReturn(Optional.of(subscription(
                    status,
                    OffsetDateTime.now().plusDays(1)
                )));

            assertThat(service.hasEntitlement(ENTITLEMENT)).isFalse();
        }

        verifyNoInteractions(planLimitRepository);
    }

    private TenantSubscription subscription(
        SubscriptionStatus status,
        OffsetDateTime trialEndsAt
    ) {
        return TenantSubscription.builder()
            .tenantId(COMPANY_ID)
            .planId(PLAN_ID)
            .status(status)
            .trialEndsAt(trialEndsAt)
            .build();
    }

    private PlanLimit entitlement(String key) {
        return PlanLimit.builder()
            .planId(PLAN_ID)
            .limitKey(key)
            .limitKind(LimitKind.ENTITLEMENT)
            .enabledValue(true)
            .build();
    }
}
