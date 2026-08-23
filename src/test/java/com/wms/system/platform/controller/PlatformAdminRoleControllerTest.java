package com.wms.system.platform.controller;

import com.wms.system.controller.GlobalExceptionHandler;
import com.wms.system.platform.dto.PlatformAdminRoleChangeResponse;
import com.wms.system.platform.dto.PlatformAdminStatusChallengeResponse;
import com.wms.system.platform.service.PlatformAdminRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAdminRoleControllerTest {
    private final PlatformAdminRoleService service = mock(PlatformAdminRoleService.class);
    private final MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new PlatformAdminRoleController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void bindsDesiredJobIntoChallengeAndIdempotentRoleMutation() throws Exception {
        when(service.startChallenge(eq(9L), eq("PLATFORM_SECURITY_AUDITOR"), any()))
            .thenReturn(new PlatformAdminStatusChallengeResponse("role-challenge", 300_000L, 4L));

        mvc.perform(post("/api/platform/admins/9/roles/change/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobRoleCode\":\"PLATFORM_SECURITY_AUDITOR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.challengeToken").value("role-challenge"))
            .andExpect(jsonPath("$.targetSecurityVersion").value(4));

        OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-23T08:00:00+08:00");
        when(service.changeRole(eq(9L), eq("role-command-1"), any(), any()))
            .thenReturn(new PlatformAdminRoleChangeResponse(
                9L, true, false,
                List.of("PLATFORM_OPERATIONS_ADMIN"),
                List.of("PLATFORM_SECURITY_AUDITOR"),
                0L, 5L, completedAt));

        mvc.perform(put("/api/platform/admins/9/roles")
                .header("Idempotency-Key", "role-command-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "jobRoleCode":"PLATFORM_SECURITY_AUDITOR",
                      "challengeToken":"role-challenge",
                      "password":"current-password",
                      "code":"123456",
                      "reason":"Separate duties"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetUserId").value(9))
            .andExpect(jsonPath("$.changed").value(true))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.beforeRoles[0]").value("PLATFORM_OPERATIONS_ADMIN"))
            .andExpect(jsonPath("$.afterRoles[0]").value("PLATFORM_SECURITY_AUDITOR"))
            .andExpect(jsonPath("$.securityVersion").value(5))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.challengeToken").doesNotExist());

        verify(service).startChallenge(eq(9L), eq("PLATFORM_SECURITY_AUDITOR"), any());
        verify(service).changeRole(eq(9L), eq("role-command-1"), any(), any());
    }
}
