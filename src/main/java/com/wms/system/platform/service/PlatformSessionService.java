package com.wms.system.platform.service;

import com.wms.system.security.JwtProperties;
import com.wms.system.security.PlatformJwtClaims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformSessionService {
    private final JdbcTemplate jdbc;
    private final JwtProperties properties;
    private final PlatformAuditService audit;
    private final PlatformBrowserBinding browsers;

    public record Window(UUID sessionId, OffsetDateTime startedAt,
                         OffsetDateTime lastActivityAt, OffsetDateTime idleExpiresAt,
                         OffsetDateTime expiresAt, OffsetDateTime serverNow) { }

    /** Called only after completed MFA, in the same transaction as challenge consumption. */
    @Transactional
    public Window create(long userId, long version) {
        // Explicit independent-session fixture/internal entry. Public MFA flows use createForBrowser.
        return createBound(userId, version, UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", ""));
    }

    @Transactional
    public Window createForBrowser(long userId, long version, HttpServletRequest request) {
        return createBound(userId, version, browsers.requireHash(request));
    }

    private Window createBound(long userId, long version, String browserHash) {
        lockIdentity(userId, version, true);
        OffsetDateTime now = databaseNow().truncatedTo(ChronoUnit.SECONDS);
        long duration = Math.min(properties.getPlatformExpiration(), 43_200_000L);
        if (duration < 1000) throw new IllegalStateException("Platform lifetime must be at least one second");
        OffsetDateTime absolute = now.plusSeconds(duration / 1000);
        OffsetDateTime idle = earlier(now.plusMinutes(30), absolute);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into platform_sessions(session_id,platform_user_id,security_version,
                started_at,last_activity_at,idle_expires_at,absolute_expires_at,browser_binding_hash)
            values(?,?,?,?,?,?,?,?)
            """, id, userId, version, now, now, idle, absolute, browserHash);
        return new Window(id, now, now, idle, absolute, now);
    }

    /** All platform API calls check, but do NOT renew, the persisted window. */
    @Transactional
    public Window check(PlatformJwtClaims claims) { return locked(claims, false, false, null); }

    @Transactional
    public Window activity(PlatformJwtClaims claims) { return locked(claims, true, false, null); }

    @Transactional
    public void logout(PlatformJwtClaims claims, HttpServletRequest request) {
        locked(claims, false, true, request);
    }

    private Window locked(PlatformJwtClaims claims, boolean activity, boolean revoke,
                          HttpServletRequest request) {
        if (claims.sessionId() == null) throw invalid();
        // Consistent identity -> session lock order. Account changes cannot race an activity update.
        // Exclusive user lock serializes browser logout with every login and activity for this user.
        // Taking it BEFORE any session row avoids cross-session logout deadlocks.
        lockIdentity(claims.platformUserId(), claims.securityVersion(), revoke);
        var rows = jdbc.queryForList("select * from platform_sessions where session_id=? for update", claims.sessionId());
        if (rows.size() != 1) throw invalid();
        Map<String, Object> row = rows.get(0);
        if (!(row.get("browser_binding_hash") instanceof String browserHash)
            || !browserHash.matches("[0-9a-f]{64}")) throw invalid();
        if (((Number) row.get("platform_user_id")).longValue() != claims.platformUserId()
                || ((Number) row.get("security_version")).longValue() != claims.securityVersion()) throw invalid();
        OffsetDateTime started = time(row, "started_at");
        OffsetDateTime absolute = time(row, "absolute_expires_at");
        OffsetDateTime idle = time(row, "idle_expires_at");
        OffsetDateTime last = time(row, "last_activity_at");
        if (started.toInstant().toEpochMilli() != claims.issuedAtEpochMillis()
                || absolute.toInstant().toEpochMilli() != claims.expiresAtEpochMillis()) throw invalid();
        // IMPORTANT: fetch wall time AFTER waiting for locks; transaction-start time is unsafe here.
        OffsetDateTime now = databaseNow();
        if (row.get("revoked_at") != null) throw invalid();
        if (!now.isBefore(absolute)) throw new PlatformSessionException("PLATFORM_SESSION_EXPIRED");
        if (!now.isBefore(idle)) throw new PlatformSessionException("PLATFORM_SESSION_IDLE_EXPIRED");
        if (revoke) {
            int revoked = jdbc.update("""
                update platform_sessions set revoked_at=?
                where platform_user_id=? and browser_binding_hash=? and revoked_at is null
                """, now, claims.platformUserId(), browserHash);
            audit.recordInCurrentTransaction(claims.platformUserId(), null, "SESSIONS_REVOKED",
                "platform_session", "SUCCESS", Map.of("scope", "BROWSER_PLATFORM_SESSIONS",
                    "operation", "BROWSER_LOGOUT", "sessionsRevoked", revoked), request);
        } else if (activity) {
            last = now;
            idle = earlier(now.plusMinutes(30), absolute);
            jdbc.update("update platform_sessions set last_activity_at=?,idle_expires_at=? where session_id=?",
                last, idle, claims.sessionId());
        }
        return new Window(claims.sessionId(), started, last, idle, absolute, now);
    }

    private void lockIdentity(long userId, long version, boolean exclusive) {
        var rows = jdbc.queryForList("select enabled,security_version from platform_users where id=? for "
            + (exclusive ? "no key update" : "share"), userId);
        if (rows.size() != 1 || !Boolean.TRUE.equals(rows.get(0).get("enabled"))
                || ((Number) rows.get(0).get("security_version")).longValue() != version) throw invalid();
    }

    /** One bounded batch per invocation; no relationship to audit or submitted jobs. */
    @Transactional
    public int cleanup() {
        return jdbc.update("""
            delete from platform_sessions where session_id in (
                select session_id from platform_sessions
                where least(idle_expires_at,absolute_expires_at,revoked_at) <= clock_timestamp() - interval '7 days'
                order by least(idle_expires_at,absolute_expires_at,revoked_at)
                limit 1000 for update skip locked
            )
            """);
    }

    private OffsetDateTime databaseNow() { return jdbc.queryForObject("select clock_timestamp()", OffsetDateTime.class); }
    private static OffsetDateTime earlier(OffsetDateTime a, OffsetDateTime b) { return a.isBefore(b) ? a : b; }
    private static OffsetDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof OffsetDateTime time) return time;
        return ((java.sql.Timestamp) value).toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    private static PlatformSessionException invalid() { return new PlatformSessionException("PLATFORM_SESSION_INVALIDATED"); }
}
