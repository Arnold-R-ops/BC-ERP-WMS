package com.wms.system.identity;

import com.fasterxml.jackson.databind.*;
import com.wms.system.WmsSystemApplication;
import com.wms.system.signup.mail.DevelopmentVerificationMailbox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@EnabledIfSystemProperty(named="ledger.launch.authorized", matches="true")
@SpringBootTest(classes={WmsSystemApplication.class, LedgerLaunchIntegrationTest.NoJobs.class}, properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55519/ledger_launch_test_20261010",
    "spring.datasource.username=wms_upgrade_app", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false", "spring.flyway.enabled=true",
    "spring.jpa.open-in-view=false",
    "spring.flyway.user=postgres", "spring.flyway.password=",
    "spring.jpa.properties.hibernate.format_sql=false",
    "wms.tenancy.enabled=true", "wms.tenancy.require-safe-database-role=true", "wms.tenancy.allow-local-development-host=false",
    "wms.tenancy.base-domain=ledger.fongvo.com", "wms.tenancy.app-host=ledger.fongvo.com",
    "wms.tenancy.platform-host=ops.ledger.fongvo.com",
    "wms.platform-bootstrap.enabled=false", "wms.legacy-admin-bootstrap-enabled=false",
    "wms.signup.delivery-mode=DEVELOPMENT", "wms.tenant-security.controls-enabled=false",
    "jwt.tenant-secret=dGVzdC10ZW5hbnQtand0LXNlY3JldC1rZXktZm9yLWludGVncmF0aW9uLXRlc3Rpbmctb25seS0yMDI2",
    "jwt.platform-secret=dGVzdC1wbGF0Zm9ybS1qd3Qtc2VjcmV0LWtleS1mb3ItaW50ZWdyYXRpb24tdGVzdGluZy1vbmx5LTIwMjY=",
    "batch.hashids.salt=LEDGER_LAUNCH_TEST_ONLY", "logging.level.com.wms=WARN",
    "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerLaunchIntegrationTest {
    @TestConfiguration static class NoJobs {
        @Bean static BeanDefinitionRegistryPostProcessor disableJobs() {
            return new BeanDefinitionRegistryPostProcessor() {
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    String bean="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                    if (registry.containsBeanDefinition(bean)) registry.removeBeanDefinition(bean);
                }
                public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {}
            };
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DevelopmentVerificationMailbox mailbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @org.springframework.boot.test.mock.mockito.MockBean org.springframework.mail.javamail.JavaMailSender mail;
    String email, host, otherHost, password="LedgerTest123!";
    long tenantId, identityId, userId;
    String handoff, signupId;
    final String publicHost="ledger.fongvo.com";

    @BeforeAll void provisionThroughPublicApi() throws Exception {
        assertEquals("ledger_launch_test_20261010", jdbc.queryForObject("select current_database()",String.class));
        assertEquals(false, jdbc.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user",Boolean.class));
        String suffix=UUID.randomUUID().toString().substring(0,8);
        email="launch+"+suffix+"@example.com"; host="launch-"+suffix+".ledger.fongvo.com";
        handoff=signup(email,"launch-"+suffix);
        otherHost="other-"+suffix+".ledger.fongvo.com";
        signup("other+"+suffix+"@example.com","other-"+suffix);
        tenantId=jdbc.queryForObject("select id from tenants where slug=?",Long.class,"launch-"+suffix);
        identityId=jdbc.queryForObject("select id from user_identities where normalized_email=?",Long.class,email);
        userId=jdbc.queryForObject("select tenant_user_id from tenant_memberships where identity_id=?",Long.class,identityId);
        Files.createDirectories(Path.of("target/ledger-launch"));
        Files.writeString(Path.of("target/ledger-launch/test-account.local.json"),json.writeValueAsString(
            Map.of("email",email,"host",host,"password",password,"tenantId",tenantId,"otherHost",otherHost)));
    }
    String signup(String address,String slug) throws Exception {
        JsonNode begun=body(call(post("/public/v1/signup").header("Idempotency-Key",UUID.randomUUID().toString()),
            publicHost,Map.of("email",address,"planCode","TRIAL"),202));
        String id=begun.path("signupId").asText();
        if (address.equals(email)) signupId=id;
        call(post("/public/v1/email-verification"),publicHost,Map.of("signupId",id,"code",mailbox.latest(address).orElseThrow().code()),200);
        return body(call(post("/public/v1/signup/"+id+"/complete"),publicHost,
            Map.of("firstName","Launch","lastName","Test","companyName","Launch verification","slug",slug,"password",password),200))
            .path("handoffCode").asText();
    }
    @Test void discoveryReturnsRegisteredTenantAndGenericErrors() throws Exception {
        var found=call(post("/public/v1/account-finder"),publicHost,Map.of("email"," "+email.toUpperCase()+" "),200);
        assertEquals("https://"+host+"/login?email="+java.net.URLEncoder.encode(email,java.nio.charset.StandardCharsets.UTF_8),
            body(found).path("loginUrl").asText());
        assertEquals("no-store",found.getResponse().getHeader("Cache-Control"));
        call(post("/public/v1/account-finder"),publicHost,Map.of("email","missing@example.com"),404);
        call(post("/public/v1/account-finder"),publicHost,Map.of("email","x".repeat(255)+"@example.com"),400);
    }
    @Test void emailLoginAndUsernameLoginKeepCurrentPasswordAuthority() throws Exception {
        var login=body(call(post("/api/auth/login"),host,Map.of("username",email,"password",password),200));
        assertEquals("owner-"+identityId,login.path("username").asText());
        call(post("/api/auth/login"),host,Map.of("username","owner-"+identityId,"password",password),200);
        call(post("/api/auth/login"),host,Map.of("username",email,"password","wrong-password"),401);
        call(post("/api/auth/login"),otherHost,Map.of("username",email,"password",password),401);
        String original=jdbc.queryForObject("select password_hash from user_identities where id=?",String.class,identityId);
        try {
            jdbc.update("update user_identities set password_hash='unusable-stale-identity-hash' where id=?",identityId);
            call(post("/api/auth/login"),host,Map.of("username",email,"password",password),200);
        } finally { jdbc.update("update user_identities set password_hash=? where id=?",original,identityId); }
    }
    @Test void hostAndTokenSurfacesAreSeparatedAndForwardedHostCannotOverride() throws Exception {
        String token=login();
        call(get("/api/inventory").header("Authorization","Bearer "+token),otherHost,null,401);
        call(get("/api/inventory").header("Authorization","Bearer "+token),publicHost,null,404);
        call(get("/api/inventory").header("Authorization","Bearer "+token),"ops.ledger.fongvo.com",null,404);
        call(get("/api/platform/companies").header("Authorization","Bearer "+token),host,null,404);
        call(post("/public/v1/account-finder"),host,Map.of("email",email),404);
        call(post("/api/auth/login"),publicHost,Map.of("username",email,"password",password),404);
        for (String invalid:List.of("unknown.ledger.fongvo.com","ledger.fongvo.com.evil.invalid","localhost","127.0.0.1","a.b.ledger.fongvo.com")) {
            call(get("/public/v1/entry").header("X-Forwarded-Host",host),invalid,null,400);
        }
        call(post("/api/auth/refresh-token").header("Authorization","Bearer "+token).header("X-Forwarded-Host",otherHost),
            host,null,204);
    }
    @Test void revokedMembershipDisabledIdentityAndExpiredSubscriptionRejectExistingJwt() throws Exception {
        String token=login();
        try {
            jdbc.update("update tenant_memberships set status='REVOKED' where identity_id=?",identityId);
            call(post("/api/auth/refresh-token").header("Authorization","Bearer "+token),host,null,401);
            assertEquals("AUTH_INVALID_CREDENTIALS",body(call(post("/api/auth/login"),host,
                Map.of("username","owner-"+identityId,"password",password),401)).path("errorKey").asText());
        } finally { jdbc.update("update tenant_memberships set status='ACTIVE' where identity_id=?",identityId); }
        try {
            jdbc.update("update user_identities set enabled=false where id=?",identityId);
            call(post("/api/auth/refresh-token").header("Authorization","Bearer "+token),host,null,401);
        } finally { jdbc.update("update user_identities set enabled=true where id=?",identityId); }
        try {
            jdbc.update("update tenant_subscriptions set status='EXPIRED' where tenant_id=?",tenantId);
            call(post("/api/auth/refresh-token").header("Authorization","Bearer "+token),host,null,401);
            call(post("/public/v1/account-finder"),publicHost,Map.of("email",email),404);
        } finally { jdbc.update("update tenant_subscriptions set status='TRIALING' where tenant_id=?",tenantId); }
    }
    @Test void oneUseHandoffAndSessionRevocationRemainEffective() throws Exception {
        handoff=body(call(post("/public/v1/signup/"+signupId+"/complete"),publicHost,
            Map.of("firstName","Launch","lastName","Test","companyName","Launch verification",
                "slug",host.split("\\.")[0],"password",password),200)).path("handoffCode").asText();
        call(post("/public/v1/session-handoff/consume"),otherHost,Map.of("code",handoff),400);
        call(post("/public/v1/session-handoff/consume"),host,Map.of("code",handoff),200);
        call(post("/public/v1/session-handoff/consume"),host,Map.of("code",handoff),400);
        String token=login();
        call(post("/api/auth/revoke-all-sessions").header("Authorization","Bearer "+token),host,Map.of("currentPassword",password),204);
        call(post("/api/auth/refresh-token").header("Authorization","Bearer "+token),host,null,401);
        login();
    }
    @Test void missingTenantContextDoesNotFallBackToCompanyOne() {
        Long company=new TransactionTemplate(transactions).execute(tx ->
            jdbc.queryForObject("select current_setting('app.company_id')::bigint",Long.class));
        Integer count=new TransactionTemplate(transactions).execute(tx ->
            jdbc.queryForObject("select count(*) from users",Integer.class));
        assertEquals(Long.valueOf(-1),company);
        assertEquals(Integer.valueOf(0),count);
    }
    @Test void databaseRejectsOneIdentityInTwoCurrentLedgerTenants() {
        Long other=jdbc.queryForObject("select id from tenants where slug=?",Long.class,otherHost.split("\\.")[0]);
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
            jdbc.update("update tenant_memberships set identity_id=? where tenant_id=?",identityId,other));
    }
    @Test void signupCannotClaimConfiguredPlatformHostname() throws Exception {
        String address="reserved-"+UUID.randomUUID()+"@example.com";
        String id=body(call(post("/public/v1/signup").header("Idempotency-Key",UUID.randomUUID().toString()),
            publicHost,Map.of("email",address,"planCode","TRIAL"),202)).path("signupId").asText();
        call(post("/public/v1/email-verification"),publicHost,Map.of("signupId",id,"code",mailbox.latest(address).orElseThrow().code()),200);
        assertEquals("SIGNUP_SLUG_UNAVAILABLE",body(call(post("/public/v1/signup/"+id+"/complete"),publicHost,
            Map.of("firstName","Launch","lastName","Test","companyName","Reserved host test","slug","ops","password",password),409))
            .path("errorKey").asText());
    }
    @Test void emailLoginUsesNewPasswordAndOldSessionsAreInvalidAfterPasswordChange() throws Exception {
        String oldToken=login();
        String changed="LedgerChanged456!";
        call(put("/api/users/me/password").header("Authorization","Bearer "+oldToken),host,
            Map.of("oldPassword",password,"newPassword",changed),204);
        try {
            call(post("/api/auth/login"),host,Map.of("username",email,"password",password),401);
            call(post("/api/auth/refresh-token").header("Authorization","Bearer "+oldToken),host,null,401);
            call(post("/api/auth/login"),host,Map.of("username",email,"password",changed),200);
        } finally {
            String token=body(call(post("/api/auth/login"),host,Map.of("username",email,"password",changed),200)).path("token").asText();
            call(put("/api/users/me/password").header("Authorization","Bearer "+token),host,
                Map.of("oldPassword",changed,"newPassword",password),204);
        }
    }
    String login() throws Exception {
        return body(call(post("/api/auth/login"),host,Map.of("username",email,"password",password),200)).path("token").asText();
    }
    MvcResult call(MockHttpServletRequestBuilder request,String hostname,Object data,int expected) throws Exception {
        request.with(http -> { http.setServerName(hostname); http.setRemoteAddr("192.0.2.10"); return http; });
        if(data!=null) request.contentType("application/json").content(json.writeValueAsString(data));
        var result=mvc.perform(request).andReturn();
        assertEquals(expected,result.getResponse().getStatus(), request+" "+result.getResponse().getContentAsString());
        return result;
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
}
