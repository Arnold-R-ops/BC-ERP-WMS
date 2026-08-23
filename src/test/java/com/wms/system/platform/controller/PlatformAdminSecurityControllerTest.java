package com.wms.system.platform.controller;

import com.wms.system.controller.GlobalExceptionHandler;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.dto.PlatformAdminSecurityChallengeResponse;
import com.wms.system.platform.dto.PlatformAdminSecurityMutationResponse;
import com.wms.system.platform.service.PlatformAdminSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAdminSecurityControllerTest {
    private final PlatformAdminSecurityService service = mock(PlatformAdminSecurityService.class);
    private final MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new PlatformAdminSecurityController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void exposesBoundSessionChallengeAndIdempotentFinalCommand() throws Exception {
        when(service.startSessionRevoke(eq(8L), any()))
            .thenReturn(new PlatformAdminSecurityChallengeResponse(
                "one-time-challenge", 300_000L, 5L));

        mvc.perform(post("/api/platform/admins/8/sessions/revoke/challenge"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.challengeToken").value("one-time-challenge"))
            .andExpect(jsonPath("$.expiresIn").value(300_000))
            .andExpect(jsonPath("$.targetSecurityVersion").value(5));

        OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-22T22:00:00+08:00");
        when(service.revokeSessions(eq(8L), eq("revoke-command-1"), any(), any()))
            .thenReturn(new PlatformAdminSecurityMutationResponse(
                8L, "ADMIN_SESSIONS_REVOKED", true, true, "ENROLLED",
                List.of("HISTORICAL_UNKNOWN"), 2L, 6L, completedAt));

        mvc.perform(post("/api/platform/admins/8/sessions/revoke")
                .header("Idempotency-Key", "revoke-command-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "challengeToken":"one-time-challenge",
                      "password":"current-password",
                      "code":"123456",
                      "reason":"Compromised laptop"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetUserId").value(8))
            .andExpect(jsonPath("$.action").value("ADMIN_SESSIONS_REVOKED"))
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.mfaStatus").value("ENROLLED"))
            .andExpect(jsonPath("$.securityVersion").value(6))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.challengeToken").doesNotExist());
        verify(service).revokeSessions(eq(8L), eq("revoke-command-1"), any(), any());
    }

    @Test
    void mfaResetRequiresIdempotencyHeaderAndReturnsSafeJson() throws Exception {
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-22T22:05:00+08:00");
        when(service.resetMfa(eq(8L), eq("mfa-command-1"), any(), any()))
            .thenReturn(new PlatformAdminSecurityMutationResponse(
                8L, "MFA_RESET", true, true, "NOT_ENROLLED",
                List.of("PLATFORM_SUPER_ADMIN"), 1L, 6L, completedAt));

        mvc.perform(post("/api/platform/admins/8/mfa-reset")
                .header("Idempotency-Key", "mfa-command-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "challengeToken":"one-time-challenge",
                      "password":"current-password",
                      "code":"654321",
                      "reason":"Lost authenticator"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action").value("MFA_RESET"))
            .andExpect(jsonPath("$.mfaStatus").value("NOT_ENROLLED"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.challengeToken").doesNotExist());
    }

    @Test
    void mapsDisabledAndMfaNotEnrolledToStableConflicts() throws Exception {
        when(service.startSessionRevoke(eq(8L), any())).thenThrow(new BusinessException(
            ErrorKeys.PLATFORM_ADMIN_DISABLED, Map.of("targetUserId", 8L)));
        mvc.perform(post("/api/platform/admins/8/sessions/revoke/challenge"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorKey").value("PLATFORM_ADMIN_DISABLED"));

        when(service.startMfaReset(eq(8L), any())).thenThrow(new BusinessException(
            ErrorKeys.PLATFORM_ADMIN_MFA_NOT_ENROLLED, Map.of("targetUserId", 8L)));
        mvc.perform(post("/api/platform/admins/8/mfa-reset/challenge"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorKey").value("PLATFORM_ADMIN_MFA_NOT_ENROLLED"));
    }
}
