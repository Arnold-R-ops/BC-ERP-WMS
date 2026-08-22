package com.wms.system.tenant.service;

import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.model.TenantDomain;
import com.wms.system.tenant.model.TenantDomainStatus;
import com.wms.system.tenant.model.TenantDomainType;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.repository.TenantDomainRepository;
import com.wms.system.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.IDN;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TenantHostResolver {

    private static final Pattern SLUG_PATTERN = Pattern.compile(
        "[a-z0-9][a-z0-9-]{1,28}[a-z0-9]"
    );

    private final TenancyProperties properties;
    private final TenantDomainRepository tenantDomainRepository;
    private final TenantRepository tenantRepository;

    public Optional<TenantContext> resolve(String rawHost) {
        String host = normalizeHost(rawHost);

        if (host.equals(normalizeConfiguredHost(properties.getAppHost()))) {
            return Optional.of(new TenantContext(null, null, host, RequestSurface.APP, null));
        }
        if (host.equals(normalizeConfiguredHost(properties.getPlatformHost()))) {
            return Optional.of(new TenantContext(null, null, host, RequestSurface.PLATFORM, null));
        }
        if (properties.isAllowLocalDevelopmentHost()
            && (host.equals("localhost") || host.equals("127.0.0.1"))) {
            return Optional.of(new TenantContext(null, null, host, RequestSurface.LOCAL_DEVELOPMENT, null));
        }

        String suffix = "." + properties.getBaseDomain().toLowerCase(Locale.ROOT);
        if (!host.endsWith(suffix)) {
            return Optional.empty();
        }

        String slug = host.substring(0, host.length() - suffix.length());
        if (slug.contains(".") || !SLUG_PATTERN.matcher(slug).matches()
            || properties.getReservedSlugs().contains(slug)) {
            return Optional.empty();
        }

        TenantDomain domain = tenantDomainRepository
            .findByHostnameAndStatus(host, TenantDomainStatus.ACTIVE)
            .orElse(null);
        if (domain == null) {
            return Optional.empty();
        }

        return tenantRepository.findById(domain.getTenantId())
            .filter(tenant -> tenant.getStatus() != TenantStatus.PURGED)
            .filter(tenant -> domain.getDomainType() != TenantDomainType.PLATFORM_SUBDOMAIN
                || slug.equals(tenant.getSlug()))
            .map(tenant -> new TenantContext(
                tenant.getId(), tenant.getSlug(), host, RequestSurface.TENANT, tenant.getStatus()
            ));
    }

    String normalizeHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank() || rawHost.indexOf(',') >= 0
            || rawHost.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid Host header");
        }
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Host must not include a port");
        }
        String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
        if (!ascii.equals(host)) {
            throw new IllegalArgumentException("Internationalized hosts are not supported");
        }
        return ascii;
    }

    private String normalizeConfiguredHost(String configuredHost) {
        if (configuredHost == null) {
            return "";
        }
        return configuredHost.trim().toLowerCase(Locale.ROOT);
    }
}
