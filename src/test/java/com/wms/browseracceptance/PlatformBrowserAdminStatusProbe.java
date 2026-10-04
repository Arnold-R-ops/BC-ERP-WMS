package com.wms.browseracceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.security.JwtProperties;
import com.wms.system.security.JwtUtil;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Fixed A/C synthetic browser acceptance only. No login, MFA, account mutations or DB writes. */
public final class PlatformBrowserAdminStatusProbe {
    private static final String ACTOR="session-uat-a@platform.invalid", TARGET="session-uat-c@platform.invalid";
    private static final Instant ACTOR_START=Instant.parse("2026-09-10T08:36:15Z");
    private static final Instant TARGET_START=Instant.parse("2026-09-10T08:48:49Z");
    private static final Instant ROLE_EARLIEST_START=Instant.parse("2026-09-10T12:40:33Z");
    private static final Path BASELINE=PlatformBrowserHarness.RUN.resolve("admin-c-status-http-baseline-20260910.json");
    private static final Path ROLE_BASELINE=PlatformBrowserHarness.RUN.resolve("admin-c-role-http-baseline-20260910.json");
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector() {
            public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            public void connectFailed(URI uri,SocketAddress address,IOException failure) { }
        }).build();

    private record Row(long userId,String email,boolean enabled,boolean mfa,long accountVersion,
                       UUID sessionId,long sessionVersion,Instant started,Instant last,Instant idle,
                       Instant absolute,Instant revoked,List<String> roles) { }

    public static void main(String[] args) {
        try {
            require(Path.of("").toAbsolutePath().normalize().equals(PlatformBrowserHarness.ROOT));
            require(args.length==1 && Set.of("before-disable","after-disable","after-reenable","before-role-change","after-role-change","auditor-relogin").contains(args[0]));
            PlatformBrowserHarness.validate(PlatformBrowserHarness.URL);
            require(!JSON.readTree(PlatformBrowserHarness.RUN.resolve("logout-transport-fault.json").toFile()).path("enabled").asBoolean());
            Path report=PlatformBrowserHarness.RUN.resolve("admin-c-status-http-"+args[0]+"-20260910.json");
            require(!Files.exists(report));
            try(Connection db=DriverManager.getConnection(PlatformBrowserHarness.URL,"p3_m1_app","")) {
                db.setReadOnly(true);
                if("auditor-relogin".equals(args[0])) verifyAuditorRelogin(db);
                else if(args[0].endsWith("role-change")) verifyRole(db,args[0]);
                else verify(db,args[0]);
            }
            JSON.writeValue(report.toFile(),Map.of("observedAt",Instant.now().toString(),
                "database","p3_m1_platform_browser","checks",CHECKS,"passed",true,
                "tokenSource","in-memory synthetic re-signing of fixed MFA-created sessions; not browser token capture"));
            System.out.println("ISOLATED_ADMIN_STATUS_PROBE_PASSED "+args[0]);
        } catch(Throwable failure) {
            // JWT/transport exceptions may contain credentials: print type only.
            System.err.println("ISOLATED_ADMIN_STATUS_PROBE_STOPPED type="+failure.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void verifyAuditorRelogin(Connection db) throws Exception {
        Instant started=Instant.parse("2026-09-10T13:30:12Z");
        Row target=read(db,TARGET,started);
        require(target.enabled && target.mfa && target.accountVersion==5 && target.sessionVersion==5);
        require(target.roles.equals(List.of("PLATFORM_SECURITY_AUDITOR")));
        requireUnexpired(target);
        require(currentOrFutureGrants(db,target.userId)==0 && audits(db,target.userId,"ADMIN_ROLES_CHANGED")==1);
        Properties keys=new Properties();
        try(var reader=Files.newBufferedReader(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"),StandardCharsets.UTF_8)) {
            keys.load(reader);
        }
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties); context.registerBean(JwtUtil.class); context.refresh();
            JwtUtil jwt=context.getBean(JwtUtil.class);
            String token=sign(jwt,target);
            require(jwt.parsePlatformToken(token).sessionId().equals(target.sessionId));
            check("auditor-new-session-me","GET","/api/platform/auth/me",token,200);
            check("auditor-admin-directory-read","GET","/api/platform/admins",token,200);
            check("auditor-audit-read","GET","/api/platform/audit-logs",token,200);
            check("auditor-grant-inventory-read","GET","/api/platform/access-grants/users",token,200);
            check("auditor-effective-access-read","GET","/api/platform/access-grants/me",token,200);
            check("auditor-tenant-directory-denied","GET","/api/platform/companies",token,403);
            check("auditor-super-invitation-list-denied","GET","/api/platform/admins/invitations",token,403);
        }
        require(read(db,TARGET,started).equals(target));
        require(currentOrFutureGrants(db,target.userId)==0 && audits(db,target.userId,"ADMIN_ROLES_CHANGED")==1);
        CHECKS.add(Map.of("case","new-auditor-session-account-and-role-mutation-unchanged","passed",true,
            "sessionStartedAt",started.toString(),"accountAndSessionVersion",5,"currentOrFutureGrants",0,
            "currentRoles",target.roles,"activityRequestsSent",false,
            "readAuditWritesExpected",true));
    }

    private static void verifyRole(Connection db,String mode) throws Exception {
        boolean before="before-role-change".equals(mode);
        Instant actorStart,targetStart;
        if(before) {
            require(!Files.exists(ROLE_BASELINE));
            actorStart=singleActiveRoleSessionStart(db,ACTOR);
            targetStart=singleActiveRoleSessionStart(db,TARGET);
        } else {
            var baseline=JSON.readTree(ROLE_BASELINE.toFile());
            actorStart=Instant.parse(baseline.path("actorSessionStartedAt").asText());
            targetStart=Instant.parse(baseline.path("targetSessionStartedAt").asText());
            require(!actorStart.isBefore(ROLE_EARLIEST_START) && !targetStart.isBefore(ROLE_EARLIEST_START));
            require(baseline.path("targetSessionVersion").asLong()==4);
            require(baseline.path("roleChangeAudits").asLong(-1)==0);
            require(baseline.path("originalRoleClaim").asText().equals("PLATFORM_OPERATIONS_ADMIN"));
        }
        // Pin both original sessions from the successful baseline; never switch to a fresh login after mutation.
        Row actor=read(db,ACTOR,actorStart),target=read(db,TARGET,targetStart);
        require(actor.enabled && actor.mfa && actor.accountVersion==4 && actor.sessionVersion==4);
        require(actor.roles.equals(List.of("PLATFORM_SUPER_ADMIN")));
        require(target.enabled && target.mfa && target.sessionVersion==4);
        require(target.accountVersion==(before?4:5));
        require(target.roles.equals(List.of(before?"PLATFORM_OPERATIONS_ADMIN":"PLATFORM_SECURITY_AUDITOR")));
        requireUnexpired(actor); requireUnexpired(target);
        long changed=audits(db,target.userId,"ADMIN_ROLES_CHANGED");
        require(changed==(before?0:1));
        require(audits(db,target.userId,"ADMIN_DISABLED")==1 && audits(db,target.userId,"ADMIN_ENABLED")==1);
        require(currentOrFutureGrants(db,target.userId)==0);
        Properties keys=new Properties();
        try(var reader=Files.newBufferedReader(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"),StandardCharsets.UTF_8)) {
            keys.load(reader);
        }
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties); context.registerBean(JwtUtil.class); context.refresh();
            JwtUtil jwt=context.getBean(JwtUtil.class);
            String actorToken=sign(jwt,actor);
            // Keep the original role/version claims, including after the persisted role changes.
            String targetToken=jwt.generatePlatformToken(target.userId,target.email,List.of("PLATFORM_OPERATIONS_ADMIN"),
                target.sessionVersion,target.sessionId,target.started.toEpochMilli(),target.absolute.toEpochMilli());
            require(jwt.parsePlatformToken(actorToken).sessionId().equals(actor.sessionId));
            require(jwt.parsePlatformToken(targetToken).sessionId().equals(target.sessionId));
            check("actor-me-unaffected","GET","/api/platform/auth/me",actorToken,200);
            check("actor-admin-directory-unaffected","GET","/api/platform/admins",actorToken,200);
            check("target-original-operations-session-me","GET","/api/platform/auth/me",targetToken,before?200:401);
            if(!before) check("target-old-role-session-cannot-renew","POST","/api/platform/auth/session/activity",targetToken,401);
        }
        require(read(db,ACTOR,actorStart).equals(actor) && read(db,TARGET,targetStart).equals(target));
        require(audits(db,target.userId,"ADMIN_ROLES_CHANGED")==changed);
        require(audits(db,target.userId,"ADMIN_DISABLED")==1 && audits(db,target.userId,"ADMIN_ENABLED")==1);
        require(currentOrFutureGrants(db,target.userId)==0);
        CHECKS.add(Map.of("case","role-session-account-and-grants-unchanged-by-probe","passed",true,
            "targetEnabled",true,"targetAccountVersion",target.accountVersion,"targetSessionVersion",4,
            "idleAndAbsoluteFutureAtProbeStart",true,"roleChangeAudits",changed,"currentOrFutureGrants",0,
            "originalRoleClaim","PLATFORM_OPERATIONS_ADMIN","currentRoles",target.roles));
        if(before) JSON.writeValue(ROLE_BASELINE.toFile(),Map.of("observedAt",Instant.now().toString(),
            "actorSessionStartedAt",actor.started.toString(),"targetSessionStartedAt",target.started.toString(),
            "targetSessionVersion",4,"roleChangeAudits",changed,"originalRoleClaim","PLATFORM_OPERATIONS_ADMIN",
            "targetIdleExpiresAt",target.idle.toString(),"targetAbsoluteExpiresAt",target.absolute.toString()));
    }

    private static Instant singleActiveRoleSessionStart(Connection db,String email) throws SQLException {
        require(Set.of(ACTOR,TARGET).contains(email));
        try(var query=db.prepareStatement("select s.started_at from platform_users u join platform_sessions s on s.platform_user_id=u.id where u.normalized_email=? and s.started_at>=? and u.enabled and u.mfa_enabled and u.security_version=4 and s.security_version=4 and s.browser_binding_hash is not null and s.revoked_at is null and s.idle_expires_at>now() and s.absolute_expires_at>now()")) {
            query.setString(1,email); query.setTimestamp(2,Timestamp.from(ROLE_EARLIEST_START));
            try(var row=query.executeQuery()) {
                require(row.next()); Instant started=instant(row,"started_at");
                require(!row.next()); return started;
            }
        }
    }

    private static long currentOrFutureGrants(Connection db,long targetId) throws SQLException {
        try(var query=db.prepareStatement("select count(*) from platform_access_grants where grantee_platform_user_id=? and revoked_at is null and expires_at>now()")) {
            query.setLong(1,targetId);
            try(var row=query.executeQuery()) { require(row.next()); return row.getLong(1); }
        }
    }

    private static void verify(Connection db,String mode) throws Exception {
        Row actor=read(db,ACTOR,ACTOR_START),target=read(db,TARGET,TARGET_START);
        require(actor.enabled && actor.mfa && actor.accountVersion==4 && actor.sessionVersion==4);
        require(actor.roles.equals(List.of("PLATFORM_SUPER_ADMIN")));
        require(target.mfa && target.sessionVersion==2 && target.revoked==null);
        require(target.roles.equals(List.of("PLATFORM_OPERATIONS_ADMIN")));
        requireUnexpired(actor); requireUnexpired(target);
        long disabled=audits(db,target.userId,"ADMIN_DISABLED"),enabled=audits(db,target.userId,"ADMIN_ENABLED");
        if("before-disable".equals(mode)) {
            require(!Files.exists(BASELINE));
            require(target.enabled && target.accountVersion==2 && disabled==0 && enabled==0);
        } else {
            var baseline=JSON.readTree(BASELINE.toFile());
            require(baseline.path("targetSessionStartedAt").asText().equals(TARGET_START.toString()));
            require(baseline.path("targetSessionVersion").asLong()==2);
            require(baseline.path("disableAudits").asLong(-1)==0 && baseline.path("enableAudits").asLong(-1)==0);
            boolean reenabled="after-reenable".equals(mode);
            require(target.enabled==reenabled && target.accountVersion==(reenabled?4:3));
            require(disabled==1 && enabled==(reenabled?1:0));
        }
        Properties keys=new Properties();
        try(var reader=Files.newBufferedReader(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"),StandardCharsets.UTF_8)) {
            keys.load(reader);
        }
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties); context.registerBean(JwtUtil.class); context.refresh();
            JwtUtil jwt=context.getBean(JwtUtil.class);
            String actorToken=sign(jwt,actor),targetToken=sign(jwt,target);
            require(jwt.parsePlatformToken(actorToken).sessionId().equals(actor.sessionId));
            require(jwt.parsePlatformToken(targetToken).sessionId().equals(target.sessionId));
            check("actor-me-unaffected","GET","/api/platform/auth/me",actorToken,200);
            check("actor-admin-directory-unaffected","GET","/api/platform/admins",actorToken,200);
            boolean before="before-disable".equals(mode);
            check("target-fixed-session-me","GET","/api/platform/auth/me",targetToken,before?200:401);
            if(!before) check("target-old-session-cannot-renew","POST","/api/platform/auth/session/activity",targetToken,401);
        }
        require(read(db,ACTOR,ACTOR_START).equals(actor) && read(db,TARGET,TARGET_START).equals(target));
        require(audits(db,target.userId,"ADMIN_DISABLED")==disabled && audits(db,target.userId,"ADMIN_ENABLED")==enabled);
        CHECKS.add(Map.of("case","session-account-and-status-audit-records-unchanged-by-probe","passed",true,
            "targetEnabled",target.enabled,"targetAccountVersion",target.accountVersion,"targetSessionVersion",target.sessionVersion,
            "idleAndAbsoluteFutureAtProbeStart",true,"disableAudits",disabled,"enableAudits",enabled));
        if("before-disable".equals(mode)) {
            JSON.writeValue(BASELINE.toFile(),Map.of("observedAt",Instant.now().toString(),
                "actorSessionStartedAt",actor.started.toString(),"targetSessionStartedAt",target.started.toString(),
                "targetSessionVersion",2,"disableAudits",disabled,"enableAudits",enabled,
                "targetIdleExpiresAt",target.idle.toString(),"targetAbsoluteExpiresAt",target.absolute.toString()));
        }
    }

    private static Row read(Connection db,String email,Instant start) throws SQLException {
        try(var query=db.prepareStatement("select u.id,u.enabled,u.mfa_enabled,u.security_version as account_version,s.session_id,s.security_version as session_version,s.started_at,s.last_activity_at,s.idle_expires_at,s.absolute_expires_at,s.revoked_at from platform_users u join platform_sessions s on s.platform_user_id=u.id where u.normalized_email=? and s.started_at=? and s.browser_binding_hash is not null")) {
            query.setString(1,email); query.setTimestamp(2,Timestamp.from(start));
            try(var row=query.executeQuery()) {
                require(row.next());
                long userId=row.getLong("id");
                List<String> roles=new ArrayList<>();
                try(var roleQuery=db.prepareStatement("select r.role_code from platform_user_roles ur join platform_roles r on r.id=ur.platform_role_id where ur.platform_user_id=? order by r.role_code")) {
                    roleQuery.setLong(1,userId);
                    try(var roleRows=roleQuery.executeQuery()) { while(roleRows.next()) roles.add(roleRows.getString(1)); }
                }
                Row result=new Row(userId,email,row.getBoolean("enabled"),row.getBoolean("mfa_enabled"),
                    row.getLong("account_version"),row.getObject("session_id",UUID.class),row.getLong("session_version"),
                    instant(row,"started_at"),instant(row,"last_activity_at"),instant(row,"idle_expires_at"),
                    instant(row,"absolute_expires_at"),instant(row,"revoked_at"),List.copyOf(roles));
                require(!row.next()); return result;
            }
        }
    }

    private static long audits(Connection db,long targetId,String action) throws SQLException {
        try(var query=db.prepareStatement("select count(*) from platform_audit_logs where target_platform_user_id=? and action=? and result='SUCCESS'")) {
            query.setLong(1,targetId); query.setString(2,action);
            try(var row=query.executeQuery()) { require(row.next()); return row.getLong(1); }
        }
    }

    private static String sign(JwtUtil jwt,Row row) {
        return jwt.generatePlatformToken(row.userId,row.email,row.roles,row.sessionVersion,row.sessionId,
            row.started.toEpochMilli(),row.absolute.toEpochMilli());
    }

    private static void check(String label,String method,String path,String token,int expected) throws Exception {
        require(Set.of("/api/platform/auth/me","/api/platform/admins","/api/platform/auth/session/activity",
            "/api/platform/audit-logs","/api/platform/access-grants/users","/api/platform/access-grants/me",
            "/api/platform/companies","/api/platform/admins/invitations").contains(path));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:15173"+path))
            .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token);
        var response=HTTP.send("POST".equals(method)?request.POST(HttpRequest.BodyPublishers.noBody()).build():request.GET().build(),
            HttpResponse.BodyHandlers.discarding());
        CHECKS.add(Map.of("case",label,"method",method,"path",path,"status",response.statusCode(),"expected",expected));
        System.out.println(label+" status="+response.statusCode()+" expected="+expected);
        require(response.statusCode()==expected);
    }

    private static void requireUnexpired(Row row) {
        Instant now=Instant.now();
        require(row.revoked==null && row.idle.isAfter(now) && row.absolute.isAfter(now));
    }

    private static Instant instant(ResultSet row,String name) throws SQLException {
        Timestamp timestamp=row.getTimestamp(name); return timestamp==null?null:timestamp.toInstant();
    }

    private static void require(boolean condition) {
        if(!condition) throw new IllegalStateException("Isolated administrator probe assertion failed");
    }
}
