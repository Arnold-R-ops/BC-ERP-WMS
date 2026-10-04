package com.wms.system.tenant.security;

import com.wms.system.tenant.config.TenantSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TenantSecurityAlertService {
    private final JdbcTemplate jdbc;
    private final TenantSecurityProperties properties;
    private final ObjectProvider<JavaMailSender> mail;
    private final PlatformTransactionManager transactionManager;

    public void record(long tenant, String issue, String summary) {
        record(tenant,issue,summary,1);
    }
    public void record(long tenant,String issue,String summary,int severity) {
        jdbc.update("insert into tenant_security_alerts(tenant_id,issue_key,summary,severity) values(?,?,?,?) "
            + "on conflict(tenant_id,issue_key) where status='OPEN' do update set occurrence_count=tenant_security_alerts.occurrence_count+1,updated_at=current_timestamp,"
            + "next_mail_at=case when excluded.severity>tenant_security_alerts.severity then current_timestamp else tenant_security_alerts.next_mail_at end,severity=greatest(excluded.severity,tenant_security_alerts.severity)",
            tenant, issue, summary,severity);
    }
    public List<String> recipients() {
        List<String> result = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(properties.getAlertRecipientIds()))
            result.addAll(jdbc.queryForList("select u.normalized_email from platform_users u where u.id=? and u.enabled and exists(select 1 from platform_user_roles ur join platform_roles r on r.id=ur.platform_role_id where ur.platform_user_id=u.id and r.role_code='PLATFORM_SUPER_ADMIN')", String.class, id));
        return result;
    }
    @Scheduled(fixedDelayString="${wms.tenant-security.alert-poll-ms:60000}")
    public void deliverDue() {
        if (!properties.isControlsEnabled()) return;
        for (int count=0; count<10; count++) {
            Boolean found = new TransactionTemplate(transactionManager).execute(tx -> {
                var due=jdbc.queryForList("select * from tenant_security_alerts where status='OPEN' and next_mail_at<=current_timestamp order by next_mail_at,id limit 1 for update skip locked");
                if (due.isEmpty()) return false;
                var row=due.get(0); long id=((Number)row.get("id")).longValue();
                try {
                    List<String> recipients=recipients();
                    JavaMailSender sender=mail.getIfAvailable();
                    if (recipients.isEmpty() || sender==null) throw new IllegalStateException("No alert delivery configuration");
                    SimpleMailMessage message=new SimpleMailMessage();
                    message.setTo(recipients.toArray(String[]::new));
                    message.setSubject("BCWMS platform safety alert #"+id);
                    message.setText("Tenant #"+row.get("tenant_id")+": "+row.get("summary")+"\nSign in to the platform to review the incident. No credentials or business payloads are included.");
                    sender.send(message);
                    jdbc.update("update tenant_security_alerts set mail_status='SUBMITTED',last_mail_at=current_timestamp,next_mail_at=current_timestamp+interval '6 hours',first_mail_failure_at=null,mail_attempts=0 where id=?",id);
                } catch(Exception failure) {
                    int attempts=((Number)row.get("mail_attempts")).intValue()+1;
                    Instant first=row.get("first_mail_failure_at")==null?Instant.now():((java.sql.Timestamp)row.get("first_mail_failure_at")).toInstant();
                    OffsetDateTime next=attempts<=3?OffsetDateTime.ofInstant(first.plusSeconds(new int[]{300,900,3600}[attempts-1]),ZoneOffset.UTC):null;
                    jdbc.update("update tenant_security_alerts set mail_status=?,mail_attempts=?,first_mail_failure_at=?,next_mail_at=? where id=?",
                        attempts<=3?"RETRY_PENDING":"MANUAL_REQUIRED",attempts,OffsetDateTime.ofInstant(first,ZoneOffset.UTC),next,id);
                }
                return true;
            });
            if (!Boolean.TRUE.equals(found)) break;
        }
    }
}
