package com.wms.system.tenant.web;

import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.model.TenantStatus;
import com.wms.system.tenant.service.TenantHostResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantResolutionFilterTest {

    private TenancyProperties properties;
    private TenantHostResolver resolver;
    private TenantResolutionFilter filter;

    @BeforeEach
    void setUp() {
        properties = new TenancyProperties();
        properties.setEnabled(true);
        resolver = mock(TenantHostResolver.class);
        filter = new TenantResolutionFilter(properties, resolver, new TenantRequestGate());
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantContextIsAvailableDownstreamAndAlwaysCleared() throws Exception {
        TenantContext context = activeContext(10L, "alpha");
        when(resolver.resolve("alpha.bcwms.com")).thenReturn(Optional.of(context));
        MockHttpServletRequest request = request("alpha.bcwms.com", "/api/inventory");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<TenantContext> observed = new AtomicReference<>();
        FilterChain chain = (req, res) -> observed.set(TenantContextHolder.requireTenant());

        filter.doFilter(request, response, chain);

        assertEquals(10L, observed.get().tenantId());
        assertTrue(TenantContextHolder.current().isEmpty());
    }

    @Test
    void contextIsClearedWhenDownstreamThrows() {
        TenantContext context = activeContext(10L, "alpha");
        when(resolver.resolve("alpha.bcwms.com")).thenReturn(Optional.of(context));
        MockHttpServletRequest request = request("alpha.bcwms.com", "/api/inventory");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new ServletException("boom"); };

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));
        assertTrue(TenantContextHolder.current().isEmpty());
    }

    @Test
    void consecutiveCompaniesOnSameThreadDoNotLeak() throws Exception {
        when(resolver.resolve("alpha.bcwms.com")).thenReturn(Optional.of(activeContext(10L, "alpha")));
        when(resolver.resolve("beta.bcwms.com")).thenReturn(Optional.of(activeContext(20L, "beta")));
        AtomicReference<Long> observed = new AtomicReference<>();
        FilterChain chain = (req, res) -> observed.set(TenantContextHolder.requireTenant().tenantId());

        filter.doFilter(request("alpha.bcwms.com", "/api/inventory"), new MockHttpServletResponse(), chain);
        assertEquals(10L, observed.get());
        filter.doFilter(request("beta.bcwms.com", "/api/inventory"), new MockHttpServletResponse(), chain);
        assertEquals(20L, observed.get());
        assertTrue(TenantContextHolder.current().isEmpty());
    }

    @Test
    void rejectsUnknownHostBeforeCallingChain() throws IOException, ServletException {
        when(resolver.resolve("evil.example")).thenReturn(Optional.empty());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("evil.example", "/api/inventory"), response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("TENANT_HOST_INVALID"));
        verifyNoInteractions(chain);
    }

    @Test
    void appHostCannotReachTenantApi() throws Exception {
        TenantContext app = new TenantContext(null, null, "app.bcwms.com", RequestSurface.APP, null);
        when(resolver.resolve("app.bcwms.com")).thenReturn(Optional.of(app));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("app.bcwms.com", "/api/inventory"), response, chain);

        assertEquals(404, response.getStatus());
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest request(String host, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServerName(host);
        request.setRequestURI(path);
        return request;
    }

    private TenantContext activeContext(Long id, String slug) {
        return new TenantContext(id, slug, slug + ".bcwms.com", RequestSurface.TENANT, TenantStatus.ACTIVE);
    }
}
