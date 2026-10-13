package com.wms.system.identity.service;

import com.wms.system.entity.User;
import com.wms.system.identity.model.MembershipStatus;
import com.wms.system.identity.model.UserIdentity;
import com.wms.system.identity.repository.TenantMembershipRepository;
import com.wms.system.identity.repository.UserIdentityRepository;
import com.wms.system.repository.UserRepository;
import com.wms.system.subscription.service.SubscriptionEntitlementService;
import com.wms.system.tenant.config.TenancyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

/** Verified email is an alias; the existing tenant user remains the password authority. */
@Service
@RequiredArgsConstructor
public class LedgerIdentityAccess {
    private final UserIdentityRepository identities;
    private final TenantMembershipRepository memberships;
    private final UserRepository users;
    private final SubscriptionEntitlementService subscriptions;
    private final TenancyProperties tenancy;

    @Transactional(readOnly = true)
    public String resolveUsername(long tenantId, String identifier) {
        String value = identifier.trim();
        if (!value.contains("@")) return value;
        // Keep legacy usernames containing @ usable when no verified identity exists.
        var identity = identities.findByNormalizedEmail(value.toLowerCase(Locale.ROOT));
        if (identity.isEmpty()) return value;
        if (!eligible(identity.get())) throw new BadCredentialsException("Invalid credentials");
        var membership = memberships.findByIdentityIdAndTenantId(identity.get().getId(), tenantId)
            .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return users.findByIdAndCompanyId(membership.getTenantUserId(), tenantId)
            .map(User::getUsername)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    }

    @Transactional(readOnly = true)
    public void requireAccess(User user) {
        if (!tenancy.isEnabled()) return;
        if (!subscriptions.hasActiveSubscription(user.getCompanyId()))
            throw new DisabledException("Ledger access unavailable");
        // Pre-existing users without an identity link retain tenant-local login.
        memberships.findByTenantIdAndTenantUserId(user.getCompanyId(), user.getId()).ifPresent(m -> {
            if (m.getStatus() != MembershipStatus.ACTIVE
                || identities.findById(m.getIdentityId()).filter(LedgerIdentityAccess::eligible).isEmpty())
                throw new DisabledException("Ledger access unavailable");
        });
    }

    public static boolean eligible(UserIdentity identity) {
        return Boolean.TRUE.equals(identity.getEnabled()) && identity.getEmailVerifiedAt() != null;
    }
}
