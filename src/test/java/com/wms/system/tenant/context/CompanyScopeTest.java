package com.wms.system.tenant.context;

import com.wms.system.entity.User;
import com.wms.system.security.SecurityUser;
import com.wms.system.tenant.model.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyScopeTest {

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsMatchingHostAndAuthenticatedCompany() {
        TenantContextHolder.set(tenant(20L, "beta"));
        authenticate(20L);

        assertThat(CompanyScope.currentCompanyId()).isEqualTo(20L);
    }

    @Test
    void rejectsAuthenticatedCompanyThatDoesNotMatchHost() {
        TenantContextHolder.set(tenant(20L, "beta"));
        authenticate(10L);

        assertThatThrownBy(CompanyScope::currentCompanyId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not match request Host");
    }

    @Test
    void doesNotAcceptCompanyFromPlatformPrincipalOrRequestParameters() {
        TenantContextHolder.set(new TenantContext(
            null, null, "platform.bcwms.com", RequestSurface.PLATFORM, null));

        assertThatThrownBy(CompanyScope::currentCompanyId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not available on this request surface");
    }

    private void authenticate(Long companyId) {
        User user = User.builder()
            .id(7L)
            .username("operator")
            .password("unused")
            .enabled(true)
            .build();
        user.setCompanyId(companyId);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new SecurityUser(user), null, java.util.List.of()));
    }

    private TenantContext tenant(Long companyId, String slug) {
        return new TenantContext(
            companyId,
            slug,
            slug + ".bcwms.com",
            RequestSurface.TENANT,
            TenantStatus.ACTIVE
        );
    }
}
