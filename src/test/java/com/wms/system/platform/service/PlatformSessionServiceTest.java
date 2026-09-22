package com.wms.system.platform.service;

import com.wms.system.security.JwtProperties;
import com.wms.system.security.PlatformJwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PlatformSessionServiceTest {
    JdbcTemplate jdbc;
    PlatformSessionService service;
    PlatformAuditService audit;
    PlatformJwtClaims claims;
    Map<String,Object> row;
    OffsetDateTime start=OffsetDateTime.parse("2026-09-09T00:00:00Z");
    @BeforeEach void setup() {
        jdbc=mock(JdbcTemplate.class);
        audit=mock(PlatformAuditService.class);
        service=new PlatformSessionService(jdbc,new JwtProperties(),audit,mock(PlatformBrowserBinding.class));
        UUID id=UUID.randomUUID();
        claims=new PlatformJwtClaims(1L,"isolated@invalid.test",List.of("PLATFORM_SUPER_ADMIN"),1L,id,start.toInstant().toEpochMilli(),start.plusHours(12).toInstant().toEpochMilli());
        row=new HashMap<>(Map.of("platform_user_id",1L,"security_version",1L,"started_at",start,"last_activity_at",start,
            "idle_expires_at",start.plusMinutes(30),"absolute_expires_at",start.plusHours(12)));
        row.put("browser_binding_hash", "a".repeat(64));
        when(jdbc.queryForList("select enabled,security_version from platform_users where id=? for share",1L))
            .thenReturn(List.of(Map.of("enabled",true,"security_version",1L)));
        when(jdbc.queryForList("select * from platform_sessions where session_id=? for update",id)).thenReturn(List.of(row));
    }
    void clock(OffsetDateTime now) { when(jdbc.queryForObject("select clock_timestamp()",OffsetDateTime.class)).thenReturn(now); }
    @Test void exactThirtyMinutesRejectsBeforeAnyUpdate() {
        clock(start.plusMinutes(30));
        assertEquals("PLATFORM_SESSION_IDLE_EXPIRED",assertThrows(PlatformSessionException.class,()->service.activity(claims)).getErrorKey());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void justBeforeIdleExpiryCanExtendButNeverPastAbsoluteDeadline() {
        clock(start.plusMinutes(30).minusNanos(1));
        assertEquals(start.plusMinutes(60).minusNanos(1),service.activity(claims).idleExpiresAt());
        row.put("idle_expires_at",start.plusHours(12)); clock(start.plusHours(12).minusNanos(1));
        assertEquals(start.plusHours(12),service.activity(claims).idleExpiresAt());
    }
    @Test void exactTwelveHoursRejectsEvenIfIdleWindowIsStillCurrent() {
        row.put("idle_expires_at",start.plusHours(12)); clock(start.plusHours(12));
        assertEquals("PLATFORM_SESSION_EXPIRED",assertThrows(PlatformSessionException.class,()->service.activity(claims)).getErrorKey());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void revokedSessionCannotBeTouched() {
        row.put("revoked_at",start.plusMinutes(1)); clock(start.plusMinutes(2));
        assertThrows(PlatformSessionException.class,()->service.activity(claims));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void legacySessionWithoutBrowserBindingFailsClosed() {
        row.remove("browser_binding_hash"); clock(start.plusMinutes(1));
        assertThrows(PlatformSessionException.class,()->service.check(claims));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void logoutDerivesScopeFromStoredSessionAndLocksIdentityBeforeUpdatingGroup() {
        when(jdbc.queryForList("select enabled,security_version from platform_users where id=? for no key update",1L))
            .thenReturn(List.of(Map.of("enabled",true,"security_version",1L)));
        clock(start.plusMinutes(1));
        when(jdbc.update(contains("browser_binding_hash=?"),eq(start.plusMinutes(1)),eq(1L),eq("a".repeat(64)))).thenReturn(2);
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Browser-Id","untrusted-group");
        service.logout(claims,request);
        var ordered=inOrder(jdbc,audit);
        ordered.verify(jdbc).queryForList("select enabled,security_version from platform_users where id=? for no key update",1L);
        ordered.verify(jdbc).queryForList("select * from platform_sessions where session_id=? for update",claims.sessionId());
        ordered.verify(jdbc).queryForObject("select clock_timestamp()",OffsetDateTime.class);
        ordered.verify(jdbc).update(contains("browser_binding_hash=?"),eq(start.plusMinutes(1)),eq(1L),eq("a".repeat(64)));
        ordered.verify(audit).recordInCurrentTransaction(1L,null,"SESSIONS_REVOKED","platform_session","SUCCESS",
            Map.of("scope","BROWSER_PLATFORM_SESSIONS","operation","BROWSER_LOGOUT","sessionsRevoked",2),request);
    }
    @Test void expiredInitiatorCannotReportBrowserGroupRevocationSucceeded() {
        when(jdbc.queryForList("select enabled,security_version from platform_users where id=? for no key update",1L))
            .thenReturn(List.of(Map.of("enabled",true,"security_version",1L)));
        clock(start.plusMinutes(30));
        assertThrows(PlatformSessionException.class,()->service.logout(claims,null));
        verify(jdbc,never()).update(anyString(),any(Object[].class)); verifyNoInteractions(audit);
    }
}
