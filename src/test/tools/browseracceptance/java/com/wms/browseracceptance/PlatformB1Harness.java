package com.wms.browseracceptance;

import com.wms.system.WmsSystemApplication;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformUserRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.Filter;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Separate test-only live harness; never added to product classes or its jar. */
public final class PlatformB1Harness {
    static final Path ROOT=Path.of("D:/ERP_WMS/BC warehouse/2G");
    static final Path RUN=ROOT.resolve("target/platform-browser-invitation-20260920/b4");
    static final Path PREPARATION=ROOT.resolve("target/platform-b4-preparation-20260920/a1");
    static final Path PRIVATE=RUN.resolve("private");
    static final String A="inviter-b4@platform.invalid", B="invitee-b4@platform.invalid";
    static volatile boolean ready;
    static Properties secrets;

    public static void main(String[] args) {
        var diagnostic=new PlatformBackendDiagnostic.Trace(System.err);
        try {
            diagnostic.enter(PlatformBackendDiagnostic.Stage.ARGUMENTS);
            if(args.length!=0 || !Path.of("").toAbsolutePath().normalize().equals(ROOT)) throw new IllegalStateException("B1_ARGUMENTS");
            diagnostic.enter(PlatformBackendDiagnostic.Stage.CREDENTIALS_READ);
            secrets=new Properties();
            try(var r=Files.newBufferedReader(PRIVATE.resolve("runtime.properties"),StandardCharsets.UTF_8)){secrets.load(r);}
            diagnostic.enter(PlatformBackendDiagnostic.Stage.ISOLATION_CONFIG);
            var settings=PlatformB1Isolation.settings(PlatformB1Isolation.configuration(),false,PlatformB1Isolation.MAIN);
            // Guard before Spring construction, then guard every subsequent datasource connection.
            diagnostic.enter(PlatformBackendDiagnostic.Stage.DATABASE_IDENTITY);
            try(var c=PlatformB1Isolation.guarded(settings,()->java.sql.DriverManager.getConnection(PlatformB1Isolation.url(settings.database()),settings.role(),settings.password())).getConnection()) { }
            diagnostic.enter(PlatformBackendDiagnostic.Stage.SPRING_CONFIGURATION);
            SpringApplication app=new SpringApplication(WmsSystemApplication.class,Isolation.class);
            var config=configuration(secrets);
            app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("immutable-b1-isolation",config)));
            diagnostic.enter(PlatformBackendDiagnostic.Stage.SPRING_START);
            try(var context=app.run("--spring.config.location=file:"+PREPARATION.resolve("snapshot/platform-b1-isolation.properties").toString().replace('\\','/'),"--spring.profiles.active=platform-b1-isolation")) {
                diagnostic.enter(PlatformBackendDiagnostic.Stage.SYNTHETIC_ACCOUNT_PREPARATION);
                seed(context.getBean(PlatformUserRepository.class),context.getBean(PasswordEncoder.class),context.getBean(JdbcTemplate.class),context.getBean(PlatformTransactionManager.class));
                ready=true;
                diagnostic.enter(PlatformBackendDiagnostic.Stage.READY);
                System.out.println("B1_BACKEND_READY");
                diagnostic.enter(PlatformBackendDiagnostic.Stage.WAIT_STOP);
                while(!Files.exists(RUN.resolve("backend.stop"))) Thread.sleep(200);
                if(!Files.readString(RUN.resolve("backend.stop")).equals(secrets.getProperty("instance"))) throw new IllegalStateException("B1_STOP_IDENTITY");
                ready=false;
                diagnostic.enter(PlatformBackendDiagnostic.Stage.SPRING_CLOSE);
            }
            diagnostic.enter(PlatformBackendDiagnostic.Stage.COMPLETE);
            System.out.println("B1_BACKEND_CLOSED");
        }catch(Throwable failure){
            diagnostic.failure(failure);
            System.exit(1);
        }
    }
    static Map<String,Object> configuration(Properties s){
        Map<String,Object> p=new HashMap<>();
        p.put("server.address","127.0.0.1");p.put("server.port",18092);
        p.put("server.shutdown","graceful");p.put("spring.lifecycle.timeout-per-shutdown-phase","20s");
        p.put("spring.datasource.url",PlatformB1Isolation.url(PlatformB1Isolation.MAIN));p.put("spring.datasource.username",PlatformB1Isolation.APP);
        p.put("spring.datasource.password",Objects.requireNonNull(System.getenv("P3_B4_APP_PASSWORD")));
        p.put("spring.flyway.enabled",false);p.put("spring.jpa.hibernate.ddl-auto","validate");p.put("spring.sql.init.mode","never");
        p.put("spring.jpa.open-in-view",false);p.put("spring.main.allow-bean-definition-overriding",false);
        p.put("spring.jpa.hibernate.naming.physical-strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        p.put("spring.jpa.hibernate.naming.implicit-strategy","org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl");
        p.put("jwt.expiration",86400000);p.put("jwt.platform-expiration",43200000);
        p.put("wms.platform-bootstrap.enabled",false);p.put("wms.legacy-admin-bootstrap-enabled",false);
        p.put("wms.tenancy.enabled",false);p.put("wms.tenancy.allow-local-development-host",true);
        p.put("wms.tenancy.require-safe-database-role",true);p.put("wms.tenancy.purge-enabled",false);
        p.put("wms.integration.shopify.scheduler-enabled",false);p.put("wms.integration.shopify.reconcile-enabled",false);
        p.put("wms.tenant-security.controls-enabled",false);p.put("wms.tenant-security.webhook-worker-enabled",false);
        p.put("wms.platform-access.export-directory",PRIVATE.resolve("exports").toString());
        p.put("batch.hashids.salt","b4-isolation-only");p.put("batch.hashids.min-length",6);
        p.put("server.error.include-message","never");p.put("server.error.include-stacktrace","never");
        p.put("server.tomcat.accesslog.enabled",false);p.put("spring.main.banner-mode","off");
        p.put("logging.level.root","OFF");p.put("spring.jpa.show-sql",false);p.put("spring.devtools.restart.enabled",false);
        for(String key:List.of("jwt.tenant-secret","jwt.platform-secret","wms.platform-mfa.encryption-key","wms.tenant-security.credential-encryption-key"))p.put(key,Objects.requireNonNull(s.getProperty(key)));
        return Map.copyOf(p);
    }
    static void seed(PlatformUserRepository users,PasswordEncoder encoder,JdbcTemplate jdbc,PlatformTransactionManager tx){
        new TransactionTemplate(tx).executeWithoutResult(t->{
            if(jdbc.queryForObject("select count(*) from platform_users",Long.class)!=0L
                ||jdbc.queryForObject("select count(*) from platform_admin_invitations",Long.class)!=0L)throw new IllegalStateException("B1_UNKNOWN_ACCOUNT_RESIDUE");
            for(String role:List.of("PLATFORM_SUPER_ADMIN","PLATFORM_SECURITY_AUDITOR"))
                if(jdbc.queryForObject("select count(*) from platform_roles where role_code=?",Long.class,role)!=1L)throw new IllegalStateException("B1_REFERENCE_ROLE_MISSING");
            var a=users.saveAndFlush(PlatformUser.builder().normalizedEmail(A).displayName("[B4 ISOLATED] Inviter A")
                .passwordHash(encoder.encode(secrets.getProperty("password.a"))).build());
            if(jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) select ?,id from platform_roles where role_code='PLATFORM_SUPER_ADMIN'",a.getId())!=1)throw new IllegalStateException("B1_SEED_ROLE_FAILED");
        });
    }
    @Configuration(proxyBeanMethods=false)
    public static class Isolation {
        @Bean DataSource dataSource(){return PlatformB1Isolation.runtimeDataSource();}
        @Bean static BeanDefinitionRegistryPostProcessor disableScheduling(){return new BeanDefinitionRegistryPostProcessor(){
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry r){String n="org.springframework.context.annotation.internalScheduledAnnotationProcessor";if(r.containsBeanDefinition(n))r.removeBeanDefinition(n);}
            public void postProcessBeanFactory(ConfigurableListableBeanFactory f){}
        };}
        @Bean static BeanPostProcessor noExternalHttp(){return new BeanPostProcessor(){
            public Object postProcessAfterInitialization(Object bean,String name){if(bean instanceof RestTemplate t)t.getInterceptors().add((request,body,execution)->{throw new IOException("B1_EXTERNAL_HTTP_DISABLED");});return bean;}
        };}
        @Bean @Primary JavaMailSender noMail(){return new JavaMailSenderImpl(){
            @Override public void send(MimeMessage... messages){throw new org.springframework.mail.MailSendException("B1_REAL_MAIL_DISABLED");}
            @Override public void send(SimpleMailMessage... messages){throw new org.springframework.mail.MailSendException("B1_REAL_MAIL_DISABLED");}
        };}
        @Bean FilterRegistrationBean<Filter> isolationBoundary(){
            FilterRegistrationBean<Filter> bean=new FilterRegistrationBean<>((request,response,chain)->{
                var r=(HttpServletRequest)request;var out=(HttpServletResponse)response;
                out.setHeader("Cache-Control","no-store");out.setHeader("Referrer-Policy","no-referrer");
                if("/__isolation/health".equals(r.getRequestURI()) && "GET".equals(r.getMethod())){
                    out.setStatus(ready?200:503);out.setContentType("application/json");
                    out.getWriter().write("{\"ready\":"+ready+",\"database\":\"p3_platform_browser_b4\",\"port\":18092,\"instance\":\""+secrets.getProperty("instance")+"\"}");return;
                }
                String u=r.getRequestURI();
                if(!ready||!(u.startsWith("/api/platform/auth/")||u.startsWith("/api/platform/admin")||u.startsWith("/api/platform/audit")
                    ||("GET".equals(r.getMethod())&&u.equals("/api/platform/access-grants/me")))){out.setStatus(404);return;}
                chain.doFilter(request,response);
            });bean.setOrder(Ordered.HIGHEST_PRECEDENCE);return bean;
        }
    }
}
