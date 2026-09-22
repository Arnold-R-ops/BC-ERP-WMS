package com.wms.system.tenant.security;

import com.wms.system.tenant.model.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantSecurityStateService {
    private final JdbcTemplate jdbc;

    public record State(long tenantId, TenantStatus status, long sessionVersion, String suspensionKind) { }

    public State read(long tenantId) {
        return jdbc.query("select id, status, session_version, suspension_kind from tenants where id = ?",
            (rs, row) -> new State(rs.getLong(1), TenantStatus.valueOf(rs.getString(2)), rs.getLong(3), rs.getString(4)), tenantId)
            .stream().findFirst().orElseThrow(() -> new TenantAccessException("TENANT_SERVICE_UNAVAILABLE", 410));
    }

    public State require(long tenantId, TenantOperationPurpose purpose, Long expectedVersion) {
        State state = read(tenantId);
        if (expectedVersion != null && state.sessionVersion() != expectedVersion)
            throw new TenantAccessException("AUTH_SESSION_INVALIDATED", 401);
        TenantAccessPolicy.require(state.status(), purpose);
        return state;
    }

    public long sessionVersionForIssue(long tenantId) {
        TenantExecutionScope scope = TenantExecutionScope.current();
        if (scope == null || scope.tenantId() != tenantId)
            throw new IllegalStateException("Token issuance requires tenant admission");
        State state = require(tenantId, scope.purpose(), scope.sessionVersion());
        scope.pin(state.sessionVersion());
        return state.sessionVersion();
    }
}
