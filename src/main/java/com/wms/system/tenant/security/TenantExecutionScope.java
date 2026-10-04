package com.wms.system.tenant.security;

import java.util.Objects;

/** Pins the admission version across ALL transactions of one request or job. */
public final class TenantExecutionScope implements AutoCloseable {
    private static final ThreadLocal<TenantExecutionScope> CURRENT = new ThreadLocal<>();
    private final TenantExecutionScope previous;
    private final long tenantId;
    private final TenantOperationPurpose purpose;
    private Long sessionVersion;

    private TenantExecutionScope(long tenantId, TenantOperationPurpose purpose, Long version) {
        if (tenantId <= 0 || (version != null && version < 1)) throw new IllegalArgumentException("Invalid tenant admission");
        previous = CURRENT.get();
        if (previous != null && previous.tenantId != tenantId) throw new IllegalStateException("Cross-tenant execution scope");
        if (previous != null && previous.sessionVersion != null) {
            if(version!=null && !version.equals(previous.sessionVersion)) throw new TenantAccessException("AUTH_SESSION_INVALIDATED",401);
            version=previous.sessionVersion;
        }
        this.tenantId = tenantId;
        this.purpose = Objects.requireNonNull(purpose);
        this.sessionVersion = version;
        CURRENT.set(this);
    }

    public static TenantExecutionScope open(long id, TenantOperationPurpose purpose, Long version) {
        return new TenantExecutionScope(id, purpose, version);
    }
    public static TenantExecutionScope current() { return CURRENT.get(); }
    public long tenantId() { return tenantId; }
    public TenantOperationPurpose purpose() { return purpose; }
    public Long sessionVersion() { return sessionVersion; }
    public void pin(long version) {
        if (sessionVersion != null && sessionVersion != version) throw new TenantAccessException("AUTH_SESSION_INVALIDATED", 401);
        sessionVersion = version;
    }
    @Override public void close() {
        if (CURRENT.get() != this) throw new IllegalStateException("Unbalanced tenant execution scope");
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
