package com.wms.system.tenant.context;

import com.wms.system.security.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the company boundary from server-controlled request state.
 *
 * <p>The Host-derived tenant context wins for unauthenticated flows such as
 * company login. For authenticated flows the JWT-backed principal must agree
 * with that Host. The company is never accepted from request parameters.</p>
 *
 * <p>The company {@code 1} fallback is limited to the current legacy/local
 * runtime while the global tenancy safety interlock remains locked. It must be
 * removed when the multi-company runtime switch is enabled.</p>
 */
public final class CompanyScope {

    public static final long LEGACY_COMPANY_ID = 1L;

    private CompanyScope() {
    }

    public static Long currentCompanyId() {
        TenantContext requestContext = TenantContextHolder.current().orElse(null);
        if (requestContext != null
                && (requestContext.surface() == RequestSurface.APP
                    || requestContext.surface() == RequestSurface.PLATFORM)) {
            throw new IllegalStateException(
                "Company-scoped data is not available on this request surface");
        }
        Long hostCompanyId = requestContext != null && requestContext.isTenantRequest()
            ? requestContext.tenantId()
            : null;

        Long principalCompanyId = companyIdFrom(
            SecurityContextHolder.getContext().getAuthentication());

        if (hostCompanyId != null && principalCompanyId != null
                && !hostCompanyId.equals(principalCompanyId)) {
            throw new IllegalStateException(
                "Authenticated company does not match request Host");
        }
        if (hostCompanyId != null) {
            return hostCompanyId;
        }
        if (principalCompanyId != null) {
            return principalCompanyId;
        }
        return LEGACY_COMPANY_ID;
    }

    public static Long companyIdFrom(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.getUser().getCompanyId();
        }
        return null;
    }
}
