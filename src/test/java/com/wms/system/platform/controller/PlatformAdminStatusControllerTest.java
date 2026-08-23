package com.wms.system.platform.controller;

import com.wms.system.controller.GlobalExceptionHandler;
import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import com.wms.system.platform.dto.PlatformAdminMutationResponse;
import com.wms.system.platform.dto.PlatformAdminStatusChallengeResponse;
import com.wms.system.platform.service.PlatformAdminStatusService;
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

class PlatformAdminStatusControllerTest {
    private final PlatformAdminStatusService service = mock(PlatformAdminStatusService.class);
    private final MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new PlatformAdminStatusController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void exposesBoundChallengeAndIdempotentDisableWithoutSerializingCredentials() throws Exception {
        when(service.startChallenge(eq(8L), eq(false), any()))
            .thenReturn(new PlatformAdminStatusChallengeResponse("one-time-challenge", 300_000L, 5L));

        mvc.perform(post("/api/platform/admins/8/status-change/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desiredEnabled\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.challengeToken").value("one-time-challenge"))
            .andExpect(jsonPath("$.expiresIn").value(300_000))
            .andExpect(jsonPath("$.targetSecurityVersion").value(5));
        verify(service).startChallenge(eq(8L), eq(false), any());

        OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-22T20:00:00+08:00");
        when(service.changeStatus(eq(8L), eq(false), eq("disable-command-1"), any(), any()))
            .thenReturn(new PlatformAdminMutationResponse(
                8L, "ADMIN_DISABLED", true, false,
                List.of("PLATFORM_SUPER_ADMIN"), 2L, 6L, completedAt));

        mvc.perform(post("/api/platform/admins/8/disable")
                .header("Idempotency-Key", "disable-command-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "challengeToken":"one-time-challenge",
                      "password":"current-password",
                      "code":"123456",
                      "reason":"Security incident"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetUserId").value(8))
            .andExpect(jsonPath("$.action").value("ADMIN_DISABLED"))
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.activeGrantCount").value(2))
            .andExpect(jsonPath("$.securityVersion").value(6))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.challengeToken").doesNotExist());
        verify(service).changeStatus(eq(8L), eq(false), eq("disable-command-1"), any(), any());
    }

    @Test
    void mapsLastSuperAdministratorProtectionToStableConflict() throws Exception {
        when(service.startChallenge(eq(8L), eq(false), any()))
            .thenThrow(new BusinessException(ErrorKeys.PLATFORM_ADMIN_LAST_SUPER_ADMIN,
                Map.of("targetUserId", 8L)));

        mvc.perform(post("/api/platform/admins/8/status-change/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"desiredEnabled\":false}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorKey").value("PLATFORM_ADMIN_LAST_SUPER_ADMIN"))
            .andExpect(jsonPath("$.params.targetUserId").value(8));
    }
}
