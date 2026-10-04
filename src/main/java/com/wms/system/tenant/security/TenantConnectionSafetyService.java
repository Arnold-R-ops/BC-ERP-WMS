package com.wms.system.tenant.security;

import com.wms.system.entity.IntegrationConfig;
import com.wms.system.integration.ShopifyTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Only tenant-specific safety state; no generic integration/task management. */
@Service @RequiredArgsConstructor
public class TenantConnectionSafetyService {
    private final JdbcTemplate jdbc;
    private final TenantSecurityStateService states;
    private final TenantCredentialCrypto crypto;
    private final org.springframework.beans.factory.ObjectProvider<ShopifyTokenProvider> tokenProvider;

    public void requireBusiness(IntegrationConfig config) {
        TenantExecutionScope scope = TenantExecutionScope.current();
        if (scope == null || scope.tenantId() != config.getCompanyId())
            throw new TenantAccessException("TENANT_EXECUTION_CONTEXT_REQUIRED", 403);
        states.require(scope.tenantId(), TenantOperationPurpose.BUSINESS, scope.sessionVersion());
        var rows = jdbc.queryForList("select * from tenant_security_connections where tenant_id=? and config_id=?",
            scope.tenantId(), config.getId());
        if (!Boolean.TRUE.equals(config.getIsActive()) || (!rows.isEmpty()
                && (!"CHECK_PASSED".equals(rows.get(0).get("state")) || !"PASSED".equals(rows.get(0).get("check_status")))))
            throw new TenantAccessException("TENANT_CONNECTION_BLOCKED", 423);
        if (!rows.isEmpty() && Boolean.TRUE.equals(rows.get(0).get("suspected_compromise"))) {
            var row = rows.get(0);
            if ((row.get("original_signing_fingerprint") != null && Objects.equals(row.get("original_signing_fingerprint"), crypto.fingerprint(config.getClientSecret())))
                || (row.get("original_access_fingerprint") != null && Objects.equals(row.get("original_access_fingerprint"), crypto.fingerprint(config.getAccessToken()))))
                throw new TenantAccessException("TENANT_COMPROMISED_CREDENTIAL_UNCHANGED", 423);
        }
    }

    public boolean trustedWebhook(long tenantId, long configId) {
        var rows = jdbc.queryForList("select s.*,r.signing_secret from tenant_security_connections s left join channel_webhook_routes r on r.tenant_id=s.tenant_id and r.integration_config_id=s.config_id where s.tenant_id=? and s.config_id=?", tenantId, configId);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("suspected_compromise"))) return true;
        var row = rows.get(0);
        return Set.of("REVOCATION_VERIFIED","ROTATION_VERIFIED").contains(row.get("remote_status"))
            && "PASSED".equals(row.get("check_status")) && row.get("signing_secret") != null
            && !Objects.equals(row.get("original_signing_fingerprint"),crypto.fingerprint((String)row.get("signing_secret")));
    }

    /** Called inside the pause transaction after its tenant row has been locked. */
    public void quarantine(long tenantId, String incident, List<Long> affectedIds) {
        crypto.requireConfigured();
        String previous = jdbc.queryForObject("select current_setting('app.company_id', true)", String.class);
        jdbc.queryForObject("select set_config('app.company_id',?,true)", String.class, Long.toString(tenantId));
        try {
            List<Map<String,Object>> configs = jdbc.queryForList("select id,is_active,api_key,client_secret,access_token from integration_configs where company_id=? order by id for update", tenantId);
            Set<Long> actualIds = new HashSet<>();
            configs.forEach(row -> actualIds.add(((Number)row.get("id")).longValue()));
            if (affectedIds != null && !actualIds.containsAll(affectedIds)) throw new TenantAccessException("TENANT_CONNECTION_NOT_FOUND", 404);
            for (var row : configs) {
                long configId = ((Number)row.get("id")).longValue();
                boolean compromised = affectedIds == null || affectedIds.contains(configId);
                jdbc.update("insert into tenant_security_connections(tenant_id,config_id,incident_id,was_enabled,suspected_compromise,state,remote_status) values(?,?,?,?,?,?,?) "
                    + "on conflict(tenant_id,config_id) do update set incident_id=excluded.incident_id,was_enabled=excluded.was_enabled,"
                    + "suspected_compromise=excluded.suspected_compromise,state=excluded.state,remote_status=excluded.remote_status,check_status='NOT_CHECKED',gap_status='PENDING',evidence=null,checked_by=null,checked_at=null,reconciliation_evidence=null,reconciliation_report_id=null,reconciled_at=null,credential_version=tenant_security_connections.credential_version+1",
                    tenantId, configId, incident, row.get("is_active"), compromised, compromised ? "RISK_PENDING" : "LOCAL_BLOCKED", compromised ? "NOT_VERIFIED" : "NOT_REQUIRED");
                jdbc.update("update integration_configs set api_key=?,client_secret=?,access_token=?,is_active=false where company_id=? and id=?",
                    crypto.protect((String)row.get("api_key")), crypto.protect((String)row.get("client_secret")), crypto.protect((String)row.get("access_token")), tenantId, configId);
                jdbc.update("update tenant_security_connections set original_signing_fingerprint=?,original_access_fingerprint=? where tenant_id=? and config_id=?",
                    crypto.fingerprint((String)row.get("client_secret")),crypto.fingerprint((String)row.get("access_token")),tenantId,configId);
                for (var route : jdbc.queryForList("select signing_secret from channel_webhook_routes where tenant_id=? and integration_config_id=?", tenantId, configId))
                    jdbc.update("update channel_webhook_routes set signing_secret=? where tenant_id=? and integration_config_id=?",
                        crypto.protect((String)route.get("signing_secret")), tenantId, configId);
                tokenProvider.getObject().invalidate(configId);
            }
        } finally { jdbc.queryForObject("select set_config('app.company_id',?,true)", String.class, previous == null ? "" : previous); }
    }

    /** Explicit human evidence, NOT an assertion that BCWMS automatically revoked/probed the provider. */
    public void recordReview(long tenant, String incident, long actor, TenantSecurityCommand.Draft draft) {
        if (draft.configId() == null || !Set.of("NOT_REQUIRED","REVOCATION_VERIFIED","ROTATION_VERIFIED","FAILED").contains(Objects.toString(draft.remoteStatus(), "")))
            throw new TenantAccessException("TENANT_CONNECTION_REVIEW_INVALID", 400);
        var rows = jdbc.queryForList("select * from tenant_security_connections where tenant_id=? and config_id=? and incident_id=? for update", tenant, draft.configId(), incident);
        if (rows.isEmpty()) throw new TenantAccessException("TENANT_CONNECTION_NOT_FOUND", 404);
        boolean compromised = Boolean.TRUE.equals(rows.get(0).get("suspected_compromise"));
        boolean riskCleared = !compromised || Set.of("REVOCATION_VERIFIED","ROTATION_VERIFIED").contains(draft.remoteStatus());
        boolean passed = Boolean.TRUE.equals(draft.connectionCheckPassed());
        if (passed && (!riskCleared || "FAILED".equals(draft.remoteStatus()))) throw new TenantAccessException("TENANT_CONNECTION_RISK_UNRESOLVED", 409);
        if (passed && compromised) {
            String previous=jdbc.queryForObject("select current_setting('app.company_id',true)",String.class);
            jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,Long.toString(tenant));
            try {
                var current=jdbc.queryForMap("select c.client_secret,c.access_token,r.signing_secret from integration_configs c left join channel_webhook_routes r on r.tenant_id=c.company_id and r.integration_config_id=c.id where c.company_id=? and c.id=?",tenant,draft.configId());
                var old=rows.get(0);
                if ((old.get("original_signing_fingerprint")!=null && (Objects.equals(old.get("original_signing_fingerprint"),crypto.fingerprint((String)current.get("client_secret")))
                        || current.get("signing_secret")==null || Objects.equals(old.get("original_signing_fingerprint"),crypto.fingerprint((String)current.get("signing_secret")))))
                    || (old.get("original_access_fingerprint")!=null && (current.get("access_token")==null || Objects.equals(old.get("original_access_fingerprint"),crypto.fingerprint((String)current.get("access_token"))))))
                    throw new TenantAccessException("TENANT_COMPROMISED_CREDENTIAL_UNCHANGED",409);
            } finally { jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,previous==null?"":previous); }
        }
        jdbc.update("update tenant_security_connections set remote_status=?,check_status=?,state=?,evidence=?,checked_by=?,checked_at=current_timestamp where tenant_id=? and config_id=?",
            draft.remoteStatus(), passed ? "PASSED" : "FAILED", !riskCleared ? "RISK_PENDING" : passed ? "CHECK_PASSED" : "REAUTH_REQUIRED",
            "MANUAL_EVIDENCE: " + draft.reason(), actor, tenant, draft.configId());
        if(!riskCleared || !passed) jdbc.update("insert into tenant_security_alerts(tenant_id,issue_key,summary,severity) values(?,?,?,2) on conflict(tenant_id,issue_key) where status='OPEN' do update set occurrence_count=tenant_security_alerts.occurrence_count+1,updated_at=current_timestamp",tenant,"CONNECTION_"+draft.configId()+"_REVIEW_FAILED","Connection check or remote credential verification failed; connection remains blocked.");
        if("ACTIVE".equals(jdbc.queryForObject("select status from tenants where id=?",String.class,tenant))) {
            String previous=jdbc.queryForObject("select current_setting('app.company_id',true)",String.class);
            jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,Long.toString(tenant));
            try {
                boolean enable=passed && riskCleared && Boolean.TRUE.equals(rows.get(0).get("was_enabled"));
                jdbc.update("update integration_configs set is_active=? where company_id=? and id=?",enable,tenant,draft.configId());
                jdbc.update("update channel_webhook_routes set active=? where tenant_id=? and integration_config_id=?",enable,tenant,draft.configId());
            } finally { jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,previous==null?"":previous); }
        }
    }

    public void requireRiskCleared(long tenant, String incident) {
        if (jdbc.queryForObject("select count(*) from tenant_security_connections where tenant_id=? and incident_id=? "
                + "and (check_status='NOT_CHECKED' or (suspected_compromise and remote_status not in ('REVOCATION_VERIFIED','ROTATION_VERIFIED')))", Long.class, tenant, incident) > 0)
            throw new TenantAccessException("TENANT_CONNECTION_RISK_UNRESOLVED", 409);
    }

    public void applyResume(long tenant) {
        String previous = jdbc.queryForObject("select current_setting('app.company_id',true)", String.class);
        jdbc.queryForObject("select set_config('app.company_id',?,true)", String.class, Long.toString(tenant));
        try {
            jdbc.update("update integration_configs c set is_active=s.was_enabled and s.state='CHECK_PASSED' and s.check_status='PASSED' "
                + "from tenant_security_connections s where s.tenant_id=? and c.company_id=s.tenant_id and c.id=s.config_id", tenant);
            jdbc.update("update channel_webhook_routes r set active=s.was_enabled and s.state='CHECK_PASSED' and s.check_status='PASSED' "
                + "from tenant_security_connections s where s.tenant_id=? and r.tenant_id=s.tenant_id and r.integration_config_id=s.config_id", tenant);
        } finally { jdbc.queryForObject("select set_config('app.company_id',?,true)", String.class, previous == null ? "" : previous); }
    }

    public void replaceCredentials(long tenant,String incident,TenantSecurityCommand.Draft draft) {
        var update=draft.credentials();
        if(draft.configId()==null || update==null || (update.clientSecret()==null && update.accessToken()==null)
            || (update.clientSecret()!=null && update.clientSecret().isBlank()) || (update.accessToken()!=null && update.accessToken().isBlank()))
            throw new TenantAccessException("TENANT_CREDENTIAL_UPDATE_INVALID",400);
        if(jdbc.queryForList("select config_id from tenant_security_connections where tenant_id=? and config_id=? and incident_id=? for update",tenant,draft.configId(),incident).isEmpty())
            throw new TenantAccessException("TENANT_CONNECTION_NOT_FOUND",404);
        String previous=jdbc.queryForObject("select current_setting('app.company_id',true)",String.class);
        jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,Long.toString(tenant));
        try {
            if(update.clientSecret()!=null) {
                jdbc.update("update integration_configs set client_secret=? where company_id=? and id=?",crypto.protect(update.clientSecret()),tenant,draft.configId());
                jdbc.update("update channel_webhook_routes set signing_secret=? where tenant_id=? and integration_config_id=?",crypto.protect(update.clientSecret()),tenant,draft.configId());
            }
            if(update.accessToken()!=null) jdbc.update("update integration_configs set access_token=? where company_id=? and id=?",crypto.protect(update.accessToken()),tenant,draft.configId());
            jdbc.update("update integration_configs set is_active=false where company_id=? and id=?",tenant,draft.configId());
            jdbc.update("update tenant_security_connections set credential_version=credential_version+1,state='LOCAL_BLOCKED',check_status='NOT_CHECKED',checked_at=null where tenant_id=? and config_id=?",tenant,draft.configId());
            tokenProvider.getObject().invalidate(draft.configId());
        } finally { jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,previous==null?"":previous); }
    }

    /** An existing missing-order report plus explicit human evidence for updates/cancellations/refunds. */
    public void recordReconciliation(long tenant,String incident,TenantSecurityCommand.Draft draft) {
        if(draft.configId()==null || draft.reportEventId()==null) throw new TenantAccessException("TENANT_RECONCILIATION_REPORT_REQUIRED",400);
        String previous=jdbc.queryForObject("select current_setting('app.company_id',true)",String.class);
        jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,Long.toString(tenant));
        try {
            var reports=jdbc.queryForList("select e.payload::text as payload from channel_raw_events e join integration_configs c on c.company_id=e.company_id and c.store_url=e.store_identifier join tenants t on t.id=c.company_id where e.id=? and e.company_id=? and c.id=? and e.source='RECONCILE' and e.event_type='RECONCILE_REPORT' and e.status='PROCESSED' and e.created_at>=t.security_resumed_at",draft.reportEventId(),tenant,draft.configId());
            if(reports.isEmpty()) throw new TenantAccessException("TENANT_RECONCILIATION_REPORT_INVALID",409);
            try {
                var report=new com.fasterxml.jackson.databind.ObjectMapper().readTree((String)reports.get(0).get("payload"));
                var paused=jdbc.queryForObject("select suspended_at from tenants where id=?",java.sql.Timestamp.class,tenant).toInstant();
                if(!report.has("missingOrderCount") || report.path("missingOrderCount").asInt(-1)!=0
                    || report.path("configId").asLong()!=draft.configId()
                    || java.time.OffsetDateTime.parse(report.path("updatedAtMin").asText()).toInstant().isAfter(paused))
                    throw new TenantAccessException("TENANT_RECONCILIATION_INCOMPLETE",409);
            } catch(TenantAccessException denied) { throw denied; }
            catch(Exception invalid) { throw new TenantAccessException("TENANT_RECONCILIATION_REPORT_INVALID",409); }
            int changed=jdbc.update("update tenant_security_connections set gap_status='RECONCILED',reconciliation_evidence=?,reconciliation_report_id=?,reconciled_at=current_timestamp where tenant_id=? and config_id=? and incident_id=? and state='CHECK_PASSED'", "MANUAL_EVIDENCE: "+draft.reason(),draft.reportEventId(),tenant,draft.configId(),incident);
            if(changed!=1) throw new TenantAccessException("TENANT_CONNECTION_BLOCKED",409);
            // Retain receipts, do not replay stale business changes or claim they were individually processed.
            jdbc.update("update tenant_webhook_inbox set state=case when state='PENDING' then 'REVIEW_REQUIRED' else state end,last_error='COVERED_BY_VERIFIED_RECONCILIATION' where tenant_id=? and config_id=? and state in ('PENDING','REVIEW_REQUIRED','EXPIRED') and first_received_at<=(select security_resumed_at from tenants where id=?)",tenant,draft.configId(),tenant);
        } finally { jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,previous==null?"":previous); }
    }
}
