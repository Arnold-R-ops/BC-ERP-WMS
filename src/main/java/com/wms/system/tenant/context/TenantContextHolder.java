package com.wms.system.tenant.context;

import java.util.Optional;
import java.util.function.Supplier;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static Optional<TenantContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static TenantContext requireTenant() {
        TenantContext context = CONTEXT.get();
        if (context == null || !context.isTenantRequest() || context.tenantId() == null) {
            throw new IllegalStateException("A tenant request context is required");
        }
        return context;
    }

    public static void set(TenantContext context) {
        if (CONTEXT.get() != null) {
            throw new IllegalStateException("Tenant context is already set for this thread");
        }
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static <T> T runWithTenant(TenantContext context, Supplier<T> action) {
        set(context);
        try {
            return action.get();
        } finally {
            clear();
        }
    }

    public static void runWithTenant(TenantContext context, Runnable action) {
        set(context);
        try {
            action.run();
        } finally {
            clear();
        }
    }

    /**
     * Replaces a non-tenant request surface after a trusted server-side route
     * has resolved the company (for example, after channel HMAC verification).
     * A tenant context can never be replaced by another tenant context.
     */
    public static <T> T runWithResolvedTenant(TenantContext context, Supplier<T> action) {
        if (context == null || !context.isTenantRequest() || context.tenantId() == null) {
            throw new IllegalArgumentException("A resolved tenant context is required");
        }
        TenantContext previous = CONTEXT.get();
        if (previous != null && previous.isTenantRequest()) {
            throw new IllegalStateException("An existing tenant context cannot be replaced");
        }
        CONTEXT.set(context);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}
