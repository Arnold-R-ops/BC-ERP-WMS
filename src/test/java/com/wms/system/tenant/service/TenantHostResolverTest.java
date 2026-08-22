package com.wms.system.tenant.service;

import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.TenantDomainRepository;
import com.wms.system.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantHostResolverTest {

    private TenantDomainRepository domainRepository;
    private TenantRepository tenantRepository;
    private TenantHostResolver resolver;

    @BeforeEach
    void setUp() {
        TenancyProperties properties = new TenancyProperties();
        properties.setAllowLocalDevelopmentHost(false);
        domainRepository = mock(TenantDomainRepository.class);
        tenantRepository = mock(TenantRepository.class);
        resolver = new TenantHostResolver(properties, domainRepository, tenantRepository);
    }

    @Test
    void resolvesAppAndPlatformSurfacesWithoutTenantLookup() {
        assertEquals(RequestSurface.APP, resolver.resolve("app.bcwms.com").orElseThrow().surface());
        assertEquals(RequestSurface.PLATFORM, resolver.resolve("platform.bcwms.com").orElseThrow().surface());
        verifyNoInteractions(domainRepository, tenantRepository);
    }

    @Test
    void resolvesOnlyRegisteredActiveTenantDomain() {
        TenantDomain domain = TenantDomain.builder()
            .tenantId(42L)
            .hostname("acme.bcwms.com")
            .domainType(TenantDomainType.PLATFORM_SUBDOMAIN)
            .status(TenantDomainStatus.ACTIVE)
            .build();
        Tenant tenant = Tenant.builder()
            .id(42L)
            .slug("acme")
            .status(TenantStatus.ACTIVE)
            .build();
        when(domainRepository.findByHostnameAndStatus("acme.bcwms.com", TenantDomainStatus.ACTIVE))
            .thenReturn(Optional.of(domain));
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant));

        var context = resolver.resolve("ACME.BCWMS.COM").orElseThrow();

        assertEquals(42L, context.tenantId());
        assertEquals("acme", context.slug());
        assertEquals(RequestSurface.TENANT, context.surface());
        assertEquals(TenantStatus.ACTIVE, context.tenantStatus());
    }

    @Test
    void rejectsUnknownMultiLevelAndReservedHosts() {
        assertTrue(resolver.resolve("unknown.example.com").isEmpty());
        assertTrue(resolver.resolve("a.b.bcwms.com").isEmpty());
        assertEquals(RequestSurface.APP, resolver.resolve("app.bcwms.com").orElseThrow().surface());
    }

    @Test
    void rejectsRegisteredPurgedTenant() {
        TenantDomain domain = TenantDomain.builder()
            .tenantId(42L)
            .hostname("gone.bcwms.com")
            .domainType(TenantDomainType.PLATFORM_SUBDOMAIN)
            .status(TenantDomainStatus.ACTIVE)
            .build();
        Tenant tenant = Tenant.builder().id(42L).slug("gone").status(TenantStatus.PURGED).build();
        when(domainRepository.findByHostnameAndStatus("gone.bcwms.com", TenantDomainStatus.ACTIVE))
            .thenReturn(Optional.of(domain));
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant));

        assertTrue(resolver.resolve("gone.bcwms.com").isEmpty());
    }

    @Test
    void rejectsPlatformSubdomainBoundToDifferentTenantSlug() {
        TenantDomain domain = TenantDomain.builder()
            .tenantId(42L)
            .hostname("acme.bcwms.com")
            .domainType(TenantDomainType.PLATFORM_SUBDOMAIN)
            .status(TenantDomainStatus.ACTIVE)
            .build();
        Tenant tenant = Tenant.builder().id(42L).slug("beta").status(TenantStatus.ACTIVE).build();
        when(domainRepository.findByHostnameAndStatus("acme.bcwms.com", TenantDomainStatus.ACTIVE))
            .thenReturn(Optional.of(domain));
        when(tenantRepository.findById(42L)).thenReturn(Optional.of(tenant));

        assertTrue(resolver.resolve("acme.bcwms.com").isEmpty());
    }

    @Test
    void ignoresForwardedHostBecauseResolverUsesOnlySuppliedServerHost() {
        assertTrue(resolver.resolve("attacker.example").isEmpty());
        verifyNoInteractions(domainRepository, tenantRepository);
    }

    @Test
    void rejectsHostValuesContainingPortsOrNonNumericSuffixes() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("app.bcwms.com:443"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("app.bcwms.com:anything"));
    }

    @Test
    void rejectsOneAndTwoCharacterTenantSlugs() {
        assertTrue(resolver.resolve("a.bcwms.com").isEmpty());
        assertTrue(resolver.resolve("ab.bcwms.com").isEmpty());
    }
}
