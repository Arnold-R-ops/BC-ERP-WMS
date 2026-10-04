package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.springframework.jdbc.core.JdbcTemplate;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Safe diagnostics and exact branch contract; no request/Throwable serialization. */
final class PlatformMfaFailureFixture {
    record Before(long enabledSuperAdmins, long pendingSuperInvitations, long allPendingInvitations,
                  long matchingActor, long matchingChallenge, int actorAttempts, int challengeAttempts) { }
    private PlatformMfaFailureFixture() { }
    static Before inspect(JdbcTemplate jdbc, long actor, long challenge) {
        long enabled=jdbc.queryForObject("select count(*) from platform_user_roles ur join platform_users u on u.id=ur.platform_user_id join platform_roles r on r.id=ur.platform_role_id where r.role_code='PLATFORM_SUPER_ADMIN' and u.enabled=true",Long.class);
        long pending=jdbc.queryForObject("select count(*) from platform_admin_invitations where role_code='PLATFORM_SUPER_ADMIN' and accepted_at is null and revoked_at is null and expires_at>clock_timestamp()",Long.class);
        long allPending=jdbc.queryForObject("select count(*) from platform_admin_invitations where accepted_at is null and revoked_at is null and expires_at>clock_timestamp()",Long.class);
        long actorMatch=jdbc.queryForObject("select count(*) from platform_users where id=? and enabled=true and mfa_enabled=true and security_version>0 and (mfa_locked_until is null or mfa_locked_until<=clock_timestamp())",Long.class,actor);
        long challengeMatch=jdbc.queryForObject("select count(*) from platform_mfa_challenges where id=? and platform_user_id=? and purpose='ADMIN_INVITE' and consumed_at is null and expires_at>clock_timestamp()",Long.class,challenge,actor);
        Integer actorAttempts=jdbc.queryForObject("select coalesce((select mfa_failed_attempts from platform_users where id=?),-1)",Integer.class,actor);
        Integer challengeAttempts=jdbc.queryForObject("select coalesce((select attempt_count from platform_mfa_challenges where id=?),-1)",Integer.class,challenge);
        return new Before(enabled,pending,allPending,actorMatch,challengeMatch,actorAttempts,challengeAttempts);
    }
    static void requireReady(Before before, Consumer<String> output) {
        output.accept("MFA_FIXTURE_PRECONDITIONS " + before);
        assertEquals(new Before(1,0,0,1,1,4,4),before,"Wrong fixture branch preconditions; do not invoke invitation creation");
    }
    static void assertCredentialFailure(Throwable failure, long actor, String challengeHash, Consumer<String> output) {
        String type=failure==null?"NONE":failure.getClass().getName();
        String key=failure instanceof BusinessException b?safeKey(b.getErrorKey()):"NOT_A_BUSINESS_EXCEPTION";
        output.accept("MFA_FIXTURE_EXCEPTION type="+type+" errorKey="+key);
        // Compare class objects/booleans, not the Throwable or params, which may contain private data.
        assertEquals(PlatformAdminInvitationMfaCredentialException.class,failure==null?null:failure.getClass());
        var actual=(PlatformAdminInvitationMfaCredentialException)failure;
        assertEquals(ErrorKeys.AUTH_FAILED,key);
        assertTrue("MFA_VERIFICATION_FAILED".equals(actual.getParam("reason")),"Wrong credential-failure reason");
        assertEquals(actor,actual.actorId());
        assertTrue(Objects.equals(challengeHash,actual.challengeTokenHash()),"Wrong challenge ownership");
    }
    private static String safeKey(String key) {
        for(var f:ErrorKeys.class.getFields()) {
            if(f.getType()!=String.class || !Modifier.isStatic(f.getModifiers())) continue;
            try { if(Objects.equals(f.get(null),key)) return key; }
            catch(IllegalAccessException ignored) { }
        }
        return "UNRECOGNIZED_ERROR_KEY";
    }
}
