package com.wms.system.tenant.web;

import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.service.TenantHostResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenancyProperties properties;
    private final TenantHostResolver hostResolver;
    private final TenantRequestGate requestGate;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<TenantContext> resolved;
        try {
            resolved = hostResolver.resolve(request.getServerName());
        } catch (IllegalArgumentException exception) {
            rejectUnknownHost(response);
            return;
        }
        if (resolved.isEmpty()) {
            rejectUnknownHost(response);
            return;
        }

        TenantContext context = resolved.get();
        TenantRequestGate.GateDecision gateDecision = requestGate.decide(context, request);
        if (!gateDecision.allowed()) {
            reject(response, gateDecision.status(), gateDecision.errorKey());
            return;
        }

        TenantContextHolder.set(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void rejectUnknownHost(HttpServletResponse response) throws IOException {
        reject(response, HttpServletResponse.SC_BAD_REQUEST, "TENANT_HOST_INVALID");
    }

    private void reject(HttpServletResponse response, int status, String errorKey) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"errorKey\":\"" + errorKey + "\"}");
    }
}
