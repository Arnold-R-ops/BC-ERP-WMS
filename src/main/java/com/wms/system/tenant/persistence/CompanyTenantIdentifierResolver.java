package com.wms.system.tenant.persistence;

import com.wms.system.tenant.context.CompanyScope;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import java.util.Map;

import static org.hibernate.cfg.MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER;

/**
 * Supplies Hibernate's discriminator tenant for every persistence session.
 *
 * <p>APP and PLATFORM requests deliberately receive an impossible company id.
 * They may query control-plane entities, but tenant-owned entities then return
 * no rows. Service-layer access through {@link CompanyScope} remains stricter
 * and throws for those surfaces.</p>
 */
public final class CompanyTenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    /** PostgreSQL BIGINT company identifiers are positive. */
    public static final long NO_COMPANY_ID = -1L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        TenantContext context = TenantContextHolder.current().orElse(null);
        if (context != null
                && (context.surface() == RequestSurface.APP
                    || context.surface() == RequestSurface.PLATFORM)) {
            return NO_COMPANY_ID;
        }
        return CompanyScope.currentCompanyId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(Long tenantId) {
        // There is intentionally no unfiltered/root Hibernate tenant.
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
