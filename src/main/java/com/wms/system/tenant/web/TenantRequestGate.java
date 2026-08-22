package com.wms.system.tenant.web;

import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.model.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fail-closed routing policy between the unified app, platform and company
 * surfaces. This gate is intentionally independent from user permissions.
 */
@Component
public class TenantRequestGate {

    private static final Set<String> APP_EXACT_PATHS = Set.of(
        "/health/check",
        "/api/auth/health",
        "/api/auth/login",
        "/api/webhooks/shopify",
        "/public/v1/signup",
        "/public/v1/email-verification",
        "/public/v1/session-handoff"
    );

    public GateDecision decide(TenantContext context, HttpServletRequest request) {
        String path = request.getRequestURI();
        RequestSurface surface = context.surface();

        if (surface == RequestSurface.LOCAL_DEVELOPMENT) {
            return GateDecision.allow();
        }
        if (surface == RequestSurface.APP) {
            boolean allowed = APP_EXACT_PATHS.contains(path)
                || path.startsWith("/public/v1/signup/")
                || path.startsWith("/public/v1/email-verification/")
                || path.startsWith("/public/v1/session-handoff/");
            return allowed ? GateDecision.allow() : GateDecision.reject("REQUEST_SURFACE_FORBIDDEN", 404);
        }
        if (surface == RequestSurface.PLATFORM) {
            boolean allowed = path.equals("/health/check")
                || path.startsWith("/api/platform/");
            return allowed ? GateDecision.allow() : GateDecision.reject("REQUEST_SURFACE_FORBIDDEN", 404);
        }
        if (surface != RequestSurface.TENANT || context.tenantStatus() == null) {
            return GateDecision.reject("TENANT_CONTEXT_INVALID", 403);
        }

        TenantStatus status = context.tenantStatus();
        if (status == TenantStatus.ACTIVE) {
            if (path.equals("/public/v1/session-handoff/consume")) {
                return GateDecision.allow();
            }
            if (isNonTenantSurfacePath(path)) {
                return GateDecision.reject("REQUEST_SURFACE_FORBIDDEN", 404);
            }
            return GateDecision.allow();
        }
        if (status == TenantStatus.SUSPENDED) {
            boolean allowed = path.equals("/api/auth/login")
                || path.equals("/api/auth/health")
                || path.startsWith("/api/company-access/");
            return allowed ? GateDecision.allow() : GateDecision.reject("COMPANY_SUSPENDED", 423);
        }
        if (status == TenantStatus.PROVISIONING) {
            boolean allowed = path.equals("/api/company-provisioning/status")
                || path.equals("/api/auth/health");
            return allowed ? GateDecision.allow() : GateDecision.reject("COMPANY_PROVISIONING", 423);
        }
        return GateDecision.reject("COMPANY_CLOSED", 410);
    }

    private boolean isNonTenantSurfacePath(String path) {
        return path.equals("/public/v1/signup")
            || path.startsWith("/public/v1/signup/")
            || path.equals("/public/v1/email-verification")
            || path.startsWith("/public/v1/email-verification/")
            || path.equals("/public/v1/session-handoff")
            || path.startsWith("/public/v1/session-handoff/")
            || path.equals("/api/platform")
            || path.startsWith("/api/platform/");
    }

    public record GateDecision(boolean allowed, String errorKey, int status) {
        static GateDecision allow() {
            return new GateDecision(true, null, 200);
        }

        static GateDecision reject(String errorKey, int status) {
            return new GateDecision(false, errorKey, status);
        }
    }
}
