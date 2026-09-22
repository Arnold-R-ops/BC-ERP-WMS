package com.wms.system.tenant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.platform.model.*;
import com.wms.system.platform.repository.*;
import com.wms.system.platform.service.*;
import com.wms.system.platform.config.PlatformMfaProperties;
import com.wms.system.security.PlatformSecurityUser;
import com.wms.system.tenant.config.TenantSecurityProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="p3.r2.database-authorized",matches="true")
class TenantSecurityCommandsPostgresTest {
    static javax.sql.DataSource ds;
    static JdbcTemplate jdbc;
    PlatformTenantSecurityService service;
    PlatformMfaCrypto mfa;
    PlatformAuditLogRepository auditRows;
    long tenant, first, second;
    com.wms.platformverification.PlatformFixtureScope fixtures;
    static final String SECRET="JBSWY3DPEHPK3PXP";
    static final String PASSWORD="P3-disposable-test-password";

    @BeforeAll static void schema() {
        ds=com.wms.platformverification.PlatformR2Isolation.runtimeDataSource(); jdbc=new JdbcTemplate(ds);
        // All migration and fixture DDL is owned by the separately authorized R2 preparer.
    }
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        fixtures=com.wms.platformverification.PlatformFixtureScope.open(jdbc,new DataSourceTransactionManager(ds),getClass().getName(),"commandFixture");
        var mfaProperties=new PlatformMfaProperties(); mfaProperties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        mfa=new PlatformMfaCrypto(mfaProperties);
        var passwordEncoder=new BCryptPasswordEncoder(4);
        tenant=jdbc.queryForObject("insert into tenants(tenant_code,display_name,slug,status,timezone,locale,version) values(?,?,?,'ACTIVE','UTC','en-US',0) returning id",Long.class,"P3-"+UUID.randomUUID(),"Isolated command tenant","p3-"+UUID.randomUUID().toString().substring(0,20));
        fixtures.ownTenant(tenant);
        first=actor(passwordEncoder); second=actor(passwordEncoder);
        var properties=new TenantSecurityProperties(); properties.setControlsEnabled(true); properties.setCredentialEncryptionKey(Base64.getEncoder().encodeToString(new byte[32])); properties.setAlertRecipientIds(List.of(first));
        var crypto=new TenantCredentialCrypto(properties);
        var tokens=mock(ObjectProvider.class);
        when(tokens.getObject()).thenReturn(mock(com.wms.system.integration.ShopifyTokenProvider.class));
        var connections=new TenantConnectionSafetyService(jdbc,new TenantSecurityStateService(jdbc),crypto,tokens);
        var json=new ObjectMapper().findAndRegisterModules();
        auditRows=mock(PlatformAuditLogRepository.class);
        when(auditRows.saveAndFlush(any())).thenAnswer(invocation -> {
            PlatformAuditLog row=invocation.getArgument(0);
            jdbc.update("insert into platform_audit_logs(platform_user_id,target_tenant_id,action,resource_type,result,detail_json) values(?,?,?,?,?,?)",
                row.getPlatformUserId(),row.getTargetTenantId(),row.getAction(),row.getResourceType(),row.getResult(),row.getDetailJson());
            return row;
        });
        service=new PlatformTenantSecurityService(new PlatformAccessGuard(mock(PlatformAccessGrantRepository.class)),jdbc,
            new DataSourceTransactionManager(ds),mfa,passwordEncoder,json,new PlatformAuditService(auditRows,json),properties,connections,crypto);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); if(fixtures!=null) fixtures.close(); }
    long actor(BCryptPasswordEncoder encoder) {
        long id=jdbc.queryForObject("insert into platform_users(normalized_email,password_hash,display_name,enabled,security_version,mfa_enabled,mfa_secret_encrypted,mfa_failed_attempts) values(?,?,?,true,1,true,?,0) returning id",Long.class,
            UUID.randomUUID()+"@p3.invalid",encoder.encode(PASSWORD),"Isolated reviewer",mfa.encrypt(SECRET));
        fixtures.ownUser(id);
        jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) select ?,id from platform_roles where role_code='PLATFORM_SUPER_ADMIN'",id);
        return id;
    }
    void login(long id) {
        var user=PlatformUser.builder().id(id).securityVersion(1L).enabled(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new PlatformSecurityUser(user),null,List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPER_ADMIN"))));
    }
    TenantSecurityCommand.Draft draft(TenantSecurityCommand.Action action,String request) { return new TenantSecurityCommand.Draft(action,"Isolated verified risk evidence",request,List.of(),null,null,null); }
    TenantSecurityCommand.Execute command(long actor,TenantSecurityCommand.Draft draft) {
        login(actor);
        var challenge=service.challenge(tenant,draft,null);
        return new TenantSecurityCommand.Execute(UUID.randomUUID().toString(),(String)challenge.get("challengeToken"),PASSWORD,code(),draft);
    }
    Map<String,Object> run(long actor,TenantSecurityCommand.Draft draft) { var command=command(actor,draft); return service.execute(tenant,command,null); }
    String requestResume() { return (String)run(first,draft(TenantSecurityCommand.Action.REQUEST_RESUME,null)).get("requestId"); }
    @Test void twoPersonAtomicResumeAndIdempotency() {
        var pause=command(first,draft(TenantSecurityCommand.Action.SUSPEND,null));
        var paused=service.execute(tenant,pause,null);
        assertEquals("SUSPENDED",paused.get("status"));
        assertEquals(2,((Number)paused.get("session_version")).longValue());
        assertEquals(2,((Number)service.execute(tenant,pause,null).get("session_version")).longValue());
        String request=requestResume();
        assertThrows(TenantAccessException.class,() -> command(first,draft(TenantSecurityCommand.Action.APPROVE_RESUME,request)));
        var resumed=run(second,draft(TenantSecurityCommand.Action.APPROVE_RESUME,request));
        assertEquals("ACTIVE",resumed.get("status")); assertEquals(3,((Number)resumed.get("session_version")).longValue());
        assertEquals("APPLIED",jdbc.queryForObject("select status from tenant_security_requests where public_id=?",String.class,request));
        assertThrows(TenantAccessException.class,() -> command(first,draft(TenantSecurityCommand.Action.WITHDRAW_RESUME,request)));
    }
    @Test void failedAuditRollsBackApprovalAndTenantTogether() {
        run(first,draft(TenantSecurityCommand.Action.SUSPEND,null));
        String request=requestResume(); var approval=command(second,draft(TenantSecurityCommand.Action.APPROVE_RESUME,request));
        doThrow(new IllegalStateException("isolated audit failure")).when(auditRows).saveAndFlush(any());
        assertThrows(IllegalStateException.class,() -> service.execute(tenant,approval,null));
        assertEquals("SUSPENDED",jdbc.queryForObject("select status from tenants where id=?",String.class,tenant));
        assertEquals("PENDING",jdbc.queryForObject("select status from tenant_security_requests where public_id=?",String.class,request));
        assertEquals(0,jdbc.queryForObject("select count(*) from tenant_security_commands where actor_id=? and idempotency_key=?",Integer.class,second,approval.idempotencyKey()));
    }
    @Test void initiatorOnlyWithdrawalAndNoExpiry() {
        run(first,draft(TenantSecurityCommand.Action.SUSPEND,null)); String request=requestResume();
        assertThrows(TenantAccessException.class,() -> command(second,draft(TenantSecurityCommand.Action.WITHDRAW_RESUME,request)));
        run(first,draft(TenantSecurityCommand.Action.WITHDRAW_RESUME,request));
        assertEquals("WITHDRAWN",jdbc.queryForObject("select status from tenant_security_requests where public_id=?",String.class,request));
        assertEquals("SUSPENDED",jdbc.queryForObject("select status from tenants where id=?",String.class,tenant));
        assertNotEquals(request,requestResume());
    }
    @Test void pendingRecoveryRemainsValidWithoutBusinessExpiry() {
        run(first,draft(TenantSecurityCommand.Action.SUSPEND,null));
        String id=UUID.randomUUID().toString();
        jdbc.update("insert into tenant_security_requests(public_id,tenant_id,incident_id,tenant_session_version,initiator_id,status,reason,created_at) select ?,id,security_incident_id,session_version,?,'PENDING','Old verified request',current_timestamp-interval '365 days' from tenants where id=?",id,first,tenant);
        assertEquals("ACTIVE",run(second,draft(TenantSecurityCommand.Action.APPROVE_RESUME,id)).get("status"));
    }
    @Test void reauthenticationFailuresPersistWithoutSuspending() {
        var valid=command(first,draft(TenantSecurityCommand.Action.SUSPEND,null));
        var invalid=new TenantSecurityCommand.Execute(valid.idempotencyKey(),valid.challengeToken(),"wrong",valid.code(),valid.draft());
        for(int i=0;i<5;i++) assertThrows(TenantAccessException.class,() -> service.execute(tenant,invalid,null));
        assertEquals(5,jdbc.queryForObject("select mfa_failed_attempts from platform_users where id=?",Integer.class,first));
        assertEquals("ACTIVE",jdbc.queryForObject("select status from tenants where id=?",String.class,tenant));
        assertThrows(TenantAccessException.class,() -> service.execute(tenant,valid,null));
    }
    @Test void concurrentPauseOnlyMutatesOnce() throws Exception {
        var a=command(first,draft(TenantSecurityCommand.Action.SUSPEND,null));
        var b=command(second,draft(TenantSecurityCommand.Action.SUSPEND,null));
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            var futures=List.of(pool.submit(() -> race(first,a,start)),pool.submit(() -> race(second,b,start)));
            start.countDown(); int applied=0; for(var future:futures) if(future.get(5,TimeUnit.SECONDS)) applied++;
            assertEquals(1,applied); assertEquals(2,jdbc.queryForObject("select session_version from tenants where id=?",Long.class,tenant));
        } finally { pool.shutdownNow(); }
    }
    boolean race(long actor,TenantSecurityCommand.Execute command,CountDownLatch start) throws Exception {
        start.await(); login(actor); try { service.execute(tenant,command,null); return true; }
        catch(TenantAccessException conflict) { return false; } finally { SecurityContextHolder.clearContext(); }
    }
    String code() {
        try {
            var method=PlatformMfaCrypto.class.getDeclaredMethod("totp",String.class,long.class); method.setAccessible(true);
            return (String)method.invoke(mfa,SECRET,Instant.now().getEpochSecond()/30);
        } catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
