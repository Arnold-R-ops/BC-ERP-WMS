package com.wms.system.tenant.security;

import com.wms.system.WmsSystemApplication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="p3.r2.database-authorized",matches="true")
@SpringBootTest(classes={WmsSystemApplication.class,TenantSecuritySpringPostgresTest.NoJobs.class},properties="spring.config.location=classpath:platform-r2-isolation.properties")
@org.springframework.test.context.ContextConfiguration(initializers=com.wms.platformverification.PlatformR2SpringInitializer.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class TenantSecuritySpringPostgresTest {
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
    @Autowired PlatformTransactionManager transactions;
    @Autowired TenantCredentialCrypto credentials;
    @Autowired com.wms.system.repository.IntegrationConfigRepository configs;
    @Autowired com.wms.system.tenant.repository.ChannelWebhookRouteRepository routes;
    @Autowired TenantWebhookInboxService inbox;
    @Autowired TenantWebhookInboxWorker worker;
    @Autowired TenantConnectionSafetyService connections;
    @Autowired com.wms.system.tenant.config.TenantSecurityProperties settings;
    @Autowired com.wms.system.service.ShopifyWebhookService webhooks;
    @Autowired com.wms.system.security.JwtUtil jwt;
    @Autowired com.wms.system.platform.service.PlatformSessionService platformSessions;
    @Autowired com.wms.system.tenant.web.TenantSecurityAdmissionFilter admissionFilter;
    @Autowired TenantSecurityAlertService alerts;
    @Autowired PlatformTenantDataSafety platformReads;
    @org.springframework.boot.test.mock.mockito.MockBean org.springframework.mail.javamail.JavaMailSender mail;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.wms.system.signup.service.SignupVerificationService signup;
    @Autowired com.wms.system.signup.service.TenantProvisioningService provisioning;
    @Autowired com.wms.system.signup.service.SessionHandoffService handoff;
    @Autowired com.wms.system.signup.mail.DevelopmentVerificationMailbox mailbox;
    @Autowired com.wms.system.subscription.repository.SubscriptionPlanRepository plans;
    @Autowired com.wms.system.signup.repository.SignupRequestRepository signupRows;
    @Autowired com.wms.system.platform.service.PlatformMfaCrypto mfa;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired com.wms.system.service.ChannelRawEventService rawEvents;
    @Autowired com.wms.system.platform.service.PlatformExportWorker exportWorker;
    @Autowired com.wms.system.platform.repository.PlatformExportJobRepository exportJobs;
    @Autowired com.wms.system.platform.config.PlatformAccessProperties exportSettings;
    com.wms.platformverification.PlatformFixtureScope fixtures;
    @BeforeEach void beginOwnedFixtures(TestInfo info) {
        fixtures=com.wms.platformverification.PlatformFixtureScope.open(jdbc,transactions,getClass().getName(),info.getTestMethod().orElseThrow().getName());
    }
    @AfterEach void restoreSettings() { settings.setInboxMaxEventsPerTenant(10000); settings.setWebhookPerTenantPerMinute(300); settings.setInboxMaxBytesPerTenant(104857600); settings.setControlsEnabled(false); if(fixtures!=null) fixtures.close(); }
    long newTenant() {
        return fixtures.ownTenant(jdbc.queryForObject("insert into tenants(tenant_code,display_name,slug,status,timezone,locale,version) values(?,?,?,'ACTIVE','UTC','en-US',0) returning id",Long.class,"P3-"+java.util.UUID.randomUUID(),"P3 smoke","p3-"+java.util.UUID.randomUUID().toString().substring(0,20)));
    }
    String platformToken(long user, String email) {
        fixtures.ownUser(user);
        var window = platformSessions.create(user, 1);
        return jwt.generatePlatformToken(user,email,java.util.List.of("PLATFORM_SUPER_ADMIN"),1L,
            window.sessionId(),window.startedAt().toInstant().toEpochMilli(),window.expiresAt().toInstant().toEpochMilli());
    }
    com.wms.system.tenant.model.ChannelWebhookRoute connection(long tenant) {
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,1L)) {
            var config=com.wms.system.entity.IntegrationConfig.builder().storeUrl("p3-"+tenant+".myshopify.com").clientId("dummy-client").clientSecret("dummy-signing").accessToken("dummy-token").isActive(true).build();
            configs.saveAndFlush(config);
            String stored=new TransactionTemplate(transactions).execute(tx->jdbc.queryForObject("select client_secret from integration_configs where id=?",String.class,config.getId()));
            assertTrue(stored.startsWith("tc1."));
            assertEquals("dummy-signing",configs.findById(config.getId()).orElseThrow().getClientSecret());
            routes.saveAndFlush(com.wms.system.tenant.model.ChannelWebhookRoute.builder().integrationConfigId(config.getId()).tenantId(tenant).platform("SHOPIFY").canonicalStoreIdentifier(config.getStoreUrl()).signingSecret("dummy-signing").active(true).build());
            return routes.findById(config.getId()).orElseThrow();
        }
    }
    @Test void trustedSuspendedIntakeCapsDuplicatesAndLegalHoldCopies() {
        long tenant=newTenant(); var route=connection(tenant);
        jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?",tenant);
        settings.setInboxMaxEventsPerTenant(1);
        assertEquals(TenantWebhookInboxService.Outcome.ACCEPTED,inbox.accept(route,"orders/create","receipt-a","{\"id\":1}"));
        var expiry=jdbc.queryForObject("select expires_at from tenant_webhook_inbox where tenant_id=?",java.sql.Timestamp.class,tenant);
        assertEquals(TenantWebhookInboxService.Outcome.DUPLICATE,inbox.accept(route,"orders/create","receipt-a","{\"id\":1}"));
        assertEquals(expiry,jdbc.queryForObject("select expires_at from tenant_webhook_inbox where tenant_id=?",java.sql.Timestamp.class,tenant));
        assertEquals(TenantWebhookInboxService.Outcome.CONFLICT,inbox.accept(route,"orders/create","receipt-a","{\"id\":2}"));
        assertEquals(TenantWebhookInboxService.Outcome.OVER_LIMIT,inbox.accept(route,"orders/create","receipt-b","{}"));
        long id=jdbc.queryForObject("select id from tenant_webhook_inbox where tenant_id=?",Long.class,tenant);
        worker.processOne(id);
        assertEquals("PENDING",jdbc.queryForObject("select state from tenant_webhook_inbox where id=?",String.class,id));
        jdbc.update("update tenant_webhook_inbox set legal_hold=true,expires_at=current_timestamp-interval '1 second' where id=?",id);
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,Long.toString(tenant));
            jdbc.update("insert into channel_raw_events(company_id,channel,store_identifier,source,event_type,payload,status,source_inbox_id) values(?,'SHOPIFY',?,'WEBHOOK','ORDER','{\"id\":1}'::jsonb,'FAILED',?)",tenant,route.getCanonicalStoreIdentifier(),id);
        });
        inbox.expireDueBodies(); assertNotNull(jdbc.queryForObject("select payload from tenant_webhook_inbox where id=?",String.class,id));
        jdbc.update("update tenant_webhook_inbox set legal_hold=false where id=?",id);
        inbox.expireDueBodies(); assertNull(jdbc.queryForObject("select payload from tenant_webhook_inbox where id=?",String.class,id));
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.queryForObject("select set_config('app.company_id',?,true)",String.class,Long.toString(tenant));
            assertEquals("{}",jdbc.queryForObject("select payload::text from channel_raw_events where source_inbox_id=?",String.class,id));
            jdbc.update("update channel_raw_events set payload='{\"stale\":true}'::jsonb where source_inbox_id=?",id);
            assertEquals("{}",jdbc.queryForObject("select payload::text from channel_raw_events where source_inbox_id=?",String.class,id));
        });
    }
    @Test void compromisedCredentialCannotPassReviewOrBeTrustedUntilChanged() {
        long tenant=newTenant(); var route=connection(tenant); String incident=java.util.UUID.randomUUID().toString();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.queryForList("select id from tenants where id=? for update",tenant);
            jdbc.update("update tenants set status='SUSPENDED',session_version=2,security_incident_id=?,suspension_kind='SECURITY',suspended_at=current_timestamp where id=?",incident,tenant);
            connections.quarantine(tenant,incident,null);
        });
        assertEquals(TenantWebhookInboxService.Outcome.UNTRUSTED,inbox.accept(route,"orders/create","old-key","{}"));
        var draft=new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.RECORD_CONNECTION_REVIEW,"Manual provider evidence",null,null,route.getIntegrationConfigId(),"ROTATION_VERIFIED",true);
        assertThrows(TenantAccessException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->connections.recordReview(tenant,incident,1,draft)));
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,1L)) {
            assertThrows(TenantAccessException.class,()->configs.findById(route.getIntegrationConfigId()));
        }
    }
    @Test void signedOldSessionsRefreshRoleSwitchAndRestrictedEntryNeverRevive() throws Exception {
        long tenant=newTenant();
        String old=jwt.generateTenantToken(99L,tenant,"dummy-user","TENANT_ADMIN",java.util.List.of("TENANT_ADMIN"),1L,System.currentTimeMillis(),1L);
        jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?",tenant);
        for(String path:java.util.List.of("/api/auth/refresh","/api/auth/switch-role","/api/company-access/status","/api/inventory")) assertRejected(path,old,401);
        String limited=jwt.generateTenantToken(99L,tenant,"dummy-user","TENANT_ADMIN",java.util.List.of("TENANT_ADMIN"),1L,System.currentTimeMillis(),2L);
        assertRejected("/api/inventory",limited,423);
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/company-access/status"); request.addHeader("Authorization","Bearer "+limited);
        var response=new org.springframework.mock.web.MockHttpServletResponse(); var called=new java.util.concurrent.atomic.AtomicBoolean();
        admissionFilter.doFilter(request,response,(req,res)->called.set(true)); assertTrue(called.get());
        jdbc.update("update tenants set status='ACTIVE',session_version=3 where id=?",tenant);
        assertRejected("/api/inventory",old,401); assertRejected("/api/company-access/status",limited,401);
        assertNull(TenantExecutionScope.current());
    }
    void assertRejected(String path,String token,int status) throws Exception {
        var request=new org.springframework.mock.web.MockHttpServletRequest("POST",path); request.addHeader("Authorization","Bearer "+token);
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        admissionFilter.doFilter(request,response,(req,res)->fail("Rejected work entered controller"));
        assertEquals(status,response.getStatus());
    }
    @Test void platformSuspendedReadRequiresReasonWhileTenantExportIsDenied() {
        long tenant=newTenant(); jdbc.update("update tenants set status='SUSPENDED',session_version=2,suspension_kind='SECURITY' where id=?",tenant);
        assertThrows(TenantAccessException.class,()->platformReads.auditContext(tenant,"%20"));
        var context=platformReads.auditContext(tenant,java.net.URLEncoder.encode("安全排查",java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("安全排查",context.get("accessReason")); assertEquals("SECURITY",context.get("suspensionKind"));
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.PLATFORM_READ,null)) {
            new TransactionTemplate(transactions).executeWithoutResult(tx->assertNotNull(jdbc.queryForObject("select id from tenants where id=?",Long.class,tenant)));
        }
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,2L)) {
            assertThrows(TenantAccessException.class,()->new TransactionTemplate(transactions).execute(tx->"export"));
        }
    }
    @Test void realPlatformExportRequiresSuspensionReasonAndRechecksDownloadAndActor() throws Exception {
        long tenant=newTenant();
        String email=java.util.UUID.randomUUID()+"@p3.invalid";
        long actor=jdbc.queryForObject("insert into platform_users(normalized_email,password_hash,display_name) values(?,'dummy-hash','Dummy export reviewer') returning id",Long.class,email);
        fixtures.ownUser(actor);
        jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) select ?,id from platform_roles where role_code='PLATFORM_SUPER_ADMIN'",actor);
        String token=platformToken(actor,email);
        jdbc.update("update tenants set status='SUSPENDED',session_version=2,suspension_kind='SECURITY' where id=?",tenant);
        String base="/api/platform/companies/"+tenant;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/exports").header("Authorization","Bearer "+token).contentType("application/json").content("{\"resource\":\"users\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("select count(*) from platform_export_jobs where target_tenant_id=?",Integer.class,tenant));
        String reason="Security investigation 100% verified",header=java.net.URLEncoder.encode(reason,java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base+"/data/users").header("Authorization","Bearer "+token).header(PlatformTenantDataSafety.REASON_HEADER,header))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var taskRunner=new com.wms.system.platform.service.PlatformExportTestConfiguration.TrackingExecutor(Runnable::run);
        // Preserve this synchronous real-worker scenario; track completion and the exact owned file.
        try(var directory=taskRunner.begin(exportSettings,fixtures.owner(),java.time.Duration.ofSeconds(10))) {
        var job=exportJobs.saveAndFlush(com.wms.system.platform.model.PlatformExportJob.builder().publicId(java.util.UUID.randomUUID().toString()).platformUserId(actor).targetTenantId(tenant).status("PENDING").requestedResource("users").accessReason(reason).expiresAt(java.time.OffsetDateTime.now().plusHours(1)).build());
        // Page sign-out does not cancel already submitted jobs; downloads still require a fresh login.
        platformSessions.logout(jwt.parsePlatformToken(token),null);
        directory.bind(job.getPublicId());
        taskRunner.execute(()->exportWorker.run(job.getId()));
        directory.awaitWorker();
        var completed=exportJobs.findById(job.getId()).orElseThrow();
        assertEquals("COMPLETED",completed.getStatus());
        assertEquals(directory.artifact(job.getPublicId()),java.nio.file.Path.of(completed.getFilePath()).toAbsolutePath().normalize());
        assertTrue(java.nio.file.Files.isRegularFile(directory.artifact(job.getPublicId())));
        exportWorker.run(job.getId());
        assertEquals(1,jdbc.queryForObject("select count(*) from platform_audit_logs where target_tenant_id=? and action='EXPORT' and result='SUCCESS'",Integer.class,tenant));
        String detail=jdbc.queryForObject("select detail_json::text from platform_audit_logs where target_tenant_id=? and action='EXPORT' and result='SUCCESS'",String.class,tenant);
        assertEquals(reason,json.readTree(detail).path("safety").path("accessReason").asText());
        String download=base+"/exports/"+job.getPublicId()+"/download";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(download).header("Authorization","Bearer "+token).header(PlatformTenantDataSafety.REASON_HEADER,header))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        token=platformToken(actor,email);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(download).header("Authorization","Bearer "+token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        var file=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(download).header("Authorization","Bearer "+token).header(PlatformTenantDataSafety.REASON_HEADER,header))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(file.length>2); assertEquals('P',file[0]); assertEquals('K',file[1]);
        }
        jdbc.update("update platform_users set enabled=false where id=?",actor);
        try(var directory=taskRunner.begin(exportSettings,fixtures.owner(),java.time.Duration.ofSeconds(10))) {
        var revoked=exportJobs.saveAndFlush(com.wms.system.platform.model.PlatformExportJob.builder().publicId(java.util.UUID.randomUUID().toString()).platformUserId(actor).targetTenantId(tenant).status("PENDING").requestedResource("users").accessReason(reason).expiresAt(java.time.OffsetDateTime.now().plusHours(1)).build());
        directory.bind(revoked.getPublicId());
        taskRunner.execute(()->exportWorker.run(revoked.getId()));
        directory.awaitWorker();
        assertEquals("FAILED",exportJobs.findById(revoked.getId()).orElseThrow().getStatus());
        assertNull(exportJobs.findById(revoked.getId()).orElseThrow().getFilePath());
        }
    }
    @Test void webhookRateAndBodyBudgetsDoNotAcknowledgeUnstoredEvents() {
        long tenant=newTenant(); var route=connection(tenant); settings.setWebhookPerTenantPerMinute(1);
        assertEquals(TenantWebhookInboxService.Outcome.ACCEPTED,inbox.accept(route,"orders/create","a","{}"));
        assertEquals(TenantWebhookInboxService.Outcome.OVER_LIMIT,inbox.accept(route,"orders/create","b","{}"));
        assertEquals(1,jdbc.queryForObject("select count(*) from tenant_webhook_inbox where tenant_id=?",Integer.class,tenant));
        settings.setWebhookPerTenantPerMinute(300); settings.setInboxMaxBytesPerTenant(2);
        assertEquals(TenantWebhookInboxService.Outcome.OVER_LIMIT,inbox.accept(route,"orders/create","c","{}"));
        assertEquals(1,jdbc.queryForObject("select count(*) from tenant_security_alerts where tenant_id=? and issue_key='WEBHOOK_OVER_LIMIT'",Integer.class,tenant));
    }
    @Test void postResumeBacklogIsNotBlindlyReplayed() {
        long tenant=newTenant(); var route=connection(tenant);
        assertEquals(TenantWebhookInboxService.Outcome.ACCEPTED,inbox.accept(route,"orders/cancelled","a","{\"id\":1}"));
        jdbc.update("update tenants set session_version=3,security_resumed_at=current_timestamp where id=?",tenant);
        long id=jdbc.queryForObject("select id from tenant_webhook_inbox where tenant_id=?",Long.class,tenant);
        worker.processOne(id);
        assertEquals("REVIEW_REQUIRED",jdbc.queryForObject("select state from tenant_webhook_inbox where id=?",String.class,id));
        assertNotNull(jdbc.queryForObject("select payload from tenant_webhook_inbox where id=?",String.class,id));
    }
    @Test void alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate() throws Exception {
        long tenant=newTenant();
        long actor=jdbc.queryForObject("insert into platform_users(normalized_email,password_hash,display_name) values(?,'dummy-hash','Dummy recipient') returning id",Long.class,java.util.UUID.randomUUID()+"@p3.invalid");
        fixtures.ownUser(actor);
        jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) select ?,id from platform_roles where role_code='PLATFORM_SUPER_ADMIN'",actor);
        var previousRecipients=java.util.List.copyOf(settings.getAlertRecipientIds());
        boolean previousControls=settings.isControlsEnabled();
        String recipient=jdbc.queryForObject("select normalized_email from platform_users where id=?",String.class,actor);
        var reject=new java.util.concurrent.atomic.AtomicBoolean(true);
        try {
        settings.setAlertRecipientIds(java.util.List.of(actor)); settings.setControlsEnabled(true);
        alerts.record(tenant,"TEST_MAIL","No payload included.");
        long id=jdbc.queryForObject("select id from tenant_security_alerts where tenant_id=?",Long.class,tenant);
        org.mockito.Mockito.doAnswer(invocation->{
            SpringSecurityFixture.requireOwnedMail(invocation.getArgument(0),id,tenant,recipient);
            if(reject.get()) throw new org.springframework.mail.MailSendException("Simulated failure");
            return null;
        }).when(mail).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        try(var locked=SpringSecurityFixture.AlertLocks.acquire(jdbc.getDataSource(),id,tenant)) {
        for(int attempt=0;attempt<4;attempt++) {
            jdbc.update("update tenant_security_alerts set next_mail_at=current_timestamp where id=? and tenant_id=?",id,tenant); alerts.deliverDue();
            var row=jdbc.queryForMap("select * from tenant_security_alerts where id=?",id);
            assertEquals(attempt+1,((Number)row.get("mail_attempts")).intValue());
            if(attempt<3) assertEquals(new int[]{300,900,3600}[attempt],java.time.Duration.between(((java.sql.Timestamp)row.get("first_mail_failure_at")).toInstant(),((java.sql.Timestamp)row.get("next_mail_at")).toInstant()).getSeconds());
            else assertEquals("MANUAL_REQUIRED",row.get("mail_status"));
        }
        alerts.record(tenant,"TEST_MAIL","No payload included.",3);
        assertNotNull(jdbc.queryForObject("select next_mail_at from tenant_security_alerts where id=?",java.sql.Timestamp.class,id));
        reject.set(false); alerts.deliverDue();
        assertEquals("SUBMITTED",jdbc.queryForObject("select mail_status from tenant_security_alerts where id=?",String.class,id));
        assertEquals(21600,jdbc.queryForObject("select extract(epoch from next_mail_at-last_mail_at)::bigint from tenant_security_alerts where id=?",Long.class,id));
        }
        } finally {
            org.mockito.Mockito.reset(mail);
            settings.setAlertRecipientIds(previousRecipients);
            settings.setControlsEnabled(previousControls);
        }
    }
    @Test void webhookHttpOnlyAcknowledgesCommittedTrustedReceipt() throws Exception {
        long tenant=newTenant(); var route=connection(tenant); String body="{\"id\":1}";
        var mac=javax.crypto.Mac.getInstance("HmacSHA256"); mac.init(new javax.crypto.spec.SecretKeySpec("dummy-signing".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=java.util.Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?",tenant);
        settings.setInboxMaxEventsPerTenant(1);
        for(int n=0;n<2;n++) mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/webhooks/shopify").contentType("application/json").content(body)
            .header("X-Shopify-Shop-Domain",route.getCanonicalStoreIdentifier()).header("X-Shopify-Topic","orders/create").header("X-Shopify-Webhook-Id","http-"+n).header("X-Shopify-Hmac-Sha256",signature))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(n==0?200:429));
        assertEquals(1,jdbc.queryForObject("select count(*) from tenant_webhook_inbox where tenant_id=?",Integer.class,tenant));
    }
    @Test void provisionAndLoginHandoffPreserveErpAndCannotReusePrePauseCode() throws Exception {
        var trial=com.wms.system.subscription.model.PlanCode.TRIAL;
        SpringSecurityFixture.requireTrial(plans);
        String suffix=java.util.UUID.randomUUID().toString().substring(0,8), email="p3-"+suffix+"@p3.invalid";
        var begun=signup.begin(new com.wms.system.signup.dto.BeginSignupRequest(email,trial),java.util.UUID.randomUUID().toString());
        signup.verify(new com.wms.system.signup.dto.VerifyEmailRequest(begun.signupId(),mailbox.latest(email).orElseThrow().code()));
        var details=new com.wms.system.signup.dto.CompleteSignupRequest("P3","Test","Disposable tenant","p3-test-"+suffix,"P3-test-only-123456");
        var completed=provisioning.complete(begun.signupId(),details);
        long tenant=signupRows.findByPublicId(begun.signupId()).orElseThrow().getTenantId();
        fixtures.ownTenant(tenant);
        var context=new com.wms.system.tenant.context.TenantContext(tenant,"p3-test-"+suffix,"p3-test-"+suffix+".bcwms.com",com.wms.system.tenant.context.RequestSurface.TENANT,com.wms.system.tenant.model.TenantStatus.ACTIVE);
        var session=com.wms.system.tenant.context.TenantContextHolder.runWithTenant(context,()->handoff.consume(completed.handoffCode()));
        assertEquals(tenant,jwt.parseTenantToken(session.getToken()).companyId());
        var retry=provisioning.complete(begun.signupId(),details);
        jdbc.update("update tenants set session_version=3,security_resumed_at=current_timestamp where id=?",tenant);
        assertThrows(TenantAccessException.class,()->com.wms.system.tenant.context.TenantContextHolder.runWithTenant(context,()->handoff.consume(retry.handoffCode())));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/company-access/status").header("Authorization","Bearer "+session.getToken()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
    @Test void platformHttpTwoPersonRecoveryCommitsWithRealAuditRepository() throws Exception {
        long tenant=newTenant();
        String[] tokens=new String[2]; long[] actors=new long[2];
        for(int i=0;i<2;i++) {
            String email=java.util.UUID.randomUUID()+"@p3.invalid";
            actors[i]=jdbc.queryForObject("insert into platform_users(normalized_email,password_hash,display_name,enabled,security_version,mfa_enabled,mfa_secret_encrypted) values(?,?,?,true,1,true,?) returning id",Long.class,email,passwords.encode("P3-http-password-123"),"P3 HTTP reviewer",mfa.encrypt("JBSWY3DPEHPK3PXP"));
            fixtures.ownUser(actors[i]);
            jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) select ?,id from platform_roles where role_code='PLATFORM_SUPER_ADMIN'",actors[i]);
            tokens[i]=platformToken(actors[i],email);
        }
        settings.setControlsEnabled(true); settings.setAlertRecipientIds(java.util.List.of(actors[0]));
        var pause=httpCommand(tenant,tokens[0],new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.SUSPEND,"Verified security incident",null,java.util.List.of(),null,null,null));
        assertEquals("SUSPENDED",pause.path("status").asText());
        var request=httpCommand(tenant,tokens[0],new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.REQUEST_RESUME,"Risk resolved",null,null,null,null,null));
        String requestId=request.path("requestId").asText();
        var approve=new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.APPROVE_RESUME,"Independent review passed",requestId,null,null,null,null);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/platform/tenants/"+tenant+"/security/challenges").header("Authorization","Bearer "+tokens[0]).contentType("application/json").content(json.writeValueAsBytes(approve)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        platformSessions.logout(jwt.parsePlatformToken(tokens[0]),null);
        // The submitted request survives its initiator's session ending.
        assertEquals("PENDING",jdbc.queryForObject("select status from tenant_security_requests where public_id=?",String.class,requestId));
        var result=httpCommand(tenant,tokens[1],approve);
        assertEquals("ACTIVE",result.path("status").asText()); assertEquals(3,result.path("session_version").asInt());
        assertEquals("APPLIED",jdbc.queryForObject("select status from tenant_security_requests where public_id=?",String.class,requestId));
        assertEquals(3,jdbc.queryForObject("select count(*) from platform_audit_logs where target_tenant_id=? and action='OPERATION_EXECUTED' and result='SUCCESS'",Integer.class,tenant));
    }
    com.fasterxml.jackson.databind.JsonNode httpCommand(long tenant,String token,TenantSecurityCommand.Draft draft) throws Exception {
        String base="/api/platform/tenants/"+tenant+"/security";
        var challenge=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/challenges").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsBytes(draft)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String raw=json.readTree(challenge.getResponse().getContentAsString()).path("challengeToken").asText();
        var method=com.wms.system.platform.service.PlatformMfaCrypto.class.getDeclaredMethod("totp",String.class,long.class); method.setAccessible(true);
        String code=(String)method.invoke(mfa,"JBSWY3DPEHPK3PXP",java.time.Instant.now().getEpochSecond()/30);
        String key=java.util.UUID.randomUUID().toString();
        var command=java.util.Map.of("idempotencyKey",key,"challengeToken",raw,"password","P3-http-password-123","code",code,"draft",draft);
        var applied=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/commands").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsBytes(command)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var queried=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base+"/commands/"+key).header("Authorization","Bearer "+token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        assertEquals(json.readTree(applied.getResponse().getContentAsString()),json.readTree(queried.getResponse().getContentAsString()));
        return json.readTree(applied.getResponse().getContentAsString());
    }
    @Test void failedConnectionStaysDisabledAndGapNeedsCurrentVerifiedEvidence() {
        long tenant=newTenant(); var route=connection(tenant); long config=route.getIntegrationConfigId(); String incident=java.util.UUID.randomUUID().toString();
        long actor=jdbc.queryForObject("insert into platform_users(normalized_email,password_hash,display_name) values(?,'dummy-hash','Dummy evidence reviewer') returning id",Long.class,java.util.UUID.randomUUID()+"@p3.invalid");
        fixtures.ownUser(actor);
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            jdbc.queryForList("select id from tenants where id=? for update",tenant);
            jdbc.update("update tenants set status='SUSPENDED',session_version=2,security_incident_id=?,suspension_kind='SECURITY',suspended_at=current_timestamp where id=?",incident,tenant);
            connections.quarantine(tenant,incident,null);
            connections.replaceCredentials(tenant,incident,new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.REPLACE_CONNECTION_CREDENTIALS,"Manual rotated credentials",null,null,config,null,null,new TenantSecurityCommand.CredentialUpdate("new-dummy-signing","new-dummy-token"),null,null));
            connections.recordReview(tenant,incident,actor,new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.RECORD_CONNECTION_REVIEW,"Old provider credential revoked; connection check failed",null,null,config,"ROTATION_VERIFIED",false));
            connections.requireRiskCleared(tenant,incident);
            jdbc.update("update tenants set status='ACTIVE',session_version=3,security_resumed_at=current_timestamp where id=?",tenant);
            connections.applyResume(tenant);
        });
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,3L)) {
            var disabled=configs.findById(config).orElseThrow(); assertFalse(disabled.getIsActive());
            assertThrows(TenantAccessException.class,()->connections.requireBusiness(disabled));
        }
        assertEquals("REAUTH_REQUIRED",jdbc.queryForObject("select state from tenant_security_connections where tenant_id=? and config_id=?",String.class,tenant,config));
        new TransactionTemplate(transactions).executeWithoutResult(tx->connections.recordReview(tenant,incident,actor,new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.RECORD_CONNECTION_REVIEW,"Provider credentials changed; independent connectivity check now passed",null,null,config,"ROTATION_VERIFIED",true)));
        assertEquals(TenantWebhookInboxService.Outcome.UNTRUSTED,inbox.accept(route,"orders/create","stale-signature","{}"));
        var liveRoute=routes.findById(config).orElseThrow();
        assertEquals(TenantWebhookInboxService.Outcome.ACCEPTED,inbox.accept(liveRoute,"orders/create","new-signature","{}"));
        assertThrows(TenantAccessException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->connections.recordReconciliation(tenant,incident,new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.RECORD_RECONCILIATION,"Unproven claim",null,null,config,null,null))));
        long report;
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,3L)) {
            assertTrue(configs.findById(config).orElseThrow().getIsActive());
            report=new TransactionTemplate(transactions).execute(tx->jdbc.queryForObject("insert into channel_raw_events(company_id,channel,store_identifier,source,event_type,payload,status) values(?,'SHOPIFY',?,'RECONCILE','RECONCILE_REPORT',?::jsonb,'PROCESSED') returning id",Long.class,tenant,route.getCanonicalStoreIdentifier(),"{\"configId\":"+config+",\"missingOrderCount\":0,\"updatedAtMin\":\"2020-01-01T00:00:00Z\"}"));
        }
        new TransactionTemplate(transactions).executeWithoutResult(tx->connections.recordReconciliation(tenant,incident,new TenantSecurityCommand.Draft(TenantSecurityCommand.Action.RECORD_RECONCILIATION,"Missing orders checked; cancellations, modifications and refunds manually reconciled",null,null,config,null,null,null,report,null)));
        assertEquals("RECONCILED",jdbc.queryForObject("select gap_status from tenant_security_connections where tenant_id=? and config_id=?",String.class,tenant,config));
    }
    @Test void rolledBackBusinessCannotLeaveASuccessfulRawEventDedupMarker() {
        long tenant=newTenant(); var route=connection(tenant); String eventId="raw-"+java.util.UUID.randomUUID();
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,1L)) {
            assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
                var event=rawEvents.recordWebhook("SHOPIFY",route.getCanonicalStoreIdentifier(),"ORDER_CANCELLED","1","{\"id\":1}",eventId);
                rawEvents.markProcessed(event.getId());
                throw new IllegalStateException("Simulated business rollback after status update");
            }));
            assertFalse(rawEvents.webhookAlreadyReceived(eventId));
            var first=rawEvents.recordWebhook("SHOPIFY",route.getCanonicalStoreIdentifier(),"ORDER_CANCELLED","1","{\"id\":1}",eventId);
            var retry=rawEvents.recordWebhook("SHOPIFY",route.getCanonicalStoreIdentifier(),"ORDER_CANCELLED","1","{\"id\":1}",eventId);
            assertEquals(first.getId(),retry.getId());
            new TransactionTemplate(transactions).executeWithoutResult(tx->rawEvents.markProcessed(first.getId()));
            assertTrue(rawEvents.webhookAlreadyReceived(eventId));
        }
    }
    @Test void fullApplicationWiresAndRealJpaAdmissionRejectsOldWork() {
        assertInstanceOf(com.wms.system.tenant.persistence.CompanyRlsJpaTransactionManager.class,transactions);
        long tenant=jdbc.queryForObject("insert into tenants(tenant_code,display_name,slug,status,timezone,locale,version) values(?,?,?,'ACTIVE','UTC','en-US',0) returning id",Long.class,"P3-"+java.util.UUID.randomUUID(),"P3 smoke","p3-"+java.util.UUID.randomUUID().toString().substring(0,20));
        fixtures.ownTenant(tenant);
        try(var scope=TenantExecutionScope.open(tenant,TenantOperationPurpose.BUSINESS,1L)) {
            Long bound=new TransactionTemplate(transactions).execute(tx->jdbc.queryForObject("select current_setting('app.company_id')",Long.class));
            assertEquals(Long.valueOf(tenant),bound);
            jdbc.update("update tenants set status='SUSPENDED',session_version=2 where id=?",tenant);
            assertThrows(TenantAccessException.class,()->new TransactionTemplate(transactions).execute(tx->1));
        }
        String encrypted=credentials.protect("dummy-secret");
        assertNotEquals("dummy-secret",encrypted); assertEquals("dummy-secret",credentials.reveal(encrypted));
    }
}
