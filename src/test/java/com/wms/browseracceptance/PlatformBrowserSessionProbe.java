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
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Fixed synthetic-account HTTP replay. Never starts Spring Boot, migrates, or updates the DB directly. */
public final class PlatformBrowserSessionProbe {
    private static final String EMAIL = "session-uat-a@platform.invalid";
    private static final Path REPLAY = PlatformBrowserHarness.PRIVATE.resolve("a-session-replay.properties");
    private static final Path FAILED_REPLAY = PlatformBrowserHarness.PRIVATE.resolve("a-failed-logout-replay.properties");
    private static final Path ALL_REPLAY = PlatformBrowserHarness.PRIVATE.resolve("a-logout-all-replay.properties");
    private static final Path FAULT_CONTROL = PlatformBrowserHarness.RUN.resolve("logout-transport-fault.json");
    // User-authorized assisted click; preserve all prior expired-window evidence separately.
    private static final String GROUP_FAILURE_ATTEMPT = "assisted-1";
    private static final Path GROUP_FAILURE_BASELINE = PlatformBrowserHarness.RUN.resolve("browser-group-failure-baseline-20260910-"+GROUP_FAILURE_ATTEMPT+".json");
    private static final Instant GROUP_FAILURE_TARGET = Instant.parse("2026-09-10T08:05:48Z");
    private static final Set<Instant> GROUP_FAILURE_STARTS = Set.of(
        GROUP_FAILURE_TARGET, Instant.parse("2026-09-10T07:59:34Z"));
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER)
        .proxy(new ProxySelector() {
            public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
        }).build();

    private record Snapshot(long userId, UUID id, long version, Instant started, Instant last,
                            Instant idle, Instant absolute, Instant revoked) { }

    public static void main(String[] args) {
        try {
            require(Path.of("").toAbsolutePath().normalize().equals(PlatformBrowserHarness.ROOT));
            require(args.length == 1 && Set.of("prepare", "after-logout", "prepare-failed-logout", "arm-failed-logout", "after-failed-logout", "prepare-logout-all", "after-logout-all", "after-browser-group-logout", "after-cross-browser-logout-all", "arm-browser-group-failed-logout", "after-browser-group-failed-logout").contains(args[0]));
            // Disarm first: a later database validation failure must not leave the experiment enabled.
            if ("after-browser-group-failed-logout".equals(args[0])) disableFault();
            PlatformBrowserHarness.validate(PlatformBrowserHarness.URL);
            require(Files.isDirectory(PlatformBrowserHarness.PRIVATE));
            try (Connection db = DriverManager.getConnection(PlatformBrowserHarness.URL,"p3_m1_app","")) {
                db.setReadOnly(true);
                switch(args[0]) {
                    case "prepare" -> prepare(db);
                    case "after-logout" -> afterLogout(db);
                    case "prepare-failed-logout" -> prepareFailedLogout(db);
                    case "arm-failed-logout" -> armFailedLogout(db);
                    case "after-failed-logout" -> afterFailedLogout(db);
                    case "prepare-logout-all" -> prepareLogoutAll(db);
                    case "after-logout-all" -> afterLogoutAll(db);
                    case "after-browser-group-logout" -> afterBrowserGroupLogout(db);
                    case "after-cross-browser-logout-all" -> afterCrossBrowserLogoutAll(db);
                    case "arm-browser-group-failed-logout" -> armBrowserGroupFailedLogout(db);
                    case "after-browser-group-failed-logout" -> afterBrowserGroupFailedLogout(db);
                    default -> throw new IllegalStateException();
                }
            }
            String attemptSuffix=args[0].endsWith("browser-group-failed-logout")?"-"+GROUP_FAILURE_ATTEMPT:"";
            new ObjectMapper().writeValue(PlatformBrowserHarness.RUN.resolve("http-replay-"+args[0]+attemptSuffix+".json").toFile(),
                Map.of("observedAt",Instant.now().toString(),"database","p3_m1_platform_browser",
                    "tokenSource","synthetic re-signing of existing MFA-created session; not browser token capture",
                    "checks",results,"passed",true));
            System.out.println("ISOLATED_SESSION_PROBE_PASSED " + args[0]);
        } catch (Throwable failure) {
            if (args.length==1 && "arm-browser-group-failed-logout".equals(args[0])) {
                try { disableFault(); }
                catch (IOException cleanupFailure) { System.err.println("ISOLATED_FAULT_DISABLE_FAILED"); }
            }
            // Exceptions from JWT libraries can contain token material. Do not print messages or traces.
            System.err.println("ISOLATED_SESSION_PROBE_STOPPED type="+failure.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static void prepare(Connection db) throws Exception {
        require(!Files.exists(REPLAY));
        List<Snapshot> before = snapshots(db);
        require(before.size()==2);
        Snapshot old = before.get(0), current = before.get(1);
        Instant now = Instant.now();
        require(old.idle.isBefore(now) && old.absolute.isAfter(now) && old.revoked==null);
        require(current.idle.isAfter(now) && current.absolute.isAfter(now) && current.revoked==null);
        require(old.version==current.version);
        List<String> roles = new ArrayList<>();
        try (PreparedStatement query=db.prepareStatement("select r.role_code from platform_roles r join platform_user_roles ur on ur.platform_role_id=r.id where ur.platform_user_id=? order by r.role_code")) {
            query.setLong(1,current.userId);
            try (ResultSet rows=query.executeQuery()) { while(rows.next()) roles.add(rows.getString(1)); }
        }
        require(roles.equals(List.of("PLATFORM_SUPER_ADMIN")));
        Properties privateKeys=load(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"));
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(privateKeys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(privateKeys.getProperty("jwt.platform-secret"));
        String oldToken, currentToken;
        try (var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties);
            context.registerBean(JwtUtil.class);
            context.refresh();
            JwtUtil jwt=context.getBean(JwtUtil.class);
            oldToken=sign(jwt,old,roles); currentToken=sign(jwt,current,roles);
            // Both signatures and JWT absolute expirations must be valid before testing backend idle rules.
            require(jwt.parsePlatformToken(oldToken).sessionId().equals(old.id));
            require(jwt.parsePlatformToken(currentToken).sessionId().equals(current.id));
        }
        check("new-session-me", "GET", "/api/platform/auth/me", currentToken, 200);
        check("new-session-admin-directory", "GET", "/api/platform/admins", currentToken, 200);
        check("idle-expired-me", "GET", "/api/platform/auth/me", oldToken, 401);
        check("idle-expired-admin-directory", "GET", "/api/platform/admins", oldToken, 401);
        check("idle-expired-activity", "POST", "/api/platform/auth/session/activity", oldToken, 401);
        require(snapshots(db).equals(before));
        results.add(Map.of("case","session-rows-and-deadlines-unchanged","passed",true));
        Properties replay=new Properties();
        replay.setProperty("token",currentToken);
        replay.setProperty("sessionId",current.id.toString());
        replay.setProperty("startedAt",current.started.toString());
        replay.setProperty("absoluteExpiresAt",current.absolute.toString());
        try(var writer=Files.newBufferedWriter(REPLAY,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            replay.store(writer,"PRIVATE synthetic session replay; never attach to evidence; invalidate with test session.");
        }
    }

    private static void afterLogout(Connection db) throws Exception {
        Properties replay=load(REPLAY);
        UUID id=UUID.fromString(replay.getProperty("sessionId"));
        List<Snapshot> before=snapshots(db);
        Snapshot current=before.stream().filter(s->s.id.equals(id)).findFirst().orElseThrow();
        require(current.started.toString().equals(replay.getProperty("startedAt")));
        require(current.absolute.toString().equals(replay.getProperty("absoluteExpiresAt")));
        // Observe actual browser logout before exercising the replay; never log out on the user's behalf.
        require(current.revoked!=null && current.absolute.isAfter(Instant.now()) && current.idle.isAfter(Instant.now()));
        String token=replay.getProperty("token");
        check("revoked-me","GET","/api/platform/auth/me",token,401);
        check("revoked-admin-directory","GET","/api/platform/admins",token,401);
        check("revoked-activity","POST","/api/platform/auth/session/activity",token,401);
        require(snapshots(db).equals(before));
        results.add(Map.of("case","revoked-session-cannot-be-renewed","passed",true));
    }

    /** Only the pair already logged in and logged out manually on 2026-09-10; not an arbitrary impersonation tool. */
    private static void afterBrowserGroupLogout(Connection db) throws Exception {
        List<Snapshot> before=snapshots(db);
        Set<Instant> starts=Set.of(Instant.parse("2026-09-10T02:23:27Z"),Instant.parse("2026-09-10T02:26:05Z"));
        List<Snapshot> pair=before.stream().filter(row->starts.contains(row.started)).toList();
        require(pair.size()==2 && pair.get(0).userId==pair.get(1).userId);
        long version=accountVersion(db);
        require(version==3 && pair.stream().allMatch(row->row.version==version));
        Instant expectedRevocation=Instant.parse("2026-09-10T02:28:36.754290Z");
        Instant now=Instant.now();
        require(pair.stream().allMatch(row->expectedRevocation.equals(row.revoked)
            && row.idle.isAfter(now) && row.absolute.isAfter(now)));
        try(var query=db.prepareStatement("select count(*),count(distinct browser_binding_hash) from platform_sessions where session_id in (?,?) and browser_binding_hash is not null")) {
            query.setObject(1,pair.get(0).id); query.setObject(2,pair.get(1).id);
            try(var row=query.executeQuery()) { require(row.next() && row.getInt(1)==2 && row.getInt(2)==1); }
        }
        long auditBefore=browserRevocationAudits(db);
        require(auditBefore==1);
        List<String> roles=new ArrayList<>();
        try(var query=db.prepareStatement("select r.role_code from platform_roles r join platform_user_roles ur on ur.platform_role_id=r.id where ur.platform_user_id=? order by r.role_code")) {
            query.setLong(1,pair.get(0).userId);
            try(var rows=query.executeQuery()) { while(rows.next()) roles.add(rows.getString(1)); }
        }
        require(roles.equals(List.of("PLATFORM_SUPER_ADMIN")));
        Properties keys=load(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"));
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties); context.registerBean(JwtUtil.class); context.refresh();
            JwtUtil jwt=context.getBean(JwtUtil.class);
            for(int i=0;i<pair.size();i++) {
                Snapshot row=pair.get(i);
                String token=sign(jwt,row,roles); // Memory only; no browser token capture or private replay file.
                require(jwt.parsePlatformToken(token).sessionId().equals(row.id));
                String label="browser-group-old-session-"+(i+1);
                checkRevoked(label+"-me","GET","/api/platform/auth/me",token);
                checkRevoked(label+"-admin-directory","GET","/api/platform/admins",token);
                checkRevoked(label+"-activity","POST","/api/platform/auth/session/activity",token);
            }
        }
        require(snapshots(db).equals(before) && accountVersion(db)==version && browserRevocationAudits(db)==auditBefore);
        results.add(Map.of("case","both-old-group-sessions-remain-revoked-without-renewal","passed",true,
            "sessions",2,"securityVersion",version,"revocationAuditsUnchanged",true,
            "idleAndAbsoluteStillFutureAtProbeStart",true));
    }

    private static long browserRevocationAudits(Connection db) throws SQLException {
        try(var query=db.prepareStatement("select count(*) from platform_audit_logs a join platform_users u on u.id=a.platform_user_id where u.normalized_email=? and a.action='SESSIONS_REVOKED' and a.result='SUCCESS' and a.detail_json::jsonb->>'scope'='BROWSER_PLATFORM_SESSIONS' and a.detail_json::jsonb->>'operation'='BROWSER_LOGOUT' and a.detail_json::jsonb->>'sessionsRevoked'='2'")) {
            query.setString(1,EMAIL);
            try(var row=query.executeQuery()) { require(row.next()); return row.getLong(1); }
        }
    }

    private static void checkRevoked(String name,String method,String path,String token) throws Exception {
        require(Set.of("/api/platform/auth/me","/api/platform/admins","/api/platform/auth/session/activity").contains(path));
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:15173"+path))
            .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token);
        var request="POST".equals(method)?builder.POST(HttpRequest.BodyPublishers.noBody()).build():builder.GET().build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        require(response.statusCode()==401);
        String errorKey=new ObjectMapper().readTree(response.body()).path("errorKey").asText();
        require("PLATFORM_SESSION_INVALIDATED".equals(errorKey));
        results.add(Map.of("case",name,"method",method,"path",path,"status",401,"errorKey",errorKey,"passed",true));
        System.out.println(name+" status=401 errorKey=PLATFORM_SESSION_INVALIDATED");
    }

    private static void prepareFailedLogout(Connection db) throws Exception {
        require(!Files.exists(FAILED_REPLAY));
        List<Snapshot> before=snapshots(db);
        require(before.size()==3 && before.get(1).revoked!=null);
        Snapshot current=before.get(2);
        requireValid(current);
        Properties keys=load(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"));
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        String token;
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties);
            context.registerBean(JwtUtil.class); context.refresh();
            token=sign(context.getBean(JwtUtil.class),current,List.of("PLATFORM_SUPER_ADMIN"));
        }
        check("failure-test-baseline-me","GET","/api/platform/auth/me",token,200);
        check("failure-test-baseline-admin-directory","GET","/api/platform/admins",token,200);
        require(snapshots(db).equals(before));
        Properties replay=new Properties();
        replay.setProperty("token",token); replay.setProperty("sessionId",current.id.toString());
        replay.setProperty("startedAt",current.started.toString());
        replay.setProperty("absoluteExpiresAt",current.absolute.toString());
        replay.setProperty("revocationAuditCount",Long.toString(revocationAudits(db)));
        try(var writer=Files.newBufferedWriter(FAILED_REPLAY,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            replay.store(writer,"PRIVATE synthetic failed-logout replay. Exclude from reports.");
        }
        results.add(Map.of("case","failure-baseline-does-not-renew-session","passed",true));
    }

    private static void armFailedLogout(Connection db) throws Exception {
        Properties replay=load(FAILED_REPLAY);
        List<Snapshot> before=snapshots(db);
        Snapshot current=replaySession(before,replay); requireValid(current);
        String token=replay.getProperty("token");
        check("fault-arm-me-before","GET","/api/platform/auth/me",token,200);
        Instant now=Instant.now();
        String sessionHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(current.id.toString().getBytes(StandardCharsets.UTF_8)));
        var control=Map.of("enabled",true,"scenario","drop-logout-before-upstream","sessionSha256",sessionHash,
            "armedAt",now.toString(),"expiresAt",now.plus(Duration.ofMinutes(30)).toString());
        new ObjectMapper().writeValue(FAULT_CONTROL.toFile(),control);
        boolean passed=false;
        try {
            // A deliberately invalid signature ensures this canary cannot revoke the session even if interception fails.
            String[] parts=token.split("\\."); require(parts.length==3);
            byte[] invalidSignature=new byte[64];
            invalidSignature[0]=1;
            String canary=parts[0]+"."+parts[1]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(invalidSignature);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:15173/api/platform/auth/logout"))
                .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+canary)
                .header("X-Isolation-Fault-Canary","1").POST(HttpRequest.BodyPublishers.noBody()).build();
            boolean disconnected=false;
            try { HTTP.send(request,HttpResponse.BodyHandlers.discarding()); }
            catch(IOException expected) { disconnected=true; }
            require(disconnected && faultEventsSince(now,"invalid-signature-canary")>0);
            check("fault-arm-me-after","GET","/api/platform/auth/me",token,200);
            require(snapshots(db).equals(before));
            require(revocationAudits(db)==Long.parseLong(replay.getProperty("revocationAuditCount")));
            results.add(Map.of("case","only-logout-disconnected-before-upstream","passed",true));
            passed=true;
        } finally {
            if(!passed) disableFault();
        }
    }

    private static void afterFailedLogout(Connection db) throws Exception {
        var control=new ObjectMapper().readTree(FAULT_CONTROL.toFile());
        Instant armedAt=Instant.parse(control.get("armedAt").asText());
        // Remove the fault immediately, even when later acceptance assertions fail.
        disableFault();
        require(faultEventsSince(armedAt,"manual-browser")>0);
        Properties replay=load(FAILED_REPLAY);
        List<Snapshot> before=snapshots(db);
        Snapshot current=replaySession(before,replay); requireValid(current);
        require(revocationAudits(db)==Long.parseLong(replay.getProperty("revocationAuditCount")));
        check("failed-logout-session-still-valid-me","GET","/api/platform/auth/me",replay.getProperty("token"),200);
        check("failed-logout-session-still-valid-admin-directory","GET","/api/platform/admins",replay.getProperty("token"),200);
        require(snapshots(db).equals(before));
        results.add(Map.of("case","no-server-revocation-and-no-false-success-audit","passed",true));
        results.add(Map.of("case","fault-disabled-after-observation","passed",true));
    }

    /** Only the two manually authenticated synthetic sessions from this fixed acceptance step. */
    private static List<Snapshot> groupFailurePair(Connection db,List<Snapshot> rows) throws Exception {
        List<Snapshot> pair=rows.stream().filter(row->GROUP_FAILURE_STARTS.contains(row.started)).toList();
        require(pair.size()==2 && pair.get(0).userId==pair.get(1).userId && accountVersion(db)==4);
        for(Snapshot row:pair) { requireValid(row); require(row.version==4); }
        try(var query=db.prepareStatement("select count(*),count(distinct browser_binding_hash) from platform_sessions where session_id in (?,?) and browser_binding_hash is not null")) {
            query.setObject(1,pair.get(0).id); query.setObject(2,pair.get(1).id);
            try(var row=query.executeQuery()) { require(row.next() && row.getInt(1)==2 && row.getInt(2)==1); }
        }
        return pair;
    }

    private static List<String> groupFailureTokens(Connection db,List<Snapshot> pair) throws Exception {
        List<String> roles=new ArrayList<>();
        try(var query=db.prepareStatement("select r.role_code from platform_roles r join platform_user_roles ur on ur.platform_role_id=r.id where ur.platform_user_id=? order by r.role_code")) {
            query.setLong(1,pair.get(0).userId);
            try(var rows=query.executeQuery()) { while(rows.next()) roles.add(rows.getString(1)); }
        }
        require(roles.equals(List.of("PLATFORM_SUPER_ADMIN")));
        Properties keys=load(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"));
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        List<String> tokens=new ArrayList<>();
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties); context.registerBean(JwtUtil.class); context.refresh();
            JwtUtil jwt=context.getBean(JwtUtil.class);
            for(Snapshot row:pair) {
                String token=sign(jwt,row,roles); // Memory only; no original browser token is captured.
                require(jwt.parsePlatformToken(token).sessionId().equals(row.id)); tokens.add(token);
            }
        }
        return tokens;
    }

    private static void checkGroupFailureTokens(String label,List<String> tokens) throws Exception {
        for(int i=0;i<tokens.size();i++) {
            check(label+"-session-"+(i+1)+"-me","GET","/api/platform/auth/me",tokens.get(i),200);
            check(label+"-session-"+(i+1)+"-admins","GET","/api/platform/admins",tokens.get(i),200);
        }
    }

    private static long successfulRevocationAudits(Connection db) throws SQLException {
        try(var query=db.prepareStatement("select count(*) from platform_audit_logs a join platform_users u on u.id=a.platform_user_id where u.normalized_email=? and a.action='SESSIONS_REVOKED' and a.result='SUCCESS'")) {
            query.setString(1,EMAIL);
            try(var row=query.executeQuery()) { require(row.next()); return row.getLong(1); }
        }
    }

    private static void armBrowserGroupFailedLogout(Connection db) throws Exception {
        require(!Files.exists(GROUP_FAILURE_BASELINE));
        require(!new ObjectMapper().readTree(FAULT_CONTROL.toFile()).path("enabled").asBoolean());
        List<Snapshot> before=snapshots(db),pair=groupFailurePair(db,before);
        long audits=successfulRevocationAudits(db); require(audits==7);
        List<String> tokens=groupFailureTokens(db,pair);
        Snapshot target=pair.stream().filter(row->GROUP_FAILURE_TARGET.equals(row.started)).findFirst().orElseThrow();
        String targetToken=tokens.get(pair.indexOf(target));
        checkGroupFailureTokens("group-fault-before",tokens);
        Instant armedAt=Instant.now(),expiresAt=armedAt.plus(Duration.ofMinutes(10));
        for(Snapshot row:pair) {
            Instant deadline=(row.idle.isBefore(row.absolute)?row.idle:row.absolute).minusSeconds(5);
            if(deadline.isBefore(expiresAt)) expiresAt=deadline;
        }
        require(expiresAt.isAfter(armedAt.plus(Duration.ofMinutes(2))));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(target.id.toString().getBytes(StandardCharsets.UTF_8)));
        boolean passed=false;
        try {
            new ObjectMapper().writeValue(FAULT_CONTROL.toFile(),Map.of("enabled",true,
                "scenario","drop-logout-before-upstream","sessionSha256",hash,
                "armedAt",armedAt.toString(),"expiresAt",expiresAt.toString()));
            // Invalid signature: even a missed interception cannot perform a real logout.
            String[] pieces=targetToken.split("\\."); require(pieces.length==3);
            byte[] invalidSignature=new byte[64]; invalidSignature[0]=1;
            String canary=pieces[0]+"."+pieces[1]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(invalidSignature);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:15173/api/platform/auth/logout"))
                .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+canary)
                .header("X-Isolation-Fault-Canary","1").POST(HttpRequest.BodyPublishers.noBody()).build();
            boolean disconnected=false;
            try { HTTP.send(request,HttpResponse.BodyHandlers.discarding()); }
            catch(IOException expected) { disconnected=true; }
            require(disconnected && faultEventsSince(armedAt,"invalid-signature-canary")>0);
            checkGroupFailureTokens("group-fault-after-canary",tokens);
            require(snapshots(db).equals(before) && accountVersion(db)==4 && successfulRevocationAudits(db)==audits);
            new ObjectMapper().writeValue(GROUP_FAILURE_BASELINE.toFile(),Map.of(
                "database","p3_m1_platform_browser","armedAt",armedAt.toString(),"expiresAt",expiresAt.toString(),
                "sessionStartedAt",pair.stream().map(row->row.started.toString()).toList(),
                "securityVersion",4,"successfulRevocationAuditCount",audits,"sameBrowserGroup",true,
                "automaticExpiry",true,"manualResult","pending"));
            results.add(Map.of("case","second-tab-only-logout-fault-armed","passed",true,
                "expiresAt",expiresAt.toString(),"canaryForwarded",false,"sessionRowsAndAuditsUnchanged",true));
            passed=true;
        } finally { if(!passed) disableFault(); }
    }

    private static void afterBrowserGroupFailedLogout(Connection db) throws Exception {
        // main has already removed the gate, before validating the isolated database.
        var baseline=new ObjectMapper().readTree(GROUP_FAILURE_BASELINE.toFile());
        Instant armedAt=Instant.parse(baseline.path("armedAt").asText());
        require(!new ObjectMapper().readTree(FAULT_CONTROL.toFile()).path("enabled").asBoolean());
        require(faultEventsSince(armedAt,"manual-browser")>0);
        List<Snapshot> before=snapshots(db),pair=groupFailurePair(db,before);
        long audits=baseline.path("successfulRevocationAuditCount").asLong(-1);
        require(audits==7 && successfulRevocationAudits(db)==audits);
        checkGroupFailureTokens("group-failed-logout-still-valid",groupFailureTokens(db,pair));
        require(snapshots(db).equals(before) && accountVersion(db)==4 && successfulRevocationAudits(db)==audits);
        results.add(Map.of("case","both-backend-sessions-still-valid-after-undelivered-logout",
            "passed",true,"sessions",2,"noFalseSuccessAudit",true,"noProbeActivityRenewal",true,"faultDisabled",true));
    }

    private static Snapshot replaySession(List<Snapshot> rows,Properties replay) {
        UUID id=UUID.fromString(replay.getProperty("sessionId"));
        Snapshot row=rows.stream().filter(s->s.id.equals(id)).findFirst().orElseThrow();
        require(row.started.toString().equals(replay.getProperty("startedAt")));
        require(row.absolute.toString().equals(replay.getProperty("absoluteExpiresAt")));
        return row;
    }

    private static void prepareLogoutAll(Connection db) throws Exception {
        require(!Files.exists(ALL_REPLAY));
        require(!new ObjectMapper().readTree(FAULT_CONTROL.toFile()).path("enabled").asBoolean());
        List<Snapshot> before=snapshots(db);
        require(before.size()==4);
        Snapshot current=before.get(3); requireValid(current);
        long version=accountVersion(db); require(current.version==version);
        Properties keys=load(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"));
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        String token;
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties);
            context.registerBean(JwtUtil.class); context.refresh();
            token=sign(context.getBean(JwtUtil.class),current,List.of("PLATFORM_SUPER_ADMIN"));
        }
        check("logout-all-baseline-me","GET","/api/platform/auth/me",token,200);
        check("logout-all-baseline-admin-directory","GET","/api/platform/admins",token,200);
        require(snapshots(db).equals(before));
        Properties replay=new Properties();
        replay.setProperty("token",token); replay.setProperty("sessionId",current.id.toString());
        replay.setProperty("startedAt",current.started.toString());
        replay.setProperty("absoluteExpiresAt",current.absolute.toString());
        replay.setProperty("securityVersion",Long.toString(version));
        replay.setProperty("allRevocationAuditCount",Long.toString(allRevocationAudits(db)));
        try(var writer=Files.newBufferedWriter(ALL_REPLAY,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            replay.store(writer,"PRIVATE synthetic logout-all replay. Never include in evidence.");
        }
        results.add(Map.of("case","logout-all-baseline-identity","securityVersion",version,
            "sessions",before.size(),"previousFailedSessionIdleExpired",!before.get(2).idle.isAfter(Instant.now())));
    }

    private static void afterLogoutAll(Connection db) throws Exception {
        Properties replay=load(ALL_REPLAY);
        List<Snapshot> before=snapshots(db,true);
        require(before.size()==4);
        Snapshot current=replaySession(before,replay);
        long oldVersion=Long.parseLong(replay.getProperty("securityVersion"));
        long newVersion=accountVersion(db);
        require(newVersion==oldVersion+1 && current.version==oldVersion);
        // Demonstrate identity revocation, independently of idle/absolute expiry or row revocation.
        require(current.revoked==null && current.idle.isAfter(Instant.now()) && current.absolute.isAfter(Instant.now()));
        require(before.stream().allMatch(row->row.version<newVersion));
        require(allRevocationAudits(db)==Long.parseLong(replay.getProperty("allRevocationAuditCount"))+1);
        String token=replay.getProperty("token");
        check("logout-all-current-me","GET","/api/platform/auth/me",token,401);
        check("logout-all-current-admin-directory","GET","/api/platform/admins",token,401);
        check("logout-all-current-activity","POST","/api/platform/auth/session/activity",token,401);
        Properties failed=load(FAILED_REPLAY);
        Snapshot prior=replaySession(before,failed);
        check("logout-all-prior-failed-session-me","GET","/api/platform/auth/me",failed.getProperty("token"),401);
        require(snapshots(db,true).equals(before));
        require(accountVersion(db)==newVersion);
        results.add(Map.of("case","all-existing-session-versions-invalidated","oldVersion",oldVersion,
            "newVersion",newVersion,"sessions",before.size(),"passed",true));
        results.add(Map.of("case","previous-failed-session-expiry-boundary",
            "idleAlreadyExpiredAtReplay",!prior.idle.isAfter(Instant.now())));
    }

    /** Fixed manual two-browser MFA/global logout test; no account or session creation/mutation. */
    private static void afterCrossBrowserLogoutAll(Connection db) throws Exception {
        require(!Files.exists(PlatformBrowserHarness.RUN.resolve("http-replay-after-cross-browser-logout-all.json")));
        List<Snapshot> before=snapshots(db,true);
        Set<Instant> starts=Set.of(Instant.parse("2026-09-10T02:45:34Z"),Instant.parse("2026-09-10T02:53:56Z"));
        List<Snapshot> pair=before.stream().filter(row->starts.contains(row.started)).toList();
        require(pair.size()==2 && pair.get(0).userId==pair.get(1).userId);
        long version=accountVersion(db), audits=allRevocationAudits(db);
        require(version==4 && audits==2);
        Instant now=Instant.now();
        // Neither row revocation nor natural idle/JWT expiry may explain this rejection.
        require(pair.stream().allMatch(row->row.version==3 && row.revoked==null
            && row.idle.isAfter(now) && row.absolute.isAfter(now)));
        try(var query=db.prepareStatement("select count(*),count(distinct browser_binding_hash) from platform_sessions where session_id in (?,?) and browser_binding_hash is not null")) {
            query.setObject(1,pair.get(0).id); query.setObject(2,pair.get(1).id);
            try(var row=query.executeQuery()) {require(row.next() && row.getInt(1)==2 && row.getInt(2)==2);}
        }
        List<String> roles=new ArrayList<>();
        try(var query=db.prepareStatement("select r.role_code from platform_roles r join platform_user_roles ur on ur.platform_role_id=r.id where ur.platform_user_id=? order by r.role_code")) {
            query.setLong(1,pair.get(0).userId);
            try(var rows=query.executeQuery()) {while(rows.next()) roles.add(rows.getString(1));}
        }
        require(roles.equals(List.of("PLATFORM_SUPER_ADMIN")));
        Properties keys=load(PlatformBrowserHarness.PRIVATE.resolve("runtime-secrets.properties"));
        JwtProperties properties=new JwtProperties();
        properties.setTenantSecret(keys.getProperty("jwt.tenant-secret"));
        properties.setPlatformSecret(keys.getProperty("jwt.platform-secret"));
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class,()->properties); context.registerBean(JwtUtil.class); context.refresh();
            JwtUtil jwt=context.getBean(JwtUtil.class);
            for(int i=0;i<pair.size();i++) {
                Snapshot row=pair.get(i);
                String token=sign(jwt,row,roles); // Remains in memory, never written to the report.
                var claims=jwt.parsePlatformToken(token);
                require(claims.sessionId().equals(row.id) && claims.securityVersion()==3);
                String label=i==0?"global-revoked-external":"global-revoked-internal";
                check(label+"-me","GET","/api/platform/auth/me",token,401);
                check(label+"-admin-directory","GET","/api/platform/admins",token,401);
                check(label+"-activity","POST","/api/platform/auth/session/activity",token,401);
            }
        }
        require(snapshots(db,true).equals(before) && accountVersion(db)==version && allRevocationAudits(db)==audits);
        results.add(Map.of("case","two-browser-global-version-revocation-without-expiry-or-row-revocation",
            "oldVersion",3,"currentVersion",4,"browserGroups",2,"sessions",2,
            "sessionRowsAndAuditsUnchanged",true,"idleAndAbsoluteStillFutureAtProbeStart",true,"passed",true));
    }

    private static long accountVersion(Connection db) throws SQLException {
        try(var query=db.prepareStatement("select security_version from platform_users where normalized_email=? and enabled and mfa_enabled")) {
            query.setString(1,EMAIL);
            try(var row=query.executeQuery()) { require(row.next()); return row.getLong(1); }
        }
    }

    private static long allRevocationAudits(Connection db) throws SQLException {
        try(var query=db.prepareStatement("select count(*) from platform_audit_logs a join platform_users u on u.id=a.platform_user_id where u.normalized_email=? and a.action='SESSIONS_REVOKED' and a.resource_type='platform_identity' and a.result='SUCCESS' and a.detail_json::jsonb->>'scope'='ALL_PLATFORM_SESSIONS'")) {
            query.setString(1,EMAIL);
            try(var row=query.executeQuery()) { require(row.next()); return row.getLong(1); }
        }
    }

    private static void requireValid(Snapshot row) {
        require(row.revoked==null && row.idle.isAfter(Instant.now()) && row.absolute.isAfter(Instant.now()));
    }

    private static long revocationAudits(Connection db) throws SQLException {
        try(var query=db.prepareStatement("select count(*) from platform_audit_logs a join platform_users u on u.id=a.platform_user_id where u.normalized_email=? and a.action='SESSIONS_REVOKED' and a.resource_type='platform_session' and a.result='SUCCESS' and a.detail_json::jsonb->>'scope'='CURRENT_PLATFORM_SESSION'")) {
            query.setString(1,EMAIL);
            try(var row=query.executeQuery()) { require(row.next()); return row.getLong(1); }
        }
    }

    private static long faultEventsSince(Instant since,String source) throws IOException {
        long count=0;
        for(String line:Files.readAllLines(PlatformBrowserHarness.RUN.resolve("logout-transport-fault-events.jsonl"),StandardCharsets.UTF_8)) {
            var event=new ObjectMapper().readTree(line);
            if(!Instant.parse(event.get("at").asText()).isBefore(since) && source.equals(event.get("source").asText())
                    && !event.get("forwarded").asBoolean() && "drop-logout-before-upstream".equals(event.get("scenario").asText())) count++;
        }
        return count;
    }

    private static void disableFault() throws IOException {
        new ObjectMapper().writeValue(FAULT_CONTROL.toFile(),Map.of("enabled",false));
    }

    private static String sign(JwtUtil jwt, Snapshot row, List<String> roles) {
        return jwt.generatePlatformToken(row.userId,EMAIL,roles,row.version,row.id,
            row.started.toEpochMilli(),row.absolute.toEpochMilli());
    }

    private static List<Snapshot> snapshots(Connection db) throws SQLException {
        return snapshots(db,false);
    }

    private static List<Snapshot> snapshots(Connection db,boolean includeStaleIdentityVersions) throws SQLException {
        List<Snapshot> result=new ArrayList<>();
        String versionFilter=includeStaleIdentityVersions?"":" and u.security_version=s.security_version";
        try (PreparedStatement query=db.prepareStatement("select s.session_id,s.platform_user_id,s.security_version,s.started_at,s.last_activity_at,s.idle_expires_at,s.absolute_expires_at,s.revoked_at from platform_sessions s join platform_users u on u.id=s.platform_user_id where u.normalized_email=? and u.enabled and u.mfa_enabled"+versionFilter+" order by s.started_at")) {
            query.setString(1,EMAIL);
            try(ResultSet row=query.executeQuery()) {
                while(row.next()) result.add(new Snapshot(row.getLong("platform_user_id"),
                    row.getObject("session_id",UUID.class),row.getLong("security_version"),
                    time(row,"started_at"),time(row,"last_activity_at"),time(row,"idle_expires_at"),
                    time(row,"absolute_expires_at"),time(row,"revoked_at")));
            }
        }
        return result;
    }

    private static Instant time(ResultSet row,String name) throws SQLException {
        Timestamp value=row.getTimestamp(name); return value==null?null:value.toInstant();
    }

    private static Properties load(Path file) throws IOException {
        Properties properties=new Properties();
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties;
    }

    private static void check(String name,String method,String path,String token,int expected) throws Exception {
        require(Set.of("/api/platform/auth/me","/api/platform/admins","/api/platform/auth/session/activity").contains(path));
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:15173"+path))
            .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token);
        var request=method.equals("POST")?builder.POST(HttpRequest.BodyPublishers.noBody()).build():builder.GET().build();
        int status=HTTP.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
        results.add(Map.of("case",name,"method",method,"path",path,"status",status,"expected",expected));
        System.out.println(name+" status="+status+" expected="+expected);
        require(status==expected);
    }

    private static void require(boolean condition) {
        if(!condition) throw new IllegalStateException("Isolation probe assertion failed");
    }
}
