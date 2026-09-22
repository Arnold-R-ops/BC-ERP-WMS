package com.wms.system.tenant.security;

import com.wms.system.service.ShopifyWebhookService;
import com.wms.system.tenant.context.*;
import com.wms.system.tenant.model.*;
import com.wms.system.tenant.repository.ChannelWebhookRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service @RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="wms.tenant-security.webhook-worker-enabled",havingValue="true",matchIfMissing=true)
public class TenantWebhookInboxWorker {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final TenantSecurityStateService states;
    private final TenantConnectionSafetyService connections;
    private final ChannelWebhookRouteRepository routes;
    private final ShopifyWebhookService processor;
    private final TenantWebhookInboxService inbox;
    private final TenantSecurityAlertService alerts;

    @Scheduled(fixedDelayString="${wms.tenant-security.webhook-poll-ms:5000}")
    public void poll() {
        inbox.expireDueBodies();
        var candidates=jdbc.queryForList("select i.id from tenant_webhook_inbox i join tenants t on t.id=i.tenant_id where i.state='PENDING' and i.payload is not null and i.expires_at>current_timestamp and i.next_attempt_at<=current_timestamp and t.status='ACTIVE' order by i.id limit 20",Long.class);
        candidates.forEach(this::processOne);
    }
    public void processOne(long id) {
        var seeds=jdbc.queryForList("select i.tenant_id,t.slug from tenant_webhook_inbox i join tenants t on t.id=i.tenant_id where i.id=?",id);
        if(seeds.isEmpty()) return;
        long tenant=((Number)seeds.get(0).get("tenant_id")).longValue();
        try {
            var state=states.require(tenant,TenantOperationPurpose.BUSINESS,null);
            try(var admission=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,state.sessionVersion())) {
                TenantContext context=new TenantContext(tenant,(String)seeds.get(0).get("slug"),"webhook.background",RequestSurface.TENANT,state.status());
                TenantContextHolder.runWithResolvedTenant(context,() -> new TransactionTemplate(transactionManager).execute(tx -> {
                    var rows=jdbc.queryForList("select i.*, (t.security_resumed_at is not null and i.first_received_at<=t.security_resumed_at) as needs_reconciliation from tenant_webhook_inbox i join tenants t on t.id=i.tenant_id where i.id=? and i.state='PENDING' and i.expires_at>current_timestamp for update of i skip locked",id);
                    if(rows.isEmpty()) return null;
                    var row=rows.get(0);
                    if(Boolean.TRUE.equals(row.get("needs_reconciliation"))) {
                        jdbc.update("update tenant_webhook_inbox set state='REVIEW_REQUIRED',last_error='POST_RESUME_RECONCILIATION_REQUIRED' where id=?",id);
                        jdbc.update("update tenant_security_connections set gap_status='REVIEW_REQUIRED' where tenant_id=? and config_id=?",tenant,row.get("config_id"));
                        return null;
                    }
                    long config=((Number)row.get("config_id")).longValue();
                    if(!connections.trustedWebhook(tenant,config)) throw new TenantAccessException("TENANT_CONNECTION_BLOCKED",423);
                    ChannelWebhookRoute route=routes.findById(config).filter(ChannelWebhookRoute::isActive).orElseThrow(() -> new TenantAccessException("TENANT_CONNECTION_BLOCKED",423));
                    ShopifyWebhookService.WebhookOutcome outcome;
                    try(var receipt=TenantWebhookReceiptScope.open(id)) {
                        outcome=processor.handleForCompany(route,(String)row.get("topic"),(String)row.get("webhook_id"),(String)row.get("payload"));
                    }
                    if(outcome!=ShopifyWebhookService.WebhookOutcome.ACCEPTED && outcome!=ShopifyWebhookService.WebhookOutcome.DUPLICATE)
                        throw new IllegalStateException("Webhook business processing incomplete");
                    jdbc.update("update tenant_webhook_inbox set state='PROCESSED',last_error=null,attempt_count=attempt_count+1 where id=?",id);
                    return null;
                }));
            }
        } catch(TenantAccessException blocked) {
            // State transitions are expected; retain the receipt for a later admitted attempt.
            jdbc.update("update tenant_webhook_inbox set next_attempt_at=current_timestamp+interval '1 minute',last_error=? where id=? and state='PENDING'",blocked.errorKey(),id);
        } catch(RuntimeException failed) {
            jdbc.update("update tenant_webhook_inbox set attempt_count=attempt_count+1,next_attempt_at=current_timestamp+interval '1 minute',last_error='PROCESSING_FAILED',state=case when attempt_count+1>=3 then 'REVIEW_REQUIRED' else state end where id=? and state='PENDING'",id);
            alerts.record(tenant,"WEBHOOK_PROCESSING_FAILED","A persisted webhook requires retry or manual reconciliation.");
        }
    }
}
