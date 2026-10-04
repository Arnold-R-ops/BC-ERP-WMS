package com.wms.system.tenant.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.platform.service.*;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.config.TenantSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PlatformTenantSecurityService {
    private final PlatformAccessGuard guard;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final PlatformMfaCrypto mfa;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final PlatformAuditService audit;
    private final TenantSecurityProperties properties;
    private final TenantConnectionSafetyService connections;
    private final TenantCredentialCrypto credentials;

    public Map<String,Object> result(long tenant,String key,HttpServletRequest http) {
        var actor=guard.requireSuperAdmin();
        if(key==null || key.length()>100) throw denied("TENANT_SECURITY_RECORD_NOT_FOUND",404);
        var row=one("select response_json from tenant_security_commands where actor_id=? and tenant_id=? and idempotency_key=?",actor.getId(),tenant,key);
        audit.record(actor.getId(),tenant,"READ","tenant_security_command_result","SUCCESS",Map.of("idempotencyKey",key),http);
        return readJson((String)row.get("response_json"));
    }

    public Map<String,Object> overview(long tenantId, HttpServletRequest http) {
        var actor = guard.requireSuperAdmin();
        Map<String,Object> tenant = one("select id,status,session_version,suspension_kind,suspended_at,security_incident_id,suspension_reason from tenants where id=?", tenantId);
        Map<String,Object> result = new LinkedHashMap<>(tenant);
        result.put("controlsEnabled", properties.isControlsEnabled());
        result.put("actorId", actor.getId());
        result.put("requests", jdbc.queryForList("select * from tenant_security_requests where tenant_id=? order by created_at desc limit 30", tenantId));
        result.put("connections", jdbc.queryForList("select * from tenant_security_connections where tenant_id=? order by config_id", tenantId));
        result.put("alerts", jdbc.queryForList("select id,issue_key,summary,status,mail_status,occurrence_count,created_at,updated_at from tenant_security_alerts where tenant_id=? order by id desc limit 30", tenantId));
        result.put("webhookSummary", jdbc.queryForList("select state,count(*) as event_count from tenant_webhook_inbox where tenant_id=? group by state", tenantId));
        audit.record(actor.getId(), tenantId, "READ", "tenant_security", "SUCCESS", Map.of(), http);
        return result;
    }

    public Map<String,Object> challenge(long tenant, TenantSecurityCommand.Draft draft, HttpServletRequest http) {
        requireEnabled();
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        String fingerprint = fingerprint(tenant, draft);
        return tx().execute(status -> {
            Map<String,Object> actor = lockActor(principal);
            Map<String,Object> target = one("select * from tenants where id=? for update", tenant);
            validateDraft(target, principal.getId(), draft);
            if (jdbc.queryForObject("select count(*) from tenant_security_challenges where actor_id=? and consumed_at is null and expires_at>current_timestamp", Long.class, principal.getId()) >= 30)
                throw denied("TENANT_SECURITY_CHALLENGE_LIMIT", 429);
            String raw = mfa.newChallengeToken();
            Instant expires = Instant.now().plusSeconds(Math.max(30, Math.min(300, properties.getChallengeSeconds())));
            jdbc.update("insert into tenant_security_challenges(token_hash,tenant_id,actor_id,actor_security_version,tenant_session_version,context_hash,expires_at) values(?,?,?,?,?,?,?)",
                mfa.hashToken(raw), tenant, principal.getId(), actor.get("security_version"), target.get("session_version"), fingerprint, OffsetDateTime.ofInstant(expires, ZoneOffset.UTC));
            audit.recordInCurrentTransaction(principal.getId(), tenant, "OPERATION_REQUESTED", "tenant_security_challenge", "REQUESTED", Map.of("action", draft.action()), http);
            return Map.of("challengeToken", raw, "expiresAt", expires.toString());
        });
    }

    public Map<String,Object> execute(long tenant, TenantSecurityCommand.Execute command, HttpServletRequest http) {
        requireEnabled();
        PlatformSecurityUser principal = guard.requireSuperAdmin();
        String fingerprint = fingerprint(tenant, command.draft());
        Execution result = tx().execute(status -> {
            Map<String,Object> actor = lockActor(principal);
            var completed = jdbc.queryForList("select context_hash,response_json from tenant_security_commands where actor_id=? and idempotency_key=?", principal.getId(), command.idempotencyKey());
            if (!completed.isEmpty()) {
                if (!fingerprint.equals(completed.get(0).get("context_hash"))) throw denied("TENANT_SECURITY_IDEMPOTENCY_CONFLICT", 409);
                return new Execution(readJson((String)completed.get(0).get("response_json")), null);
            }
            Map<String,Object> target = one("select * from tenants where id=? for update", tenant);
            var challenge = one("select * from tenant_security_challenges where token_hash=? for update", mfa.hashToken(command.challengeToken()));
            if (number(challenge,"actor_id") != principal.getId() || number(challenge,"tenant_id") != tenant
                || !fingerprint.equals(challenge.get("context_hash")) || challenge.get("consumed_at") != null
                || number(challenge,"tenant_session_version") != number(target,"session_version")
                || number(challenge,"actor_security_version") != number(actor,"security_version")
                || !instant(challenge.get("expires_at")).isAfter(Instant.now()) || number(challenge,"attempt_count") >= 5)
                throw denied("TENANT_SECURITY_CHALLENGE_INVALID", 409);
            validateDraft(target, principal.getId(), command.draft());
            boolean verified = passwords.matches(command.password(), (String)actor.get("password_hash"))
                && mfa.verifyTotp(mfa.decrypt((String)actor.get("mfa_secret_encrypted")), command.code(), Instant.now());
            if (!verified) {
                jdbc.update("update platform_users set mfa_failed_attempts=least(5,mfa_failed_attempts+1),mfa_locked_until=case when mfa_failed_attempts+1>=5 then current_timestamp+interval '15 minutes' else mfa_locked_until end where id=?", principal.getId());
                jdbc.update("update tenant_security_challenges set attempt_count=least(5,attempt_count+1) where token_hash=?", challenge.get("token_hash"));
                audit.recordInCurrentTransaction(principal.getId(), tenant, "MFA_FAILED", "tenant_security", "FAILED", Map.of("action", command.draft().action()), http);
                // Persist throttling without committing any lifecycle transition.
                return new Execution(null, denied("TENANT_SECURITY_REAUTHENTICATION_FAILED", 403));
            }
            jdbc.update("update platform_users set mfa_failed_attempts=0,mfa_locked_until=null where id=?", principal.getId());
            Map<String,Object> response = apply(tenant, target, principal.getId(), command.draft());
            jdbc.update("update tenant_security_challenges set consumed_at=current_timestamp where token_hash=?", challenge.get("token_hash"));
            jdbc.update("insert into tenant_security_commands(actor_id,idempotency_key,tenant_id,context_hash,response_json) values(?,?,?,?,?)",
                principal.getId(), command.idempotencyKey(), tenant, fingerprint, writeJson(response));
            audit.recordInCurrentTransaction(principal.getId(), tenant, "OPERATION_EXECUTED", "tenant_security", "SUCCESS",
                Map.of("action", command.draft().action(), "reason", command.draft().reason(), "idempotencyKey", command.idempotencyKey(), "result", response), http);
            return new Execution(response, null);
        });
        if (result == null) throw new IllegalStateException("Missing command outcome");
        if (result.error() != null) throw result.error();
        return result.response();
    }

    private Map<String,Object> apply(long tenant, Map<String,Object> target, long actor, TenantSecurityCommand.Draft draft) {
        String incident = Objects.toString(target.get("security_incident_id"), "");
        String requestId = draft.requestId();
        switch (draft.action()) {
            case SUSPEND -> {
                incident = UUID.randomUUID().toString();
                jdbc.update("update tenants set status='SUSPENDED',session_version=session_version+1,version=version+1,suspension_kind='SECURITY',security_incident_id=?,suspension_reason=?,suspended_at=current_timestamp,updated_at=current_timestamp where id=?",
                    incident, draft.reason(), tenant);
                connections.quarantine(tenant, incident, draft.affectedConnectionIds());
            }
            case REQUEST_RESUME -> {
                connections.requireRiskCleared(tenant, incident);
                requestId = UUID.randomUUID().toString();
                jdbc.update("insert into tenant_security_requests(public_id,tenant_id,incident_id,tenant_session_version,initiator_id,status,reason) values(?,?,?,?,?,'PENDING',?)",
                    requestId, tenant, incident, target.get("session_version"), actor, draft.reason());
            }
            case APPROVE_RESUME -> {
                connections.requireRiskCleared(tenant, incident);
                // Approval and ACTIVE are one commit: no approved-but-waiting recovery job exists.
                jdbc.update("update tenants set status='ACTIVE',session_version=session_version+1,version=version+1,security_resumed_at=current_timestamp,updated_at=current_timestamp where id=?", tenant);
                jdbc.update("update tenant_security_requests set status='APPLIED',reviewer_id=?,decision_reason=?,decided_at=current_timestamp where public_id=?", actor, draft.reason(), requestId);
                connections.applyResume(tenant);
            }
            case REJECT_RESUME -> jdbc.update("update tenant_security_requests set status='REJECTED',reviewer_id=?,decision_reason=?,decided_at=current_timestamp where public_id=?", actor, draft.reason(), requestId);
            case WITHDRAW_RESUME -> jdbc.update("update tenant_security_requests set status='WITHDRAWN',decision_reason=?,decided_at=current_timestamp where public_id=?", draft.reason(), requestId);
            case RECORD_CONNECTION_REVIEW -> connections.recordReview(tenant, incident, actor, draft);
            case REPLACE_CONNECTION_CREDENTIALS -> connections.replaceCredentials(tenant,incident,draft);
            case RECORD_RECONCILIATION -> connections.recordReconciliation(tenant,incident,draft);
            case RESOLVE_ALERT -> {
                if(draft.alertId()==null) throw denied("TENANT_ALERT_NOT_FOUND",404);
                if(jdbc.queryForObject("select count(*) from tenant_security_connections where tenant_id=? and (state<>'CHECK_PASSED' or gap_status<>'RECONCILED')",Long.class,tenant)>0)
                    throw denied("TENANT_ALERT_CAUSE_UNRESOLVED",409);
                if(jdbc.update("update tenant_security_alerts set status='RESOLVED',resolved_at=current_timestamp,resolved_by=?,resolution_evidence=?,next_mail_at=null where id=? and tenant_id=? and status='OPEN'",actor,draft.reason(),draft.alertId(),tenant)!=1)
                    throw denied("TENANT_ALERT_NOT_FOUND",404);
            }
        }
        Map<String,Object> result = new LinkedHashMap<>(one("select id,status,session_version,security_incident_id from tenants where id=?", tenant));
        result.put("action", draft.action().name());
        if (requestId != null) result.put("requestId", requestId);
        result.put("connectionRecoveryComplete", "ACTIVE".equals(result.get("status")) && Boolean.TRUE.equals(jdbc.queryForObject("select count(*)=0 from tenant_security_connections where tenant_id=? and was_enabled and (state<>'CHECK_PASSED' or check_status<>'PASSED')", Boolean.class, tenant)));
        result.put("dataReconciliationComplete", jdbc.queryForObject("select count(*)=0 from tenant_security_connections where tenant_id=? and gap_status<>'RECONCILED'",Boolean.class,tenant));
        return result;
    }

    private void validateDraft(Map<String,Object> target, long actor, TenantSecurityCommand.Draft draft) {
        if (draft == null || draft.action() == null || draft.reason() == null || draft.reason().isBlank() || draft.reason().length()>2000)
            throw denied("TENANT_SECURITY_REASON_REQUIRED", 400);
        long tenant = number(target,"id");
        if(draft.action()==TenantSecurityCommand.Action.RESOLVE_ALERT && "ACTIVE".equals(target.get("status"))) return;
        if (draft.action() == TenantSecurityCommand.Action.SUSPEND) {
            if (!"ACTIVE".equals(target.get("status"))) throw denied("TENANT_SECURITY_STATE_CONFLICT", 409);
            return;
        }
        if (Set.of(TenantSecurityCommand.Action.RECORD_CONNECTION_REVIEW,TenantSecurityCommand.Action.REPLACE_CONNECTION_CREDENTIALS,TenantSecurityCommand.Action.RECORD_RECONCILIATION,TenantSecurityCommand.Action.RESOLVE_ALERT).contains(draft.action())
                && "ACTIVE".equals(target.get("status")) && target.get("security_incident_id")!=null) return;
        if (!"SUSPENDED".equals(target.get("status")) || !"SECURITY".equals(target.get("suspension_kind")))
            throw denied("TENANT_SECURITY_STATE_CONFLICT", 409);
        if (Set.of(TenantSecurityCommand.Action.RECORD_CONNECTION_REVIEW,TenantSecurityCommand.Action.REPLACE_CONNECTION_CREDENTIALS).contains(draft.action())) return;
        if (Set.of(TenantSecurityCommand.Action.RECORD_RECONCILIATION,TenantSecurityCommand.Action.RESOLVE_ALERT).contains(draft.action()))
            throw denied("TENANT_SECURITY_STATE_CONFLICT",409);
        if (draft.action() == TenantSecurityCommand.Action.REQUEST_RESUME) {
            var pending = jdbc.queryForList("select public_id from tenant_security_requests where tenant_id=? and status='PENDING'", tenant);
            if (!pending.isEmpty()) throw denied("TENANT_SECURITY_REQUEST_ALREADY_PENDING", 409);
            return;
        }
        var request = one("select * from tenant_security_requests where public_id=? and tenant_id=? for update", draft.requestId(), tenant);
        if (!"PENDING".equals(request.get("status"))) throw denied("TENANT_SECURITY_REQUEST_FINAL", 409);
        boolean initiator = number(request,"initiator_id") == actor;
        if (draft.action() == TenantSecurityCommand.Action.WITHDRAW_RESUME ? !initiator : initiator)
            throw denied("TENANT_SECURITY_DIFFERENT_APPROVER_REQUIRED", 403);
        if (draft.action() == TenantSecurityCommand.Action.APPROVE_RESUME
                && (!Objects.equals(request.get("incident_id"),target.get("security_incident_id"))
                    || number(request,"tenant_session_version") != number(target,"session_version")))
            throw denied("TENANT_SECURITY_REQUEST_STALE", 409);
    }

    private Map<String,Object> lockActor(PlatformSecurityUser principal) {
        // Same role mutex/order as Phase 2 role/status mutation. Recheck live authority under the lock.
        one("select id from platform_roles where role_code='PLATFORM_SUPER_ADMIN' for update");
        var actor = one("select * from platform_users where id=? for update", principal.getId());
        if (!Boolean.TRUE.equals(actor.get("enabled")) || !Boolean.TRUE.equals(actor.get("mfa_enabled"))
                || actor.get("mfa_secret_encrypted")==null || number(actor,"security_version") != principal.getUser().getSecurityVersion()
                || jdbc.queryForObject("select count(*) from platform_user_roles ur join platform_roles r on r.id=ur.platform_role_id where ur.platform_user_id=? and r.role_code='PLATFORM_SUPER_ADMIN'", Long.class, principal.getId()) != 1)
            throw denied("TENANT_SECURITY_PERMISSION_DENIED", 403);
        if (actor.get("mfa_locked_until") != null && instant(actor.get("mfa_locked_until")).isAfter(Instant.now()))
            throw denied("TENANT_SECURITY_REAUTHENTICATION_LOCKED", 429);
        return actor;
    }
    private void requireEnabled() {
        if (!properties.isControlsEnabled()) throw denied("TENANT_SECURITY_CONTROLS_NOT_RELEASED", 409);
        credentials.requireConfigured();
        if (properties.getAlertRecipientIds().stream().noneMatch(id -> jdbc.queryForObject(
            "select count(*) from platform_users u where u.id=? and u.enabled and exists(select 1 from platform_user_roles ur join platform_roles r on r.id=ur.platform_role_id where ur.platform_user_id=u.id and r.role_code='PLATFORM_SUPER_ADMIN')",Long.class,id)>0))
            throw denied("TENANT_SECURITY_ALERT_RECIPIENT_REQUIRED",409);
    }
    private String fingerprint(long tenant, TenantSecurityCommand.Draft draft) {
        return mfa.hashToken("tenant-security-v1:" + tenant + ":" + writeJson(draft));
    }
    private Map<String,Object> one(String sql, Object... args) {
        return jdbc.queryForList(sql,args).stream().findFirst().orElseThrow(() -> denied("TENANT_SECURITY_RECORD_NOT_FOUND",404));
    }
    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }
    private long number(Map<String,Object> value,String key) { return ((Number)value.get(key)).longValue(); }
    private Instant instant(Object value) { return value instanceof OffsetDateTime date ? date.toInstant() : ((java.sql.Timestamp)value).toInstant(); }
    private String writeJson(Object value) { try { return json.writeValueAsString(value); } catch(Exception error) { throw new IllegalStateException("Security command serialization failed"); } }
    private Map<String,Object> readJson(String value) { try { return json.readValue(value,new TypeReference<>(){}); } catch(Exception error) { throw new IllegalStateException("Security result unavailable"); } }
    private TenantAccessException denied(String key,int status) { return new TenantAccessException(key,status); }
    private record Execution(Map<String,Object> response,TenantAccessException error) { }
}
