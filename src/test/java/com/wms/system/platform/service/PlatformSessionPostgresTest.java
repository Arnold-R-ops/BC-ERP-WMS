package com.wms.system.platform.service;

import com.wms.system.WmsSystemApplication;
import com.wms.system.security.JwtUtil;
import com.wms.system.security.PlatformJwtClaims;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="p3.r2.database-authorized",matches="true")
@SpringBootTest(classes={WmsSystemApplication.class,PlatformSessionPostgresTest.NoJobs.class},
    properties={"spring.config.location=classpath:platform-r2-isolation.properties", "spring.flyway.enabled=false"})
@org.springframework.test.context.ContextConfiguration(initializers=com.wms.platformverification.PlatformR2SpringInitializer.class)
// Authentication responses contain disposable secrets; never dump them on assertion failure.
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Timeout(30)
class PlatformSessionPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        var settings=com.wms.platformverification.PlatformR2Isolation.settings(com.wms.platformverification.PlatformR2Isolation.configuration(),false,com.wms.platformverification.PlatformR2Isolation.MAIN);
        props.add("spring.datasource.url",()->com.wms.platformverification.PlatformR2Isolation.url(settings.database()));
    }
    @TestConfiguration static class NoJobs {
        @Bean static BeanDefinitionRegistryPostProcessor disableEveryScheduledTask() {
            return new BeanDefinitionRegistryPostProcessor() {
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    String name="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                    if(registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
                }
                public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) { }
            };
        }
    }
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.mock.mockito.SpyBean PlatformSessionService sessions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlatformMfaCrypto crypto;
    @Autowired PlatformBrowserBinding browsers;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @MockBean org.springframework.mail.javamail.JavaMailSender mail;
    protected com.wms.platformverification.PlatformFixtureScope fixtures;
    @BeforeEach void beginOwnedFixtures(TestInfo info) {
        fixtures=com.wms.platformverification.PlatformFixtureScope.open(jdbc,transactions,getClass().getName(),info.getTestMethod().orElseThrow().getName());
    }
    @AfterEach void retireOwnedFixtures() { if(fixtures!=null) fixtures.close(); }
    private static final String SECRET="JBSWY3DPEHPK3PXP";
    private static final String PASSWORD="Isolated-Platform-Password-42!";
    record Login(long user, String email, String token, PlatformJwtClaims claims) { }
    long user(boolean mfa) {
        long id=jdbc.queryForObject("insert into platform_users(normalized_email,password_hash,display_name,mfa_enabled,mfa_secret_encrypted) values(?,?,'Session test',?,?) returning id",
            Long.class,UUID.randomUUID()+"@session.invalid",passwords.encode(PASSWORD),mfa,mfa?crypto.encrypt(SECRET):null);
        fixtures.ownUser(id);
        jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) select ?,id from platform_roles where role_code='PLATFORM_SUPER_ADMIN'",id);
        return id;
    }
    Login login(long id) {
        String email=jdbc.queryForObject("select normalized_email from platform_users where id=?",String.class,id);
        long version=jdbc.queryForObject("select security_version from platform_users where id=?",Long.class,id);
        var w=sessions.create(id,version);
        String token=jwt.generatePlatformToken(id,email,List.of("PLATFORM_SUPER_ADMIN"),version,w.sessionId(),w.startedAt().toInstant().toEpochMilli(),w.expiresAt().toInstant().toEpochMilli());
        return new Login(id,email,token,jwt.parsePlatformToken(token));
    }
    void idle(Login l) { jdbc.update("update platform_sessions set idle_expires_at=clock_timestamp() - interval '1 second' where session_id=?",l.claims.sessionId()); }
    String postJson(String path, Object body, String token, int status) throws Exception {
        var request=post(path).contentType("application/json").content(json.writeValueAsString(body));
        request.cookie(browserCookie());
        if(token!=null) request.header("Authorization","Bearer "+token);
        return mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }
    private jakarta.servlet.http.Cookie testBrowser;
    jakarta.servlet.http.Cookie browserCookie() {
        if(testBrowser==null) {
            var request=new org.springframework.mock.web.MockHttpServletRequest(); request.addHeader("X-Platform-Browser-Init","1");
            var response=new org.springframework.mock.web.MockHttpServletResponse(); browsers.initialize(request,response);
            testBrowser=response.getCookies()[0];
        }
        return testBrowser;
    }
    @Test void backendWindowIsTwelveHoursAndThirtyMinutes() {
        Login l=login(user(true)); var w=sessions.check(l.claims);
        assertEquals(12,java.time.Duration.between(w.startedAt(),w.expiresAt()).toHours());
        assertEquals(30,java.time.Duration.between(w.startedAt(),w.idleExpiresAt()).toMinutes());
    }
    @Test void meAndBusinessPollingNeverTouchActivity() throws Exception {
        Login l=login(user(true)); var before=sessions.check(l.claims);
        for(int i=0;i<3;i++) mvc.perform(get("/api/platform/auth/me").header("Authorization","Bearer "+l.token))
            .andExpect(status().isOk()).andExpect(jsonPath("idleExpiresAt").exists()).andExpect(jsonPath("serverNow").exists());
        assertEquals(before.lastActivityAt(),sessions.check(l.claims).lastActivityAt());
        assertEquals(before.idleExpiresAt(),sessions.check(l.claims).idleExpiresAt());
    }
    @Test void activityUsesServerTimeAndCapsAtAbsoluteDeadline() throws Exception {
        Login l=login(user(true));
        jdbc.update("update platform_sessions set idle_expires_at=clock_timestamp()+interval '10 seconds' where session_id=?",l.claims.sessionId());
        postJson("/api/platform/auth/session/activity",Map.of("clientNow","2099-01-01","minutes",999999),l.token,200);
        var after=sessions.check(l.claims);
        assertTrue(after.idleExpiresAt().isAfter(after.serverNow().plusMinutes(29)));
        assertFalse(after.idleExpiresAt().isAfter(after.expiresAt()));
    }
    @Test void idleJwtCannotCallMeBusinessDownloadOrActivity() throws Exception {
        Login l=login(user(true)); idle(l);
        var before=jdbc.queryForMap("select * from platform_sessions where session_id=?",l.claims.sessionId());
        for(String path:List.of("/api/platform/auth/me","/api/platform/tenants","/api/platform/exports/old/download")) {
            mvc.perform(get(path).header("Authorization","Bearer "+l.token)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("errorKey").value("PLATFORM_SESSION_IDLE_EXPIRED"));
        }
        postJson("/api/platform/auth/session/activity",Map.of(),l.token,401);
        assertEquals(before,jdbc.queryForMap("select * from platform_sessions where session_id=?",l.claims.sessionId()));
    }
    @Test void absoluteExpiredRecordCannotBeRevivedEvenWithPendingActivity() {
        Login l=login(user(true));
        OffsetDateTime end=OffsetDateTime.now().minusSeconds(1).withNano(0), start=end.minusHours(12);
        jdbc.update("update platform_sessions set started_at=?,last_activity_at=?,absolute_expires_at=?,idle_expires_at=? where session_id=?",start,end.minusMinutes(1),end,end,l.claims.sessionId());
        var c=new PlatformJwtClaims(l.user,l.email,List.of("PLATFORM_SUPER_ADMIN"),1L,l.claims.sessionId(),start.toInstant().toEpochMilli(),end.toInstant().toEpochMilli());
        assertEquals("PLATFORM_SESSION_EXPIRED",assertThrows(PlatformSessionException.class,()->sessions.activity(c)).getErrorKey());
    }
    @Test void nearTwelveHourBoundaryCapsActivityThenRejectsTheSameJwt() throws Exception {
        Login original=login(user(true));
        OffsetDateTime end=jdbc.queryForObject("select clock_timestamp()",OffsetDateTime.class).plusSeconds(5).withNano(0);
        OffsetDateTime start=end.minusHours(12);
        jdbc.update("update platform_sessions set started_at=?,last_activity_at=?,absolute_expires_at=?,idle_expires_at=? where session_id=?",
            start,end.minusSeconds(5),end,end,original.claims.sessionId());
        String token=jwt.generatePlatformToken(original.user,original.email,List.of("PLATFORM_SUPER_ADMIN"),1L,
            original.claims.sessionId(),start.toInstant().toEpochMilli(),end.toInstant().toEpochMilli());
        var claims=jwt.parsePlatformToken(token);
        mvc.perform(get("/api/platform/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        postJson("/api/platform/auth/session/activity",Map.of(),token,200);
        assertEquals(end,sessions.check(claims).idleExpiresAt());
        var before=jdbc.queryForMap("select last_activity_at,idle_expires_at,absolute_expires_at from platform_sessions where session_id=?",claims.sessionId());
        long waitLimit=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(jdbc.queryForObject("select clock_timestamp()",OffsetDateTime.class).isBefore(end)) {
            if(System.nanoTime()>waitLimit) fail("Synthetic absolute boundary did not elapse");
            Thread.sleep(25);
        }
        assertEquals("PLATFORM_SESSION_EXPIRED",assertThrows(PlatformSessionException.class,()->sessions.activity(claims)).getErrorKey());
        mvc.perform(get("/api/platform/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        postJson("/api/platform/auth/session/activity",Map.of(),token,401);
        assertEquals(before,jdbc.queryForMap("select last_activity_at,idle_expires_at,absolute_expires_at from platform_sessions where session_id=?",claims.sessionId()));
    }
    @Test void preMigrationSessionWithoutBrowserGroupFailsClosedAndIsNeverBackfilled() throws Exception {
        Login original=login(user(true));
        jdbc.update("update platform_sessions set browser_binding_hash=null where session_id=?",original.claims.sessionId());
        mvc.perform(get("/api/platform/auth/me").header("Authorization","Bearer "+original.token))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("errorKey").value("PLATFORM_SESSION_INVALIDATED"));
        postJson("/api/platform/auth/session/activity",Map.of(),original.token,401);
        assertNull(jdbc.queryForObject("select browser_binding_hash from platform_sessions where session_id=?",String.class,original.claims.sessionId()));
    }
    @Test void currentLogoutIsDurableAuditedAndDoesNotRevokeOtherLogin() throws Exception {
        long user=user(true); Login a=login(user),b=login(user);
        postJson("/api/platform/auth/logout",Map.of(),a.token,204);
        assertThrows(PlatformSessionException.class,()->sessions.activity(a.claims));
        assertNotNull(sessions.check(b.claims));
        assertEquals(1,jdbc.queryForObject("select count(*) from platform_audit_logs where platform_user_id=? and action='SESSIONS_REVOKED' and resource_type='platform_session'",Integer.class,user));
        postJson("/api/platform/auth/logout",Map.of(),a.token,401);
    }
    @Test void currentLogoutAndAuditRollBackTogether() {
        Login l=login(user(true));
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            sessions.logout(l.claims,null); throw new IllegalStateException("isolated rollback");
        }));
        assertNotNull(sessions.check(l.claims));
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_audit_logs where platform_user_id=?",Integer.class,l.user));
    }
    @Test void separateLoginsHaveSeparateIdleWindows() {
        long user=user(true); Login a=login(user),b=login(user); idle(b);
        sessions.activity(a.claims);
        assertThrows(PlatformSessionException.class,()->sessions.activity(b.claims));
    }
    @Test void absentLegacyAndWrongOwnerSessionCannotAuthenticate() throws Exception {
        Login l=login(user(true));
        assertThrows(PlatformSessionException.class,()->sessions.check(new PlatformJwtClaims(l.user,l.email,List.of("PLATFORM_SUPER_ADMIN"),1L)));
        String unregistered=jwt.generatePlatformToken(l.user,l.email,List.of("PLATFORM_SUPER_ADMIN"),1L);
        mvc.perform(get("/api/platform/auth/me").header("Authorization","Bearer "+unregistered)).andExpect(status().isUnauthorized());
        var c=l.claims;
        long other=user(true);
        assertThrows(PlatformSessionException.class,()->sessions.activity(new PlatformJwtClaims(other,c.normalizedEmail(),c.roles(),1L,c.sessionId(),c.issuedAtEpochMillis(),c.expiresAtEpochMillis())));
        jdbc.update("delete from platform_sessions where session_id=?",c.sessionId());
        assertThrows(PlatformSessionException.class,()->sessions.check(c));
    }
    @Test void disableReenableAndVersionChangesDoNotResurrectOldLogin() throws Exception {
        Login l=login(user(true));
        jdbc.update("update platform_users set enabled=false,security_version=security_version+1 where id=?",l.user);
        postJson("/api/platform/auth/session/activity",Map.of(),l.token,401);
        jdbc.update("update platform_users set enabled=true where id=?",l.user);
        postJson("/api/platform/auth/session/activity",Map.of(),l.token,401);
        assertNotNull(sessions.check(login(l.user).claims));
    }
    @Test void liveRoleChangeRejectsOldToken() throws Exception {
        Login l=login(user(true));
        jdbc.update("delete from platform_user_roles where platform_user_id=?",l.user);
        mvc.perform(get("/api/platform/auth/me").header("Authorization","Bearer "+l.token)).andExpect(status().isUnauthorized());
    }
    @Test void waitingActivityRechecksDatabaseWallTimeAfterLock() throws Exception {
        Login l=login(user(true)); var pool=Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> attempted=new CompletableFuture<>();
            Future<?>[] result=new Future<?>[1];
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                jdbc.queryForList("select session_id from platform_sessions where session_id=? for update",l.claims.sessionId());
                jdbc.update("update platform_sessions set idle_expires_at=clock_timestamp()+interval '500 milliseconds' where session_id=?",l.claims.sessionId());
                result[0]=pool.submit(()->{ attempted.complete(null); assertThrows(PlatformSessionException.class,()->sessions.activity(l.claims)); });
                attempted.join();
                try { Thread.sleep(800); } catch(InterruptedException ex) { throw new IllegalStateException(ex); }
            });
            result[0].get(5,TimeUnit.SECONDS);
            assertThrows(PlatformSessionException.class,()->sessions.check(l.claims));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentLogoutAndActivityAlwaysEndRevoked() throws Exception {
        Login l=login(user(true)); var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{ await(start); try { sessions.activity(l.claims); } catch(PlatformSessionException expected) { } });
            var b=pool.submit(()->{ await(start); sessions.logout(l.claims,null); });
            start.countDown(); a.get(5,TimeUnit.SECONDS); b.get(5,TimeUnit.SECONDS);
            assertThrows(PlatformSessionException.class,()->sessions.activity(l.claims));
        } finally { pool.shutdownNow(); }
    }
    @Test void sevenDayCleanupKeepsRecentAndActiveRecordsAndAllAuditRows() {
        Login old=login(user(true)),recent=login(user(true)),active=login(user(true));
        sessions.logout(old.claims,null);
        jdbc.update("update platform_sessions set revoked_at=clock_timestamp()-interval '8 days' where session_id=?",old.claims.sessionId());
        sessions.logout(recent.claims,null);
        long audit=jdbc.queryForObject("select count(*) from platform_audit_logs",Long.class);
        assertTrue(sessions.cleanup()>=1);
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_sessions where session_id=?",Integer.class,old.claims.sessionId()));
        assertEquals(2,jdbc.queryForObject("select count(*) from platform_sessions where session_id in (?,?)",Integer.class,recent.claims.sessionId(),active.claims.sessionId()));
        assertEquals(audit,jdbc.queryForObject("select count(*) from platform_audit_logs",Long.class));
        assertThrows(PlatformSessionException.class,()->sessions.check(old.claims));
    }
    @Test void mfaPasswordStepAndWrongCodeNeverCreateSession() throws Exception {
        long user=user(true); String email=jdbc.queryForObject("select normalized_email from platform_users where id=?",String.class,user);
        String challenge=json.readTree(postJson("/api/platform/auth/login",Map.of("email",email,"password",PASSWORD),null,200)).get("challengeToken").asText();
        String bad="000000".equals(totp())?"111111":"000000";
        postJson("/api/platform/auth/mfa/verify",Map.of("challengeToken",challenge,"code",bad),null,401);
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_sessions where platform_user_id=?",Integer.class,user));
        String token=json.readTree(postJson("/api/platform/auth/mfa/verify",Map.of("challengeToken",challenge,"code",totp()),null,200)).get("token").asText();
        assertNotNull(sessions.check(jwt.parsePlatformToken(token)));
        assertEquals(1,jdbc.queryForObject("select count(*) from platform_sessions where platform_user_id=?",Integer.class,user));
    }
    @Test void failedSessionCreationRollsBackMfaConsumptionAndSuccessAudit() throws Exception {
        long user=user(true); String email=jdbc.queryForObject("select normalized_email from platform_users where id=?",String.class,user);
        String challenge=json.readTree(postJson("/api/platform/auth/login",Map.of("email",email,"password",PASSWORD),null,200)).get("challengeToken").asText();
        org.mockito.Mockito.doThrow(new IllegalStateException("isolated session creation failure")).when(sessions)
            .createForBrowser(org.mockito.ArgumentMatchers.eq(user),org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.any());
        postJson("/api/platform/auth/mfa/verify",Map.of("challengeToken",challenge,"code",totp()),null,500);
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_sessions where platform_user_id=?",Integer.class,user));
        assertNull(jdbc.queryForObject("select consumed_at from platform_mfa_challenges where token_hash=?",OffsetDateTime.class,crypto.hashToken(challenge)));
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_audit_logs where platform_user_id=? and action='MFA_VERIFIED'",Integer.class,user));
    }
    @Test void enrollmentAndRecoveryCodeLoginRegisterNewIndependentSessions() throws Exception {
        long user=user(false); String email=jdbc.queryForObject("select normalized_email from platform_users where id=?",String.class,user);
        var step=json.readTree(postJson("/api/platform/auth/login",Map.of("email",email,"password",PASSWORD),null,200));
        var enrolled=json.readTree(postJson("/api/platform/auth/mfa/enroll/confirm",Map.of("challengeToken",step.get("challengeToken").asText(),"code",totp(step.get("enrollmentSecret").asText())),null,200));
        var first=jwt.parsePlatformToken(enrolled.get("token").asText()); assertNotNull(sessions.check(first));
        String recovery=enrolled.get("recoveryCodes").get(0).asText();
        var next=json.readTree(postJson("/api/platform/auth/login",Map.of("email",email,"password",PASSWORD),null,200));
        var recovered=json.readTree(postJson("/api/platform/auth/mfa/verify",Map.of("challengeToken",next.get("challengeToken").asText(),"recoveryCode",recovery),null,200));
        var second=jwt.parsePlatformToken(recovered.get("token").asText());
        assertNotEquals(first.sessionId(),second.sessionId()); assertNotNull(sessions.check(first)); assertNotNull(sessions.check(second));
    }
    @Test void logoutAllMfaAndIdleChallengeBoundaryAreEnforced() throws Exception {
        long user=user(true); Login a=login(user),b=login(user);
        String challenge=json.readTree(postJson("/api/platform/auth/logout-all/challenge",Map.of(),a.token,200)).get("challengeToken").asText();
        idle(a);
        postJson("/api/platform/auth/logout-all",Map.of("challengeToken",challenge,"code",totp()),a.token,401);
        assertNotNull(sessions.check(b.claims));
        String fresh=json.readTree(postJson("/api/platform/auth/logout-all/challenge",Map.of(),b.token,200)).get("challengeToken").asText();
        postJson("/api/platform/auth/logout-all",Map.of("challengeToken",fresh,"code",totp()),b.token,200);
        assertThrows(PlatformSessionException.class,()->sessions.check(b.claims));
    }
    Login browserLogin(long id, jakarta.servlet.http.Cookie cookie) {
        var request=new org.springframework.mock.web.MockHttpServletRequest(); request.setCookies(cookie);
        String email=jdbc.queryForObject("select normalized_email from platform_users where id=?",String.class,id);
        long version=jdbc.queryForObject("select security_version from platform_users where id=?",Long.class,id);
        var w=sessions.createForBrowser(id,version,request);
        String token=jwt.generatePlatformToken(id,email,List.of("PLATFORM_SUPER_ADMIN"),version,w.sessionId(),w.startedAt().toInstant().toEpochMilli(),w.expiresAt().toInstant().toEpochMilli());
        return new Login(id,email,token,jwt.parsePlatformToken(token));
    }
    @Test void browserLogoutRevokesEverySiblingButNotAnotherBrowserOrAccount() throws Exception {
        long id=user(true); var cookie=browserCookie();
        Login a=browserLogin(id,cookie),b=browserLogin(id,cookie),otherBrowser=login(id),otherAccount=browserLogin(user(true),cookie);
        var bBefore=sessions.check(b.claims); var otherBefore=sessions.check(otherBrowser.claims);
        postJson("/api/platform/auth/logout",Map.of("browserId","attacker-selected-value"),a.token,204);
        for(Login rejected:List.of(a,b)) {
            mvc.perform(get("/api/platform/auth/me").header("Authorization","Bearer "+rejected.token)).andExpect(status().isUnauthorized());
            postJson("/api/platform/auth/session/activity",Map.of(),rejected.token,401);
        }
        assertNotNull(sessions.check(otherAccount.claims));
        assertEquals(otherBefore.lastActivityAt(),sessions.check(otherBrowser.claims).lastActivityAt());
        assertEquals(bBefore.idleExpiresAt(),jdbc.queryForObject("select idle_expires_at from platform_sessions where session_id=?",OffsetDateTime.class,b.claims.sessionId()));
        assertEquals(2,jdbc.queryForObject("select (detail_json::jsonb->>'sessionsRevoked')::int from platform_audit_logs where platform_user_id=? and detail_json::jsonb->>'scope'='BROWSER_PLATFORM_SESSIONS'",Integer.class,id));
        assertEquals(1L,jdbc.queryForObject("select security_version from platform_users where id=?",Long.class,id));
    }
    @Test void browserGroupAndAuditRollbackTogether() {
        long id=user(true); var cookie=browserCookie(); Login a=browserLogin(id,cookie),b=browserLogin(id,cookie);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            sessions.logout(a.claims,null); throw new IllegalStateException("rollback browser logout");
        }));
        assertNotNull(sessions.check(a.claims)); assertNotNull(sessions.check(b.claims));
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_audit_logs where platform_user_id=?",Integer.class,id));
    }
    @Test void siblingActivityAndTwoConcurrentLogoutsCannotDeadlockOrReviveAnySibling() throws Exception {
        long id=user(true); var cookie=browserCookie(); Login a=browserLogin(id,cookie),b=browserLogin(id,cookie);
        var pool=Executors.newFixedThreadPool(3); var start=new CountDownLatch(1);
        try {
            var futures=List.of(pool.submit(()->{await(start); try {sessions.logout(a.claims,null);} catch(PlatformSessionException expected) {}}),
                pool.submit(()->{await(start); try {sessions.logout(b.claims,null);} catch(PlatformSessionException expected) {}}),
                pool.submit(()->{await(start); try {sessions.activity(b.claims);} catch(PlatformSessionException expected) {}}));
            start.countDown(); for(var future:futures) future.get(5,TimeUnit.SECONDS);
            assertThrows(PlatformSessionException.class,()->sessions.activity(a.claims));
            assertThrows(PlatformSessionException.class,()->sessions.activity(b.claims));
        } finally {pool.shutdownNow();}
    }
    @Test void newMfaLoginAfterBrowserLogoutIsNewSessionNotResurrectedOldRows() throws Exception {
        long id=user(true); var cookie=browserCookie(); Login old=browserLogin(id,cookie); sessions.logout(old.claims,null);
        var step=json.readTree(postJson("/api/platform/auth/login",Map.of("email",old.email,"password",PASSWORD),null,200));
        var verified=json.readTree(postJson("/api/platform/auth/mfa/verify",Map.of("challengeToken",step.get("challengeToken").asText(),"code",totp()),null,200));
        var fresh=jwt.parsePlatformToken(verified.get("token").asText());
        assertNotEquals(old.claims.sessionId(),fresh.sessionId());
        assertNotNull(sessions.check(fresh)); assertThrows(PlatformSessionException.class,()->sessions.check(old.claims));
        assertEquals(1,jdbc.queryForObject("select count(distinct browser_binding_hash) from platform_sessions where session_id in (?,?)",Integer.class,old.claims.sessionId(),fresh.sessionId()));
    }
    @Test void concurrentLoginAndBrowserLogoutAreSerializedAndNeverReviveOldRows() throws Exception {
        long id=user(true); var cookie=browserCookie(); Login old=browserLogin(id,cookie);
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var logout=pool.submit(()->{await(start); sessions.logout(old.claims,null);});
            var login=pool.submit(()->{await(start); return browserLogin(id,cookie);});
            start.countDown(); logout.get(5,TimeUnit.SECONDS); Login fresh=login.get(5,TimeUnit.SECONDS);
            assertThrows(PlatformSessionException.class,()->sessions.check(old.claims));
            assertNotEquals(old.claims.sessionId(),fresh.claims.sessionId());
            int revoked=jdbc.queryForObject("select (detail_json::jsonb->>'sessionsRevoked')::int from platform_audit_logs where platform_user_id=? and detail_json::jsonb->>'operation'='BROWSER_LOGOUT'",Integer.class,id);
            if(revoked==2) assertThrows(PlatformSessionException.class,()->sessions.check(fresh.claims));
            else {assertEquals(1,revoked); assertNotNull(sessions.check(fresh.claims));}
        } finally {pool.shutdownNow();}
    }
    @Test void missingBrowserBindingRollsBackCompletedMfaAndAllowsRetryAfterInitialization() throws Exception {
        long id=user(true); String email=jdbc.queryForObject("select normalized_email from platform_users where id=?",String.class,id);
        String challenge=json.readTree(postJson("/api/platform/auth/login",Map.of("email",email,"password",PASSWORD),null,200)).get("challengeToken").asText();
        var body=Map.of("challengeToken",challenge,"code",totp());
        mvc.perform(post("/api/platform/auth/mfa/verify").contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("errorKey").value("PLATFORM_BROWSER_BINDING_REQUIRED"));
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_sessions where platform_user_id=?",Integer.class,id));
        assertNull(jdbc.queryForObject("select consumed_at from platform_mfa_challenges where token_hash=?",OffsetDateTime.class,crypto.hashToken(challenge)));
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_audit_logs where platform_user_id=? and action='MFA_VERIFIED'",Integer.class,id));
        postJson("/api/platform/auth/mfa/verify",body,null,200);
    }
    @Test void groupingCookieAloneNeverAuthenticatesAPlatformRequest() throws Exception {
        mvc.perform(post("/api/platform/auth/browser-session").header("X-Platform-Browser-Init","1"))
            .andExpect(status().isNoContent()).andExpect(header().exists("Set-Cookie"));
        mvc.perform(get("/api/platform/auth/me").cookie(browserCookie())).andExpect(status().isForbidden());
    }
    private static void await(CountDownLatch latch) { try { latch.await(); } catch(InterruptedException ex) { throw new IllegalStateException(ex); } }
    String totp() throws Exception { return totp(SECRET); }
    String totp(String secret) throws Exception {
        byte[] key=new org.apache.commons.codec.binary.Base32().decode(secret);
        var mac=javax.crypto.Mac.getInstance("HmacSHA1"); mac.init(new javax.crypto.spec.SecretKeySpec(key,"HmacSHA1"));
        byte[] bytes=mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(java.time.Instant.now().getEpochSecond()/30).array());
        int offset=bytes[19]&15;
        int binary=((bytes[offset]&127)<<24)|((bytes[offset+1]&255)<<16)|((bytes[offset+2]&255)<<8)|(bytes[offset+3]&255);
        return String.format(java.util.Locale.ROOT,"%06d",binary%1_000_000);
    }
}
