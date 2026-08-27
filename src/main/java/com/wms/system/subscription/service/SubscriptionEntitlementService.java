package com.wms.system.subscription.service;

import com.wms.system.subscription.model.LimitKind;
import com.wms.system.subscription.model.PlanLimit;
import com.wms.system.subscription.model.SubscriptionStatus;
import com.wms.system.subscription.model.TenantSubscription;
import com.wms.system.subscription.repository.PlanLimitRepository;
import com.wms.system.subscription.repository.TenantSubscriptionRepository;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Read-only tenant-side boundary for subscription feature entitlements.
 *
 * <p>Business code asks for a feature key and never branches on a commercial
 * plan code. The platform subscription domain remains responsible for deciding
 * which plan/version grants the key.</p>
 */
@Service
@RequiredArgsConstructor
public class SubscriptionEntitlementService {
    public static final String ALL_SERVICES = "ALL_SERVICES";

    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final PlanLimitRepository planLimitRepository;

    @Transactional(readOnly = true)
    public boolean hasEntitlement(String entitlementKey) {
        return tenantSubscriptionRepository.findByTenantId(CompanyScope.currentCompanyId())
            .filter(this::isEntitlementEligible)
            .map(subscription -> planLimitRepository.findAllByPlanId(subscription.getPlanId()))
            .orElseGet(java.util.List::of)
            .stream()
            .filter(limit -> limit.getLimitKind() == LimitKind.ENTITLEMENT)
            .filter(limit -> Boolean.TRUE.equals(limit.getEnabledValue()))
            .map(PlanLimit::getLimitKey)
            .anyMatch(key -> ALL_SERVICES.equals(key) || entitlementKey.equals(key));
    }

    private boolean isEntitlementEligible(TenantSubscription subscription) {
        SubscriptionStatus status = subscription.getStatus();
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.FREE) {
            return true;
        }
        return status == SubscriptionStatus.TRIALING
            && subscription.getTrialEndsAt() != null
            && subscription.getTrialEndsAt().isAfter(OffsetDateTime.now());
    }
}
