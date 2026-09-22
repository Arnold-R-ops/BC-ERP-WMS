package com.wms.system.tenant.security;

import com.wms.system.platform.repository.PlatformAccessGrantRepository;
import com.wms.system.tenant.model.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class PlatformTenantDataSafety {
    public static final String REASON_HEADER="X-Platform-Access-Reason";
    private final TenantSecurityStateService states;
    private final JdbcTemplate jdbc;
    private final PlatformAccessGrantRepository grants;
    public static String decodeReason(String value) {
        if(value==null) return null;
        try { return java.net.URLDecoder.decode(value,java.nio.charset.StandardCharsets.UTF_8).trim(); }
        catch(IllegalArgumentException invalid) { throw new TenantAccessException("PLATFORM_ACCESS_REASON_INVALID",400); }
    }
    public TenantSecurityStateService.State require(long tenant,String reason) {
        var state=states.require(tenant,TenantOperationPurpose.PLATFORM_READ,null);
        if(reason!=null && reason.length()>2000) throw new TenantAccessException("PLATFORM_ACCESS_REASON_INVALID",400);
        if(state.status()==TenantStatus.SUSPENDED && (reason==null || reason.isBlank()))
            throw new TenantAccessException("PLATFORM_SUSPENDED_ACCESS_REASON_REQUIRED",400);
        return state;
    }
    public Map<String,Object> auditContext(long tenant,String reason) {
        return auditDecodedContext(tenant,decodeReason(reason));
    }
    /** Persisted export reasons are already decoded; do not decode percent characters twice. */
    public Map<String,Object> auditDecodedContext(long tenant,String decoded) {
        var state=require(tenant,decoded);
        return Map.of("tenantStatus",state.status().name(),"suspensionKind",Objects.toString(state.suspensionKind(),"NONE"),"accessReason",Objects.toString(decoded,""));
    }
    public void requireWorkerExport(long actor,long tenant,String dataset,String reason) {
        require(tenant,reason);
        var roles=jdbc.queryForList("select r.role_code from platform_users u join platform_user_roles ur on ur.platform_user_id=u.id join platform_roles r on r.id=ur.platform_role_id where u.id=? and u.enabled",String.class,actor);
        if(roles.contains("PLATFORM_SUPER_ADMIN")) return;
        if((!roles.contains("PLATFORM_OPERATIONS_ADMIN") && !roles.contains("PLATFORM_TENANT_EXPORT"))
            || !grants.hasActive(actor,"EXPORT",tenant,dataset,OffsetDateTime.now()))
            throw new TenantAccessException("PLATFORM_EXPORT_AUTHORIZATION_REVOKED",403);
    }
}
