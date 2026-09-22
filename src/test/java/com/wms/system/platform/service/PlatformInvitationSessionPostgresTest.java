package com.wms.system.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic API/real-PostgreSQL acceptance, not a user-browser authentication test.
 * Reuses the isolated fixture and guards; the closeout runner selects only invitation* methods.
 * No browser credentials, MFA seeds, tokens or recovery codes are read or logged. */
@EnabledIfSystemProperty(named = "p3.r2.database-authorized", matches = "true")
class PlatformInvitationSessionPostgresTest extends PlatformSessionPostgresTest {
    // Matches the parent fixture only; never used for A/B/C or production identities.
    private static final String FIXTURE_PASSWORD = "Isolated-Platform-Password-42!";
    private static final String ROLE = "PLATFORM_SECURITY_AUDITOR";
    record Invitation(long id, String email, String token, String challenge, String secret) {
        @Override public String toString() { return "Invitation[synthetic, credentials redacted]"; }
    }

    @BeforeEach void requireDedicatedScopeDatabase() {
        assertEquals(com.wms.platformverification.PlatformR2Isolation.MAIN, jdbc.queryForObject("select current_database()", String.class));
        assertEquals(com.wms.platformverification.PlatformR2Isolation.PORT, jdbc.queryForObject("select inet_server_port()", Integer.class));
        assertEquals("127.0.0.1", jdbc.queryForObject("select host(inet_server_addr())", String.class));
        assertEquals(com.wms.platformverification.PlatformR2Isolation.APP, jdbc.queryForObject("select current_user", String.class));
    }

    Invitation prepareInvitation() throws Exception {
        Login actor = login(user(true));
        String challenge = json.readTree(postJson("/api/platform/admins/invitations/challenge",
            Map.of(), actor.token(), 200)).path("challengeToken").asText();
        String email = UUID.randomUUID() + "@invitation-session.invalid";
        fixtures.expectInvitation(actor.user(), email);
        JsonNode invitation = json.readTree(postJson("/api/platform/admins/invitations", Map.of(
            "challengeToken", challenge, "password", FIXTURE_PASSWORD, "code", totp(),
            "email", email, "displayName", "Isolated invitation session", "reason", "Session acceptance only",
            "invitationType", "ORDINARY_ADMIN", "roleCode", ROLE), actor.token(), 200));
        String path = invitation.path("activationPath").asText();
        assertTrue(path.startsWith("/platform.html#/activate?token="), "Expected local activation path");
        String token = path.substring(path.indexOf("token=") + 6);
        assertEquals(0, userCount(email));
        JsonNode enrollment = json.readTree(postJson("/api/platform/auth/invitations/activate",
            Map.of("token", token, "password", FIXTURE_PASSWORD), null, 200));
        assertEquals("MFA_ENROLLMENT_REQUIRED", enrollment.path("status").asText());
        assertFalse(enrollment.hasNonNull("token"), "No JWT before completed invitation MFA");
        assertEquals(0, userCount(email));
        return new Invitation(invitation.path("id").asLong(), email, token,
            enrollment.path("challengeToken").asText(), enrollment.path("enrollmentSecret").asText());
    }

    Map<String, String> confirmation(Invitation invitation) throws Exception {
        return Map.of("challengeToken", invitation.challenge(), "code", totp(invitation.secret()));
    }

    JsonNode confirm(Invitation invitation) throws Exception {
        return json.readTree(postJson("/api/platform/auth/invitations/activate/confirm",
            confirmation(invitation), null, 200));
    }

    int userCount(String email) {
        return jdbc.queryForObject("select count(*) from platform_users where normalized_email=?", Integer.class, email);
    }

    Map<String, Object> transactionCounts() {
        return jdbc.queryForMap("""
            select (select count(*) from platform_users) as users,
                   (select count(*) from platform_user_roles) as roles,
                   (select count(*) from platform_sessions) as sessions,
                   (select count(*) from platform_audit_logs) as audits
            """);
    }

    void assertStillPending(Invitation invitation) {
        assertEquals(0, userCount(invitation.email()));
        assertNull(jdbc.queryForObject("select accepted_at from platform_admin_invitations where id=?",
            OffsetDateTime.class, invitation.id()));
        assertNull(jdbc.queryForObject("select consumed_at from platform_admin_invitation_activations where token_hash=?",
            OffsetDateTime.class, crypto.hashToken(invitation.challenge())));
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_admin_invitation_active_emails where invitation_id=?",
            Integer.class, invitation.id()));
    }

    @Test void invitationCreatesBoundSessionAndCannotReplayOrReviveAfterLogout() throws Exception {
        Invitation invitation = prepareInvitation();
        JsonNode response = confirm(invitation);
        assertEquals("AUTHENTICATED", response.path("status").asText());
        assertEquals(10, response.path("recoveryCodes").size());
        String token = response.path("token").asText();
        var claims = jwt.parsePlatformToken(token);
        var window = sessions.check(claims);
        assertEquals(List.of(ROLE), claims.roles());
        assertEquals(12, Duration.between(window.startedAt(), window.expiresAt()).toHours());
        assertEquals(30, Duration.between(window.startedAt(), window.idleExpiresAt()).toMinutes());
        assertTrue(jdbc.queryForObject("select browser_binding_hash ~ '^[0-9a-f]{64}$' from platform_sessions where session_id=?",
            Boolean.class, claims.sessionId()));
        assertNotNull(jdbc.queryForObject("select accepted_at from platform_admin_invitations where id=?",
            OffsetDateTime.class, invitation.id()));
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_audit_logs where platform_user_id=? and action='ADMIN_INVITATION_ACCEPTED' and result='SUCCESS'",
            Integer.class, claims.platformUserId()));
        mvc.perform(get("/api/platform/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("roles[0]").value(ROLE));
        mvc.perform(get("/api/platform/companies").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        Map<String, Object> beforeReplay = transactionCounts();
        postJson("/api/platform/auth/invitations/activate/confirm", confirmation(invitation), null, 401);
        assertEquals(beforeReplay, transactionCounts());
        assertEquals(1, userCount(invitation.email()));

        postJson("/api/platform/auth/logout", Map.of(), token, 204);
        mvc.perform(get("/api/platform/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        JsonNode loginStep = json.readTree(postJson("/api/platform/auth/login",
            Map.of("email", invitation.email(), "password", FIXTURE_PASSWORD), null, 200));
        JsonNode fresh = json.readTree(postJson("/api/platform/auth/mfa/verify",
            Map.of("challengeToken", loginStep.path("challengeToken").asText(), "code", totp(invitation.secret())), null, 200));
        var freshClaims = jwt.parsePlatformToken(fresh.path("token").asText());
        assertTrue(!claims.sessionId().equals(freshClaims.sessionId()), "Relogin must create a new session");
        assertNotNull(sessions.check(freshClaims));
        assertThrows(PlatformSessionException.class, () -> sessions.check(claims));
    }

    @Test void invitationMissingBrowserBindingRollsBackAccountActivationAndCanRetry() throws Exception {
        Invitation invitation = prepareInvitation();
        Map<String, Object> before = transactionCounts();
        mvc.perform(post("/api/platform/auth/invitations/activate/confirm").contentType("application/json")
                .content(json.writeValueAsString(confirmation(invitation))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("errorKey").value("PLATFORM_BROWSER_BINDING_REQUIRED"));
        assertEquals(before, transactionCounts());
        assertStillPending(invitation);
        assertEquals("AUTHENTICATED", confirm(invitation).path("status").asText());
    }

    @Test void invitationSessionStorageFailureRollsBackAccountMfaAndAuditTogether() throws Exception {
        Invitation invitation = prepareInvitation();
        Map<String, Object> before = transactionCounts();
        doThrow(new IllegalStateException("Synthetic invitation session persistence failure"))
            .when(sessions).createForBrowser(anyLong(), anyLong(), any());
        try {
            postJson("/api/platform/auth/invitations/activate/confirm", confirmation(invitation), null, 500);
        } finally {
            doCallRealMethod().when(sessions).createForBrowser(anyLong(), anyLong(), any());
        }
        assertEquals(before, transactionCounts());
        assertStillPending(invitation);
        assertEquals("AUTHENTICATED", confirm(invitation).path("status").asText());
    }

    @Test void invitationWrongMfaHasNoAccountOrSessionAndCorrectRetryConsumesOnce() throws Exception {
        Invitation invitation = prepareInvitation();
        String bad = "000000".equals(totp(invitation.secret())) ? "111111" : "000000";
        postJson("/api/platform/auth/invitations/activate/confirm",
            Map.of("challengeToken", invitation.challenge(), "code", bad), null, 401);
        assertStillPending(invitation);
        assertEquals(1, jdbc.queryForObject("select attempt_count from platform_admin_invitation_activations where token_hash=?",
            Integer.class, crypto.hashToken(invitation.challenge())));
        JsonNode response = confirm(invitation);
        long userId = jwt.parsePlatformToken(response.path("token").asText()).platformUserId();
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_sessions where platform_user_id=?", Integer.class, userId));
    }
}
