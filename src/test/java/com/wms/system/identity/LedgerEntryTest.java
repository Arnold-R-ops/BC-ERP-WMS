package com.wms.system.identity;

import com.wms.system.identity.controller.LedgerEntryController;
import com.wms.system.identity.model.*;
import com.wms.system.identity.repository.*;
import com.wms.system.identity.service.*;
import com.wms.system.repository.UserRepository;
import com.wms.system.entity.User;
import com.wms.system.subscription.service.SubscriptionEntitlementService;
import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.TenantRepository;
import com.wms.system.tenant.service.TenantHostResolver;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LedgerEntryTest {
    UserIdentityRepository identities = mock(UserIdentityRepository.class);
    TenantMembershipRepository memberships = mock(TenantMembershipRepository.class);
    TenantRepository tenants = mock(TenantRepository.class);
    TenantHostResolver hosts = mock(TenantHostResolver.class);
    SubscriptionEntitlementService subscriptions = mock(SubscriptionEntitlementService.class);
    UserRepository users = mock(UserRepository.class);
    TenancyProperties properties = new TenancyProperties();
    UserIdentity identity = UserIdentity.builder().id(5L).normalizedEmail("a+b@example.com")
        .emailVerifiedAt(OffsetDateTime.now()).enabled(true).build();
    TenantMembership link = TenantMembership.builder().identityId(5L).tenantId(42L)
        .tenantUserId(7L).status(MembershipStatus.ACTIVE).build();
    LedgerAccountFinder finder;
    LedgerIdentityAccess access;

    @BeforeEach void setup() {
        properties.setBaseDomain("ledger.fongvo.com");
        properties.setEnabled(true);
        finder = new LedgerAccountFinder(identities, memberships, tenants, hosts, properties, subscriptions);
        access = new LedgerIdentityAccess(identities, memberships, users, subscriptions, properties);
        when(identities.findByNormalizedEmail("a+b@example.com")).thenReturn(Optional.of(identity));
        when(identities.findById(5L)).thenReturn(Optional.of(identity));
        when(memberships.findByIdentityIdAndStatusIn(5L, List.of(MembershipStatus.ACTIVE))).thenReturn(List.of(link));
        when(tenants.findById(42L)).thenReturn(Optional.of(Tenant.builder().id(42L).slug("acme").status(TenantStatus.ACTIVE).build()));
        when(subscriptions.hasActiveSubscription(42L)).thenReturn(true);
        when(hosts.resolve("acme.ledger.fongvo.com")).thenReturn(Optional.of(
            new TenantContext(42L, "acme", "acme.ledger.fongvo.com", RequestSurface.TENANT, TenantStatus.ACTIVE)));
    }
    @Test void returnsOnlyControlledHttpsLoginAndEncodedEmail() {
        assertEquals("https://acme.ledger.fongvo.com/login?email=a%2Bb%40example.com",
            finder.find(" A+B@EXAMPLE.COM ").orElseThrow());
        verifyNoInteractions(users);
    }
    @Test void unavailableAndAmbiguousAccountsNeverReturnDestinations() {
        assertTrue(finder.find("unknown@example.com").isEmpty());
        identity.setEnabled(false);
        assertTrue(finder.find("a+b@example.com").isEmpty());
        identity.setEnabled(true);
        when(memberships.findByIdentityIdAndStatusIn(5L, List.of(MembershipStatus.ACTIVE))).thenReturn(List.of(link, link));
        assertTrue(finder.find("a+b@example.com").isEmpty());
    }
    @Test void inactiveSubscriptionOrUnregisteredHostCannotBeDiscovered() {
        when(subscriptions.hasActiveSubscription(42L)).thenReturn(false);
        assertTrue(finder.find("a+b@example.com").isEmpty());
        when(subscriptions.hasActiveSubscription(42L)).thenReturn(true);
        when(hosts.resolve("acme.ledger.fongvo.com")).thenReturn(Optional.empty());
        assertTrue(finder.find("a+b@example.com").isEmpty());
    }
    @Test void emailAliasResolvesOnlyWithinSelectedTenantAndPreservesUserPassword() {
        User user = new User(); user.setUsername("owner-5"); user.setPassword("existing-password-hash");
        when(memberships.findByIdentityIdAndTenantId(5L, 42L)).thenReturn(Optional.of(link));
        when(users.findByIdAndCompanyId(7L, 42L)).thenReturn(Optional.of(user));
        assertEquals("owner-5", access.resolveUsername(42L, "A+B@example.com"));
        assertEquals("existing-password-hash", user.getPassword());
        assertThrows(BadCredentialsException.class, () -> access.resolveUsername(43L, "a+b@example.com"));
        assertEquals("legacy-user", access.resolveUsername(42L, "legacy-user"));
    }
    @Test void revokedMembershipDisabledIdentityAndExpiredSubscriptionInvalidateAccess() {
        User user = new User(); user.setCompanyId(42L); user.setId(7L);
        when(memberships.findByTenantIdAndTenantUserId(42L, 7L)).thenReturn(Optional.of(link));
        access.requireAccess(user);
        link.setStatus(MembershipStatus.REVOKED);
        assertThrows(DisabledException.class, () -> access.requireAccess(user));
        link.setStatus(MembershipStatus.ACTIVE); identity.setEnabled(false);
        assertThrows(DisabledException.class, () -> access.requireAccess(user));
        identity.setEnabled(true); when(subscriptions.hasActiveSubscription(42L)).thenReturn(false);
        assertThrows(DisabledException.class, () -> access.requireAccess(user));
    }
    @Test void legacyUserWithoutIdentityStillUsesTenantLocalAuthentication() {
        User user = new User(); user.setCompanyId(42L); user.setId(7L);
        assertDoesNotThrow(() -> access.requireAccess(user));
    }
    @Test void accountFinderLimitsAttemptsAndDoesNotCacheResults() {
        var controller = new LedgerEntryController(finder, properties);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        for (int i=0; i<20; i++) {
            var response = controller.find(new LedgerEntryController.FindAccount("unknown@example.com"), request);
            assertEquals(404, response.getStatusCode().value());
            assertEquals("no-store", response.getHeaders().getCacheControl());
        }
        var response = controller.find(new LedgerEntryController.FindAccount("unknown@example.com"), request);
        assertEquals(429, response.getStatusCode().value());
        assertEquals("60", response.getHeaders().getFirst("Retry-After"));
    }
    @Test void onlyLoopbackProxyCanSupplyRateLimitClientAddress() {
        var controller = new LedgerEntryController(finder, properties);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); request.addHeader("X-Real-IP","192.0.2.20");
        var query = new LedgerEntryController.FindAccount("unknown@example.com");
        for (int i=0;i<20;i++) assertEquals(404,controller.find(query,request).getStatusCode().value());
        assertEquals(429,controller.find(query,request).getStatusCode().value());
        request.removeHeader("X-Real-IP"); request.addHeader("X-Real-IP","192.0.2.21");
        assertEquals(404,controller.find(query,request).getStatusCode().value());
        request.setRemoteAddr("192.0.2.20");
        assertEquals(429,controller.find(query,request).getStatusCode().value());
    }
}
