package com.wms.system.tenant.web;

import com.wms.system.security.JwtUtil;
import com.wms.system.security.TenantJwtClaims;
import com.wms.system.tenant.context.CompanyScope;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.security.*;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** State admission also applies to signed-company requests on the legacy local host. */
@Component
@RequiredArgsConstructor
public class TenantSecurityAdmissionFilter extends OncePerRequestFilter {
    public static final String VERIFIED_CLAIMS = TenantSecurityAdmissionFilter.class.getName() + ".claims";
    private final JwtUtil jwt;
    private final TenantSecurityStateService states;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/platform/") || path.startsWith("/api/webhooks/")) {
            chain.doFilter(request, response);
            return;
        }
        boolean login = path.equals("/api/auth/login");
        boolean handoff = path.equals("/public/v1/session-handoff/consume");
        boolean restricted = path.equals("/api/company-access/status");
        String header = request.getHeader("Authorization");
        TenantJwtClaims claims = null;
        if (!login && !handoff && header != null && header.startsWith("Bearer ")) {
            try { claims = jwt.parseTenantToken(header.substring(7)); }
            catch (JwtException | IllegalArgumentException invalid) {
                // Existing authentication filter preserves expiration/error semantics.
                chain.doFilter(request, response);
                return;
            }
        }
        if (claims == null && !login && !handoff) { chain.doFilter(request, response); return; }
        long tenantId = claims == null ? CompanyScope.currentCompanyId() : claims.companyId();
        var host = TenantContextHolder.current().orElse(null);
        if (host != null && host.isTenantRequest() && host.tenantId() != tenantId) {
            reject(response, new TenantAccessException("AUTH_TOKEN_INVALID", 401));
            return;
        }
        TenantOperationPurpose purpose = login || restricted ? TenantOperationPurpose.AUTHENTICATION : TenantOperationPurpose.BUSINESS;
        try (TenantExecutionScope scope = TenantExecutionScope.open(tenantId, purpose,
                claims == null ? null : claims.tenantSessionVersion())) {
            var state = states.require(tenantId, purpose, scope.sessionVersion());
            scope.pin(state.sessionVersion());
            if (claims != null) request.setAttribute(VERIFIED_CLAIMS, claims);
            chain.doFilter(request, response);
        } catch (TenantAccessException denied) {
            if (!response.isCommitted()) reject(response, denied);
        } finally {
            request.removeAttribute(VERIFIED_CLAIMS);
        }
    }

    private void reject(HttpServletResponse response, TenantAccessException denied) throws IOException {
        response.setStatus(denied.status());
        response.setContentType("application/json");
        response.getWriter().write("{\"errorKey\":\"" + denied.errorKey() + "\"}");
    }
}
