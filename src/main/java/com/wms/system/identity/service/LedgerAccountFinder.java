package com.wms.system.identity.service;

import com.wms.system.identity.model.MembershipStatus;
import com.wms.system.identity.repository.TenantMembershipRepository;
import com.wms.system.identity.repository.UserIdentityRepository;
import com.wms.system.subscription.service.SubscriptionEntitlementService;
import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantRepository;
import com.wms.system.tenant.service.TenantHostResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LedgerAccountFinder {
    private final UserIdentityRepository identities;
    private final TenantMembershipRepository memberships;
    private final TenantRepository tenants;
    private final TenantHostResolver hosts;
    private final TenancyProperties properties;
    private final SubscriptionEntitlementService subscriptions;

    @Transactional(readOnly = true)
    public Optional<String> find(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return identities.findByNormalizedEmail(normalized)
            .filter(LedgerIdentityAccess::eligible)
            .flatMap(identity -> {
                var links = memberships.findByIdentityIdAndStatusIn(identity.getId(), List.of(MembershipStatus.ACTIVE));
                if (links.size() != 1) return Optional.empty();
                return tenants.findById(links.get(0).getTenantId());
            })
            .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
            .filter(tenant -> subscriptions.hasActiveSubscription(tenant.getId()))
            .flatMap(tenant -> hosts.resolve(tenant.getSlug() + "." + properties.getBaseDomain())
                .filter(context -> context.surface() == RequestSurface.TENANT && tenant.getId().equals(context.tenantId())))
            .map(context -> "https://" + context.hostname() + "/login?email="
                + URLEncoder.encode(normalized, StandardCharsets.UTF_8));
    }
}
