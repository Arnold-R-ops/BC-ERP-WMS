package com.wms.browseracceptance;

import com.wms.system.WmsSystemApplication;
import com.wms.system.platform.model.PlatformUser;
import com.wms.system.platform.repository.PlatformUserRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Test-only live HTTP harness. Not scanned by the production application or packaged in its jar. */
public final class PlatformBrowserHarness {
    static final Path ROOT = Path.of("D:/ERP_WMS/BC warehouse/2G");
    static final Path RUN = ROOT.resolve("target/platform-session-browser");
    static final Path PRIVATE = RUN.resolve("private");
    static final String URL = "jdbc:postgresql://127.0.0.1:55439/p3_m1_platform_browser";
    static final List<String> VERSIONS = List.of("4.71","4.72","4.85","4.86","4.87","4.88","4.89","4.90");
    static volatile boolean ready;

    public static void main(String[] arguments) throws Exception {
        if (!Path.of("").toAbsolutePath().normalize().equals(ROOT)) throw new IllegalStateException("Wrong isolated working directory");
        if (arguments.length == 1 && "validate-template".equals(arguments[0])) {
            // Original template deliberately remains V4_89. Rehearsal verifier checks that
            // its sole pending increment is V4_90 and that the tested scope DB has applied it.
            PlatformBrowserScopeDatabase.main(new String[]{"verify"});
            return;
        }
        if (arguments.length != 0) throw new IllegalArgumentException("No runtime connection overrides accepted");
        validate(URL);
        if (!Files.isDirectory(PRIVATE)) throw new IllegalStateException("Create restricted private directory first");
        Properties secrets = secrets();
        Map<String,Object> config = configuration(secrets);
        System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication application = new SpringApplication(WmsSystemApplication.class, Isolation.class);
        application.addInitializers(context -> context.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("immutable-platform-browser-isolation", config)));
        var context = application.run("--spring.config.location=classpath:platform-browser-isolation.properties",
            "--spring.profiles.active=platform-browser-isolation");
        try {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            if (!"p3_m1_platform_browser".equals(jdbc.queryForObject("select current_database()",String.class)))
                throw new IllegalStateException("Wrong connected database");
            seed(context.getBean(PlatformUserRepository.class), context.getBean(PasswordEncoder.class), jdbc,
                context.getBean(PlatformTransactionManager.class), secrets);
            ready = true;
            Files.writeString(RUN.resolve("backend.pid"), Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
            System.out.println("ISOLATION_READY 127.0.0.1:18091 -> 127.0.0.1:55439/p3_m1_platform_browser; migrations disabled; synthetic accounts only");
        } catch (Exception failure) {
            context.close();
            throw failure;
        }
    }

    static void validate(String url) throws Exception {
        if (!URL.equals(url) && !"jdbc:postgresql://127.0.0.1:55439/p3_m1_platform_session".equals(url))
            throw new IllegalStateException("Unapproved database");
        try (Connection connection = DriverManager.getConnection(url,"p3_m1_app", "")) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                try (ResultSet row = statement.executeQuery("select current_database(),host(inet_server_addr()),inet_server_port(),current_user,rolsuper,rolbypassrls from pg_roles where rolname=current_user")) {
                    if (!row.next() || !url.endsWith("/" + row.getString(1)) || !"127.0.0.1".equals(row.getString(2))
                            || row.getInt(3)!=55439 || !"p3_m1_app".equals(row.getString(4)) || row.getBoolean(5) || row.getBoolean(6))
                        throw new IllegalStateException("Database identity or runtime role mismatch");
                }
                List<String> actual = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("select version,success from flyway_schema_history order by installed_rank")) {
                    while (rows.next()) {
                        if (!rows.getBoolean(2)) throw new IllegalStateException("Unsuccessful migration; STOP, do not migrate");
                        actual.add(rows.getString(1));
                    }
                }
                if (!actual.equals(VERSIONS)) throw new IllegalStateException("Migration sequence mismatch; STOP, do not migrate");
            }
        }
        // Check source checksums as well as the version list. No migrate(), clean(), repair() or baseline().
        Flyway.configure().dataSource(url,"p3_m1_app", "")
            .locations("filesystem:" + ROOT.resolve("src/main/resources/db/migration").toString().replace('\\','/'))
            .load().validate();
    }

    static Properties secrets() throws IOException {
        Path file = PRIVATE.resolve("runtime-secrets.properties");
        Properties values = new Properties();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file,StandardCharsets.UTF_8)) { values.load(reader); }
        } else {
            for (String key : List.of("jwt.tenant-secret","jwt.platform-secret","wms.platform-mfa.encryption-key","wms.tenant-security.credential-encryption-key"))
                values.setProperty(key, random(32));
            for (String account : List.of("a","b","c")) values.setProperty("password."+account,"Uat!7a"+random(18));
            try (var writer = Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                values.store(writer,"PRIVATE disposable browser acceptance secrets. Never copy into evidence or reports.");
            }
        }
        return values;
    }

    static String random(int length) {
        byte[] bytes = new byte[length]; new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    static Map<String,Object> configuration(Properties secrets) {
        Map<String,Object> p = new HashMap<>();
        p.put("server.address","127.0.0.1"); p.put("server.port",18091);
        p.put("spring.datasource.url",URL); p.put("spring.datasource.username","p3_m1_app"); p.put("spring.datasource.password","");
        p.put("spring.datasource.hikari.maximum-pool-size",8); p.put("spring.datasource.hikari.minimum-idle",1);
        p.put("spring.datasource.hikari.data-source-properties.ApplicationName","platform-session-browser-isolated");
        p.put("spring.flyway.enabled",false); p.put("spring.jpa.hibernate.ddl-auto","validate"); p.put("spring.sql.init.mode","never");
        p.put("spring.jpa.open-in-view",false); p.put("spring.main.allow-bean-definition-overriding",false);
        p.put("spring.jpa.hibernate.naming.physical-strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        p.put("spring.jpa.hibernate.naming.implicit-strategy","org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl");
        p.put("jwt.expiration",86400000); p.put("jwt.platform-expiration",43200000);
        p.put("wms.platform-bootstrap.enabled",false); p.put("wms.legacy-admin-bootstrap-enabled",false);
        p.put("wms.tenancy.enabled",false); p.put("wms.tenancy.allow-local-development-host",true);
        p.put("wms.tenancy.require-safe-database-role",true); p.put("wms.tenancy.purge-enabled",false);
        p.put("wms.integration.shopify.scheduler-enabled",false); p.put("wms.integration.shopify.reconcile-enabled",false);
        p.put("wms.tenant-security.controls-enabled",false); p.put("wms.tenant-security.webhook-worker-enabled",false);
        p.put("wms.platform-access.export-directory",PRIVATE.resolve("exports").toString());
        p.put("batch.hashids.salt","platform-browser-only-not-production"); p.put("batch.hashids.min-length",6);
        p.put("server.error.include-message","never"); p.put("server.error.include-stacktrace","never");
        p.put("server.tomcat.accesslog.enabled",false); p.put("logging.level.root","WARN");
        p.put("logging.level.com.wms.system.controller.GlobalExceptionHandler","OFF");
        p.put("logging.level.org.springframework.web","WARN"); p.put("spring.main.banner-mode","off");
        for (String key : List.of("jwt.tenant-secret","jwt.platform-secret","wms.platform-mfa.encryption-key","wms.tenant-security.credential-encryption-key"))
            p.put(key,Objects.requireNonNull(secrets.getProperty(key),"Missing private isolation key"));
        return Map.copyOf(p);
    }

    static void seed(PlatformUserRepository users, PasswordEncoder passwords, JdbcTemplate jdbc,
                     PlatformTransactionManager transactions, Properties secrets) throws IOException {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            for (String account : List.of("a","b","c")) {
                String email = "session-uat-" + account + "@platform.invalid";
                if (users.findByNormalizedEmail(email).isPresent()) continue; // Never reset MFA/passwords on restart.
                var user = users.saveAndFlush(PlatformUser.builder().normalizedEmail(email)
                    .passwordHash(passwords.encode(secrets.getProperty("password."+account)))
                    .displayName("[ISOLATED UAT] " + account.toUpperCase(Locale.ROOT)).build());
                String role = account.equals("c") ? "PLATFORM_OPERATIONS_ADMIN" : "PLATFORM_SUPER_ADMIN";
                if (jdbc.update("insert into platform_user_roles(platform_user_id,platform_role_id) select ?,id from platform_roles where role_code=?",user.getId(),role)!=1)
                    throw new IllegalStateException("Required test role missing");
            }
            jdbc.update("""
                insert into tenants(tenant_code,display_name,slug,status,timezone,locale,version)
                values('BROWSER-SESSION-UAT','[ISOLATED UAT] Synthetic company','browser-session-uat','ACTIVE','UTC','en-US',0)
                on conflict(tenant_code) do nothing
                """);
        });
        StringBuilder instructions = new StringBuilder("# 仅供本机隔离验收的临时凭据（禁止复制进报告）\n\n入口：http://127.0.0.1:15173/platform.html\n\n第一步只使用 A 账号登录；出现 MFA 绑定页面后停下。\n\n");
        for (String account : List.of("a","b","c")) {
            instructions.append("## ").append(account.toUpperCase(Locale.ROOT)).append(" 账号\n\n邮箱：session-uat-").append(account)
                .append("@platform.invalid\n\n密码：").append(secrets.getProperty("password."+account)).append("\n\n");
        }
        instructions.append("不要提供密码、MFA 二维码/密钥、原始 JWT 或恢复码截图。此文件不通过验收网站提供，结束后单独失效处理。\n");
        Files.writeString(PRIVATE.resolve("login-credentials.md"),instructions,StandardCharsets.UTF_8);
        Files.writeString(RUN.resolve("preparation.json"),"{\"preparedAt\":\""+Instant.now()+"\",\"database\":\"p3_m1_platform_browser\",\"migration\":\"4.90\",\"syntheticAccounts\":3,\"automaticMigrations\":false,\"automaticScheduling\":false}\n",StandardCharsets.UTF_8);
    }

    @Configuration(proxyBeanMethods=false)
    public static class Isolation {
        @Bean static BeanDefinitionRegistryPostProcessor disableAutomaticScheduling() {
            return new BeanDefinitionRegistryPostProcessor() {
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    String name="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                    if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
                }
                public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) { }
            };
        }
        @Bean static BeanPostProcessor noExternalHttp() {
            return new BeanPostProcessor() {
                public Object postProcessAfterInitialization(Object bean,String name) {
                    if (bean instanceof RestTemplate template) template.getInterceptors().add((request,body,execution)->{
                        throw new IOException("External HTTP is disabled in browser acceptance");
                    });
                    return bean;
                }
            };
        }
        @Bean @Primary JavaMailSender privateMailCapture() {
            return new JavaMailSenderImpl() {
                @Override public void send(MimeMessage... messages) {
                    try {
                        Path outbox=PRIVATE.resolve("outbox"); Files.createDirectories(outbox);
                        for (MimeMessage message:messages) try(var stream=Files.newOutputStream(outbox.resolve(UUID.randomUUID()+".eml"))) { message.writeTo(stream); }
                    } catch(Exception failure) { throw new org.springframework.mail.MailSendException("Local isolation mail capture failed"); }
                }
                @Override public void send(SimpleMailMessage... messages) {
                    try {
                        Path outbox=PRIVATE.resolve("outbox"); Files.createDirectories(outbox);
                        for(SimpleMailMessage message:messages) Files.writeString(outbox.resolve(UUID.randomUUID()+".txt"),Objects.toString(message.getText(),""));
                    } catch(IOException failure) { throw new org.springframework.mail.MailSendException("Local isolation mail capture failed"); }
                }
            };
        }
        @Bean FilterRegistrationBean<Filter> isolationBoundary() {
            FilterRegistrationBean<Filter> bean=new FilterRegistrationBean<>((request,response,chain)->{
                HttpServletRequest http=(HttpServletRequest)request;
                HttpServletResponse result=(HttpServletResponse)response;
                result.setHeader("Cache-Control","no-store");
                if ("/__isolation/health".equals(http.getRequestURI())) {
                    result.setStatus(ready?200:503); result.setContentType("application/json");
                    result.getWriter().write("{\"ready\":"+ready+",\"database\":\"p3_m1_platform_browser\",\"dbPort\":55439,\"backendPort\":18091,\"migration\":\"4.90\",\"automaticMigrations\":false,\"syntheticOnly\":true,\"browserScopedLogout\":true}");
                    return;
                }
                if (!ready || !http.getRequestURI().startsWith("/api/platform/")) { result.setStatus(404); return; }
                chain.doFilter(request,response);
            });
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE); return bean;
        }
    }
}
