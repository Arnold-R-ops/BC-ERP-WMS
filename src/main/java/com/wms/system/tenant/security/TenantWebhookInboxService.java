package com.wms.system.tenant.security;

import com.wms.system.tenant.config.TenantSecurityProperties;
import com.wms.system.tenant.model.ChannelWebhookRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TenantWebhookInboxService {
    public enum Outcome { ACCEPTED, DUPLICATE, UNTRUSTED, OVER_LIMIT, UNAVAILABLE, CONFLICT }
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final TenantSecurityStateService states;
    private final TenantConnectionSafetyService connections;
    private final TenantCredentialCrypto crypto;
    private final TenantSecurityProperties properties;
    private final TenantSecurityAlertService alerts;

    public void rejectOversizedIngress(long tenant) {
        try { alerts.record(tenant,"WEBHOOK_INGRESS_SIZE","Oversized ingress rejected before signature verification; not a trusted receipt."); }
        catch(RuntimeException unavailable) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Oversized webhook rejection alert unavailable: tenantId={}",tenant); }
    }

    public Outcome accept(ChannelWebhookRoute verifiedRoute,String topic,String webhookId,String body) {
        long tenant=verifiedRoute.getTenantId();
        Outcome result;
        try {
            var state=states.require(tenant,TenantOperationPurpose.WEBHOOK_INBOX,null);
            try(var admission=TenantExecutionScope.open(tenant,TenantOperationPurpose.WEBHOOK_INBOX,state.sessionVersion())) {
                result=new TransactionTemplate(transactionManager).execute(tx -> persist(verifiedRoute,topic,webhookId,body));
            }
        } catch (TenantAccessException denied) { result=Outcome.UNTRUSTED; }
        catch (RuntimeException unavailable) { result=Outcome.UNAVAILABLE; }
        if (result==Outcome.OVER_LIMIT || result==Outcome.UNAVAILABLE || result==Outcome.CONFLICT) {
            try { alerts.record(tenant,"WEBHOOK_"+result.name(),"Webhook was not acknowledged; reconcile possible delivery gaps."); }
            catch (RuntimeException unavailable) {
                // A database outage must still produce a non-2xx response; never claim durable evidence exists.
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Tenant webhook persistence/alert unavailable: tenantId={}, outcome={}",tenant,result);
            }
        }
        return result==null?Outcome.UNAVAILABLE:result;
    }
    private Outcome persist(ChannelWebhookRoute route,String topic,String webhookId,String body) {
        if (webhookId==null || webhookId.isBlank() || webhookId.length()>100 || topic==null || topic.isBlank() || topic.length()>80) return Outcome.UNTRUSTED;
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>properties.getWebhookMaxBodyBytes()) return Outcome.OVER_LIMIT;
        var live=jdbc.queryForList("select signing_secret,active from channel_webhook_routes where tenant_id=? and integration_config_id=?",route.getTenantId(),route.getIntegrationConfigId());
        if(live.isEmpty() || !Boolean.TRUE.equals(live.get(0).get("active"))
            || !Objects.equals(crypto.fingerprint((String)live.get(0).get("signing_secret")),crypto.fingerprint(route.getSigningSecret()))
            || !connections.trustedWebhook(route.getTenantId(),route.getIntegrationConfigId())) return Outcome.UNTRUSTED;
        // Same serialization point protects rate limits, duplicate checks, and global/tenant storage totals.
        jdbc.queryForList("select budget_key from tenant_webhook_budget where budget_key='GLOBAL' for update");
        String tenantKey="TENANT-"+route.getTenantId();
        jdbc.update("insert into tenant_webhook_budget(budget_key) values(?) on conflict do nothing",tenantKey);
        if(!takeRate("GLOBAL",properties.getWebhookGlobalPerMinute()) || !takeRate(tenantKey,properties.getWebhookPerTenantPerMinute())) return Outcome.OVER_LIMIT;
        String hash=hash(bytes);
        var duplicate=jdbc.queryForList("select body_hash,topic from tenant_webhook_inbox where tenant_id=? and config_id=? and webhook_id=?",route.getTenantId(),route.getIntegrationConfigId(),webhookId);
        if(!duplicate.isEmpty()) return hash.equals(duplicate.get(0).get("body_hash")) && topic.equals(duplicate.get(0).get("topic"))?Outcome.DUPLICATE:Outcome.CONFLICT;
        var usage=jdbc.queryForMap("select count(*) as n,coalesce(sum(payload_bytes),0) as bytes from tenant_webhook_inbox where tenant_id=? and payload is not null",route.getTenantId());
        long global=jdbc.queryForObject("select coalesce(sum(payload_bytes),0) from tenant_webhook_inbox where payload is not null",Long.class);
        if(((Number)usage.get("n")).longValue()>=properties.getInboxMaxEventsPerTenant()
            || ((Number)usage.get("bytes")).longValue()+bytes.length>properties.getInboxMaxBytesPerTenant()
            || global+bytes.length>properties.getInboxMaxBytesGlobal()) return Outcome.OVER_LIMIT;
        jdbc.update("insert into tenant_webhook_inbox(tenant_id,config_id,webhook_id,topic,body_hash,payload,payload_bytes,expires_at) values(?,?,?,?,?,?,?,current_timestamp+interval '7 days')",
            route.getTenantId(),route.getIntegrationConfigId(),webhookId,topic,hash,body,bytes.length);
        return Outcome.ACCEPTED;
    }
    private boolean takeRate(String key,int limit) {
        var row=jdbc.queryForMap("update tenant_webhook_budget set minute_count=case when minute_start<date_trunc('minute',current_timestamp) then 1 else minute_count+1 end,minute_start=date_trunc('minute',current_timestamp) where budget_key=? returning minute_count",key);
        return ((Number)row.get("minute_count")).intValue()<=limit;
    }
    public void expireDueBodies() {
        // This is only the seven-day webhook cache, never tenant lifecycle purge.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var rows=jdbc.queryForList("select id,tenant_id,state,last_error from tenant_webhook_inbox where expires_at<=current_timestamp and payload is not null and not legal_hold order by id limit 100 for update skip locked");
            for(var row:rows) {
                int changed=jdbc.update("update tenant_webhook_inbox set payload=null,payload_bytes=0,state=case when state='PROCESSED' then state else 'EXPIRED' end,last_error=case when state='PROCESSED' then null when last_error='COVERED_BY_VERIFIED_RECONCILIATION' then last_error else 'RETENTION_EXPIRED' end where id=? and not legal_hold and expires_at<=current_timestamp",row.get("id"));
                if(changed>0) {
                    String previous=jdbc.queryForObject("select current_setting('app.company_id',true)",String.class);
                    jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,row.get("tenant_id").toString());
                    try { jdbc.update("update channel_raw_events set payload='{}'::jsonb,error_message='WEBHOOK_RETENTION_EXPIRED' where company_id=? and source_inbox_id=?",row.get("tenant_id"),row.get("id")); }
                    finally { jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,previous==null?"":previous); }
                }
                if(changed>0 && !"PROCESSED".equals(row.get("state")) && !"COVERED_BY_VERIFIED_RECONCILIATION".equals(row.get("last_error"))) alerts.record(((Number)row.get("tenant_id")).longValue(),"WEBHOOK_RETENTION_REVIEW","Seven-day webhook payload retention elapsed; inspect reconciliation evidence for gaps.");
            }
        });
    }
    private String hash(byte[] value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value)); }
        catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
